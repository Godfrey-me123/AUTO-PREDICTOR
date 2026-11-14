package com.example.data.engine

import com.example.data.model.CustomPatternStrategy
import com.example.data.model.MultiplierRecord
import com.example.data.model.PatternCondition
import com.example.data.model.PatternLogicOperator
import com.example.data.model.RepeatNotificationPolicy
import com.example.data.model.StrategyNotificationType
import java.util.Locale
import kotlin.math.abs

data class PatternAlertEvent(
    val strategy: CustomPatternStrategy,
    val notificationType: StrategyNotificationType,
    val title: String,
    val message: String,
    val triggeringRecordKey: String? = null,
    val triggeringMultiplier: Double? = null,
    val occurrenceKey: String,
    val timestampMs: Long
)

data class PatternConditionProgress(
    val matchedCount: Int = 0,
    val requiredCount: Int = 1,
    val isApproaching: Boolean = false,
    val isReached: Boolean = false,
    val streakStartKey: String? = null,
    val streakStartTimestampMs: Long = 0L,
    val lastEvaluatedRecordKey: String? = null,
    val lastEvaluatedMultiplier: Double? = null
)

data class PatternEvaluationBatch(
    val updatedStrategies: List<CustomPatternStrategy>,
    val events: List<PatternAlertEvent>
)

/**
 * Chronological, non-predictive pattern detection engine for Section 15.
 * - Evaluates user-defined CustomPatternStrategy conditions in chronological order.
 * - Prevents double-counting via per-strategy lastEvaluatedRecordKey tracking.
 * - Resets consecutive streaks immediately when a breaking round occurs and emits
 *   STRATEGY_INVALIDATED when an approaching or active occurrence is broken.
 * - Uses strictly neutral factual status language (never predictive claims).
 */
class PatternDetectionEngine {

    companion object {
        fun formatMultiplier(value: Double): String = String.format(Locale.US, "%.2fx", value)
    }

    /**
     * Evaluates a single condition across a chronologically sorted list (`oldest -> newest`).
     * Uses records filtered to `condition.timeWindowMinutes` (relative to `nowMs`) when specified.
     */
    fun evaluateCondition(
        condition: PatternCondition,
        chronologicalRecords: List<MultiplierRecord>,
        nowMs: Long = System.currentTimeMillis()
    ): PatternConditionProgress {
        val scopedRecords = if (condition.timeWindowMinutes != null && condition.timeWindowMinutes > 0) {
            val cutoff = nowMs - (condition.timeWindowMinutes * 60_000L)
            chronologicalRecords.filter { it.crashTimestamp >= cutoff }
        } else {
            chronologicalRecords
        }

        if (scopedRecords.isEmpty()) {
            val req = if (condition.sequenceMultipliers.isNotEmpty()) {
                condition.sequenceMultipliers.size
            } else {
                condition.consecutiveRounds.coerceAtLeast(1)
            }
            return PatternConditionProgress(matchedCount = 0, requiredCount = req)
        }

        // Mode 1: Exact sequence pattern check against trailing rounds
        if (condition.sequenceMultipliers.isNotEmpty()) {
            val seq = condition.sequenceMultipliers
            val req = seq.size
            var longestTailMatch = 0
            var startKey: String? = null
            var startTs = 0L

            for (startIdx in scopedRecords.indices) {
                var matched = 0
                for (offset in seq.indices) {
                    val recIdx = startIdx + offset
                    if (recIdx >= scopedRecords.size) break
                    if (abs(scopedRecords[recIdx].multiplier - seq[offset]) <= 0.01) {
                        matched++
                    } else {
                        break
                    }
                }
                if (startIdx + matched == scopedRecords.size && matched > 0) {
                    longestTailMatch = matched
                    startKey = scopedRecords[startIdx].dedupKey
                    startTs = scopedRecords[startIdx].crashTimestamp
                    break
                }
            }

            val reached = longestTailMatch >= req
            val approaching = !reached && req >= 2 && longestTailMatch == req - 1
            val lastRec = scopedRecords.last()
            return PatternConditionProgress(
                matchedCount = longestTailMatch,
                requiredCount = req,
                isApproaching = approaching,
                isReached = reached,
                streakStartKey = startKey,
                streakStartTimestampMs = startTs,
                lastEvaluatedRecordKey = lastRec.dedupKey,
                lastEvaluatedMultiplier = lastRec.multiplier
            )
        }

        // Mode 2: Consecutive rounds meeting threshold comparison
        val required = condition.consecutiveRounds.coerceAtLeast(1)
        var streak = 0
        var streakStartKey: String? = null
        var streakStartTs = 0L

        for (rec in scopedRecords) {
            if (condition.comparator.matches(rec.multiplier, condition.threshold, condition.upperThreshold)) {
                if (streak == 0) {
                    streakStartKey = rec.dedupKey
                    streakStartTs = rec.crashTimestamp
                }
                streak++
            } else {
                streak = 0
                streakStartKey = null
                streakStartTs = 0L
            }
        }

        val reached = streak >= required
        val approaching = !reached && required >= 2 && streak == required - 1
        val lastRec = scopedRecords.last()
        return PatternConditionProgress(
            matchedCount = streak,
            requiredCount = required,
            isApproaching = approaching,
            isReached = reached,
            streakStartKey = streakStartKey,
            streakStartTimestampMs = streakStartTs,
            lastEvaluatedRecordKey = lastRec.dedupKey,
            lastEvaluatedMultiplier = lastRec.multiplier
        )
    }

