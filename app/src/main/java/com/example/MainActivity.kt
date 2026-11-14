package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ShortcutType
import com.example.service.AutomationController
import com.example.ui.ScreenEarnerViewModel
import com.example.ui.components.ScreenEarnerTopBar
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LogsAnalyticsScreen
import com.example.ui.screens.OverlayManagerScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.RiskManagerScreen
import com.example.ui.screens.SettingsAppearanceScreen
import com.example.ui.screens.StrategyScreen
import com.example.ui.theme.ScreenEarnerTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_OPEN_TAB = "extra_open_tab"
        const val EXTRA_NOTIFICATION_HISTORY_ID = "extra_notification_history_id"
        const val TAB_HOME = 0
        const val TAB_OVERLAY = 1
        const val TAB_STRATEGY = 2
        const val TAB_RISK = 3
        const val TAB_PROFILES = 4
        const val TAB_LOGS = 5
        const val TAB_SETTINGS = 6
    }

    private val viewModel: ScreenEarnerViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingTabExtra(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshOverlayRunningState()
    }

    private fun handleIncomingTabExtra(intent: Intent?) {
        try {
            if (intent == null) return
            val historyId = intent.getLongExtra(EXTRA_NOTIFICATION_HISTORY_ID, -1L)
            if (historyId > 0L) {
                viewModel.markNotificationOpened(historyId)
            }
            if (intent.hasExtra(EXTRA_OPEN_TAB)) {
                val targetTab = intent.getIntExtra(EXTRA_OPEN_TAB, TAB_HOME)
                viewModel.selectTab(targetTab.coerceIn(TAB_HOME, TAB_SETTINGS))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed handling incoming tab extra", e)
        }
    }

    /**
     * Section 13 Save & Close fix:
     * Closes ONLY the active editor screen inside Screen Earner and returns to the previous tab / Home.
     * Never calls moveTaskToBack(true), finish(), or stopService() — app & overlay services remain intact.
     */
    private fun closeCurrentEditorPanel() {
        try {
            viewModel.closeEditorScreen()
        } catch (e: Exception) {
            Log.e(TAG, "Failed returning from editor panel", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingTabExtra(intent)

        setContent {
            ScreenEarnerTheme {
                val context = LocalContext.current
                val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
                val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
                val profiles by viewModel.profiles.collectAsStateWithLifecycle()
                val strategyConfig by viewModel.strategyConfig.collectAsStateWithLifecycle()
                val riskLimits by viewModel.riskLimits.collectAsStateWithLifecycle()
                val sessionLogs by viewModel.sessionLogs.collectAsStateWithLifecycle()
                val multiplierRecords by viewModel.multiplierRecords.collectAsStateWithLifecycle()
                val customStrategies by viewModel.customStrategies.collectAsStateWithLifecycle()
                val notificationSettings by viewModel.notificationSettings.collectAsStateWithLifecycle()
                val notificationHistory by viewModel.notificationHistory.collectAsStateWithLifecycle()
                val historyMetadata by viewModel.historyMetadata.collectAsStateWithLifecycle()
                val gameState by viewModel.gameState.collectAsStateWithLifecycle()
                val automationState by viewModel.automationState.collectAsStateWithLifecycle()
                val isOverlayRunning by viewModel.isOverlayServiceRunning.collectAsStateWithLifecycle()
                val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
                val isPanelExpanded by viewModel.isPanelExpanded.collectAsStateWithLifecycle()
                val isCalibrating by viewModel.isCalibratingTarget.collectAsStateWithLifecycle()
                val calibratingShortcutId by viewModel.calibratingShortcutId.collectAsStateWithLifecycle()
                val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(userMessage) {
                    userMessage?.let { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        viewModel.clearUserMessage()
                    }
                }

                BackHandler(enabled = selectedTab != TAB_HOME) {
                    viewModel.closeEditorScreen()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        ScreenEarnerTopBar(
                            onEmergencyStop = { viewModel.triggerEmergencyStop() }
                        )
                    },
                    bottomBar = {
                        NavigationBar(modifier = Modifier.testTag("main_navigation_bar")) {
                            NavigationBarItem(
                                selected = selectedTab == TAB_HOME,
                                onClick = { viewModel.selectTab(TAB_HOME) },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home", fontSize = 10.sp) },
                                modifier = Modifier.testTag("nav_home")
                            )
                            NavigationBarItem(
                                selected = selectedTab == TAB_OVERLAY,
                                onClick = { viewModel.selectTab(TAB_OVERLAY) },
                                icon = { Icon(Icons.Default.Layers, contentDescription = "Overlay") },
                                label = { Text("Overlay", fontSize = 10.sp) },
                                modifier = Modifier.testTag("nav_overlay")
                            )
                            NavigationBarItem(
                                selected = selectedTab == TAB_STRATEGY,
                                onClick = { viewModel.selectTab(TAB_STRATEGY) },
                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Strategy") },
                                label = { Text("Strategy", fontSize = 10.sp) },
                                modifier = Modifier.testTag("nav_strategy")
                            )
                            NavigationBarItem(
                                selected = selectedTab == TAB_RISK,
                                onClick = { viewModel.selectTab(TAB_RISK) },
                                icon = { Icon(Icons.Default.Security, contentDescription = "Risk") },
                                label = { Text("Risk", fontSize = 10.sp) },
                                modifier = Modifier.testTag("nav_risk")
                            )
                            NavigationBarItem(
                                selected = selectedTab == TAB_PROFILES,
                                onClick = { viewModel.selectTab(TAB_PROFILES) },
                                icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = "Profiles") },
                                label = { Text("Profiles", fontSize = 10.sp) },
                                modifier = Modifier.testTag("nav_profiles")
                            )
                            NavigationBarItem(
                                selected = selectedTab == TAB_LOGS,
                                onClick = { viewModel.selectTab(TAB_LOGS) },
                                icon = { Icon(Icons.Default.Analytics, contentDescription = "Logs") },
                                label = { Text("Logs", fontSize = 10.sp) },
                                modifier = Modifier.testTag("nav_logs")
                            )
                            NavigationBarItem(
                                selected = selectedTab == TAB_SETTINGS,
                                onClick = { viewModel.selectTab(TAB_SETTINGS) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings", fontSize = 10.sp) },
                                modifier = Modifier.testTag("nav_settings")
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            TAB_HOME -> HomeScreen(
                                gameState = gameState,
                                automationState = automationState,
                                isOverlayServiceRunning = isOverlayRunning,
                                strategyConfig = strategyConfig,
                                riskLimits = riskLimits,
                                onToggleOverlayService = { viewModel.toggleOverlayService(context) },
                                onStartAutomation = { viewModel.startAutomation() },
                                onPauseAutomation = { viewModel.pauseAutomation() },
                                onTriggerEmergencyStop = { viewModel.triggerEmergencyStop() },
                                onClearEmergencyStop = { viewModel.clearEmergencyStop() },
                                onRetryBalanceDetection = { AutomationController.retryBalanceDetection() },
                                onSaveStrategyConfig = { viewModel.saveStrategyConfig(it) },
                                onSaveRiskLimits = { viewModel.saveRiskLimits(it) },
                                onNavigateToOverlayManager = { viewModel.selectTab(TAB_OVERLAY) },
                                onNavigateToStrategy = { viewModel.selectTab(TAB_STRATEGY) },
                                onNavigateToRisk = { viewModel.selectTab(TAB_RISK) }
                            )
                            TAB_OVERLAY -> OverlayManagerScreen(
                                shortcuts = shortcuts,
                                gameState = gameState,
                                isEditMode = isEditMode,
                                isPanelExpanded = isPanelExpanded,
                                isCalibrating = isCalibrating,
                                calibratingShortcutId = calibratingShortcutId,
                                onSetEditMode = { viewModel.setEditMode(it) },
                                onTogglePanel = { viewModel.togglePanelExpanded() },
                                onStartAutomation = { viewModel.startAutomation() },
                                onPauseAutomation = { viewModel.pauseAutomation() },
                                onResumeAutomation = { viewModel.resumeAutomation() },
                                onQuitAutomation = { viewModel.quitAutomation() },
                                onExitUtility = { viewModel.exitUtility(context) },
                                onOpenSettings = { viewModel.selectTab(TAB_SETTINGS) },
                                onAddBetShortcut = { viewModel.addNewShortcut("BET ${shortcuts.size + 1}", ShortcutType.BET, "#16A34A") },
                                onAddCashOutShortcut = { viewModel.addNewShortcut("CASH OUT ${shortcuts.size + 1}", ShortcutType.CASH_OUT, "#F59E0B") },
                                onUpdateShortcutPos = { id, x, y -> viewModel.updateShortcutVisualPosition(id, x, y) },
                                onSetCalibratedTarget = { x, y -> viewModel.setCalibratedTarget(x, y) },
                                onStartCalibration = { id -> viewModel.startCalibration(id) },
                                onSaveShortcutSettings = { shortcut -> viewModel.updateShortcut(shortcut) },
                                onDeleteShortcut = { id -> viewModel.deleteShortcut(id) }
                            )
                            TAB_STRATEGY -> StrategyScreen(
                                currentConfig = strategyConfig,
                                customStrategies = customStrategies,
                                onSaveCustomStrategy = { viewModel.saveCustomStrategy(it) },
                                onToggleCustomStrategyEnabled = { strat, enabled ->
                                    viewModel.toggleCustomStrategyEnabled(strat, enabled)
                                },
                                onToggleCustomStrategyNotifications = { strat, enabled ->
                                    viewModel.toggleCustomStrategyNotifications(strat, enabled)
                                },
                                onDeleteCustomStrategy = { viewModel.deleteCustomStrategy(it) },
                                onSaveConfig = { updatedConfig ->
                                    try {
                                        viewModel.saveStrategyConfig(updatedConfig, ensureOverlayActive = false, context = context)
                                        Toast.makeText(context, "Strategy settings saved", Toast.LENGTH_SHORT).show()
                                        closeCurrentEditorPanel()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Save failed in StrategyScreen", e)
                                        Toast.makeText(context, "Failed to save settings, please check input", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            TAB_RISK -> RiskManagerScreen(
                                currentLimits = riskLimits,
                                lastViolation = viewModel.automationEngine.lastRiskViolation.collectAsStateWithLifecycle().value,
                                onSaveLimits = { updatedLimits ->
                                    try {
                                        viewModel.saveRiskLimits(updatedLimits, ensureOverlayActive = false, context = context)
                                        Toast.makeText(context, "Risk limits saved", Toast.LENGTH_SHORT).show()
                                        closeCurrentEditorPanel()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Save failed in RiskManagerScreen", e)
                                        Toast.makeText(context, "Failed to save settings, please check input", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onTriggerEmergencyStop = { viewModel.triggerEmergencyStop() },
                                onClearEmergencyStop = { viewModel.clearEmergencyStop() }
                            )
                            TAB_PROFILES -> ProfilesScreen(
                                profiles = profiles,
                                onSelectProfile = { viewModel.setActiveProfile(it) }
                            )
                            TAB_LOGS -> LogsAnalyticsScreen(
                                sessionLogs = sessionLogs,
                                multiplierRecords = multiplierRecords,
                                historyMetadata = historyMetadata,
                                notificationHistory = notificationHistory,
                                onClearLogs = { viewModel.clearSessionLogs() },
                                onClearMultiplierRecords = { viewModel.clearMultiplierRecords() },
                                onClearNotificationHistory = { viewModel.clearNotificationHistory() },
                                onCollectHistory = { viewModel.triggerRibbonHistoryCollection(context) }
                            )
                            TAB_SETTINGS -> SettingsAppearanceScreen(
                                strategyConfig = strategyConfig,
                                riskLimits = riskLimits,
                                notificationSettings = notificationSettings,
                                lastRiskViolation = viewModel.automationEngine.lastRiskViolation.collectAsStateWithLifecycle().value,
                                onStrategyConfigChanged = { updatedConfig ->
                                    viewModel.applyStrategySelection(updatedConfig)
                                },
                                onRiskLimitsChanged = { updatedLimits ->
                                    viewModel.saveRiskLimits(updatedLimits)
                                },
                                onNotificationSettingsChanged = { updatedNotif ->
                                    viewModel.saveNotificationSettings(updatedNotif)
                                },
                                onResetSessionStats = {
                                    viewModel.automationEngine.resetSessionStats()
                                },
                                onSaveAndReturn = { updatedConfig ->
                                    try {
                                        viewModel.saveStrategyConfig(
                                            config = updatedConfig,
                                            ensureOverlayActive = false,
                                            context = context
                                        )
                                        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                                        closeCurrentEditorPanel()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Save failed in SettingsAppearanceScreen", e)
                                        Toast.makeText(context, "Failed to save settings, please check input", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
