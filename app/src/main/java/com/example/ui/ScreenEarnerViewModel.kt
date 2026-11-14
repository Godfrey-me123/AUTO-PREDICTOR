package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.data.database.ScreenEarnerDatabase
import com.example.data.database.ScreenEarnerRepository
import com.example.data.engine.AutomationState
import com.example.data.engine.HistoryCollectionMetadata
import com.example.data.model.CustomPatternStrategy
import com.example.data.model.FloatingShortcut
import com.example.data.model.GameState
import com.example.data.model.MultiplierRecord
import com.example.data.model.NotificationHistoryRecord
import com.example.data.model.NotificationInteractionStatus
import com.example.data.model.NotificationSettings
import com.example.data.model.PlatformProfile
import com.example.data.model.RiskLimits
import com.example.data.model.SessionLog
import com.example.data.model.ShortcutType
import com.example.data.model.StrategyConfig
import com.example.service.AutomationController
import com.example.service.ScreenEarnerAccessibilityService
import com.example.service.ScreenEarnerOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScreenEarnerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ScreenEarnerViewModel"
    }

    private val dao = ScreenEarnerDatabase.getDatabase(application).dao()
    val repository = ScreenEarnerRepository(dao)

    init {
        AutomationController.ensureInitialized(application)
        AutomationController.initialize(repository, application)
    }

    val automationEngine = AutomationController.engine!!
    val strategyEngine = AutomationController.engine!!.strategyEngine
    val riskManager = AutomationController.engine!!.riskManager
    val gameDetection = AutomationController.engine!!.gameDetection

    val shortcuts: StateFlow<List<FloatingShortcut>> = repository.shortcuts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val profiles: StateFlow<List<PlatformProfile>> = repository.profiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val strategyConfig: StateFlow<StrategyConfig?> = repository.strategyConfig.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StrategyConfig()
    )

    val riskLimits: StateFlow<RiskLimits?> = repository.riskLimits.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RiskLimits()
    )

    val sessionLogs: StateFlow<List<SessionLog>> = repository.sessionLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val multiplierRecords: StateFlow<List<MultiplierRecord>> = repository.multiplierRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val customStrategies: StateFlow<List<CustomPatternStrategy>> = repository.customStrategies.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val notificationSettings: StateFlow<NotificationSettings?> = repository.notificationSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationSettings()
    )

    val notificationHistory: StateFlow<List<NotificationHistoryRecord>> = repository.notificationHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val gameState: StateFlow<GameState> = gameDetection.gameState
    val historyMetadata: StateFlow<HistoryCollectionMetadata> = gameDetection.historyMetadata
    val automationState: StateFlow<AutomationState> = automationEngine.automationState

    private val _selectedTab = MutableStateFlow(MainActivity.TAB_HOME)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _previousTab = MutableStateFlow(MainActivity.TAB_HOME)
    val previousTab: StateFlow<Int> = _previousTab.asStateFlow()

    private val _isOverlayServiceRunning = MutableStateFlow(ScreenEarnerOverlayService.isRunning)
    val isOverlayServiceRunning: StateFlow<Boolean> = _isOverlayServiceRunning.asStateFlow()

    private val _isEditMode = MutableStateFlow(true)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _isPanelExpanded = MutableStateFlow(false)
    val isPanelExpanded: StateFlow<Boolean> = _isPanelExpanded.asStateFlow()

    private val _isCalibratingTarget = MutableStateFlow(false)
    val isCalibratingTarget: StateFlow<Boolean> = _isCalibratingTarget.asStateFlow()

    private val _calibratingShortcutId = MutableStateFlow<Long?>(null)
    val calibratingShortcutId: StateFlow<Long?> = _calibratingShortcutId.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun refreshOverlayRunningState() {
        _isOverlayServiceRunning.value = ScreenEarnerOverlayService.isRunning
    }

    fun selectTab(index: Int) {
        val current = _selectedTab.value
        val coerced = index.coerceIn(MainActivity.TAB_HOME, MainActivity.TAB_SETTINGS)
        if (coerced != current) {
            if (current == MainActivity.TAB_HOME || current == MainActivity.TAB_OVERLAY ||
                current == MainActivity.TAB_PROFILES || current == MainActivity.TAB_LOGS
            ) {
                _previousTab.value = current
            }
            _selectedTab.value = coerced
        }
    }

    /**
     * Section 13 Save & Close behavior: closes ONLY the current editor tab and returns
     * to the previous in-app screen (or Home), keeping Screen Earner running cleanly.
     */
    fun closeEditorScreen() {
        val current = _selectedTab.value
        val candidatePrev = _previousTab.value
        val target = if (candidatePrev != current &&
            candidatePrev != MainActivity.TAB_STRATEGY &&
            candidatePrev != MainActivity.TAB_RISK &&
            candidatePrev != MainActivity.TAB_SETTINGS
        ) {
            candidatePrev
        } else {
            MainActivity.TAB_HOME
        }
        _selectedTab.value = target
    }

    fun setEditMode(edit: Boolean) {
        _isEditMode.value = edit
    }

    fun togglePanelExpanded() {
        _isPanelExpanded.value = !_isPanelExpanded.value
    }

    fun startCalibration(shortcutId: Long) {
        _calibratingShortcutId.value = shortcutId
        _isCalibratingTarget.value = true
        _userMessage.value = "Calibration Mode Active: Set coordinates via Floating Hand or tap position."
    }

    fun setCalibratedTarget(x: Float, y: Float) {
        val targetId = _calibratingShortcutId.value ?: return
        if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f) {
            _userMessage.value = "Failed to save settings, please check input"
            _isCalibratingTarget.value = false
            _calibratingShortcutId.value = null
            return
        }
        viewModelScope.launch {
            try {
                val list = shortcuts.value
                val item = list.find { it.id == targetId }
                if (item != null) {
                    repository.updateShortcut(
                        item.copy(
                            targetX = x,
                            targetY = y,
                            isCalibrated = true
                        )
                    )
                    _userMessage.value = "Target calibrated at (${x.toInt()}, ${y.toInt()})"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save failed while calibrating target", e)
                _userMessage.value = "Failed to save settings, please check input"
            } finally {
                _isCalibratingTarget.value = false
                _calibratingShortcutId.value = null
            }
        }
    }

    fun updateShortcutVisualPosition(shortcutId: Long, visualX: Float, visualY: Float) {
        if (!visualX.isFinite() || !visualY.isFinite()) return
        viewModelScope.launch {
            try {
                val item = shortcuts.value.find { it.id == shortcutId }
                if (item != null) {
                    repository.updateShortcut(item.copy(visualX = visualX, visualY = visualY))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save failed while updating shortcut position", e)
            }
        }
    }

    fun addNewShortcut(label: String, type: ShortcutType, colorHex: String) {
        viewModelScope.launch {
            try {
                repository.insertShortcut(
                    FloatingShortcut(
                        label = label,
                        type = type,
                        panelId = 1,
                        visualX = 120f + (shortcuts.value.size * 30),
                        visualY = 350f,
                        backgroundColorHex = colorHex
                    )
                )
                _userMessage.value = "Created shortcut '$label'"
            } catch (e: Exception) {
                Log.e(TAG, "Save failed while adding shortcut", e)
                _userMessage.value = "Failed to save settings, please check input"
            }
        }
    }

    fun updateShortcut(shortcut: FloatingShortcut) {
        viewModelScope.launch {
            try {
                val sanitized = shortcut.copy(
                    stakeAmount = shortcut.stakeAmount.takeIf { it.isFinite() && it > 0.0 } ?: 10.0,
                    targetMultiplier = shortcut.targetMultiplier.takeIf { it.isFinite() && it >= 1.01 } ?: 2.0,
                    panelId = shortcut.panelId.coerceIn(1, 2),
                    visualX = shortcut.visualX.takeIf { it.isFinite() } ?: 120f,
                    visualY = shortcut.visualY.takeIf { it.isFinite() } ?: 350f,
                    targetX = shortcut.targetX.takeIf { it.isFinite() && it >= 0f } ?: 0f,
                    targetY = shortcut.targetY.takeIf { it.isFinite() && it >= 0f } ?: 0f,
                    targetPlatform = shortcut.targetPlatform.ifBlank { "BetPawa" }
                )
                repository.updateShortcut(sanitized)
                _userMessage.value = "Updated task robot settings for '${sanitized.label}'"
            } catch (e: Exception) {
                Log.e(TAG, "Save failed while updating shortcut", e)
                _userMessage.value = "Failed to save settings, please check input"
            }
        }
    }

    fun deleteShortcut(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteShortcut(id)
                _userMessage.value = "Shortcut deleted"
            } catch (e: Exception) {
                Log.e(TAG, "Delete shortcut failed", e)
                _userMessage.value = "Failed to save settings, please check input"
            }
        }
    }

    fun startAutomation() {
        try {
            val config = strategyConfig.value ?: StrategyConfig()
            val limits = riskLimits.value ?: RiskLimits()
            val result = automationEngine.startAutomationWithValidation(
                config = config,
                limits = limits,
                isAccessibilityConnected = ScreenEarnerAccessibilityService.isServiceConnected()
            )
            if (result.started) {
                _isEditMode.value = false
                _userMessage.value = "Automation Mode STARTED"
            } else {
                _userMessage.value = result.message
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start automation", e)
            _userMessage.value = "Failed to start automation"
        }
    }

    fun pauseAutomation() {
        try {
            automationEngine.pauseAutomation()
            _userMessage.value = "Automation PAUSED"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause automation", e)
        }
    }

    fun resumeAutomation() {
        try {
            val config = strategyConfig.value ?: StrategyConfig()
            val limits = riskLimits.value ?: RiskLimits()
            automationEngine.resumeAutomation(config, limits)
            _userMessage.value = "Automation RESUMED"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume automation", e)
        }
    }

    fun quitAutomation() {
        try {
            automationEngine.quitAutomation()
            _userMessage.value = "Automation STOPPED (Overlay remains active)"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to quit automation", e)
        }
    }

    fun exitUtility(context: Context) {
        try {
            quitAutomation()
            val intent = Intent(context, ScreenEarnerOverlayService::class.java)
            context.stopService(intent)
            _isOverlayServiceRunning.value = false
            _userMessage.value = "Screen Earner Service EXIT completed."
        } catch (e: Exception) {
            Log.e(TAG, "Failed exiting overlay utility", e)
        }
    }

    private fun sanitizeStrategyConfig(config: StrategyConfig): StrategyConfig {
        val sanitizedRules = config.getRuleConfigs().map { rule ->
            rule.copy(
                customAddAmount = rule.customAddAmount.takeIf { it.isFinite() && it >= 0.0 } ?: 10.0,
                targetWinsToFetch = rule.targetWinsToFetch.coerceAtLeast(1),
                requiredLosses = rule.requiredLosses.coerceAtLeast(0),
                waitMinutes = rule.waitMinutes.coerceAtLeast(0),
                minMultiplier = rule.minMultiplier.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
                maxMultiplier = rule.maxMultiplier.takeIf { it.isFinite() && it >= 0.0 } ?: 1000.0
            )
        }
        return config.copy(
            strategy1LowThreshold = config.strategy1LowThreshold.takeIf { it.isFinite() && it >= 1.01 } ?: 2.00,
            strategy1RequiredLowRounds = config.strategy1RequiredLowRounds.coerceAtLeast(1),
            strategy2HighThreshold = config.strategy2HighThreshold.takeIf { it.isFinite() && it >= 1.01 } ?: 10.00,
            strategy2WaitRounds = config.strategy2WaitRounds.coerceAtLeast(1),
            baseStake = config.baseStake.takeIf { it.isFinite() && it > 0.0 } ?: 10.0,
            multiplierProgression = config.multiplierProgression.takeIf { it.isFinite() && it >= 1.0 } ?: 2.0,
            stopAfterConsecutiveWins = config.stopAfterConsecutiveWins.coerceAtLeast(1),
            targetMultiplier = config.targetMultiplier.takeIf { it.isFinite() && it >= 1.01 } ?: 2.00,
            sessionTimeoutMinutes = config.sessionTimeoutMinutes.coerceAtLeast(1)
        ).withUpdatedRuleConfigs(sanitizedRules)
    }

    private fun sanitizeRiskLimits(limits: RiskLimits): RiskLimits {
        return limits.copy(
            maxSingleStake = limits.maxSingleStake.takeIf { it.isFinite() && it > 0.0 } ?: 500.0,
            maxSessionLoss = limits.maxSessionLoss.takeIf { it.isFinite() && it >= 0.0 } ?: 1000.0,
            maxTotalRounds = limits.maxTotalRounds.coerceAtLeast(1),
            maxProgressionLevel = limits.maxProgressionLevel.coerceAtLeast(1),
            profitTarget = limits.profitTarget.takeIf { it.isFinite() && it >= 0.0 } ?: 1500.0,
            minBalanceThreshold = limits.minBalanceThreshold.takeIf { it.isFinite() && it >= 0.0 } ?: 50.0
        )
    }

    fun applyStrategySelection(config: StrategyConfig) {
        try {
            val safeConfig = sanitizeStrategyConfig(config)
            strategyEngine.bindStrategyConfig(safeConfig)
            viewModelScope.launch {
                try {
                    repository.saveStrategyConfig(safeConfig)
                } catch (e: Exception) {
                    Log.e(TAG, "Save failed while applying strategy selection", e)
                    _userMessage.value = "Failed to save settings, please check input"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save failed while binding strategy selection", e)
            _userMessage.value = "Failed to save settings, please check input"
        }
    }

    fun saveStrategyConfig(config: StrategyConfig, ensureOverlayActive: Boolean = false, context: Context? = null) {
        try {
            val safeConfig = sanitizeStrategyConfig(config)
            strategyEngine.bindStrategyConfig(safeConfig)
            if (ensureOverlayActive && context != null) {
                ensureOverlayServiceRunning(context)
            }
            viewModelScope.launch {
                try {
                    repository.saveStrategyConfig(safeConfig)
                    _userMessage.value = "Strategy Settings Saved"
                } catch (e: Exception) {
                    Log.e(TAG, "Save failed for strategy config", e)
                    _userMessage.value = "Failed to save settings, please check input"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save failed for strategy config", e)
            _userMessage.value = "Failed to save settings, please check input"
        }
    }

    fun saveRiskLimits(limits: RiskLimits, ensureOverlayActive: Boolean = false, context: Context? = null) {
        try {
            val safeLimits = sanitizeRiskLimits(limits)
            if (ensureOverlayActive && context != null) {
                ensureOverlayServiceRunning(context)
            }
            viewModelScope.launch {
                try {
                    repository.saveRiskLimits(safeLimits)
                    _userMessage.value = "Risk Limits Saved"
                } catch (e: Exception) {
                    Log.e(TAG, "Save failed for risk limits", e)
                    _userMessage.value = "Failed to save settings, please check input"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save failed for risk limits", e)
            _userMessage.value = "Failed to save settings, please check input"
        }
    }

    // --- Section 15: User-Defined Custom Pattern Strategies ---
    fun saveCustomStrategy(strategy: CustomPatternStrategy) {
        viewModelScope.launch {
            try {
                if (strategy.name.isBlank() || strategy.getConditions().isEmpty()) {
                    _userMessage.value = "Failed to save settings, please check input"
                    return@launch
                }
                if (strategy.id == 0L) {
                    repository.insertCustomStrategy(strategy)
                    _userMessage.value = "Custom strategy '${strategy.name}' created"
                } else {
                    repository.updateCustomStrategy(strategy)
                    _userMessage.value = "Custom strategy '${strategy.name}' updated"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save failed for custom strategy", e)
                _userMessage.value = "Failed to save settings, please check input"
            }
        }
    }

    fun toggleCustomStrategyEnabled(strategy: CustomPatternStrategy, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateCustomStrategy(
                    strategy.copy(
                        isEnabled = enabled,
                        currentlyApproaching = if (!enabled) false else strategy.currentlyApproaching,
                        currentlyReached = if (!enabled) false else strategy.currentlyReached
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed toggling custom strategy", e)
            }
        }
    }

    fun toggleCustomStrategyNotifications(strategy: CustomPatternStrategy, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateCustomStrategy(strategy.copy(notificationEnabled = enabled))
            } catch (e: Exception) {
                Log.e(TAG, "Failed toggling custom strategy notifications", e)
            }
        }
    }

    fun deleteCustomStrategy(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteCustomStrategy(id)
                _userMessage.value = "Custom strategy deleted"
            } catch (e: Exception) {
                Log.e(TAG, "Failed deleting custom strategy", e)
            }
        }
    }

    // --- Section 16: Notification Settings & History ---
    fun saveNotificationSettings(settings: NotificationSettings) {
        viewModelScope.launch {
            try {
                repository.saveNotificationSettings(
                    settings.copy(
                        repeatIntervalMinutes = settings.repeatIntervalMinutes.coerceAtLeast(0),
                        quietStartHour = settings.quietStartHour.coerceIn(0, 23),
                        quietStartMinute = settings.quietStartMinute.coerceIn(0, 59),
                        quietEndHour = settings.quietEndHour.coerceIn(0, 23),
                        quietEndMinute = settings.quietEndMinute.coerceIn(0, 59)
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving notification settings", e)
                _userMessage.value = "Failed to save settings, please check input"
            }
        }
    }

    fun markNotificationOpened(historyId: Long) {
        if (historyId <= 0L) return
        viewModelScope.launch {
            try {
                repository.updateNotificationInteraction(historyId, NotificationInteractionStatus.OPENED_BY_USER)
            } catch (e: Exception) {
                Log.e(TAG, "Failed marking notification opened", e)
            }
        }
    }

    fun clearNotificationHistory() {
        viewModelScope.launch {
            try {
                repository.clearNotificationHistory()
                AutomationController.notificationManager?.clearSessionCache()
                _userMessage.value = "Notification history cleared"
            } catch (e: Exception) {
                Log.e(TAG, "Failed clearing notification history", e)
            }
        }
    }

    // --- Section 14: Manual 30m History Collection Trigger ---
    fun triggerRibbonHistoryCollection(context: Context = getApplication()) {
        try {
            AutomationController.triggerManualHistoryCollection(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed triggering ribbon history collection", e)
        }
    }

    fun ensureOverlayServiceRunning(context: Context) {
        try {
            if (!Settings.canDrawOverlays(context) || ScreenEarnerOverlayService.isRunning) {
                _isOverlayServiceRunning.value = ScreenEarnerOverlayService.isRunning
                return
            }
            val intent = Intent(context, ScreenEarnerOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _isOverlayServiceRunning.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure overlay service running", e)
        }
    }

    fun triggerEmergencyStop() {
        try {
            val limits = (riskLimits.value ?: RiskLimits()).copy(emergencyStop = true)
            saveRiskLimits(limits)
            quitAutomation()
            _userMessage.value = "EMERGENCY STOP ACTIVATED!"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger emergency stop", e)
        }
    }

    fun clearEmergencyStop() {
        try {
            val limits = (riskLimits.value ?: RiskLimits()).copy(emergencyStop = false)
            saveRiskLimits(limits)
            _userMessage.value = "Emergency Stop Cleared"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear emergency stop", e)
        }
    }

    fun toggleOverlayService(context: Context) {
        try {
            if (!Settings.canDrawOverlays(context)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                _userMessage.value = "Please grant Display Over Other Apps permission."
                return
            }

            val intent = Intent(context, ScreenEarnerOverlayService::class.java)
            if (ScreenEarnerOverlayService.isRunning) {
                context.stopService(intent)
                _isOverlayServiceRunning.value = false
                _userMessage.value = "Overlay Service Stopped."
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                _isOverlayServiceRunning.value = true
                _userMessage.value = "Overlay Service Started."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle overlay service", e)
            _userMessage.value = "Unable to start overlay service. Check overlay permissions."
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearSessionLogs() {
        viewModelScope.launch {
            try {
                repository.clearSessionLogs()
                _userMessage.value = "Session logs cleared"
            } catch (e: Exception) {
                Log.e(TAG, "Failed clearing session logs", e)
            }
        }
    }

    fun clearMultiplierRecords() {
        viewModelScope.launch {
            try {
                repository.clearMultiplierRecords()
                _userMessage.value = "Multiplier history cleared"
            } catch (e: Exception) {
                Log.e(TAG, "Failed clearing multiplier records", e)
            }
        }
    }

    fun setActiveProfile(profileId: String) {
        viewModelScope.launch {
            try {
                repository.setActiveProfile(profileId)
                _userMessage.value = "Profile activated: $profileId"
            } catch (e: Exception) {
                Log.e(TAG, "Save failed activating profile", e)
                _userMessage.value = "Failed to save settings, please check input"
            }
        }
    }
}