    /**
     * Evaluates all enabled [CustomPatternStrategy] rules against the current history records.
     * Processes only records strictly newer than each strategy's `lastEvaluatedRecordKey` step-by-step
     * so every intermediate approaching -> reached -> invalidated transition is captured accurately
     * without double-counting any round.
     */
    fun evaluateAllStrategies(
        strategies: List<CustomPatternStrategy>,
        records: List<MultiplierRecord>,
        nowMs: Long = System.currentTimeMillis()
    ): PatternEvaluationBatch {
        if (strategies.isEmpty() || records.isEmpty()) {
            return PatternEvaluationBatch(updatedStrategies = strategies, events = emptyList())
        }

        val chronological = records.sortedWith(
            compareBy<MultiplierRecord>(
                { it.crashTimestamp },
                { -it.sequenceIndex },
                { it.id }
            )
        )

        val updatedStrategies = mutableListOf<CustomPatternStrategy>()
        val emittedEvents = mutableListOf<PatternAlertEvent>()

        for (strategy in strategies) {
            if (!strategy.isEnabled) {
                updatedStrategies.add(strategy)
                continue
            }

            val conditions = strategy.getConditions()
            if (conditions.isEmpty()) {
                updatedStrategies.add(strategy)
                continue
            }

            val lastIdx = if (strategy.lastEvaluatedRecordKey != null) {
                chronological.indexOfLast { it.dedupKey == strategy.lastEvaluatedRecordKey }
            } else {
                -1
            }

            // If no new records exist after lastEvaluatedRecordKey, check only optional cooldown repeat
            if (lastIdx >= chronological.lastIndex) {
                var currentStrategy = strategy
                if (strategy.currentlyReached &&
                    strategy.repeatPolicy == RepeatNotificationPolicy.COOLDOWN_MINUTES &&
                    strategy.repeatCooldownMinutes > 0 &&
                    strategy.lastNotifiedAtMs > 0L &&
                    (nowMs - strategy.lastNotifiedAtMs) >= strategy.repeatCooldownMinutes * 60_000L
                ) {
                    val latestRec = chronological.last()
                    val occKey = strategy.lastTriggeredOccurrenceKey ?: "${strategy.id}_reach_${latestRec.dedupKey}"
                    emittedEvents.add(
                        buildEvent(
                            strategy = currentStrategy,
                            type = StrategyNotificationType.STRATEGY_REACHED,
                            triggerRec = latestRec,
                            occurrenceKey = occKey,
                            progressSummary = strategy.summaryDescription(),
                            nowMs = nowMs
                        )
                    )
                    currentStrategy = currentStrategy.copy(lastNotifiedAtMs = nowMs)
                }
                updatedStrategies.add(currentStrategy)
                continue
            }

            // Replay step-by-step through newly unseen records so rapid batches (e.g. 7 then 8th) never miss milestones
            var workingStrategy = strategy
            val startIndex = (lastIdx + 1).coerceAtLeast(0)
            for (endIdx in startIndex until chronological.size) {
                val prefixSlice = chronological.subList(0, endIdx + 1)
                val latestRec = chronological[endIdx]
                val stepResult = evaluateSingleSnapshotStep(
                    strategy = workingStrategy,
                    conditions = conditions,
                    chronologicalPrefix = prefixSlice,
                    latestRecord = latestRec,
                    nowMs = nowMs
                )
                workingStrategy = stepResult.first
                emittedEvents.addAll(stepResult.second)
            }
            updatedStrategies.add(workingStrategy)
        }

        return PatternEvaluationBatch(
            updatedStrategies = updatedStrategies,
            events = emittedEvents
        )
    }

