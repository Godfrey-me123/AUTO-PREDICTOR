package com.example.data.engine

import com.example.data.model.ActionExecutionRecord
import com.example.data.model.ActionExecutionState
import com.example.data.model.BalanceDetectionStatus
import com.example.data.model.BalanceState
import com.example.data.model.CollectionSource
import com.example.data.model.GameState
import com.example.data.model.HistoryCollectionStatus
import com.example.data.model.MultiplierRecord
import com.example.data.model.SiteButtonState
import com.example.data.model.TimestampSource
import com.example.service.GameDetectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

class GameDetection {

    companion object {
        private const val RIBBON_DUPLICATE_GUARD_WINDOW_MS = 15_000L
        private const val BALANCE_STALENESS_THRESHOLD_MS = 90_000L
        private const val BALANCE_INITIAL_SCAN_TIMEOUT_MS = 6_000L

        /**
         * Identifies the exact betting site from Chrome/browser URL bar, page text/branding, and package name.
         * Distinguishes `BetPawa Aviator` (betpawa.*) vs `SportyBet Aviator` (sportybet.*).
         */
        fun resolvePlatformFromSignals(
            packageName: String,
            detectedUrl: String?,
            pageHints: List<String>
        ): String? {
            val urlLower = detectedUrl?.lowercase(Locale.US).orEmpty()
            val pkgLower = packageName.lowercase(Locale.US)
            val hintsLower = pageHints.joinToString(" ").lowercase(Locale.US)

            return when {
                urlLower.contains("betpawa") -> "BetPawa Aviator"
                urlLower.contains("sportybet") -> "SportyBet Aviator"
                pkgLower.contains("betpawa") || hintsLower.contains("betpawa") -> "BetPawa Aviator"
                pkgLower.contains("sportybet") || hintsLower.contains("sportybet") -> "SportyBet Aviator"
                (pkgLower.contains("chrome") || pkgLower.contains("browser")) && hintsLower.contains("aviator") ->
                    "Chrome Aviator Web"
                else -> null
            }
        }

        fun resolveProfileIdForPlatform(platformName: String): String? {
            val lower = platformName.lowercase(Locale.US)
            return when {
                lower.contains("betpawa") -> "betpawa_portrait"
                lower.contains("sportybet") -> "sportybet_portrait"
                else -> null
            }
        }

        fun resolveSiteKey(platformName: String): String {
            val lower = platformName.lowercase(Locale.US)
            return when {
                lower.contains("betpawa") -> "BetPawa"
                lower.contains("sportybet") -> "SportyBet"
                else -> ""
            }
        }

        /**
         * Detects when the official top site history ribbon shifts left-to-right because a new round finished.
         * Returns the new leftmost (newest) completed round multiplier, or null if unchanged.
         */
        fun detectNewRibbonHead(
            previousRibbon: List<Double>,
            currentRibbon: List<Double>,
            sawInFlightOrCrashSinceSync: Boolean = false
        ): Double? {
            if (currentRibbon.isEmpty() || previousRibbon.isEmpty()) return null
            val newHead = currentRibbon.first()
            val prevHead = previousRibbon.first()

            if (abs(newHead - prevHead) > 0.009) {
                return newHead
            }

            // Handle consecutive identical crash multipliers (e.g., 2.00x followed by 2.00x) when shifted right
            if (sawInFlightOrCrashSinceSync && currentRibbon.size >= 2 && previousRibbon.size >= 2) {
                val overlap = min(3, min(currentRibbon.size - 1, previousRibbon.size))
                val shifted = currentRibbon.drop(1).take(overlap) == previousRibbon.take(overlap)
                if (shifted) {
                    return newHead
                }
            }
            return null
        }
    }

    private val _gameState = MutableStateFlow(
        GameState(
            isDetected = false,
            platformName = "Waiting for Game...",
            status = "WAITING_FOR_GAME",
            siteButtonState = SiteButtonState.UNKNOWN,
            currentMultiplier = 0.0,
            multiplierHistory = emptyList(),
            balanceStatus = BalanceDetectionStatus.SCANNING
        )
    )
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _balanceState = MutableStateFlow(
        BalanceState(status = BalanceDetectionStatus.SCANNING)
    )
    val balanceState: StateFlow<BalanceState> = _balanceState.asStateFlow()

