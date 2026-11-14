package com.example.service

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.data.engine.GameDetection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GameDetectionState(
    val isGameDetected: Boolean = false,
    val platformName: String = "Unknown",
    val isReady: Boolean = false,
    val gamePhase: String = "WAITING_FOR_GAME", // "WAITING_FOR_GAME", "BETTING_OPEN", "IN_FLIGHT", "CRASHED"
    val missingElements: List<String> = emptyList(),
    val detectedMultiplier: Double? = null,
    val foregroundPackage: String = "",
    val uiTargets: DetectedUiTargets = DetectedUiTargets(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

class ScreenEarnerAccessibilityService : AccessibilityController() {

    companion object {
        private const val TAG = "ScreenEarnerA11ySvc"
        var instance: ScreenEarnerAccessibilityService? = null
            private set

        private val _connectionStatus = MutableStateFlow(AccessibilityConnectionStatus.DISCONNECTED)
        val connectionStatus: StateFlow<AccessibilityConnectionStatus> = _connectionStatus.asStateFlow()

        fun isServiceConnected(): Boolean =
            _connectionStatus.value == AccessibilityConnectionStatus.CONNECTED && instance != null

        fun markConnecting() {
            if (_connectionStatus.value != AccessibilityConnectionStatus.CONNECTED) {
                _connectionStatus.value = AccessibilityConnectionStatus.CONNECTING
            }
        }

        private val _detectionState = MutableStateFlow(GameDetectionState())
        val detectionState: StateFlow<GameDetectionState> = _detectionState.asStateFlow()
    }

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            instance = this
            _connectionStatus.value = AccessibilityConnectionStatus.CONNECTED
            Log.i(TAG, "ScreenEarnerAccessibilityService onServiceConnected: Service successfully connected and active.")
        } catch (t: Throwable) {
            _connectionStatus.value = AccessibilityConnectionStatus.ERROR
            Log.e(TAG, "Error in ScreenEarnerAccessibilityService.onServiceConnected during initialization", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return

            val packageName = try {
                event.packageName?.toString().orEmpty()
            } catch (_: Throwable) {
                ""
            }
            val ownPkg = runCatching { applicationContext?.packageName }.getOrNull()
            // Ignore transient events originating from Screen Earner's own overlay bubble/cards
            if (!ownPkg.isNullOrBlank() && packageName == ownPkg) {
                return
            }

            super.onAccessibilityEvent(event)

            val targets = detectedTargets.value
            val resolvedSite = GameDetection.resolvePlatformFromSignals(
                packageName = packageName,
                detectedUrl = targets.detectedUrl,
                pageHints = targets.pageTextHints
            )

            val isAviatorFound = resolvedSite != null ||
                packageName.contains("chrome", ignoreCase = true) ||
                packageName.contains("browser", ignoreCase = true) ||
                targets.detectedMultiplier != null ||
                targets.ribbonMultipliers.isNotEmpty() ||
                targets.isBetBtnDetected ||
                targets.isCashOutBtnDetected ||
                targets.isCancelBtnDetected ||
                targets.detectedBalance != null

            val detectedPlatform = resolvedSite ?: when {
                packageName.contains("chrome", ignoreCase = true) -> "Chrome Aviator Web"
                targets.detectedUrl?.contains("betpawa", ignoreCase = true) == true -> "BetPawa Aviator"
                targets.detectedUrl?.contains("sportybet", ignoreCase = true) == true -> "SportyBet Aviator"
                else -> "Detected Aviator Game"
            }

            val missing = mutableListOf<String>()
            if (!targets.isBetBtnDetected && !targets.isCancelBtnDetected && !targets.isCashOutBtnDetected) {
                missing.add("Bet Control Target")
            }
            if (!targets.isCashOutBtnDetected) missing.add("Cash Out Target")
            if (!targets.isStakeInputDetected && !targets.isDoubleBtnDetected && !targets.isAddBtnDetected) {
                missing.add("Stake Input / 2x / + Controls")
            }

            _detectionState.value = GameDetectionState(
                isGameDetected = isAviatorFound,
                platformName = detectedPlatform,
                isReady = isAviatorFound && (
                    targets.isBetBtnDetected ||
                        targets.isCancelBtnDetected ||
                        targets.isCashOutBtnDetected ||
                        targets.detectedMultiplier != null ||
                        targets.ribbonMultipliers.isNotEmpty()
                    ),
                gamePhase = targets.gamePhase,
                missingElements = missing,
                detectedMultiplier = targets.detectedMultiplier,
                foregroundPackage = packageName,
                uiTargets = targets,
                updatedAtMs = System.currentTimeMillis()
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Caught non-fatal exception in ScreenEarnerAccessibilityService.onAccessibilityEvent (event preserved)", t)
        }
    }

    fun performClickAtCoordinates(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null) {
        dispatchTouchGesture(x, y, onComplete)
    }

    override fun onInterrupt() {
        try {
            super.onInterrupt()
            Log.w(TAG, "ScreenEarnerAccessibilityService onInterrupt called by system.")
        } catch (t: Throwable) {
            _connectionStatus.value = AccessibilityConnectionStatus.ERROR
            Log.e(TAG, "Error in ScreenEarnerAccessibilityService.onInterrupt", t)
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        return try {
            if (instance == this) {
                instance = null
            }
            _connectionStatus.value = AccessibilityConnectionStatus.DISCONNECTED
            Log.i(TAG, "ScreenEarnerAccessibilityService onUnbind: Service unbound by system.")
            super.onUnbind(intent)
        } catch (t: Throwable) {
            _connectionStatus.value = AccessibilityConnectionStatus.ERROR
            Log.e(TAG, "Error in ScreenEarnerAccessibilityService.onUnbind", t)
            false
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            if (instance == this) {
                instance = null
            }
            _connectionStatus.value = AccessibilityConnectionStatus.DISCONNECTED
            Log.i(TAG, "ScreenEarnerAccessibilityService onDestroy: Service destroyed cleanly.")
        } catch (t: Throwable) {
            _connectionStatus.value = AccessibilityConnectionStatus.ERROR
            Log.e(TAG, "Error in ScreenEarnerAccessibilityService.onDestroy", t)
        }
    }
}
