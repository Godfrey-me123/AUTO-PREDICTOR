package com.example.data.engine

import com.example.data.model.CollectionSource
import com.example.data.model.HistoryCollectionStatus
import com.example.data.model.MultiplierRecord
import com.example.data.model.TimestampSource
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.min

data class ExtractedHistoryItem(
    val multiplier: Double,
    val roundId: String? = null,
    val gameTimestampMs: Long? = null,
    val orderIndex: Int = 0,
    val source: CollectionSource = CollectionSource.VISIBLE_RIBBON
)

data class HistoryCollectionMetadata(
    val status: HistoryCollectionStatus = HistoryCollectionStatus.HISTORY_NOT_STARTED,
    val statusReason: String = "History collection has not started yet.",
    val windowMinutes: Int = 30,
    val totalCollectedInWindow: Int = 0,
    val totalVisibleInRibbon: Int = 0,
    val latestMultiplier: Double? = null,
    val latestRecordTimestampMs: Long? = null,
    val latestTimestampSource: TimestampSource? = null,
    val earliestTimestampMs: Long? = null,
    val lastCollectionAttemptMs: Long = 0L,
    val hasGapsOrPartial: Boolean = false,
    val hasExpandableHistoryControl: Boolean = false,
    val hasScrollableHistoryContainer: Boolean = false,
    val isHistoryPanelExpanded: Boolean = false
)

data class RibbonSyncResult(
    val newRecordsToPersist: List<MultiplierRecord>,
    val status: HistoryCollectionStatus,
    val reason: String,
    val isRepeatedSnapshot: Boolean
)

/**
 * Pure, deterministic ribbon & history collector/deduplicator for Section 14.
 * - Multipliers may naturally repeat; never deduplicates by multiplier value alone.
 * - Aligns visible ribbon snapshots via suffix-prefix overlap to capture newly finished rounds
 *   and detects gaps when screen refreshes or panel views jump cleanly.
 * - Preserves provenance: marks GAME_PROVIDED only when UI exposes an actual timestamp; otherwise LOCAL_OBSERVATION.
 */
class RibbonHistoryCollector {