    private val _historyMetadata = MutableStateFlow(HistoryCollectionMetadata())
    val historyMetadata: StateFlow<HistoryCollectionMetadata> = _historyMetadata.asStateFlow()

    val ribbonHistoryCollector = RibbonHistoryCollector()

    private var scanStartTimeMs: Long = 0L
    private var lastInFlightMultiplier: Double? = null
    private var hasSettledCurrentCrash: Boolean = false
    private var lastSettledRoundValue: Double? = null
    private var lastSettledTimestampMs: Long = 0L
    private var lastObservedRibbon: List<Double> = emptyList()
    private var sawActiveRoundSinceRibbonSync: Boolean = false

    fun updateHistoryStatus(
        status: HistoryCollectionStatus,
        reason: String,
        recordsInWindow: List<MultiplierRecord> = emptyList(),
        nowMs: Long = System.currentTimeMillis()
    ) {
        val current = _historyMetadata.value
        val sortedDesc = recordsInWindow.sortedByDescending { it.crashTimestamp }
        val latest = sortedDesc.firstOrNull()
        val earliest = sortedDesc.lastOrNull()
        _historyMetadata.value = current.copy(
            status = status,
            statusReason = reason,
            totalCollectedInWindow = if (recordsInWindow.isNotEmpty()) recordsInWindow.size else current.totalCollectedInWindow,
            latestMultiplier = latest?.multiplier ?: current.latestMultiplier,
            latestRecordTimestampMs = latest?.crashTimestamp ?: current.latestRecordTimestampMs,
            latestTimestampSource = latest?.timestampSource ?: current.latestTimestampSource,
            earliestTimestampMs = earliest?.crashTimestamp ?: current.earliestTimestampMs,
            lastCollectionAttemptMs = nowMs,
            hasGapsOrPartial = status != HistoryCollectionStatus.HISTORY_COMPLETE ||
                recordsInWindow.any { it.hasGapBefore }
        )
    }

    fun refreshHistoryMetadataFromRecords(
        recordsIn30mWindow: List<MultiplierRecord>,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val current = _historyMetadata.value
        val sortedDesc = recordsIn30mWindow.sortedByDescending { it.crashTimestamp }
        val latest = sortedDesc.firstOrNull()
        val earliest = sortedDesc.lastOrNull()
        _historyMetadata.value = current.copy(
            totalCollectedInWindow = recordsIn30mWindow.size,
            latestMultiplier = latest?.multiplier ?: current.latestMultiplier,
            latestRecordTimestampMs = latest?.crashTimestamp ?: current.latestRecordTimestampMs,
            latestTimestampSource = latest?.timestampSource ?: current.latestTimestampSource,
            earliestTimestampMs = earliest?.crashTimestamp ?: current.earliestTimestampMs,
            hasGapsOrPartial = current.status != HistoryCollectionStatus.HISTORY_COMPLETE ||
                recordsIn30mWindow.any { it.hasGapBefore }
        )
    }

