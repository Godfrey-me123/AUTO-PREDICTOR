package com.example.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Notifications
import com.example.data.model.NotificationSettings
import com.example.data.model.RiskLimits
import com.example.data.model.StrategyCategory
import com.example.data.model.StrategyConfig
import com.example.data.model.StrategyRuleConfig
import com.example.data.model.StrategyRuleId
import com.example.service.ScreenEarnerOverlayService
import com.example.ui.theme.ActionStartEmerald
import com.example.ui.theme.ActionStopRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAppearanceScreen(
    strategyConfig: StrategyConfig? = null,
    riskLimits: RiskLimits? = null,
    notificationSettings: NotificationSettings? = null,
    lastRiskViolation: String? = null,
    onStrategyConfigChanged: (StrategyConfig) -> Unit = {},
    onRiskLimitsChanged: (RiskLimits) -> Unit = {},
    onNotificationSettingsChanged: (NotificationSettings) -> Unit = {},
    onResetSessionStats: () -> Unit = {},
    onSaveAndReturn: (StrategyConfig) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(ScreenEarnerOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val scrollState = rememberScrollState()

    var opacityValue by remember {
        mutableStateOf(prefs.getFloat(ScreenEarnerOverlayService.PREF_BUBBLE_OPACITY, 0.95f))
    }
    var hapticFeedbackEnabled by remember {
        mutableStateOf(prefs.getBoolean(ScreenEarnerOverlayService.PREF_HAPTICS_ENABLED, true))
    }
    var bubbleSizeDp by remember {
        mutableStateOf(prefs.getFloat(ScreenEarnerOverlayService.PREF_BUBBLE_SIZE_DP, 56f))
    }

    val incomingConfig = strategyConfig ?: StrategyConfig()
    var localStrategyConfig by remember(
        incomingConfig.ruleConfigsJson,
        incomingConfig.isStrategy1Enabled,
        incomingConfig.isStrategy2Enabled,
        incomingConfig.isHighMultiplierLossRuleEnabled
    ) {
        mutableStateOf(incomingConfig)
    }

    val incomingLimits = riskLimits ?: RiskLimits()
    var localLimits by remember(
        incomingLimits.profitTarget,
        incomingLimits.maxSessionLoss,
        incomingLimits.stopOnProfitEnabled,
        incomingLimits.stopOnLossEnabled,
        incomingLimits.emergencyStop
    ) {
        mutableStateOf(incomingLimits)
    }
    var stopProfitText by remember(incomingLimits.profitTarget) {
        mutableStateOf(
            if (incomingLimits.profitTarget % 1.0 == 0.0) {
                incomingLimits.profitTarget.toLong().toString()
            } else {
                incomingLimits.profitTarget.toString()
            }
        )
    }
    var stopLossText by remember(incomingLimits.maxSessionLoss) {
        mutableStateOf(
            if (incomingLimits.maxSessionLoss % 1.0 == 0.0) {
                incomingLimits.maxSessionLoss.toLong().toString()
            } else {
                incomingLimits.maxSessionLoss.toString()
            }
        )
    }

    fun applyLimitsUpdate(updated: RiskLimits) {
        localLimits = updated
        onRiskLimitsChanged(updated)
    }

    val incomingNotif = notificationSettings ?: NotificationSettings()
    var localNotifSettings by remember(incomingNotif) {
        mutableStateOf(incomingNotif)
    }
    var repeatIntervalText by remember(incomingNotif.repeatIntervalMinutes) {
        mutableStateOf(incomingNotif.repeatIntervalMinutes.toString())
    }
    var quietStartHourText by remember(incomingNotif.quietStartHour) {
        mutableStateOf(incomingNotif.quietStartHour.toString())
    }
    var quietEndHourText by remember(incomingNotif.quietEndHour) {
        mutableStateOf(incomingNotif.quietEndHour.toString())
    }

    fun applyNotifUpdate(updated: NotificationSettings) {
        localNotifSettings = updated
        onNotificationSettingsChanged(updated)
    }

    val ruleConfigs = remember(localStrategyConfig.ruleConfigsJson) {
        localStrategyConfig.getRuleConfigs()
    }

    fun applyConfigUpdate(updated: StrategyConfig) {
        localStrategyConfig = updated
        onStrategyConfigChanged(updated)
    }

    fun toggleRule(ruleId: StrategyRuleId) {
        val currentRule = localStrategyConfig.getRuleConfig(ruleId)
        applyConfigUpdate(
            localStrategyConfig.withUpdatedRule(
                currentRule.copy(isEnabled = !currentRule.isEnabled)
            )
        )
    }

    fun selectAllStrategies() {
        val allRulesEnabled = ruleConfigs.map { it.copy(isEnabled = true) }
        applyConfigUpdate(
            localStrategyConfig.copy(
                isStrategy1Enabled = true,
                isStrategy2Enabled = true,
                isHighMultiplierLossRuleEnabled = true
            ).withUpdatedRuleConfigs(allRulesEnabled)
        )
    }

    fun clearAllStrategies() {
        val allRulesDisabled = ruleConfigs.map { it.copy(isEnabled = false) }
        applyConfigUpdate(
            localStrategyConfig.copy(
                isStrategy1Enabled = false,
                isStrategy2Enabled = false,
                isHighMultiplierLossRuleEnabled = false
            ).withUpdatedRuleConfigs(allRulesDisabled)
        )
    }

    fun selectSingleAfter9Strategy() {
        val singleOnly = ruleConfigs.map {
            it.copy(isEnabled = it.ruleId == StrategyRuleId.AFTER_9_LOSSES)
        }
        applyConfigUpdate(
            localStrategyConfig.copy(
                isStrategy1Enabled = false,
                isStrategy2Enabled = false,
                isHighMultiplierLossRuleEnabled = false
            ).withUpdatedRuleConfigs(singleOnly)
        )
    }

    fun restoreDefaultStrategies() {
        val defaults = ruleConfigs.map {
            it.copy(isEnabled = it.ruleId.defaultEnabled)
        }
        applyConfigUpdate(
            localStrategyConfig.copy(
                isStrategy1Enabled = true,
                isStrategy2Enabled = false,
                isHighMultiplierLossRuleEnabled = true
            ).withUpdatedRuleConfigs(defaults)
        )
    }

    val selectedCount = ruleConfigs.count { it.isEnabled } +
        (if (localStrategyConfig.isStrategy1Enabled) 1 else 0) +
        (if (localStrategyConfig.isStrategy2Enabled) 1 else 0) +
        (if (localStrategyConfig.isHighMultiplierLossRuleEnabled) 1 else 0)
    val totalStrategies = ruleConfigs.size + 3

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Embed Explicit Permissions Setup Component
        PermissionsScreen(scrollable = false)

        // Session Limits Settings Panel ("Stop if Profit Reaches" & "Stop if Loss Reaches")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_session_limits_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Session Limits",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SESSION LIMITS",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val limitsEnabledCount = (if (localLimits.stopOnProfitEnabled) 1 else 0) +
                        (if (localLimits.stopOnLossEnabled) 1 else 0)
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (lastRiskViolation != null) {
                            ActionStopRed.copy(alpha = 0.16f)
                        } else if (limitsEnabledCount > 0) {
                            ActionStartEmerald.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = when {
                                lastRiskViolation != null -> "HALTED"
                                limitsEnabledCount > 0 -> "$limitsEnabledCount GUARD ACTIVE"
                                else -> "DISABLED"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                lastRiskViolation != null -> ActionStopRed
                                limitsEnabledCount > 0 -> ActionStartEmerald
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Automatically halts all automation gestures immediately when session profit or loss reaches your defined threshold.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!lastRiskViolation.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF7F1D1D).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, ActionStopRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_session_limit_halt_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Limit Triggered",
                                tint = ActionStopRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = lastRiskViolation,
                                color = ActionStopRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Stop if Profit Reaches
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(
                        width = 0.75.dp,
                        color = if (localLimits.stopOnProfitEnabled) {
                            ActionStartEmerald.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Stop if Profit Reaches",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Halt automation gestures once net session profit >= target",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = localLimits.stopOnProfitEnabled,
                                onCheckedChange = { enabled ->
                                    applyLimitsUpdate(localLimits.copy(stopOnProfitEnabled = enabled))
                                },
                                modifier = Modifier.testTag("settings_stop_profit_switch")
                            )
                        }

                        OutlinedTextField(
                            value = stopProfitText,
                            onValueChange = { raw ->
                                stopProfitText = raw
                                val parsed = raw.trim().toDoubleOrNull()
                                if (parsed != null && parsed.isFinite() && parsed >= 0.0) {
                                    applyLimitsUpdate(localLimits.copy(profitTarget = parsed))
                                }
                            },
                            enabled = localLimits.stopOnProfitEnabled,
                            label = { Text("Stop if Profit Reaches ($)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_stop_profit_input")
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(100, 250, 500, 1500).forEach { preset ->
                                AssistChip(
                                    onClick = {
                                        stopProfitText = preset.toString()
                                        applyLimitsUpdate(
                                            localLimits.copy(
                                                stopOnProfitEnabled = true,
                                                profitTarget = preset.toDouble()
                                            )
                                        )
                                    },
                                    label = { Text("+$$preset", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Stop if Loss Reaches
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(
                        width = 0.75.dp,
                        color = if (localLimits.stopOnLossEnabled) {
                            ActionStopRed.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Stop if Loss Reaches",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Halt automation gestures if net session loss >= threshold",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = localLimits.stopOnLossEnabled,
                                onCheckedChange = { enabled ->
                                    applyLimitsUpdate(localLimits.copy(stopOnLossEnabled = enabled))
                                },
                                modifier = Modifier.testTag("settings_stop_loss_switch")
                            )
                        }

                        OutlinedTextField(
                            value = stopLossText,
                            onValueChange = { raw ->
                                stopLossText = raw
                                val parsed = raw.trim().toDoubleOrNull()
                                if (parsed != null && parsed.isFinite() && parsed >= 0.0) {
                                    applyLimitsUpdate(localLimits.copy(maxSessionLoss = parsed))
                                }
                            },
                            enabled = localLimits.stopOnLossEnabled,
                            label = { Text("Stop if Loss Reaches ($)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_stop_loss_input")
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(100, 250, 500, 1000).forEach { preset ->
                                AssistChip(
                                    onClick = {
                                        stopLossText = preset.toString()
                                        applyLimitsUpdate(
                                            localLimits.copy(
                                                stopOnLossEnabled = true,
                                                maxSessionLoss = preset.toDouble()
                                            )
                                        )
                                    },
                                    label = { Text("-$$preset", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                AssistChip(
                    onClick = onResetSessionStats,
                    label = { Text("Reset Session Stats & Clear Halt", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = "Reset Session Stats",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("settings_reset_session_btn")
                )
            }
        }

        // Active Strategy Selection (Radio-Button Multi-Select bound to StrategyEngine)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_strategy_selection_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Strategy Selection",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ACTIVE STRATEGY SELECTION",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selectedCount > 0) {
                            ActionStartEmerald.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        modifier = Modifier.testTag("settings_selected_strategy_count")
                    ) {
                        Text(
                            text = when (selectedCount) {
                                0 -> "None Active"
                                totalStrategies -> "All $totalStrategies Active"
                                else -> "$selectedCount / $totalStrategies Active"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedCount > 0) {
                                ActionStartEmerald
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Tap any radio option below to select one, multiple, or all strategies. Active selections bind live to StrategyEngine.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistChip(
                        onClick = { selectAllStrategies() },
                        label = { Text("Select All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "Select all strategies",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("settings_select_all_strategies")
                    )
                    AssistChip(
                        onClick = { selectSingleAfter9Strategy() },
                        label = { Text("Only After 9", fontSize = 11.sp) },
                        modifier = Modifier.testTag("settings_select_after9_only")
                    )
                    AssistChip(
                        onClick = { restoreDefaultStrategies() },
                        label = { Text("Defaults", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = "Default strategies",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("settings_select_default_strategies")
                    )
                    AssistChip(
                        onClick = { clearAllStrategies() },
                        label = { Text("Clear", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ClearAll,
                                contentDescription = "Clear all strategies",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("settings_clear_all_strategies")
                    )
                }

                // Group 1: Recovery & High-Multiplier Wait Strategies (StrategyRuleId)
                StrategyCategory.entries.forEach { category ->
                    val categoryLabel = when (category) {
                        StrategyCategory.STANDARD_RECOVERY -> "STANDARD LOSS RECOVERY"
                        StrategyCategory.MULTIPLIER_WAIT -> "HIGH-MULTIPLIER WAIT STRATEGIES"
                        StrategyCategory.QUICK_RECOVERY -> "QUICK LOSS RECOVERY"
                    }
                    Text(
                        text = categoryLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    ruleConfigs
                        .filter { it.ruleId.category == category }
                        .forEach { rule ->
                            val subtitle = buildStrategyRuleSummary(rule)
                            StrategyRadioSelectionRow(
                                title = rule.dynamicTitle(),
                                subtitle = subtitle,
                                selected = rule.isEnabled,
                                rowTag = "settings_strategy_row_${rule.ruleId.name}",
                                radioTag = "settings_strategy_radio_${rule.ruleId.name}",
                                onToggle = { toggleRule(rule.ruleId) }
                            )
                        }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Text(
                    text = "STREAK & >13.00x ENGINE RULES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                StrategyRadioSelectionRow(
                    title = "Strategy 1: Low-Multiplier Streak",
                    subtitle = "Trigger after ${localStrategyConfig.strategy1RequiredLowRounds} consecutive rounds < ${localStrategyConfig.strategy1LowThreshold}x",
                    selected = localStrategyConfig.isStrategy1Enabled,
                    rowTag = "settings_strategy_row_STRATEGY_1",
                    radioTag = "settings_strategy_radio_STRATEGY_1",
                    onToggle = {
                        applyConfigUpdate(
                            localStrategyConfig.copy(
                                isStrategy1Enabled = !localStrategyConfig.isStrategy1Enabled
                            )
                        )
                    }
                )

                StrategyRadioSelectionRow(
                    title = "Strategy 2: High-Multiplier Post-Wait",
                    subtitle = "After > ${localStrategyConfig.strategy2HighThreshold}x, wait ${localStrategyConfig.strategy2WaitRounds} rounds then bet",
                    selected = localStrategyConfig.isStrategy2Enabled,
                    rowTag = "settings_strategy_row_STRATEGY_2",
                    radioTag = "settings_strategy_radio_STRATEGY_2",
                    onToggle = {
                        applyConfigUpdate(
                            localStrategyConfig.copy(
                                isStrategy2Enabled = !localStrategyConfig.isStrategy2Enabled
                            )
                        )
                    }
                )

                StrategyRadioSelectionRow(
                    title = "High-Multiplier Loss Rule (>13.00x Double)",
                    subtitle = "Automatically double stake after any loss above 13.00x",
                    selected = localStrategyConfig.isHighMultiplierLossRuleEnabled,
                    rowTag = "settings_strategy_row_HIGH_MULT_LOSS",
                    radioTag = "settings_strategy_radio_HIGH_MULT_LOSS",
                    onToggle = {
                        applyConfigUpdate(
                            localStrategyConfig.copy(
                                isHighMultiplierLossRuleEnabled = !localStrategyConfig.isHighMultiplierLossRuleEnabled
                            )
                        )
                    }
                )
            }
        }

        // Strategy Notification Settings Card (Section 16)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_notifications_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Strategy Notifications",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STRATEGY NOTIFICATION SETTINGS",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Controls Android alerts emitted when custom multiplier pattern strategies approach, trigger, or reset.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StrategyToggleRow(
                    label = "Master Strategy Notifications",
                    checked = localNotifSettings.masterEnabled,
                    testTag = "settings_notif_master_switch"
                ) { applyNotifUpdate(localNotifSettings.copy(masterEnabled = it)) }

                StrategyToggleRow(
                    label = "Approaching Alerts (e.g. 7 of 8)",
                    checked = localNotifSettings.approachingEnabled,
                    testTag = "settings_notif_approaching_switch"
                ) { applyNotifUpdate(localNotifSettings.copy(approachingEnabled = it)) }

                StrategyToggleRow(
                    label = "Pattern Reached Alerts",
                    checked = localNotifSettings.reachedEnabled,
                    testTag = "settings_notif_reached_switch"
                ) { applyNotifUpdate(localNotifSettings.copy(reachedEnabled = it)) }

                StrategyToggleRow(
                    label = "Pattern Invalidated / Reset Alerts",
                    checked = localNotifSettings.invalidatedEnabled,
                    testTag = "settings_notif_invalidated_switch"
                ) { applyNotifUpdate(localNotifSettings.copy(invalidatedEnabled = it)) }

                StrategyToggleRow(
                    label = "History Unavailable Alerts",
                    checked = localNotifSettings.historyUnavailableEnabled,
                    testTag = "settings_notif_history_unavail_switch"
                ) { applyNotifUpdate(localNotifSettings.copy(historyUnavailableEnabled = it)) }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                StrategyToggleRow(
                    label = "Notification Sound",
                    checked = localNotifSettings.soundEnabled,
                    testTag = "settings_notif_sound_switch"
                ) { applyNotifUpdate(localNotifSettings.copy(soundEnabled = it)) }

                StrategyToggleRow(
                    label = "Notification Vibration",
                    checked = localNotifSettings.vibrationEnabled,
                    testTag = "settings_notif_vibration_switch"
                ) { applyNotifUpdate(localNotifSettings.copy(vibrationEnabled = it)) }

                OutlinedTextField(
                    value = repeatIntervalText,
                    onValueChange = { raw ->
                        repeatIntervalText = raw
                        raw.trim().toIntOrNull()?.takeIf { it >= 0 }?.let { mins ->
                            applyNotifUpdate(localNotifSettings.copy(repeatIntervalMinutes = mins))
                        }
                    },
                    label = { Text("Minimum Repeat Cooldown (minutes)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_notif_repeat_input")
                )

                StrategyToggleRow(
                    label = "Quiet Hours",
                    checked = localNotifSettings.quietHoursEnabled,
                    testTag = "settings_notif_quiet_switch"
                ) { applyNotifUpdate(localNotifSettings.copy(quietHoursEnabled = it)) }

                if (localNotifSettings.quietHoursEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = quietStartHourText,
                            onValueChange = { raw ->
                                quietStartHourText = raw
                                raw.trim().toIntOrNull()?.takeIf { it in 0..23 }?.let { h ->
                                    applyNotifUpdate(localNotifSettings.copy(quietStartHour = h))
                                }
                            },
                            label = { Text("Quiet Start (0-23h)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_notif_quiet_start_hour")
                        )
                        OutlinedTextField(
                            value = quietEndHourText,
                            onValueChange = { raw ->
                                quietEndHourText = raw
                                raw.trim().toIntOrNull()?.takeIf { it in 0..23 }?.let { h ->
                                    applyNotifUpdate(localNotifSettings.copy(quietEndHour = h))
                                }
                            },
                            label = { Text("Quiet End (0-23h)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_notif_quiet_end_hour")
                        )
                    }
                }
            }
        }

        // Controller Appearance Customization Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = "Appearance", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CONTROLLER APPEARANCE & STYLING",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("Floating Bubble Opacity (${(opacityValue * 100).toInt()}%)", fontSize = 13.sp)
                Slider(
                    value = opacityValue,
                    onValueChange = { opacityValue = it },
                    valueRange = 0.3f..1.0f,
                    modifier = Modifier.testTag("opacity_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Floating Bubble Size (${bubbleSizeDp.toInt()}dp)", fontSize = 13.sp)
                Slider(
                    value = bubbleSizeDp,
                    onValueChange = { bubbleSizeDp = it },
                    valueRange = 40f..80f,
                    modifier = Modifier.testTag("bubble_size_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Vibration, contentDescription = "Haptics", tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Haptic Feedback on Actions", fontSize = 13.sp)
                    }
                    Switch(
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = { hapticFeedbackEnabled = it },
                        modifier = Modifier.testTag("haptics_switch")
                    )
                }
            }
        }

        var isSaving by remember { mutableStateOf(false) }
        Button(
            enabled = !isSaving,
            onClick = {
                if (isSaving) return@Button
                try {
                    val safeOpacity = opacityValue.takeIf { it.isFinite() }?.coerceIn(0.3f, 1.0f) ?: 0.95f
                    val safeBubbleSize = bubbleSizeDp.takeIf { it.isFinite() }?.coerceIn(40f, 80f) ?: 56f
                    val parsedProfit = stopProfitText.trim().toDoubleOrNull()
                    val parsedLoss = stopLossText.trim().toDoubleOrNull()
                    val parsedRepeat = repeatIntervalText.trim().toIntOrNull()
                    val parsedStartH = quietStartHourText.trim().toIntOrNull()
                    val parsedEndH = quietEndHourText.trim().toIntOrNull()

                    val profitInvalid = localLimits.stopOnProfitEnabled &&
                        (parsedProfit == null || !parsedProfit.isFinite() || parsedProfit < 0.0)
                    val lossInvalid = localLimits.stopOnLossEnabled &&
                        (parsedLoss == null || !parsedLoss.isFinite() || parsedLoss < 0.0)
                    val nonEmptyProfitInvalid = stopProfitText.isNotBlank() &&
                        (parsedProfit == null || !parsedProfit.isFinite() || parsedProfit < 0.0)
                    val nonEmptyLossInvalid = stopLossText.isNotBlank() &&
                        (parsedLoss == null || !parsedLoss.isFinite() || parsedLoss < 0.0)
                    val notifInvalid = (repeatIntervalText.isNotBlank() && (parsedRepeat == null || parsedRepeat < 0)) ||
                        (localNotifSettings.quietHoursEnabled &&
                            (parsedStartH == null || parsedStartH !in 0..23 || parsedEndH == null || parsedEndH !in 0..23))

                    if (profitInvalid || lossInvalid || nonEmptyProfitInvalid || nonEmptyLossInvalid || notifInvalid) {
                        Toast.makeText(
                            context,
                            "Failed to save settings, please check input",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    isSaving = true
                    prefs.edit()
                        .putFloat(ScreenEarnerOverlayService.PREF_BUBBLE_OPACITY, safeOpacity)
                        .putFloat(ScreenEarnerOverlayService.PREF_BUBBLE_SIZE_DP, safeBubbleSize)
                        .putBoolean(ScreenEarnerOverlayService.PREF_HAPTICS_ENABLED, hapticFeedbackEnabled)
                        .apply()

                    val finalizedLimits = localLimits.copy(
                        profitTarget = parsedProfit ?: localLimits.profitTarget.takeIf { it.isFinite() && it >= 0.0 } ?: 1500.0,
                        maxSessionLoss = parsedLoss ?: localLimits.maxSessionLoss.takeIf { it.isFinite() && it >= 0.0 } ?: 1000.0
                    )
                    val finalizedNotif = localNotifSettings.copy(
                        repeatIntervalMinutes = parsedRepeat ?: localNotifSettings.repeatIntervalMinutes,
                        quietStartHour = parsedStartH ?: localNotifSettings.quietStartHour,
                        quietEndHour = parsedEndH ?: localNotifSettings.quietEndHour
                    )
                    onNotificationSettingsChanged(finalizedNotif)
                    onRiskLimitsChanged(finalizedLimits)
                    onSaveAndReturn(localStrategyConfig)
                } catch (e: Exception) {
                    isSaving = false
                    Log.e("SettingsAppearance", "Save failed", e)
                    Toast.makeText(
                        context,
                        "Failed to save settings, please check input",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_appearance_settings_btn")
        ) {
            Icon(Icons.Default.Save, contentDescription = "Save Settings")
            Spacer(modifier = Modifier.width(8.dp))
            Text("SAVE & CLOSE", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StrategyRadioSelectionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    rowTag: String,
    radioTag: String,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        border = BorderStroke(
            width = if (selected) 1.25.dp else 0.75.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag(rowTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onToggle,
                colors = RadioButtonDefaults.colors(
                    selectedColor = ActionStartEmerald,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag(radioTag)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (selected) "ON" else "OFF",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) ActionStartEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp)
            )
        }
    }
}

private fun buildStrategyRuleSummary(rule: StrategyRuleConfig): String {
    val base = if (rule.ruleId.category == StrategyCategory.MULTIPLIER_WAIT) {
        "Wait ${rule.waitMinutes}m after ${rule.minMultiplier}x–${rule.maxMultiplier}x -> fetch ${rule.targetWinsToFetch} wins"
    } else {
        "Trigger after ${rule.requiredLosses} losses -> fetch ${rule.targetWinsToFetch} wins"
    }
    return "$base · ${rule.progressionMode.label}"
}
