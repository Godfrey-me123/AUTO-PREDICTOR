package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.ScreenEarnerDatabase
import com.example.data.model.MultiplierRecord
import com.example.data.model.StrategyConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import com.example.data.engine.StrategyEngine
import com.example.data.model.StrategyRuleId
import com.example.ui.screens.SettingsAppearanceScreen
import com.example.ui.theme.ScreenEarnerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Screen Earner", appName)
    }

    @Test
    fun `room v4 persists multiplier records and strategy config rules`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ScreenEarnerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.dao()
            dao.saveStrategyConfig(StrategyConfig())
            dao.insertMultiplierRecord(MultiplierRecord(multiplier = 112.40, crashTimestamp = 1_700_000_000_000L, platform = "BetPawa Aviator"))

            val records = dao.getAllMultiplierRecords().first()
            assertEquals(1, records.size)
            assertEquals(112.40, records.first().multiplier, 0.001)

            val savedConfig = dao.getStrategyConfig().first()
            assertEquals(8, savedConfig?.getRuleConfigs()?.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `settings screen radio buttons select one, multiple, or all strategies and bind to StrategyEngine`() {
        val engine = StrategyEngine()
        val initialConfig = StrategyConfig()
        engine.bindStrategyConfig(initialConfig)

        composeRule.setContent {
            val currentConfigState = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(initialConfig)
            }
            ScreenEarnerTheme {
                SettingsAppearanceScreen(
                    strategyConfig = currentConfigState.value,
                    onStrategyConfigChanged = { updated ->
                        currentConfigState.value = updated
                        engine.bindStrategyConfig(updated)
                    }
                )
            }
        }

        // Select All chip selects every strategy and binds all 8 rules + 3 streak/high-mult rules
        composeRule.onNodeWithTag("settings_select_all_strategies").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(StrategyRuleId.entries.toSet(), engine.getState().enabledRuleIds)
        assertTrue(engine.boundConfig.value.isStrategy1Enabled)
        assertTrue(engine.boundConfig.value.isStrategy2Enabled)
        assertTrue(engine.boundConfig.value.isHighMultiplierLossRuleEnabled)
        composeRule.onNodeWithTag("settings_strategy_radio_AFTER_9_LOSSES").assertIsSelected()
        composeRule.onNodeWithTag("settings_strategy_radio_AFTER_2_LOSSES").assertIsSelected()

        // Clear all, then toggle multiple individual radio buttons
        composeRule.onNodeWithTag("settings_clear_all_strategies").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(engine.getState().enabledRuleIds.isEmpty())
        composeRule.onNodeWithTag("settings_strategy_radio_AFTER_9_LOSSES").assertIsNotSelected()

        // Pick one ('After 9 Losses') then add a second ('10x-100x High-Multiplier Wait')
        composeRule.onNodeWithTag("settings_strategy_radio_AFTER_9_LOSSES").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(setOf(StrategyRuleId.AFTER_9_LOSSES), engine.getState().enabledRuleIds)
        composeRule.onNodeWithTag("settings_strategy_radio_AFTER_9_LOSSES").assertIsSelected()

        composeRule.onNodeWithTag("settings_strategy_radio_RANGE_10_TO_100_WAIT_10M").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            setOf(StrategyRuleId.AFTER_9_LOSSES, StrategyRuleId.RANGE_10_TO_100_WAIT_10M),
            engine.getState().enabledRuleIds
        )
        assertFalse(engine.boundConfig.value.isStrategy1Enabled)
    }

    @Test
    fun `session limits settings panel edits profit and loss thresholds and floating HUD renders live stats`() {
        val updatedLimits = mutableListOf<com.example.data.model.RiskLimits>()

        composeRule.setContent {
            val limitsState = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(
                    com.example.data.model.RiskLimits(
                        profitTarget = 500.0,
                        maxSessionLoss = 250.0,
                        stopOnProfitEnabled = true,
                        stopOnLossEnabled = true
                    )
                )
            }
            ScreenEarnerTheme {
                androidx.compose.foundation.layout.Column {
                    com.example.service.FloatingSessionStatsHudComposable(
                        stats = com.example.data.model.SessionStats(
                            winCount = 4,
                            lossCount = 1,
                            totalProfit = 85.50,
                            observedCrashCount = 7,
                            limitHaltReason = null
                        ),
                        observedRounds = 7,
                        automationState = com.example.data.engine.AutomationState.RUNNING
                    )
                    SettingsAppearanceScreen(
                        strategyConfig = StrategyConfig(),
                        riskLimits = limitsState.value,
                        lastRiskViolation = "Stop if Profit Reaches triggered",
                        onRiskLimitsChanged = { newLimits ->
                            limitsState.value = newLimits
                            updatedLimits.add(newLimits)
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings_session_limits_card").assertExists()
        composeRule.onNodeWithTag("settings_session_limit_halt_banner").assertExists()
        composeRule.onNodeWithTag("settings_stop_profit_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(updatedLimits.isNotEmpty())
        assertFalse(updatedLimits.last().stopOnProfitEnabled)

        composeRule.onNodeWithTag("settings_stop_loss_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(updatedLimits.last().stopOnLossEnabled)
    }

    @Test
    fun `clicking Save with empty or invalid inputs shows Toast without crashing or saving bad state`() {
        var strategySavedCount = 0
        var riskSavedCount = 0
        val showRisk = androidx.compose.runtime.mutableStateOf(false)

        composeRule.setContent {
            ScreenEarnerTheme {
                if (!showRisk.value) {
                    com.example.ui.screens.StrategyScreen(
                        currentConfig = StrategyConfig(),
                        onSaveConfig = { strategySavedCount++ }
                    )
                } else {
                    com.example.ui.screens.RiskManagerScreen(
                        currentLimits = com.example.data.model.RiskLimits(),
                        lastViolation = null,
                        onSaveLimits = { riskSavedCount++ },
                        onTriggerEmergencyStop = {},
                        onClearEmergencyStop = {}
                    )
                }
            }
        }

        // Clear Base Stake input in StrategyScreen so it becomes invalid empty string, then tap Save
        val baseStakeNode = composeRule.onNodeWithTag("strategy_base_stake_input")
        baseStakeNode.performScrollTo()
        baseStakeNode.performTextClearance()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("save_strategy_btn"))
        composeRule.onNodeWithTag("save_strategy_btn").performClick()
        composeRule.waitForIdle()

        assertEquals(0, strategySavedCount)
        assertEquals(
            "Failed to save settings, please check input",
            org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
        )

        // Also test RiskManagerScreen with malformed input
        showRisk.value = true
        composeRule.waitForIdle()
        val maxStakeNode = composeRule.onNodeWithTag("max_stake_input")
        maxStakeNode.performScrollTo()
        maxStakeNode.performTextReplacement("NaN")
        composeRule.onNodeWithTag("save_risk_limits_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(0, riskSavedCount)
        assertEquals(
            "Failed to save settings, please check input",
            org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun `settings appearance screen validates profit threshold on Save and persists SharedPreferences when valid`() {
        var saveReturnedCount = 0
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeRule.setContent {
            val limitsState = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(
                    com.example.data.model.RiskLimits(
                        stopOnProfitEnabled = true,
                        profitTarget = 500.0
                    )
                )
            }
            ScreenEarnerTheme {
                SettingsAppearanceScreen(
                    strategyConfig = StrategyConfig(),
                    riskLimits = limitsState.value,
                    onRiskLimitsChanged = { limitsState.value = it },
                    onSaveAndReturn = { saveReturnedCount++ }
                )
            }
        }

        val profitNode = composeRule.onNodeWithTag("settings_stop_profit_input")
        profitNode.performScrollTo()
        profitNode.performTextClearance()

        composeRule.onNodeWithTag("save_appearance_settings_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(0, saveReturnedCount)
        assertEquals(
            "Failed to save settings, please check input",
            org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
        )

        profitNode.performScrollTo()
        profitNode.performTextReplacement("750")
        composeRule.onNodeWithTag("save_appearance_settings_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, saveReturnedCount)

        val prefs = context.getSharedPreferences(
            com.example.service.ScreenEarnerOverlayService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        assertTrue(prefs.contains(com.example.service.ScreenEarnerOverlayService.PREF_BUBBLE_OPACITY))
    }
}