    private fun evaluateSingleSnapshotStep(
        strategy: CustomPatternStrategy,
        conditions: List<PatternCondition>,
        chronologicalPrefix: List<MultiplierRecord>,
        latestRecord: MultiplierRecord,
        nowMs: Long
    ): Pair<CustomPatternStrategy, List<PatternAlertEvent>> {
        val progresses = conditions.map { evaluateCondition(it, chronologicalPrefix, nowMs) }
        val events = mutableListOf<PatternAlertEvent>()

        val anyBroken = progresses.any { !it.isReached && !it.isApproaching }
        val allReached = progresses.all { it.isReached }
        val anyReached = progresses.any { it.isReached }
        val isReachedNow = when (strategy.logicOperator) {
            PatternLogicOperator.AND -> allReached
            PatternLogicOperator.OR -> anyReached
        }

        val isApproachingNow = when (strategy.logicOperator) {
            PatternLogicOperator.AND -> !isReachedNow &&
                progresses.any { it.isApproaching } &&
                progresses.all { it.isApproaching || it.isReached }
            PatternLogicOperator.OR -> !isReachedNow && progresses.any { it.isApproaching }
        }

        val anchorKey = progresses.firstOrNull { it.streakStartKey != null }?.streakStartKey ?: latestRecord.dedupKey
        val waitingSatisfied = if (strategy.waitingPeriodSeconds <= 0) {
            true
        } else {
            val triggerWindowStart = progresses.maxOfOrNull { it.streakStartTimestampMs } ?: latestRecord.crashTimestamp
            (nowMs - triggerWindowStart) >= strategy.waitingPeriodSeconds * 1000L
        }

        var nextStrategy = strategy.copy(lastEvaluatedRecordKey = latestRecord.dedupKey)

        when {
            isReachedNow && waitingSatisfied -> {
                val occKey = "${strategy.id}_reach_$anchorKey"
                val shouldNotifyNew = strategy.lastTriggeredOccurrenceKey != occKey
                val shouldNotifyRepeat = !shouldNotifyNew &&
                    strategy.repeatPolicy == RepeatNotificationPolicy.COOLDOWN_MINUTES &&
                    strategy.repeatCooldownMinutes > 0 &&
                    (nowMs - strategy.lastNotifiedAtMs) >= strategy.repeatCooldownMinutes * 60_000L

                if (shouldNotifyNew || shouldNotifyRepeat) {
                    val primaryProg = progresses.firstOrNull { it.isReached } ?: progresses.first()
                    val summary = if (primaryProg.requiredCount > 1) {
                        "${primaryProg.matchedCount} of ${primaryProg.requiredCount} consecutive rounds (${strategy.summaryDescription()})"
                    } else {
                        strategy.summaryDescription()
                    }
                    events.add(
                        buildEvent(
                            strategy = nextStrategy,
                            type = StrategyNotificationType.STRATEGY_REACHED,
                            triggerRec = latestRecord,
                            occurrenceKey = occKey,
                            progressSummary = summary,
                            nowMs = nowMs
                        )
                    )
                    nextStrategy = nextStrategy.copy(
                        lastTriggeredOccurrenceKey = occKey,
                        lastNotifiedAtMs = nowMs
                    )
                }
                nextStrategy = nextStrategy.copy(
                    currentlyApproaching = false,
                    currentlyReached = true
                )
            }

            isApproachingNow && strategy.approachingEnabled -> {
                val occKey = "${strategy.id}_appr_$anchorKey"
                if (strategy.lastApproachedOccurrenceKey != occKey) {
                    val primaryProg = progresses.firstOrNull { it.isApproaching } ?: progresses.first()
                    val summary = "${primaryProg.matchedCount} of ${primaryProg.requiredCount} (${strategy.summaryDescription()})"
                    events.add(
                        buildEvent(
                            strategy = nextStrategy,
                            type = StrategyNotificationType.STRATEGY_APPROACHING,
                            triggerRec = latestRecord,
                            occurrenceKey = occKey,
                            progressSummary = summary,
                            nowMs = nowMs
                        )
                    )
                    nextStrategy = nextStrategy.copy(
                        lastApproachedOccurrenceKey = occKey,
                        lastNotifiedAtMs = nowMs
                    )
                }
                nextStrategy = nextStrategy.copy(
                    currentlyApproaching = true,
                    currentlyReached = false
                )
            }

            else -> {
                val wasActiveOrApproaching = strategy.currentlyApproaching || strategy.currentlyReached
                if (wasActiveOrApproaching && (anyBroken || !isReachedNow)) {
                    val occKey = "${strategy.id}_inv_${latestRecord.dedupKey}"
                    events.add(
                        buildEvent(
                            strategy = nextStrategy,
                            type = StrategyNotificationType.STRATEGY_INVALIDATED,
                            triggerRec = latestRecord,
                            occurrenceKey = occKey,
                            progressSummary = "Condition reset after ${formatMultiplier(latestRecord.multiplier)}",
                            nowMs = nowMs
                        )
                    )
                    nextStrategy = nextStrategy.copy(lastNotifiedAtMs = nowMs)
                }
                nextStrategy = nextStrategy.copy(
                    currentlyApproaching = false,
                    currentlyReached = false
                )
            }
        }

        return nextStrategy to events
    }

