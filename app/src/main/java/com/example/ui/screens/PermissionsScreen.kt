package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.service.ScreenEarnerAccessibilityService
import com.example.ui.theme.ActionBetGreen
import com.example.ui.theme.PrimaryIndigo

@Composable
fun PermissionsScreen(scrollable: Boolean = true) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isDrawOverAppsGranted = Settings.canDrawOverlays(context)
    val isAccessibilityConnected = ScreenEarnerAccessibilityService.isServiceConnected()
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isBatteryUnrestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else {
        true
    }

    var isNotificationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationGranted = granted
    }

    val containerModifier = if (scrollable) {
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    Column(
        modifier = containerModifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Banner Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Permissions Header",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SYSTEM PERMISSIONS & ONBOARDING",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Screen Earner requires system permissions to render floating controls over game apps, read multipliers via accessibility, and deliver strategy alerts.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Draw Over Other Apps Permission Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDrawOverAppsGranted) Color(0xFF1E293B) else Color(0xFF1E1B2E)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Draw Over Apps",
                            tint = if (isDrawOverAppsGranted) ActionBetGreen else Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Draw Over Other Apps",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    PermissionStatusBadge(
                        granted = isDrawOverAppsGranted,
                        grantedLabel = "GRANTED",
                        missingLabel = "NOT GRANTED"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Allows Screen Earner to display the floating main bubble, compact menu, and independent Bet/Cash Out task robots on top of BetPawa, SportyBet, or Chrome.",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDrawOverAppsGranted) ActionBetGreen else PrimaryIndigo
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_overlay_settings_btn")
                ) {
                    Text(
                        text = if (isDrawOverAppsGranted) "OVERLAY PERMISSION GRANTED (TAP TO MANAGE)" else "OPEN DISPLAY OVER APPS SETTINGS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 2. Accessibility Service Permission Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAccessibilityConnected) Color(0xFF1E293B) else Color(0xFF1E1B2E)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = "Accessibility",
                            tint = if (isAccessibilityConnected) ActionBetGreen else Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Accessibility Service",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    PermissionStatusBadge(
                        granted = isAccessibilityConnected,
                        grantedLabel = "CONNECTED",
                        missingLabel = "NOT CONNECTED"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Allows Screen Earner to scan on-screen Aviator multiplier ribbons, verify Bet button readiness, and execute gesture clicks on target coordinates.",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAccessibilityConnected) ActionBetGreen else PrimaryIndigo
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_accessibility_settings_btn")
                ) {
                    Text(
                        text = if (isAccessibilityConnected) "ACCESSIBILITY CONNECTED (TAP TO MANAGE)" else "OPEN ACCESSIBILITY SYSTEM SETTINGS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 3. Notifications Permission Card (Android 13+)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notification_permission_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isNotificationGranted) Color(0xFF1E293B) else Color(0xFF1E1B2E)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications Permission",
                            tint = if (isNotificationGranted) ActionBetGreen else Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Strategy Notifications",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    PermissionStatusBadge(
                        granted = isNotificationGranted,
                        grantedLabel = "GRANTED",
                        missingLabel = "REQUIRED"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Delivers high-priority alerts when your custom multiplier patterns approach or trigger, even while Screen Earner is minimized or in the background.",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationGranted) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNotificationGranted) ActionBetGreen else PrimaryIndigo
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("request_notification_permission_btn")
                ) {
                    Text(
                        text = if (isNotificationGranted) "NOTIFICATIONS GRANTED (TAP TO MANAGE)" else "ALLOW STRATEGY NOTIFICATIONS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 4. Unrestricted Background Activity (Battery Optimization Exemption) Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isBatteryUnrestricted) Color(0xFF1E293B) else Color(0xFF1E1B2E)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Unrestricted Background Activity",
                            tint = if (isBatteryUnrestricted) ActionBetGreen else Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Unrestricted Background Activity",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    PermissionStatusBadge(
                        granted = isBatteryUnrestricted,
                        grantedLabel = "UNRESTRICTED",
                        missingLabel = "REQUIRED"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Keeps Screen Earner's multi-window monitor, scheduled operating hours, and Keep-Awake service alive when you switch to another app or split-screen.",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        try {
                            val requestIntent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(requestIntent)
                        } catch (_: Exception) {
                            try {
                                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(fallbackIntent)
                            } catch (_: Exception) {
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBatteryUnrestricted) ActionBetGreen else PrimaryIndigo
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_battery_optimization_btn")
                ) {
                    Text(
                        text = if (isBatteryUnrestricted) "BATTERY UNRESTRICTED (TAP TO MANAGE)" else "ALLOW UNRESTRICTED BACKGROUND ACTIVITY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusBadge(
    granted: Boolean,
    grantedLabel: String,
    missingLabel: String
) {
    Surface(
        color = if (granted) ActionBetGreen.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (granted) ActionBetGreen else Color.Red
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = "Status",
                tint = if (granted) ActionBetGreen else Color.Red,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (granted) grantedLabel else missingLabel,
                color = if (granted) ActionBetGreen else Color.Red,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