    /**
     * Collects deduplicated MultiplierRecord entries from the visible ribbon / expanded panel
     * detected in `detection`, avoiding double-saving a round just recorded by CRASHED.
     */
    fun collectVisibleRibbonHistory(
        detection: GameDetectionState,
        skipHeadIfJustSettled: Boolean = false,
        currentTimeMs: Long = System.currentTimeMillis()
    ): List<MultiplierRecord> {
        val targets = detection.uiTargets
        val items = if (targets.ribbonItems.isNotEmpty()) {
            targets.ribbonItems
        } else {
            targets.ribbonMultipliers.mapIndexed { idx, value ->
                ExtractedHistoryItem(multiplier = value, orderIndex = idx, source = CollectionSource.VISIBLE_RIBBON)
            }
        }

        if (!detection.isGameDetected || items.isEmpty()) {
            _historyMetadata.value = _historyMetadata.value.copy(
                status = if (!detection.isGameDetected) HistoryCollectionStatus.HISTORY_NOT_STARTED else HistoryCollectionStatus.HISTORY_UNAVAILABLE,
                statusReason = if (!detection.isGameDetected) {
                    "Waiting for Aviator game window."
                } else {
                    "No visible ribbon multipliers in game interface."
                },
                totalVisibleInRibbon = 0,
                hasExpandableHistoryControl = targets.hasExpandableHistoryControl,
                hasScrollableHistoryContainer = targets.hasScrollableHistoryContainer,
                isHistoryPanelExpanded = targets.historyPanelExpanded
            )
            return emptyList()
        }

        val resolvedPlatform = resolvePlatformFromSignals(
            packageName = detection.foregroundPackage,
            detectedUrl = targets.detectedUrl,
            pageHints = targets.pageTextHints
        ) ?: detection.platformName

        val syncResult = ribbonHistoryCollector.processVisibleRibbonSnapshot(
            items = items,
            platformName = resolvedPlatform,
            isPanelExpanded = targets.historyPanelExpanded,
            hasExpandableControl = targets.hasExpandableHistoryControl,
            nowMs = currentTimeMs
        )

        val filteredRecords = if (skipHeadIfJustSettled &&
            syncResult.newRecordsToPersist.size == 1 &&
            lastSettledRoundValue != null &&
            abs(lastSettledRoundValue!! - syncResult.newRecordsToPersist.first().multiplier) < 0.01 &&
            (currentTimeMs - lastSettledTimestampMs) in 0L..RIBBON_DUPLICATE_GUARD_WINDOW_MS
        ) {
            emptyList()
        } else {
            syncResult.newRecordsToPersist
        }

        val currentMeta = _historyMetadata.value
        _historyMetadata.value = currentMeta.copy(
            status = if (currentMeta.status == HistoryCollectionStatus.HISTORY_COMPLETE && syncResult.isRepeatedSnapshot) {
                HistoryCollectionStatus.HISTORY_COMPLETE
            } else {
                syncResult.status
            },
            statusReason = syncResult.reason,
            totalVisibleInRibbon = items.size,
            lastCollectionAttemptMs = currentTimeMs,
            hasGapsOrPartial = syncResult.status != HistoryCollectionStatus.HISTORY_COMPLETE,
            hasExpandableHistoryControl = targets.hasExpandableHistoryControl,
            hasScrollableHistoryContainer = targets.hasScrollableHistoryContainer,
            isHistoryPanelExpanded = targets.historyPanelExpanded
        )

        return filteredRecords
    }

    fun retryBalanceDetection(currentTimeMs: Long = System.currentTimeMillis()) {
        scanStartTimeMs = currentTimeMs
        val resetState = BalanceState(
            status = BalanceDetectionStatus.SCANNING,
            amount = null,
            currency = _gameState.value.detectedCurrency,
            lastReadTimestampMs = 0L,
            failureReason = null
        )
        _balanceState.value = resetState
        _gameState.value = _gameState.value.copy(
            detectedBalance = null,
            balanceStatus = BalanceDetectionStatus.SCANNING,
            balanceFailureReason = null
        )
    }

    fun setVerifiedBalance(
        balance: Double,
        currency: String = "GHS",
        currentTimeMs: Long = System.currentTimeMillis()
    ) {
        val state = BalanceState(
            status = BalanceDetectionStatus.VERIFIED,
            amount = balance,
            currency = currency,
            lastReadTimestampMs = currentTimeMs,
            failureReason = null
        )
        _balanceState.value = state
        _gameState.value = _gameState.value.copy(
            detectedBalance = balance,
            detectedCurrency = currency,
            balanceStatus = BalanceDetectionStatus.VERIFIED,
            balanceLastUpdatedMs = currentTimeMs,
            balanceFailureReason = null
        )
    }

