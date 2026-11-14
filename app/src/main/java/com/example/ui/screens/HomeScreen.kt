package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.AutomationState
import com.example.data.model.BalanceDetectionStatus
import com.example.data.model.GameState
import com.example.data.model.RiskLimits
import com.example.data.model.StrategyConfig
import com.example.service.AccessibilityConnectionStatus
import com.example.service.ScreenEarnerAccessibilityService
import com.example.ui.theme.ActionBetGreen
import com.example.ui.theme.ActionStartEmerald
import com.example.ui.theme.ActionStopRed
import com.example.ui.theme.PrimaryIndigo
import java.util.Locale

@Composable
fun HomeScreen(
    gameState: GameState,
    automationState: AutomationState,
    isOverlayServiceRunning: Boolean,
    strategyConfig: StrategyConfig?,
    riskLimits: RiskLimits?,
    onToggleOverlayService: () -> Unit,
    onStartAutomation: () -> Unit,
    onPauseAutomation: () -> Unit,
    onTriggerEmergencyStop: () -> Unit = {},
    onClearEmergencyStop: () -> Unit = {},
    onRetryBalanceDetection: () -> Unit = {},
    onSaveStrategyConfig: (StrategyConfig) -> Unit,
    onSaveRiskLimits: (RiskLimits) -> Unit,
    onNavigateToOverlayManager: () -> Unit,
    onNavigateToStrategy: () -> Unit,
    onNavigateToRisk: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isDrawOverAppsGranted = Settings.canDrawOverlays(context)
    val a11yStatus by ScreenEarnerAccessibilityService.connectionStatus.collectAsState()

    val config = strategyConfig ?: StrategyConfig()
    val limits = riskLimits ?: RiskLimits()
    val isEmergencyStopped = limits.emergencyStop

    var baseStakeText by remember(config.baseStake) {
        mutableStateOf(
            if (config.baseStake % 1.0 == 0.0) config.baseStake.toLong().toString() else config.baseStake.toString()
        )
    }
    var targetMultiplierText by remember(config.targetMultiplier) {
        mutableStateOf(
            if (config.targetMultiplier % 1.0 == 0.0) config.targetMultiplier.toLong().toString() else config.targetMultiplier.toString()
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B2E),
                        Color(0xFF111318)
                    )
                )
            )
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(2.dp))

        // App Emblem Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shadow(8.dp, CircleShape)
                    .background(PrimaryIndigo, CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SE",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "SCREEN EARNER",
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Automated Game Overlay & State Engine",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }

        // ==========================================
        // 1. EMERGENCY STOP ALERT / CONTROL (Section 7, Req 8)
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isEmergencyStopped) Color(0xFF450A0A) else Color(0xFF1E293B)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (isEmergencyStopped) ActionStopRed else Color(0xFF334155)
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isEmergencyStopped) Icons.Default.Dangerous else Icons.Default.Warning,
                            contentDescription = "Emergency Stop",
                            tint = if (isEmergencyStopped) ActionStopRed else Color(0xFFF59E0B),
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = if (isEmergencyStopped) "EMERGENCY STOP ENGAGED" else "SAFETY CONTROLS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isEmergencyStopped) Color(0xFFFCA5A5) else Color(0xFFF8FAFC)
                            )
                            Text(
                                text = if (isEmergencyStopped) "All gestures blocked. Automation locked." else "Instant kill-switch halts all action",
                                fontSize = 10.sp,
                                color = if (isEmergencyStopped) Color(0xFFEF4444) else Color(0xFF94A3B8)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (isEmergencyStopped) {
                                onClearEmergencyStop()
                            } else {
                                onTriggerEmergencyStop()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEmergencyStopped) ActionStartEmerald else ActionStopRed
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("home_emergency_stop_toggle_btn")
                    ) {
                        Text(
                            text = if (isEmergencyStopped) "RESET STOP" else "EMERGENCY STOP",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. SYSTEM PERMISSIONS & SERVICE HEALTH (Section 1)
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Permissions",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SERVICE HEALTH & SYSTEM PERMISSIONS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        letterSpacing = 0.5.sp
                    )
                }

                // Overlay Permission Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Overlay",
                            tint = if (isDrawOverAppsGranted) ActionBetGreen else Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Display Over Other Apps",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFF8FAFC)
                            )
                            Text(
                                text = if (isDrawOverAppsGranted) "Overlay bubble authorized" else "Permission required for floating SE button",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDrawOverAppsGranted) ActionBetGreen.copy(alpha = 0.2f) else PrimaryIndigo
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("overlay_permission_btn")
                    ) {
                        Text(
                            text = if (isDrawOverAppsGranted) "GRANTED" else "ENABLE",
                            color = if (isDrawOverAppsGranted) ActionBetGreen else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                // Accessibility Service Explicit Status Row (Section 1)
                val a11yColor = when (a11yStatus) {
                    AccessibilityConnectionStatus.CONNECTED -> ActionBetGreen
                    AccessibilityConnectionStatus.CONNECTING -> Color(0xFFF59E0B)
                    AccessibilityConnectionStatus.ERROR -> ActionStopRed
                    AccessibilityConnectionStatus.DISCONNECTED -> Color(0xFF94A3B8)
                }
                val a11yActionLabel = when (a11yStatus) {
                    AccessibilityConnectionStatus.CONNECTED -> "CONNECTED"
                    AccessibilityConnectionStatus.CONNECTING -> "CONNECTING"
                    AccessibilityConnectionStatus.ERROR -> "RECONNECT"
                    AccessibilityConnectionStatus.DISCONNECTED -> "CONNECT"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = "Accessibility",
                            tint = a11yColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Accessibility Automation Reader",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFF8FAFC)
                            )
                            Text(
                                text = when (a11yStatus) {
                                    AccessibilityConnectionStatus.CONNECTED -> "Service healthy — screen reader & clicks active"
                                    AccessibilityConnectionStatus.CONNECTING -> "Binding accessibility service..."
                                    AccessibilityConnectionStatus.ERROR -> "Service error detected — tap to restart in Settings"
                                    AccessibilityConnectionStatus.DISCONNECTED -> "Required for reading game screen & synthetic clicks"
                                },
                                fontSize = 10.sp,
                                color = if (a11yStatus == AccessibilityConnectionStatus.ERROR) ActionStopRed else Color(0xFF94A3B8)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (a11yStatus == AccessibilityConnectionStatus.CONNECTED) {
                                ActionBetGreen.copy(alpha = 0.2f)
                            } else if (a11yStatus == AccessibilityConnectionStatus.ERROR) {
                                ActionStopRed.copy(alpha = 0.25f)
                            } else {
                                PrimaryIndigo
                            }
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("accessibility_permission_btn")
                    ) {
                        Text(
                            text = a11yActionLabel,
                            color = if (a11yStatus == AccessibilityConnectionStatus.CONNECTED) ActionBetGreen else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3. GAME DETECTION & ACCOUNT BALANCE (Sections 3 & 4)
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Game State",
                            tint = if (gameState.isDetected) ActionBetGreen else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GAME STATE & DETECTED BALANCE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            letterSpacing = 0.5.sp
                        )
                    }

                    Surface(
                        color = if (gameState.isDetected) ActionBetGreen.copy(alpha = 0.2f) else Color(0xFF334155),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (gameState.isDetected) gameState.platformName.ifBlank { "DETECTED" } else "WAITING",
                            color = if (gameState.isDetected) ActionBetGreen else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Balance display with explicit BalanceDetectionStatus chip
                val balanceAmount = gameState.detectedBalance
                val currency = gameState.detectedCurrency ?: "TZS"
                val balStatus = gameState.balanceStatus

                val statusBadgeColor = when (balStatus) {
                    BalanceDetectionStatus.VERIFIED -> ActionBetGreen
                    BalanceDetectionStatus.DETECTED -> Color(0xFF38BDF8)
                    BalanceDetectionStatus.SCANNING -> Color(0xFFFBBF24)
                    BalanceDetectionStatus.STALE -> Color(0xFFF97316)
                    BalanceDetectionStatus.UNAVAILABLE -> Color(0xFFEF4444)
                }

                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Account Balance:",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Surface(
                                    color = statusBadgeColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = balStatus.name,
                                        color = statusBadgeColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (balanceAmount != null && balanceAmount >= 0.0) {
                                    "${String.format(Locale.US, "%.2f", balanceAmount)} $currency"
                                } else {
                                    "Balance Not Scanned"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (balStatus == BalanceDetectionStatus.VERIFIED || balStatus == BalanceDetectionStatus.DETECTED) {
                                    Color(0xFFF8FAFC)
                                } else {
                                    Color(0xFFFBBF24)
                                }
                            )
                            if (gameState.balanceFailureReason != null) {
                                Text(
                                    text = gameState.balanceFailureReason ?: "",
                                    fontSize = 9.sp,
                                    color = Color(0xFFEF4444),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onRetryBalanceDetection,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("home_retry_balance_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Balance",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SCAN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Detected Targets & Phase Summary
                val targets = buildList {
                    if (gameState.isBetBtnDetected) add("BET BUTTON (READY)")
                    if (gameState.isCashOutBtnDetected) add("CASH OUT (ACTIVE)")
                    if (gameState.isStakeInputDetected) add("STAKE BOX")
                    if (gameState.isDoubleBtnDetected) add("2X")
                    if (gameState.isAddBtnDetected) add("+")
                }
                Text(
                    text = "Status: ${gameState.status} | UI Targets: ${if (targets.isEmpty()) "Waiting for game screen" else targets.joinToString(", ")}",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // ==========================================
        // 4. STRATEGY & STAKE CONFIGURATION (Section 5)
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val enabledRules = remember(config.ruleConfigsJson) {
                    config.getRuleConfigs().filter { it.isEnabled }
                }
                val activeStrategyTitle = if (enabledRules.isEmpty()) "Standard Rules" else enabledRules.joinToString(" • ") { it.ruleId.displayName }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STAKE & STRATEGY PARAMETERS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = activeStrategyTitle,
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = baseStakeText,
                        onValueChange = {
                            baseStakeText = it
                            it.trim().toDoubleOrNull()
                                ?.takeIf { v -> v.isFinite() && v > 0.0 }
                                ?.let { v -> onSaveStrategyConfig(config.copy(baseStake = v)) }
                        },
                        label = { Text("Base Stake") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFF8FAFC),
                            unfocusedTextColor = Color(0xFFF8FAFC),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedLabelColor = ActionStartEmerald,
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_base_stake_input")
                    )

                    OutlinedTextField(
                        value = targetMultiplierText,
                        onValueChange = {
                            targetMultiplierText = it
                            it.trim().toDoubleOrNull()
                                ?.takeIf { v -> v.isFinite() && v >= 1.01 }
                                ?.let { v -> onSaveStrategyConfig(config.copy(targetMultiplier = v)) }
                        },
                        label = { Text("Target Mult (x)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFF8FAFC),
                            unfocusedTextColor = Color(0xFFF8FAFC),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedLabelColor = ActionStartEmerald,
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_target_multiplier_input")
                    )
                }

                // Summary of Risk Controls
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Risk Limits: Stop Loss $${limits.maxSessionLoss} | Target Profit $${limits.profitTarget} | Max Rounds: ${limits.maxTotalRounds}",
                            color = Color(0xFFE2E8F0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Engine State: ${automationState.name} | Current Stake: $${String.format(Locale.US, "%.2f", gameState.currentStake)}",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // ==========================================
        // 5. LATEST ACTION & ACTIVITY REPORT (Section 4 & 9)
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "LATEST ACTION EXECUTION STATUS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1),
                    letterSpacing = 0.5.sp
                )

                val record = gameState.lastActionRecord
                if (record != null) {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Action: ${record.operationName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF8FAFC)
                                )
                                Text(
                                    text = if (record.stateConfirmed) "CONFIRMED" else if (record.gestureDispatched) "DISPATCHED" else "PENDING",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (record.stateConfirmed) ActionBetGreen else Color(0xFFFBBF24)
                                )
                            }
                            val coordsText = record.targetCoordinates?.let { "(${it.first.toInt()}, ${it.second.toInt()})" } ?: "N/A"
                            Text(
                                text = "Coords: $coordsText | Dispatched: ${record.gestureDispatched} | Responded: ${record.interfaceResponded}",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8)
                            )
                            if (record.failureReason != null) {
                                Text(
                                    text = "Reason: ${record.failureReason}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ActionStopRed
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No action executed in this session yet. Waiting for round start...",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // ==========================================
        // 6. HERO LAUNCHER ("FLOATING SE ICON OVER APPS")
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOverlayServiceRunning) Color(0xFF0F291E) else Color(0xFF2E1065)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isOverlayServiceRunning) "FLOATING SE ICON IS ACTIVE OVER APPS" else "ALWAYS-ON-TOP FLOATING UTILITY",
                    color = if (isOverlayServiceRunning) ActionStartEmerald else Color(0xFFA855F7),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (!isOverlayServiceRunning) {
                            onToggleOverlayService()
                        }
                        // Minimize main activity immediately to return user to Chrome / BetPawa
                        (context as? Activity)?.moveTaskToBack(true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOverlayServiceRunning) ActionStartEmerald else PrimaryIndigo
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("launch_floating_se_hero_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = "Launch Overlay",
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isOverlayServiceRunning) "RETURN TO EXTERNAL SCREEN" else "LAUNCH FLOATING SE ICON",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Launches draggable SE floating icon and minimizes this screen so you can operate BetPawa in Chrome.",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