    private fun buildEvent(
        strategy: CustomPatternStrategy,
        type: StrategyNotificationType,
        triggerRec: MultiplierRecord,
        occurrenceKey: String,
        progressSummary: String,
        nowMs: Long
    ): PatternAlertEvent {
        val customNote = strategy.customMessage.trim().takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
        val (title, message) = when (type) {
            StrategyNotificationType.STRATEGY_APPROACHING -> {
                "Pattern Approaching: ${strategy.name}" to
                    "PATTERN APPROACHING: $progressSummary. Latest round: ${formatMultiplier(triggerRec.multiplier)}. Current status: Monitoring.$customNote"
            }
            StrategyNotificationType.STRATEGY_REACHED -> {
                "Pattern Detected: ${strategy.name}" to
                    "PATTERN DETECTED: $progressSummary. Latest round: ${formatMultiplier(triggerRec.multiplier)}. Open the game and review the current betting opportunity.$customNote"
            }
            StrategyNotificationType.STRATEGY_INVALIDATED -> {
                "Pattern Reset: ${strategy.name}" to
                    "PATTERN INVALIDATED: ${strategy.name} — $progressSummary. Waiting for next sequence."
            }
            StrategyNotificationType.HISTORY_UNAVAILABLE -> {
                "History Unavailable: ${strategy.name}" to
                    "HISTORY UNAVAILABLE: Game ribbon history could not be read; pattern monitoring is paused until history resumes."
            }
            StrategyNotificationType.STRATEGY_PAUSED -> {
                "Strategy Paused: ${strategy.name}" to
                    "STRATEGY PAUSED: ${strategy.name} monitoring paused."
            }
        }

        return PatternAlertEvent(
            strategy = strategy,
            notificationType = type,
            title = title,
            message = message,
            triggeringRecordKey = triggerRec.dedupKey,
            triggeringMultiplier = triggerRec.multiplier,
            occurrenceKey = occurrenceKey,
            timestampMs = nowMs
        )
    }
}