    companion object {
        const val ROLLING_WINDOW_MINUTES = 30
        const val ROLLING_WINDOW_MS = ROLLING_WINDOW_MINUTES * 60_000L

        private val TIME_HMS_PATTERN: Pattern = Pattern.compile("\\b(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\b")
        private val ROUND_ID_PATTERN: Pattern = Pattern.compile("(?:round|#)\\s*[:#]?\\s*([a-z0-9_-]{4,24})", Pattern.CASE_INSENSITIVE)

        fun parseGameTimestampFromText(text: String?, nowMs: Long = System.currentTimeMillis()): Long? {
            if (text.isNullOrBlank()) return null
            val matcher = TIME_HMS_PATTERN.matcher(text)
            if (!matcher.find()) return null
            val hour = matcher.group(1)?.toIntOrNull() ?: return null
            val minute = matcher.group(2)?.toIntOrNull() ?: return null
            val second = matcher.group(3)?.toIntOrNull() ?: 0
            if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null

            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, second)
                set(Calendar.MILLISECOND, 0)
            }
            // Handle midnight rollover if parsed time is ahead of current time by > 5 minutes
            if (cal.timeInMillis > nowMs + 5 * 60_000L) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            return cal.timeInMillis
        }

        fun parseRoundIdFromText(text: String?): String? {
            if (text.isNullOrBlank()) return null
            val matcher = ROUND_ID_PATTERN.matcher(text)
            return if (matcher.find()) matcher.group(1) else null
        }

        fun formatMultKey(multiplier: Double): String = String.format(Locale.US, "%.2f", multiplier)

        /**
         * Aligns `current` (newest-first) against `previous` (newest-first) to return how many
         * new items appear at the head of `current`.
         * Returns null if there is no reliable overlap (disjoint refresh / first scan).
         */
        fun findShiftPrefixCount(previous: List<Double>, current: List<Double>): Int? {
            if (previous.isEmpty() || current.isEmpty()) return null
            if (current.size == previous.size &&
                current.indices.all { abs(current[it] - previous[it]) < 0.009 }
            ) {
                return 0
            }
            // Check prefix match at shift 0 when one list is just a shorter/longer snapshot of the same head
            val minLen = min(previous.size, current.size)
            if (minLen >= 2 && (0 until minLen).all { abs(current[it] - previous[it]) < 0.009 }) {
                return 0
            }

            for (shift in 1 until current.size) {
                val remainingCurrent = current.size - shift
                val maxOverlap = min(remainingCurrent, previous.size)
                val requiredOverlap = min(maxOverlap, if (previous.size >= 3 && current.size >= 3) 2 else 1)
                if (maxOverlap >= requiredOverlap && requiredOverlap >= 1) {
                    val matches = (0 until maxOverlap).all { idx ->
                        abs(current[shift + idx] - previous[idx]) < 0.009
                    }
                    if (matches) {
                        return shift
                    }
                }
            }
            return null
        }
    }

    private var lastVisibleRibbon: List<Double> = emptyList()
    private var lastPlatform: String = ""
    private var monotonicCounter: Long = 0L

    fun reset() {
        lastVisibleRibbon = emptyList()
        lastPlatform = ""
    }

    /**
     * Converts a newly observed visible ribbon (left-to-right = newest-to-oldest) into
     * deduplicated [MultiplierRecord] entries while strictly preserving repeated multipliers
     * across distinct rounds and never claiming HISTORY_COMPLETE for partial visible slices.
     */
    fun processVisibleRibbonSnapshot(
        items: List<ExtractedHistoryItem>,
        platformName: String,
        gameId: String = "aviator",
        isPanelExpanded: Boolean = false,
        hasExpandableControl: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): RibbonSyncResult {
        val validItems = items.filter { it.multiplier.isFinite() && it.multiplier >= 1.0 }
        if (validItems.isEmpty()) {
            return RibbonSyncResult(
                newRecordsToPersist = emptyList(),
                status = HistoryCollectionStatus.HISTORY_UNAVAILABLE,
                reason = "No visible game ribbon multipliers detected.",
                isRepeatedSnapshot = false
            )
        }

        val currentValues = validItems.map { it.multiplier }
        if (platformName != lastPlatform && lastPlatform.isNotBlank()) {
            lastVisibleRibbon = emptyList()
        }
        lastPlatform = platformName

        val shift = findShiftPrefixCount(lastVisibleRibbon, currentValues)
        if (shift == 0 && !isPanelExpanded) {
            return RibbonSyncResult(
                newRecordsToPersist = emptyList(),
                status = if (isPanelExpanded) HistoryCollectionStatus.COLLECTING_VISIBLE_HISTORY else HistoryCollectionStatus.HISTORY_PARTIAL,
                reason = "Visible ribbon unchanged (repeated snapshot skipped).",
                isRepeatedSnapshot = true
            )
        }

        val cleanPlatform = platformName.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")
        val newRecords = mutableListOf<MultiplierRecord>()

        if (shift != null && shift > 0) {
            // Only the newly shifted items at the front are genuinely new rounds
            val newHeadItems = validItems.take(shift)
            for (idx in newHeadItems.indices) {
                val item = newHeadItems[idx]
                // Reverse relative order offset so newest (idx=0) has highest timestamp
                val obsTs = nowMs - (idx * 50L)
                val tsSource = if (item.gameTimestampMs != null) {
                    TimestampSource.GAME_PROVIDED
                } else {
                    TimestampSource.LOCAL_OBSERVATION
                }
                val effectiveTs = item.gameTimestampMs ?: obsTs
                val batchSeq = ++monotonicCounter
                val key = when {
                    !item.roundId.isNullOrBlank() -> "${gameId}_${cleanPlatform}_rid_${item.roundId}"
                    item.gameTimestampMs != null -> "${gameId}_${cleanPlatform}_gt_${item.gameTimestampMs}_${formatMultKey(item.multiplier)}"
                    else -> "${gameId}_${cleanPlatform}_seq_${nowMs}_${batchSeq}_${idx}_${formatMultKey(item.multiplier)}"
                }
                newRecords.add(
                    MultiplierRecord(
                        gameId = gameId,
                        roundId = item.roundId,
                        multiplier = item.multiplier,
                        gameTimestampMs = item.gameTimestampMs,
                        observationTimestampMs = obsTs,
                        crashTimestamp = effectiveTs,
                        platform = platformName,
                        timestampSource = tsSource,
                        collectionSource = item.source,
                        collectionStatus = if (isPanelExpanded) {
                            HistoryCollectionStatus.COLLECTING_VISIBLE_HISTORY
                        } else {
                            HistoryCollectionStatus.HISTORY_PARTIAL
                        },
                        dedupKey = key,
                        sequenceIndex = idx,
                        hasGapBefore = false
                    )
                )
            }
        } else {
            // First scan or disjoint refresh / expanded panel items: persist all visible entries cleanly
            val hadPrevious = lastVisibleRibbon.isNotEmpty()
            for (idx in validItems.indices) {
                val item = validItems[idx]
                val obsTs = nowMs - (idx * 100L)
                val tsSource = if (item.gameTimestampMs != null) {
                    TimestampSource.GAME_PROVIDED
                } else {
                    TimestampSource.LOCAL_OBSERVATION
                }
                val effectiveTs = item.gameTimestampMs ?: obsTs
                val batchSeq = ++monotonicCounter
                val key = when {
                    !item.roundId.isNullOrBlank() -> "${gameId}_${cleanPlatform}_rid_${item.roundId}"
                    item.gameTimestampMs != null -> "${gameId}_${cleanPlatform}_gt_${item.gameTimestampMs}_${formatMultKey(item.multiplier)}"
                    else -> "${gameId}_${cleanPlatform}_gap_${nowMs}_${batchSeq}_${idx}_${formatMultKey(item.multiplier)}"
                }
                val isGapBoundary = hadPrevious && idx == validItems.lastIndex
                newRecords.add(
                    MultiplierRecord(
                        gameId = gameId,
                        roundId = item.roundId,
                        multiplier = item.multiplier,
                        gameTimestampMs = item.gameTimestampMs,
                        observationTimestampMs = obsTs,
                        crashTimestamp = effectiveTs,
                        platform = platformName,
                        timestampSource = tsSource,
                        collectionSource = item.source,
                        collectionStatus = if (isPanelExpanded) {
                            HistoryCollectionStatus.COLLECTING_VISIBLE_HISTORY
                        } else {
                            HistoryCollectionStatus.HISTORY_PARTIAL
                        },
                        dedupKey = key,
                        sequenceIndex = idx,
                        hasGapBefore = isGapBoundary
                    )
                )
            }
        }

        lastVisibleRibbon = currentValues
        val status = if (isPanelExpanded) {
            HistoryCollectionStatus.COLLECTING_VISIBLE_HISTORY
        } else {
            HistoryCollectionStatus.HISTORY_PARTIAL
        }
        val reason = when {
            isPanelExpanded -> "Collecting from expanded history panel (${validItems.size} visible)."
            hasExpandableControl -> "Collecting visible ribbon (${validItems.size} visible) — expand history panel for older rounds."
            else -> "Collected visible ribbon (${validItems.size} visible); full history panel not expanded."
        }
        return RibbonSyncResult(
            newRecordsToPersist = newRecords,
            status = status,
            reason = reason,
            isRepeatedSnapshot = false
        )
    }

    /**
     * Processes records read from an expanded/scrolled history panel.
     * Deduplicates using game timestamps or round IDs when available, or position/batch fallback.
     */
    fun processExpandedHistoryBatch(
        items: List<ExtractedHistoryItem>,
        platformName: String,
        gameId: String = "aviator",
        isScrolledExhausted: Boolean = false,
        coversFull30Minutes: Boolean = false,
        source: CollectionSource = CollectionSource.EXPANDED_PANEL,
        nowMs: Long = System.currentTimeMillis()
    ): RibbonSyncResult {
        val validItems = items.filter { it.multiplier.isFinite() && it.multiplier >= 1.0 }
        if (validItems.isEmpty()) {
            return RibbonSyncResult(
                newRecordsToPersist = emptyList(),
                status = HistoryCollectionStatus.HISTORY_PARTIAL,
                reason = "Expanded panel contained no readable multiplier history items.",
                isRepeatedSnapshot = false
            )
        }

        val cleanPlatform = platformName.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")
        val finalStatus = if (isScrolledExhausted || coversFull30Minutes) {
            HistoryCollectionStatus.HISTORY_COMPLETE
        } else {
            HistoryCollectionStatus.HISTORY_PARTIAL
        }

        val records = validItems.mapIndexed { idx, item ->
            val obsTs = nowMs - (idx * 100L)
            val tsSource = if (item.gameTimestampMs != null) {
                TimestampSource.GAME_PROVIDED
            } else {
                TimestampSource.LOCAL_OBSERVATION
            }
            val effectiveTs = item.gameTimestampMs ?: obsTs
            val batchSeq = ++monotonicCounter
            val key = when {
                !item.roundId.isNullOrBlank() -> "${gameId}_${cleanPlatform}_rid_${item.roundId}"
                item.gameTimestampMs != null -> "${gameId}_${cleanPlatform}_gt_${item.gameTimestampMs}_${formatMultKey(item.multiplier)}"
                else -> "${gameId}_${cleanPlatform}_exp_${nowMs}_${batchSeq}_${idx}_${formatMultKey(item.multiplier)}"
            }
            MultiplierRecord(
                gameId = gameId,
                roundId = item.roundId,
                multiplier = item.multiplier,
                gameTimestampMs = item.gameTimestampMs,
                observationTimestampMs = obsTs,
                crashTimestamp = effectiveTs,
                platform = platformName,
                timestampSource = tsSource,
                collectionSource = source,
                collectionStatus = finalStatus,
                dedupKey = key,
                sequenceIndex = idx,
                hasGapBefore = false
            )
        }

        val reason = if (finalStatus == HistoryCollectionStatus.HISTORY_COMPLETE) {
            "Collected ${records.size} accessible history records covering available 30-minute span."
        } else {
            "Collected ${records.size} records from accessible panel; older history still scrollable or unexposed."
        }

        return RibbonSyncResult(
            newRecordsToPersist = records,
            status = finalStatus,
            reason = reason,
            isRepeatedSnapshot = false
        )
    }
}
