package com.example.data.model

enum class SiteButtonState(val displayLabel: String) {
    IDLE_READY_TO_BET("BET READY"),
    BET_QUEUED("QUEUED (CANCEL)"),
    CASH_OUT_AVAILABLE("CASH OUT"),
    CRASHED("CRASHED"),
    UNKNOWN("WAITING")
}

data class SessionStats(
    val sessionStartMs: Long = 0L,
    val winCount: Int = 0,
    val lossCount: Int = 0,
    val totalProfit: Double = 0.0,
    val observedCrashCount: Int = 0,
    val limitHaltReason: String? = null
)

data class GameState(
    val isDetected: Boolean = false,
    val platformName: String = "Waiting for Game...",
    val detectedUrl: String? = null,
    val status: String = "WAITING_FOR_GAME", // "WAITING_FOR_GAME", "BETTING_OPEN", "IN_FLIGHT", "CRASHED", "NO_GAME_DETECTED"
    val siteButtonState: SiteButtonState = SiteButtonState.UNKNOWN,
    val currentMultiplier: Double = 0.00,
    val consecutiveLowCount: Int = 0, // rounds < 2.00x or < target
    val postHighWaitCount: Int = 0, // rounds after > 10.00x
    val highMultiplierLossesInHour: Int = 0,
    val isBetPlaced: Boolean = false,
    val isCashOutAvailable: Boolean = false,
    val multiplierHistory: List<Double> = emptyList(),
    val recentRibbonHistory: List<Double> = emptyList(),
    val activeSessionRounds: Int = 0,
    val sessionWins: Int = 0,
    val sessionLosses: Int = 0,
    val sessionTotalProfit: Double = 0.0,
    val sessionLimitHaltReason: String? = null,
    val currentStakeLevel: Int = 1,
    val currentStake: Double = 10.0,
    val winsFetchedInSession: Int = 0,
    val targetWinsToFetch: Int = 2,
    val waitingRuleName: String? = null,
    val activeWaitRemainingMs: Long = 0L,
    val isSessionLocked: Boolean = false,
    val sessionTimeoutRemainingMs: Long = 0L,
    val activeTrigger: String? = null,
    val isStakeInputDetected: Boolean = false,
    val isDoubleBtnDetected: Boolean = false,
    val isAddBtnDetected: Boolean = false,
    val isBetBtnDetected: Boolean = false,
    val isCashOutBtnDetected: Boolean = false,
    val detectedStakeText: String? = null,
    val balanceStatus: BalanceDetectionStatus = BalanceDetectionStatus.SCANNING,
    val detectedBalance: Double? = null,
    val detectedCurrency: String? = null,
    val balanceLastUpdatedMs: Long = 0L,
    val balanceFailureReason: String? = null,
    val executionState: ActionExecutionState = ActionExecutionState.IDLE,
    val lastActionRecord: ActionExecutionRecord? = null,
    val isStandbyOutsideSchedule: Boolean = false,
    val scheduleStandbyMessage: String? = null
)