    fun setUnavailableBalance(reason: String = "Balance not visible on screen") {
        val state = BalanceState(
            status = BalanceDetectionStatus.UNAVAILABLE,
            amount = null,
            currency = _gameState.value.detectedCurrency,
            lastReadTimestampMs = 0L,
            failureReason = reason
        )
        _balanceState.value = state
        _gameState.value = _gameState.value.copy(
            detectedBalance = null,
            balanceStatus = BalanceDetectionStatus.UNAVAILABLE,
            balanceFailureReason = reason
        )
    }

    fun checkBalanceStaleness(
        currentTimeMs: Long = System.currentTimeMillis(),
        stalenessThresholdMs: Long = BALANCE_STALENESS_THRESHOLD_MS
    ) {
        val curr = _balanceState.value
        if (curr.status == BalanceDetectionStatus.VERIFIED && curr.lastUpdatedMs > 0L) {
            if (currentTimeMs - curr.lastUpdatedMs > stalenessThresholdMs) {
                val stale = curr.copy(
                    status = BalanceDetectionStatus.STALE,
                    failureReason = "Balance reading is older than ${stalenessThresholdMs / 1000}s; refresh game page"
                )
                _balanceState.value = stale
                _gameState.value = _gameState.value.copy(
                    balanceStatus = BalanceDetectionStatus.STALE,
                    balanceFailureReason = stale.failureReason
                )
            }
        }
    }

    fun updateExecutionRecord(record: ActionExecutionRecord) {
        _gameState.value = _gameState.value.copy(
            executionState = record.state,
            lastActionRecord = record
        )
    }

