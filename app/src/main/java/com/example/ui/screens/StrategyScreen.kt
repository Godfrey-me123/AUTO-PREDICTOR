package com.example.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExposurePlus2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import com.example.data.model.CustomPatternStrategy
import com.example.data.model.NotificationPriorityLevel
import com.example.data.model.PatternComparator
import com.example.data.model.PatternCondition
import com.example.data.model.PatternLogicOperator
import com.example.data.model.RepeatNotificationPolicy
import com.example.data.model.StakeProgressionMode
import com.example.data.model.StrategyCategory
import com.example.data.model.StrategyConfig
import com.example.data.model.StrategyRuleConfig
import com.example.ui.theme.ActionCashOutAmber
import com.example.ui.theme.ActionStartEmerald
import com.example.ui.theme.ActionStopRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrategyScreen(
    currentConfig: StrategyConfig?,
    customStrategies: List<CustomPatternStrategy> = emptyList(),
    onSaveCustomStrategy: (CustomPatternStrategy) -> Unit = {},
    onToggleCustomStrategyEnabled: (CustomPatternStrategy, Boolean) -> Unit = { _, _ -> },
    onToggleCustomStrategyNotifications: (CustomPatternStrategy, Boolean) -> Unit = { _, _ -> },
    onDeleteCustomStrategy: (Long) -> Unit = {},
    onSaveConfig: (StrategyConfig) -> Unit
) {
    val context = LocalContext.current
    val config = currentConfig ?: StrategyConfig()

    var ruleConfigs by remember(config.ruleConfigsJson) { mutableStateOf(config.getRuleConfigs()) }
    var isStrategy1Enabled by remember(config.isStrategy1Enabled) { mutableStateOf(config.isStrategy1Enabled) }
    var isStrategy2Enabled by remember(config.isStrategy2Enabled) { mutableStateOf(config.isStrategy2Enabled) }
    var isHighMultiplierLossRuleEnabled by remember(config.isHighMultiplierLossRuleEnabled) { mutableStateOf(config.isHighMultiplierLossRuleEnabled) }

    var strategy1LowThreshold by remember(config.strategy1LowThreshold) { mutableStateOf(config.strategy1LowThreshold.toString()) }
    var strategy1RequiredLowRounds by remember(config.strategy1RequiredLowRounds) { mutableStateOf(config.strategy1RequiredLowRounds.toString()) }
    var strategy2HighThreshold by remember(config.strategy2HighThreshold) { mutableStateOf(config.strategy2HighThreshold.toString()) }
    var strategy2WaitRounds by remember(config.strategy2WaitRounds) { mutableStateOf(config.strategy2WaitRounds.toString()) }
    var baseStake by remember(config.baseStake) { mutableStateOf(config.baseStake.toString()) }
    var targetMultiplier by remember(config.targetMultiplier) { mutableStateOf(config.targetMultiplier.toString()) }
    var stopAfterWins by remember(config.stopAfterConsecutiveWins) { mutableStateOf(config.stopAfterConsecutiveWins.toString()) }
    var sessionTimeoutMinutes by remember(config.sessionTimeoutMinutes) { mutableStateOf(config.sessionTimeoutMinutes.toString()) }
    var isAutoBetEnabled by remember(config.isAutoBetEnabled) { mutableStateOf(config.isAutoBetEnabled) }
    var isAutoCashOutEnabled by remember(config.isAutoCashOutEnabled) { mutableStateOf(config.isAutoCashOutEnabled) }

    var editingCustomStrategy by remember { mutableStateOf<CustomPatternStrategy?>(null) }
    var showCustomBuilder by remember { mutableStateOf(false) }

    fun updateRule(updated: StrategyRuleConfig) {
        ruleConfigs = ruleConfigs.map { if (it.ruleId == updated.ruleId) updated else it }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "STRATEGY & PATTERN ENGINE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Build custom pattern alert strategies (no hardcoded presets) or configure automated progression rules.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 15: User-Defined Custom Pattern Strategies & Alerts
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_pattern_strategies_card"),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CUSTOM PATTERN STRATEGIES & ALERTS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Evaluated chronologically on live 30-minute ribbon history.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                editingCustomStrategy = null
                                showCustomBuilder = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("create_custom_strategy_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New Strategy", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("NEW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (customStrategies.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_strategies_empty_state")
                        ) {
                            Text(
                                text = "No custom strategies defined yet. Tap NEW to create your own threshold, consecutive round, time-window, or sequence notification rule.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customStrategies.forEach { strategy ->
                                CustomStrategyItemCard(
                                    strategy = strategy,
                                    onToggleEnabled = { onToggleCustomStrategyEnabled(strategy, it) },
                                    onToggleNotifications = { onToggleCustomStrategyNotifications(strategy, it) },
                                    onEdit = {
                                        editingCustomStrategy = strategy
                                        showCustomBuilder = true
                                    },
                                    onDelete = { onDeleteCustomStrategy(strategy.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Base Execution Parameters & 3-Minute Guard
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "BASE EXECUTION & TIMEOUT GUARD",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = baseStake,
                            onValueChange = { baseStake = it },
                            label = { Text("Base Stake ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("strategy_base_stake_input")
                        )
                        OutlinedTextField(
                            value = targetMultiplier,
                            onValueChange = { targetMultiplier = it },
                            label = { Text("Target Mult (x)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("strategy_target_mult_input")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = stopAfterWins,
                            onValueChange = { stopAfterWins = it },
                            label = { Text("Fetch Wins & Reset") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("strategy_stop_wins_input")
                        )
                        OutlinedTextField(
                            value = sessionTimeoutMinutes,
                            onValueChange = { sessionTimeoutMinutes = it },
                            label = { Text("Timeout Guard (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("strategy_timeout_minutes_input")
                        )
                    }
                    StrategyToggleRow("Auto-Bet Execution", isAutoBetEnabled) { isAutoBetEnabled = it }
                    StrategyToggleRow("Auto-CashOut at Target", isAutoCashOutEnabled) { isAutoCashOutEnabled = it }
                }
            }
        }

        // Render the 3 Comprehensive Strategy Categories
        StrategyCategory.entries.forEach { category ->
            val categoryRules = ruleConfigs.filter { it.ruleId.category == category }
            item {
                Text(
                    text = category.title.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ActionStartEmerald,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(categoryRules, key = { it.ruleId.name }) { ruleConfig ->
                StrategyRuleCard(
                    ruleConfig = ruleConfig,
                    onRuleChanged = { updateRule(it) }
                )
            }
        }

        // Legacy & >13.00x High Multiplier Rules
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ADVANCED STREAK & >13.00x RULES",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    StrategyToggleRow(
                        label = "High Mult Loss Rule (>13.00x Double)",
                        checked = isHighMultiplierLossRuleEnabled,
                        testTag = "rule_high_mult_switch"
                    ) { isHighMultiplierLossRuleEnabled = it }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    StrategyToggleRow(
                        label = "Strategy 1 (Custom Low Streak)",
                        checked = isStrategy1Enabled,
                        testTag = "rule_strat1_switch"
                    ) { isStrategy1Enabled = it }

                    if (isStrategy1Enabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = strategy1LowThreshold,
                                onValueChange = { strategy1LowThreshold = it },
                                label = { Text("Low Threshold (x)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("strat1_thresh_input")
                            )
                            OutlinedTextField(
                                value = strategy1RequiredLowRounds,
                                onValueChange = { strategy1RequiredLowRounds = it },
                                label = { Text("Required Rounds") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("strat1_rounds_input")
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    StrategyToggleRow(
                        label = "Strategy 2 (Post-High Rounds Wait)",
                        checked = isStrategy2Enabled,
                        testTag = "rule_strat2_switch"
                    ) { isStrategy2Enabled = it }

                    if (isStrategy2Enabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = strategy2HighThreshold,
                                onValueChange = { strategy2HighThreshold = it },
                                label = { Text("High Threshold (x)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("strat2_thresh_input")
                            )
                            OutlinedTextField(
                                value = strategy2WaitRounds,
                                onValueChange = { strategy2WaitRounds = it },
                                label = { Text("Wait Rounds") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("strat2_wait_input")
                            )
                        }
                    }
                }
            }
        }

        // Save & Close Button (Closes ONLY the Strategy editor screen, never exits app)
        item {
            var isSaving by remember { mutableStateOf(false) }
            Button(
                enabled = !isSaving,
                onClick = {
                    if (isSaving) return@Button
                    try {
                        val parsedStake = baseStake.trim().toDoubleOrNull()
                        val parsedTargetMult = targetMultiplier.trim().toDoubleOrNull()
                        val parsedStopWins = stopAfterWins.trim().toIntOrNull()
                        val parsedTimeout = sessionTimeoutMinutes.trim().toIntOrNull()
                        val parsedStrat1Low = strategy1LowThreshold.trim().toDoubleOrNull()
                        val parsedStrat1Rounds = strategy1RequiredLowRounds.trim().toIntOrNull()
                        val parsedStrat2High = strategy2HighThreshold.trim().toDoubleOrNull()
                        val parsedStrat2Wait = strategy2WaitRounds.trim().toIntOrNull()

                        val baseInvalid = parsedStake == null || !parsedStake.isFinite() || parsedStake <= 0.0 ||
                            parsedTargetMult == null || !parsedTargetMult.isFinite() || parsedTargetMult < 1.01 ||
                            parsedStopWins == null || parsedStopWins < 1 ||
                            parsedTimeout == null || parsedTimeout < 1
                        val strat1Invalid = isStrategy1Enabled &&
                            (parsedStrat1Low == null || !parsedStrat1Low.isFinite() || parsedStrat1Low < 1.01 ||
                                parsedStrat1Rounds == null || parsedStrat1Rounds < 1)
                        val strat2Invalid = isStrategy2Enabled &&
                            (parsedStrat2High == null || !parsedStrat2High.isFinite() || parsedStrat2High < 1.01 ||
                                parsedStrat2Wait == null || parsedStrat2Wait < 1)

                        if (baseInvalid || strat1Invalid || strat2Invalid) {
                            Toast.makeText(
                                context,
                                "Failed to save settings, please check input",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }

                        isSaving = true
                        val baseConfig = StrategyConfig(
                            isStrategy1Enabled = isStrategy1Enabled,
                            isStrategy2Enabled = isStrategy2Enabled,
                            isHighMultiplierLossRuleEnabled = isHighMultiplierLossRuleEnabled,
                            strategy1LowThreshold = parsedStrat1Low?.takeIf { it.isFinite() && it >= 1.01 } ?: config.strategy1LowThreshold,
                            strategy1RequiredLowRounds = parsedStrat1Rounds?.coerceAtLeast(1) ?: config.strategy1RequiredLowRounds,
                            strategy2HighThreshold = parsedStrat2High?.takeIf { it.isFinite() && it >= 1.01 } ?: config.strategy2HighThreshold,
                            strategy2WaitRounds = parsedStrat2Wait?.coerceAtLeast(1) ?: config.strategy2WaitRounds,
                            baseStake = parsedStake!!,
                            multiplierProgression = config.multiplierProgression.takeIf { it.isFinite() && it >= 1.0 } ?: 2.0,
                            stopAfterConsecutiveWins = parsedStopWins!!.coerceAtLeast(1),
                            targetMultiplier = parsedTargetMult!!,
                            isAutoBetEnabled = isAutoBetEnabled,
                            isAutoCashOutEnabled = isAutoCashOutEnabled,
                            sessionTimeoutMinutes = parsedTimeout!!.coerceAtLeast(1)
                        ).withUpdatedRuleConfigs(ruleConfigs)
                        onSaveConfig(baseConfig)
                    } catch (e: Exception) {
                        isSaving = false
                        Log.e("StrategyScreen", "Save failed", e)
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
                    .testTag("save_strategy_btn")
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save & Close")
                Spacer(modifier = Modifier.width(8.dp))
                Text("SAVE & CLOSE", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showCustomBuilder) {
        CustomStrategyBuilderDialog(
            initial = editingCustomStrategy,
            onDismiss = {
                showCustomBuilder = false
                editingCustomStrategy = null
            },
            onSave = { built ->
                onSaveCustomStrategy(built)
                showCustomBuilder = false
                editingCustomStrategy = null
            }
        )
    }
}

@Composable
private fun CustomStrategyItemCard(
    strategy: CustomPatternStrategy,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusText = when {
        !strategy.isEnabled -> "DISABLED"
        strategy.currentlyReached -> "DETECTED"
        strategy.currentlyApproaching -> "APPROACHING"
        else -> "MONITORING"
    }
    val statusColor = when {
        !strategy.isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        strategy.currentlyReached -> ActionStartEmerald
        strategy.currentlyApproaching -> ActionCashOutAmber
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(
            width = 1.dp,
            color = if (strategy.isEnabled) statusColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("custom_strategy_card_${strategy.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strategy.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strategy.summaryDescription(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = strategy.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("custom_strategy_switch_${strategy.id}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = strategy.notificationLevel.label,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onToggleNotifications(!strategy.notificationEnabled) },
                        modifier = Modifier.testTag("custom_strategy_notif_btn_${strategy.id}")
                    ) {
                        Icon(
                            imageVector = if (strategy.notificationEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            contentDescription = "Toggle Strategy Notifications",
                            tint = if (strategy.notificationEnabled) ActionStartEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Custom Strategy",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Custom Strategy",
                            tint = ActionStopRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomStrategyBuilderDialog(
    initial: CustomPatternStrategy?,
    onDismiss: () -> Unit,
    onSave: (CustomPatternStrategy) -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var logicOperator by remember { mutableStateOf(initial?.logicOperator ?: PatternLogicOperator.AND) }
    var conditions by remember {
        mutableStateOf(
            initial?.getConditions()?.takeIf { it.isNotEmpty() }
                ?: listOf(PatternCondition(comparator = PatternComparator.LESS_THAN, threshold = 2.00, consecutiveRounds = 8))
        )
    }
    var waitingPeriodText by remember { mutableStateOf((initial?.waitingPeriodSeconds ?: 0).toString()) }
    var priority by remember { mutableStateOf(initial?.notificationLevel ?: NotificationPriorityLevel.HIGH) }
    var approachingEnabled by remember { mutableStateOf(initial?.approachingEnabled ?: true) }
    var notificationEnabled by remember { mutableStateOf(initial?.notificationEnabled ?: true) }
    var customMessage by remember { mutableStateOf(initial?.customMessage ?: "") }
    var repeatPolicy by remember { mutableStateOf(initial?.repeatPolicy ?: RepeatNotificationPolicy.ONCE_PER_OCCURRENCE) }
    var cooldownMinutesText by remember { mutableStateOf((initial?.repeatCooldownMinutes ?: 5).toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (initial == null) "CREATE PATTERN STRATEGY" else "EDIT PATTERN STRATEGY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Strategy Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("builder_strategy_name_input")
                )

                Text("Condition Logic", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PatternLogicOperator.entries.forEach { op ->
                        FilterChip(
                            selected = logicOperator == op,
                            onClick = { logicOperator = op },
                            label = { Text(op.name, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                conditions.forEachIndexed { idx, cond ->
                    ConditionEditorBlock(
                        index = idx,
                        condition = cond,
                        canRemove = conditions.size > 1,
                        onUpdate = { updated ->
                            conditions = conditions.toMutableList().also { it[idx] = updated }
                        },
                        onRemove = {
                            conditions = conditions.toMutableList().also { it.removeAt(idx) }
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        conditions = conditions + PatternCondition(
                            comparator = PatternComparator.LESS_THAN,
                            threshold = 2.00,
                            consecutiveRounds = 5
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Condition", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ADD CONDITION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = waitingPeriodText,
                    onValueChange = { waitingPeriodText = it },
                    label = { Text("Waiting Period After Trigger (seconds, 0 = immediate)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Notification Priority", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NotificationPriorityLevel.entries.forEach { lvl ->
                        FilterChip(
                            selected = priority == lvl,
                            onClick = { priority = lvl },
                            label = { Text(lvl.label, fontSize = 11.sp) }
                        )
                    }
                }

                StrategyToggleRow("Send Approaching Alert (1 round before)", approachingEnabled) {
                    approachingEnabled = it
                }
                StrategyToggleRow("Enable Notifications for This Strategy", notificationEnabled) {
                    notificationEnabled = it
                }

                Text("Repeat Policy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RepeatNotificationPolicy.entries.forEach { pol ->
                        FilterChip(
                            selected = repeatPolicy == pol,
                            onClick = { repeatPolicy = pol },
                            label = { Text(pol.label, fontSize = 10.sp) }
                        )
                    }
                }
                if (repeatPolicy == RepeatNotificationPolicy.COOLDOWN_MINUTES) {
                    OutlinedTextField(
                        value = cooldownMinutesText,
                        onValueChange = { cooldownMinutesText = it },
                        label = { Text("Cooldown Interval (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = customMessage,
                    onValueChange = { customMessage = it },
                    label = { Text("Optional Custom Note in Alert") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCEL")
                    }
                    Button(
                        onClick = {
                            val trimmedName = name.trim()
                            val waitSec = waitingPeriodText.trim().toIntOrNull()?.coerceAtLeast(0)
                            val cooldownMin = cooldownMinutesText.trim().toIntOrNull()?.coerceAtLeast(1) ?: 5
                            if (trimmedName.isEmpty() || waitSec == null || conditions.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Failed to save settings, please check input",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            val built = (initial ?: CustomPatternStrategy(name = trimmedName))
                                .copy(
                                    name = trimmedName,
                                    isEnabled = initial?.isEnabled ?: true,
                                    logicOperator = logicOperator,
                                    waitingPeriodSeconds = waitSec,
                                    notificationLevel = priority,
                                    approachingEnabled = approachingEnabled,
                                    customMessage = customMessage.trim(),
                                    repeatPolicy = repeatPolicy,
                                    repeatCooldownMinutes = cooldownMin,
                                    notificationEnabled = notificationEnabled
                                )
                                .withConditions(conditions)
                            onSave(built)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("builder_save_strategy_btn")
                    ) {
                        Text("SAVE & CLOSE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionEditorBlock(
    index: Int,
    condition: PatternCondition,
    canRemove: Boolean,
    onUpdate: (PatternCondition) -> Unit,
    onRemove: () -> Unit
) {
    var threshText by remember(condition) { mutableStateOf(condition.threshold.toString()) }
    var upperText by remember(condition) { mutableStateOf(condition.upperThreshold?.toString() ?: "10.0") }
    var consecutiveText by remember(condition) { mutableStateOf(condition.consecutiveRounds.toString()) }
    var windowMinText by remember(condition) { mutableStateOf(condition.timeWindowMinutes?.toString() ?: "") }
    var sequenceText by remember(condition) {
        mutableStateOf(condition.sequenceMultipliers.joinToString(", "))
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Condition #${index + 1}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove Condition", tint = ActionStopRed, modifier = Modifier.size(16.dp))
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PatternComparator.entries.forEach { comp ->
                    FilterChip(
                        selected = condition.comparator == comp,
                        onClick = { onUpdate(condition.copy(comparator = comp)) },
                        label = { Text(comp.symbol, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = threshText,
                    onValueChange = { raw ->
                        threshText = raw
                        raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 1.0 }?.let {
                            onUpdate(condition.copy(threshold = it))
                        }
                    },
                    label = { Text(if (condition.comparator == PatternComparator.BETWEEN) "Min (x)" else "Threshold (x)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (condition.comparator == PatternComparator.BETWEEN) {
                    OutlinedTextField(
                        value = upperText,
                        onValueChange = { raw ->
                            upperText = raw
                            raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 1.0 }?.let {
                                onUpdate(condition.copy(upperThreshold = it))
                            }
                        },
                        label = { Text("Max (x)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    OutlinedTextField(
                        value = consecutiveText,
                        onValueChange = { raw ->
                            consecutiveText = raw
                            raw.trim().toIntOrNull()?.coerceAtLeast(1)?.let {
                                onUpdate(condition.copy(consecutiveRounds = it))
                            }
                        },
                        label = { Text("Consecutive") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = windowMinText,
                    onValueChange = { raw ->
                        windowMinText = raw
                        val parsed = raw.trim().toIntOrNull()?.takeIf { it > 0 }
                        onUpdate(condition.copy(timeWindowMinutes = parsed))
                    },
                    label = { Text("Window (min, opt)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = sequenceText,
                    onValueChange = { raw ->
                        sequenceText = raw
                        val seqList = raw.split(",")
                            .mapNotNull { it.trim().toDoubleOrNull()?.takeIf { v -> v.isFinite() && v >= 1.0 } }
                        onUpdate(condition.copy(sequenceMultipliers = seqList))
                    },
                    label = { Text("Exact Seq (e.g. 1.2,1.5)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrategyRuleCard(
    ruleConfig: StrategyRuleConfig,
    onRuleChanged: (StrategyRuleConfig) -> Unit
) {
    var addAmountText by remember(ruleConfig.customAddAmount) {
        mutableStateOf(
            if (ruleConfig.customAddAmount % 1.0 == 0.0) {
                ruleConfig.customAddAmount.toLong().toString()
            } else {
                ruleConfig.customAddAmount.toString()
            }
        )
    }
    var reqLossesText by remember(ruleConfig.requiredLosses) { mutableStateOf(ruleConfig.requiredLosses.toString()) }
    var targetWinsText by remember(ruleConfig.targetWinsToFetch) { mutableStateOf(ruleConfig.targetWinsToFetch.toString()) }
    var waitMinutesText by remember(ruleConfig.waitMinutes) { mutableStateOf(ruleConfig.waitMinutes.toString()) }
    var minMultText by remember(ruleConfig.minMultiplier) { mutableStateOf(ruleConfig.minMultiplier.toString()) }
    var maxMultText by remember(ruleConfig.maxMultiplier) { mutableStateOf(ruleConfig.maxMultiplier.toString()) }

    val isRangeWait = ruleConfig.ruleId.category == StrategyCategory.MULTIPLIER_WAIT

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("strategy_rule_card_${ruleConfig.ruleId.name}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ruleConfig.isEnabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isRangeWait) Icons.Default.Schedule else Icons.Default.TrendingUp,
                        contentDescription = ruleConfig.dynamicTitle(),
                        tint = if (ruleConfig.isEnabled) ActionStartEmerald else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = ruleConfig.dynamicTitle(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = ruleConfig.ruleId.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = ruleConfig.isEnabled,
                    onCheckedChange = { enabled ->
                        onRuleChanged(ruleConfig.copy(isEnabled = enabled))
                    },
                    modifier = Modifier.testTag("rule_switch_${ruleConfig.ruleId.name}")
                )
            }

            if (ruleConfig.isEnabled) {
                Spacer(modifier = Modifier.height(10.dp))

                if (isRangeWait) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minMultText,
                            onValueChange = { v ->
                                minMultText = v
                                v.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                                    onRuleChanged(ruleConfig.copy(minMultiplier = it))
                                }
                            },
                            label = { Text("Min (x)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rule_min_mult_${ruleConfig.ruleId.name}")
                        )
                        OutlinedTextField(
                            value = maxMultText,
                            onValueChange = { v ->
                                maxMultText = v
                                v.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                                    onRuleChanged(ruleConfig.copy(maxMultiplier = it))
                                }
                            },
                            label = { Text("Max (x)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rule_max_mult_${ruleConfig.ruleId.name}")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = waitMinutesText,
                            onValueChange = { v ->
                                waitMinutesText = v
                                v.toIntOrNull()?.coerceAtLeast(1)?.let { onRuleChanged(ruleConfig.copy(waitMinutes = it)) }
                            },
                            label = { Text("Wait (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rule_wait_min_${ruleConfig.ruleId.name}")
                        )
                        OutlinedTextField(
                            value = targetWinsText,
                            onValueChange = { v ->
                                targetWinsText = v
                                v.toIntOrNull()?.coerceAtLeast(1)?.let { onRuleChanged(ruleConfig.copy(targetWinsToFetch = it)) }
                            },
                            label = { Text("Wins to Fetch") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rule_wins_fetch_${ruleConfig.ruleId.name}")
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = reqLossesText,
                            onValueChange = { v ->
                                reqLossesText = v
                                v.toIntOrNull()?.coerceAtLeast(1)?.let { onRuleChanged(ruleConfig.copy(requiredLosses = it)) }
                            },
                            label = { Text("Loss Trigger Count") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rule_loss_count_${ruleConfig.ruleId.name}")
                        )
                        OutlinedTextField(
                            value = targetWinsText,
                            onValueChange = { v ->
                                targetWinsText = v
                                v.toIntOrNull()?.coerceAtLeast(1)?.let { onRuleChanged(ruleConfig.copy(targetWinsToFetch = it)) }
                            },
                            label = { Text("Wins to Fetch") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rule_wins_fetch_${ruleConfig.ruleId.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Stake Progression After Loss (Fetch ${ruleConfig.targetWinsToFetch} Wins):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = ruleConfig.progressionMode == StakeProgressionMode.ADD_AMOUNT,
                        onClick = {
                            onRuleChanged(ruleConfig.copy(progressionMode = StakeProgressionMode.ADD_AMOUNT))
                        },
                        label = { Text("Option A: Add Amount", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (ruleConfig.progressionMode == StakeProgressionMode.ADD_AMOUNT) {
                                    Icons.Default.Check
                                } else {
                                    Icons.Default.Add
                                },
                                contentDescription = "Add Amount",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("rule_option_add_${ruleConfig.ruleId.name}")
                    )

                    FilterChip(
                        selected = ruleConfig.progressionMode == StakeProgressionMode.DOUBLE_AMOUNT,
                        onClick = {
                            onRuleChanged(ruleConfig.copy(progressionMode = StakeProgressionMode.DOUBLE_AMOUNT))
                        },
                        label = { Text("Option B: Double (2x)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (ruleConfig.progressionMode == StakeProgressionMode.DOUBLE_AMOUNT) {
                                    Icons.Default.Check
                                } else {
                                    Icons.Default.ExposurePlus2
                                },
                                contentDescription = "Double Amount",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("rule_option_double_${ruleConfig.ruleId.name}")
                    )
                }

                if (ruleConfig.progressionMode == StakeProgressionMode.ADD_AMOUNT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addAmountText,
                        onValueChange = { value ->
                            addAmountText = value
                            value.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }?.let { parsed ->
                                onRuleChanged(ruleConfig.copy(customAddAmount = parsed))
                            }
                        },
                        label = { Text("Custom Add Amount Per Loss (+$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rule_add_amount_input_${ruleConfig.ruleId.name}")
                    )
                }
            }
        }
    }
}

@Composable
fun StrategyToggleRow(
    label: String,
    checked: Boolean,
    testTag: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