    /**
     * Updates real-time game state strictly from ScreenEarnerAccessibilityService node reading.
     * Distinguishes BetPawa vs SportyBet via URL/DOM hints, separates the top history ribbon from the
     * live center multiplier, tracks real site button state (`Bet` / `Cancel` / `Cash Out` / `Flew Away`),
     * and records every finished round into history via both `CRASHED` transitions and ribbon shifts.
     */
    fun updateFromAccessibilityDetection(
        detection: GameDetectionState,
        onRealRoundCompleted: (Double) -> Unit,
        onPhaseUpdate: ((phase: String, liveMultiplier: Double) -> Unit)? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ) {
        val current = _gameState.value

        if (!detection.isGameDetected) {
            scanStartTimeMs = 0L
            val unavailableState = BalanceState(
                status = BalanceDetectionStatus.UNAVAILABLE,
                amount = null,
                currency = null,
                lastReadTimestampMs = current.balanceLastUpdatedMs,
                failureReason = "No supported game detected"
            )
            _balanceState.value = unavailableState
            _gameState.value = current.copy(
                isDetected = false,
                platformName = "Waiting for Game...",
                detectedUrl = null,
                status = "NO_GAME_DETECTED",
                siteButtonState = SiteButtonState.UNKNOWN,
                isStakeInputDetected = false,
                isDoubleBtnDetected = false,
                isAddBtnDetected = false,
                isBetBtnDetected = false,
                isCashOutBtnDetected = false,
                detectedStakeText = null,
                detectedBalance = null,
                balanceStatus = BalanceDetectionStatus.UNAVAILABLE,
                balanceFailureReason = "No supported game detected"
            )
            return
        }

        if (scanStartTimeMs == 0L) {
            scanStartTimeMs = currentTimeMs
        }

        val targets = detection.uiTargets
        val resolvedPlatform = resolvePlatformFromSignals(
            packageName = detection.foregroundPackage,
            detectedUrl = targets.detectedUrl,
            pageHints = targets.pageTextHints
        ) ?: detection.platformName

        val multiplier = detection.detectedMultiplier
        val phase = detection.gamePhase
        var updatedHistory = current.multiplierHistory

        fun recordCompletedRound(crashedValue: Double) {
            hasSettledCurrentCrash = true
            lastSettledRoundValue = crashedValue
            lastSettledTimestampMs = currentTimeMs
            sawActiveRoundSinceRibbonSync = false
            updatedHistory = (listOf(crashedValue) + updatedHistory).take(50)
            onRealRoundCompleted(crashedValue)
        }

        when (phase) {
            "IN_FLIGHT" -> {
                hasSettledCurrentCrash = false
                sawActiveRoundSinceRibbonSync = true
                if (multiplier != null && multiplier >= 1.0) {
                    lastInFlightMultiplier = multiplier
                }
            }
            "CRASHED" -> {
                sawActiveRoundSinceRibbonSync = true
                val crashedValue = multiplier ?: lastInFlightMultiplier
                if (!hasSettledCurrentCrash && crashedValue != null && crashedValue >= 1.0) {
                    recordCompletedRound(crashedValue)
                }
                lastInFlightMultiplier = null
            }
            "BETTING_OPEN" -> {
                // Fallback if "Flew Away" frame was skipped between IN_FLIGHT and BETTING_OPEN
                val prev = lastInFlightMultiplier
                if (!hasSettledCurrentCrash && prev != null && prev >= 1.0) {
                    recordCompletedRound(prev)
                }
                lastInFlightMultiplier = null
            }
        }

        // Also sync directly with the official top history ribbon so leftmost new crashes are never missed
        val incomingRibbon = targets.ribbonMultipliers
        if (incomingRibbon.isNotEmpty()) {
            val newRibbonHead = detectNewRibbonHead(
                previousRibbon = lastObservedRibbon,
                currentRibbon = incomingRibbon,
                sawInFlightOrCrashSinceSync = sawActiveRoundSinceRibbonSync
            )
            if (newRibbonHead != null && newRibbonHead >= 1.0) {
                val alreadyRecordedByCrash = lastSettledRoundValue != null &&
                    abs(lastSettledRoundValue!! - newRibbonHead) < 0.01 &&
                    (currentTimeMs - lastSettledTimestampMs) in 0L..RIBBON_DUPLICATE_GUARD_WINDOW_MS
                if (!alreadyRecordedByCrash) {
                    recordCompletedRound(newRibbonHead)
                } else {
                    sawActiveRoundSinceRibbonSync = false
                }
            }
            lastObservedRibbon = incomingRibbon
        }

        val displayMultiplier = when (phase) {
            "IN_FLIGHT" -> multiplier ?: current.currentMultiplier
            "CRASHED" -> multiplier ?: lastSettledRoundValue ?: lastInFlightMultiplier ?: current.currentMultiplier
            else -> multiplier ?: 0.0
        }

        // Sync live balance + auto-detected currency with explicit states
        val incomingBalance = targets.detectedBalance
        val incomingCurrency = targets.detectedCurrency ?: current.detectedCurrency

        val resolution = when {
            incomingBalance != null && incomingBalance.isFinite() && incomingBalance > 0.0 -> {
                BalanceResolution(incomingBalance, BalanceDetectionStatus.VERIFIED, currentTimeMs, null)
            }
            current.detectedBalance != null && current.balanceLastUpdatedMs > 0L -> {
                val age = currentTimeMs - current.balanceLastUpdatedMs
                if (age > BALANCE_STALENESS_THRESHOLD_MS) {
                    BalanceResolution(
                        current.detectedBalance,
                        BalanceDetectionStatus.STALE,
                        current.balanceLastUpdatedMs,
                        "Balance reading is older than 90s; refresh game page"
                    )
                } else {
                    BalanceResolution(
                        current.detectedBalance,
                        current.balanceStatus,
                        current.balanceLastUpdatedMs,
                        current.balanceFailureReason
                    )
                }
            }
            currentTimeMs - scanStartTimeMs > BALANCE_INITIAL_SCAN_TIMEOUT_MS -> {
                BalanceResolution(null, BalanceDetectionStatus.UNAVAILABLE, 0L, "Balance element not visible in game interface")
            }
            else -> {
                BalanceResolution(null, BalanceDetectionStatus.SCANNING, 0L, null)
            }
        }

        val updatedBalanceState = BalanceState(
            status = resolution.status,
            amount = resolution.balance,
            currency = incomingCurrency,
            lastReadTimestampMs = resolution.lastUpdatedMs,
            failureReason = resolution.failureReason
        )
        _balanceState.value = updatedBalanceState

        val nextState = current.copy(
            isDetected = true,
            platformName = resolvedPlatform,
            detectedUrl = targets.detectedUrl ?: current.detectedUrl,
            status = phase,
            siteButtonState = targets.siteButtonState,
            currentMultiplier = displayMultiplier,
            multiplierHistory = updatedHistory,
            recentRibbonHistory = incomingRibbon.ifEmpty { current.recentRibbonHistory },
            isStakeInputDetected = targets.isStakeInputDetected,
            isDoubleBtnDetected = targets.isDoubleBtnDetected,
            isAddBtnDetected = targets.isAddBtnDetected,
            isBetBtnDetected = targets.isBetBtnDetected,
            isCashOutBtnDetected = targets.isCashOutBtnDetected,
            isCashOutAvailable = phase == "IN_FLIGHT" &&
                (targets.siteButtonState == SiteButtonState.CASH_OUT_AVAILABLE || targets.isCashOutBtnDetected),
            detectedStakeText = targets.detectedStakeText,
            detectedBalance = resolution.balance,
            detectedCurrency = incomingCurrency,
            balanceStatus = resolution.status,
            balanceLastUpdatedMs = resolution.lastUpdatedMs,
            balanceFailureReason = resolution.failureReason
        )
        _gameState.value = nextState

        onPhaseUpdate?.invoke(phase, displayMultiplier)
    }

    fun updateScheduleStandby(isStandby: Boolean, standbyMessage: String?) {
        val current = _gameState.value
        if (current.isStandbyOutsideSchedule != isStandby || current.scheduleStandbyMessage != standbyMessage) {
            _gameState.value = current.copy(
                isStandbyOutsideSchedule = isStandby,
                scheduleStandbyMessage = standbyMessage
            )
        }
    }

    fun setBetPlaced(placed: Boolean) {
        _gameState.value = _gameState.value.copy(isBetPlaced = placed)
    }

    fun setCashOutAvailable(available: Boolean) {
        _gameState.value = _gameState.value.copy(isCashOutAvailable = available)
    }

    fun updateStrategyTelemetry(strategyState: StrategyState, activeTriggerLabel: String?) {
        _gameState.value = _gameState.value.copy(
            consecutiveLowCount = strategyState.consecutiveLowCount,
            postHighWaitCount = strategyState.postHighWaitCount,
            highMultiplierLossesInHour = strategyState.highMultiplierLossesInHour,
            currentStakeLevel = strategyState.currentStakeLevel,
            currentStake = strategyState.currentCalculatedStake,
            winsFetchedInSession = strategyState.consecutiveWinsInSession,
            targetWinsToFetch = strategyState.targetWinsToFetch,
            waitingRuleName = strategyState.pendingWaitRuleLabel ?: strategyState.pendingWaitRuleId?.displayName,
            activeWaitRemainingMs = strategyState.activeWaitRemainingMs,
            isSessionLocked = strategyState.isBettingSessionActive,
            sessionTimeoutRemainingMs = strategyState.sessionTimeoutRemainingMs,
            activeTrigger = activeTriggerLabel
        )
    }

    fun setTelemetryCounts(consecutiveLow: Int, postHighWait: Int, activeTrigger: String?) {
        _gameState.value = _gameState.value.copy(
            consecutiveLowCount = consecutiveLow,
            postHighWaitCount = postHighWait,
            activeTrigger = activeTrigger
        )
    }

    fun updateSessionStats(stats: com.example.data.model.SessionStats) {
        _gameState.value = _gameState.value.copy(
            sessionWins = stats.winCount,
            sessionLosses = stats.lossCount,
            sessionTotalProfit = stats.totalProfit,
            sessionLimitHaltReason = stats.limitHaltReason
        )
    }
}

private data class BalanceResolution(
    val balance: Double?,
    val status: BalanceDetectionStatus,
    val lastUpdatedMs: Long,
    val failureReason: String?
)
