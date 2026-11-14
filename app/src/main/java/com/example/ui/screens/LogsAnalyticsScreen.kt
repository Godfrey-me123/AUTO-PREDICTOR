package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.HistoryCollectionMetadata
import com.example.data.engine.MultiplierHistoryAnalyzer
import com.example.data.engine.RibbonHistoryCollector
import com.example.data.engine.TimeWindowFilter
import com.example.data.model.HistoryCollectionStatus
import com.example.data.model.MultiplierRecord
import com.example.data.model.NotificationDeliveryStatus
import com.example.data.model.NotificationHistoryRecord
import com.example.data.model.NotificationInteractionStatus
import com.example.data.model.SessionLog
import com.example.data.model.StrategyNotificationType
import com.example.data.model.TimestampSource
import com.example.ui.theme.ActionBetGreen
import com.example.ui.theme.ActionCashOutAmber
import com.example.ui.theme.ActionStartEmerald
import com.example.ui.theme.ActionStopRed
import java.util.Locale

private enum class LogsSectionTab {
    RIBBON_GRID_30M,
    AUTO_BETS,
    ALERTS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogsAnalyticsScreen(
    sessionLogs: List<SessionLog>,
    multiplierRecords: List<MultiplierRecord> = emptyList(),
    historyMetadata: HistoryCollectionMetadata = HistoryCollectionMetadata(),
    notificationHistory: List<NotificationHistoryRecord> = emptyList(),
    onClearLogs: () -> Unit,
    onClearMultiplierRecords: () -> Unit = {},
    onClearNotificationHistory: () -> Unit = {},
    onCollectHistory: () -> Unit = {}
) {
    var selectedSection by remember { mutableStateOf(LogsSectionTab.RIBBON_GRID_30M) }
    var selectedWindow by remember { mutableStateOf(TimeWindowFilter.THIRTY_MINUTES) }
    var gridColumns by remember { mutableIntStateOf(5) }

    val nowMs = System.currentTimeMillis()
    val cutoff30m = nowMs - RibbonHistoryCollector.ROLLING_WINDOW_MS
    val ribbon30mRecords = remember(multiplierRecords, cutoff30m) {
        multiplierRecords
            .filter { it.crashTimestamp >= cutoff30m }
            .sortedWith(
                compareByDescending<MultiplierRecord> { it.crashTimestamp }
                    .thenBy { it.sequenceIndex }
                    .thenByDescending { it.id }
            )
    }

    val windowStats = remember(multiplierRecords, selectedWindow, nowMs) {
        MultiplierHistoryAnalyzer.analyzeWindow(multiplierRecords, selectedWindow, nowMs)
    }
    val spotlight1h = remember(multiplierRecords, nowMs) {
        MultiplierHistoryAnalyzer.getLastHourSpotlight(multiplierRecords, nowMs)
    }

    val totalProfit = sessionLogs.sumOf { it.netProfit }
    val winCount = sessionLogs.count { it.outcome == "WIN" }
    val totalBetCount = sessionLogs.size
    val winRate = if (totalBetCount > 0) (winCount.toDouble() / totalBetCount * 100.0) else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Context Clear Action
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HISTORY & ALERTS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedButton(
                    onClick = {
                        when (selectedSection) {
                            LogsSectionTab.RIBBON_GRID_30M -> onClearMultiplierRecords()
                            LogsSectionTab.AUTO_BETS -> onClearLogs()
                            LogsSectionTab.ALERTS -> onClearNotificationHistory()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier.testTag("clear_logs_btn")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (selectedSection) {
                            LogsSectionTab.RIBBON_GRID_30M -> "CLEAR ROUNDS"
                            LogsSectionTab.AUTO_BETS -> "CLEAR BETS"
                            LogsSectionTab.ALERTS -> "CLEAR ALERTS"
                        },
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Primary 3-Tab Selector: Game Ribbon Grid (30m) | Auto Bets | Alerts
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSection == LogsSectionTab.RIBBON_GRID_30M,
                    onClick = { selectedSection = LogsSectionTab.RIBBON_GRID_30M },
                    label = { Text("Ribbon (${ribbon30mRecords.size})", fontSize = 11.sp, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.GridView, contentDescription = "Ribbon Grid", modifier = Modifier.size(15.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_ribbon_grid")
                )
                FilterChip(
                    selected = selectedSection == LogsSectionTab.AUTO_BETS,
                    onClick = { selectedSection = LogsSectionTab.AUTO_BETS },
                    label = { Text("Bets ($totalBetCount)", fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_auto_bets")
                )
                FilterChip(
                    selected = selectedSection == LogsSectionTab.ALERTS,
                    onClick = { selectedSection = LogsSectionTab.ALERTS },
                    label = { Text("Alerts (${notificationHistory.size})", fontSize = 11.sp, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = "Alerts", modifier = Modifier.size(15.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_notification_history")
                )
            }
        }

        when (selectedSection) {
            LogsSectionTab.RIBBON_GRID_30M -> {
                // Section 14 Collection Status & 30m Window Metadata Card
                item {
                    val statusColor = when (historyMetadata.status) {
                        HistoryCollectionStatus.HISTORY_COMPLETE -> ActionStartEmerald
                        HistoryCollectionStatus.HISTORY_PARTIAL,
                        HistoryCollectionStatus.COLLECTING_VISIBLE_HISTORY,
                        HistoryCollectionStatus.EXPANDING_HISTORY,
                        HistoryCollectionStatus.SCROLLING_HISTORY -> ActionCashOutAmber
                        HistoryCollectionStatus.HISTORY_UNAVAILABLE,
                        HistoryCollectionStatus.HISTORY_ERROR -> ActionStopRed
                        HistoryCollectionStatus.HISTORY_NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_collection_status_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "GAME RIBBON HISTORY (LAST 30 MIN)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = historyMetadata.statusReason,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = statusColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, statusColor),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = historyMetadata.status.displayLabel,
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatMiniTile(
                                    title = "30m Count",
                                    value = "${ribbon30mRecords.size}",
                                    accent = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                StatMiniTile(
                                    title = "Latest",
                                    value = ribbon30mRecords.firstOrNull()?.let {
                                        String.format(Locale.US, "%.2fx", it.multiplier)
                                    } ?: "--",
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                StatMiniTile(
                                    title = "Timestamps",
                                    value = ribbon30mRecords.firstOrNull()?.timestampSource?.displayLabel ?: "None",
                                    accent = if (ribbon30mRecords.firstOrNull()?.timestampSource == TimestampSource.GAME_PROVIDED) {
                                        ActionStartEmerald
                                    } else {
                                        ActionCashOutAmber
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (historyMetadata.lastCollectionAttemptMs > 0L) {
                                        "Last attempt: ${MultiplierHistoryAnalyzer.formatFullTimestamp(historyMetadata.lastCollectionAttemptMs)}"
                                    } else {
                                        "Waiting for active game window..."
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = onCollectHistory,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("collect_ribbon_history_btn")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Collect History", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("COLLECT HISTORY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Responsive 4-col / 5-col × 7–8 rows Game Ribbon Grid
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ribbon_grid_container_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RECENT ROUNDS GRID (${gridColumns} COLS)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = gridColumns == 4,
                                        onClick = { gridColumns = 4 },
                                        label = { Text("4 Cols", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = gridColumns == 5,
                                        onClick = { gridColumns = 5 },
                                        label = { Text("5 Cols", fontSize = 10.sp) }
                                    )
                                }
                            }

                            if (ribbon30mRecords.isEmpty()) {
                                EmptyHistoryMessage("No game ribbon multipliers recorded in the last 30 minutes yet.")
                            } else {
                                // 7-8 visible rows of multiplier tiles (scrollable grid viewport)
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(gridColumns),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp, max = 440.dp)
                                        .testTag("ribbon_history_grid")
                                ) {
                                    items(
                                        items = ribbon30mRecords,
                                        key = { "${it.dedupKey}_${it.id}" }
                                    ) { rec ->
                                        RibbonMultiplierTile(rec)
                                    }
                                }
                            }
                        }
                    }
                }

                // Big Multiplier Spotlight (Last 1 Hour)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("big_multiplier_spotlight_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1065))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Big Multiplier Spotlight",
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "BIG MULTIPLIER SPOTLIGHT (LAST 1 HOUR)",
                                    color = Color(0xFFFDE68A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (spotlight1h != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.2f", spotlight1h.highestMultiplier)}x",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 30.sp,
                                        modifier = Modifier.testTag("spotlight_multiplier_value")
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Time: ${MultiplierHistoryAnalyzer.formatFullTimestamp(spotlight1h.crashTimestamp)}",
                                            color = Color(0xFFE9D5FF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.testTag("spotlight_timestamp")
                                        )
                                        Text(
                                            text = spotlight1h.platform,
                                            color = Color(0xFFC4B5FD),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Waiting for verified rounds in the last 1 hour...",
                                    color = Color(0xFFD8B4FE),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Rolling Time Window Analytics
                item {
                    Column {
                        Text(
                            text = "STATISTICAL WINDOW",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TimeWindowFilter.entries.forEach { window ->
                                FilterChip(
                                    selected = selectedWindow == window,
                                    onClick = { selectedWindow = window },
                                    label = { Text(window.label, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = window.label,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("window_chip_${window.label}")
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("window_stats_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "STATISTICAL BREAKDOWN (${selectedWindow.label.uppercase()})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatMiniTile(
                                    title = "Total Rounds",
                                    value = "${windowStats.totalRounds}",
                                    accent = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                StatMiniTile(
                                    title = "< 2.00x",
                                    value = "${windowStats.below2xCount}",
                                    accent = Color(0xFFEF4444),
                                    modifier = Modifier.weight(1f)
                                )
                                StatMiniTile(
                                    title = "> 50.00x",
                                    value = "${windowStats.above50xCount}",
                                    accent = ActionCashOutAmber,
                                    modifier = Modifier.weight(1f)
                                )
                                StatMiniTile(
                                    title = "> 100.00x",
                                    value = "${windowStats.above100xCount}",
                                    accent = ActionStartEmerald,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider()

                            ThresholdTimestampSection(
                                title = "Above 100.00x (${windowStats.above100xCount})",
                                records = windowStats.above100xRecords,
                                badgeColor = ActionStartEmerald
                            )
                            ThresholdTimestampSection(
                                title = "Above 50.00x (${windowStats.above50xCount})",
                                records = windowStats.above50xRecords,
                                badgeColor = ActionCashOutAmber
                            )
                            ThresholdTimestampSection(
                                title = "Below 2.00x (${windowStats.below2xCount})",
                                records = windowStats.below2xRecords,
                                badgeColor = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }

            LogsSectionTab.AUTO_BETS -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMiniTile(
                            title = "Net Profit",
                            value = "${if (totalProfit >= 0) "+" else ""}${String.format(Locale.US, "%.2f", totalProfit)}",
                            accent = if (totalProfit >= 0) ActionBetGreen else Color.Red,
                            modifier = Modifier.weight(1f)
                        )
                        StatMiniTile(
                            title = "Win Rate",
                            value = "${String.format(Locale.US, "%.1f", winRate)}%",
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        StatMiniTile(
                            title = "Total Bets",
                            value = "$totalBetCount",
                            accent = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (sessionLogs.isEmpty()) {
                    item {
                        EmptyHistoryMessage("No automated bet logs recorded yet.")
                    }
                } else {
                    items(sessionLogs, key = { "bet_${it.id}_${it.timestamp}" }) { log ->
                        SessionLogCard(log)
                    }
                }
            }

            LogsSectionTab.ALERTS -> {
                if (notificationHistory.isEmpty()) {
                    item {
                        EmptyHistoryMessage("No strategy notifications recorded yet. Enable or create a custom pattern strategy in the Strategy tab.")
                    }
                } else {
                    items(notificationHistory, key = { "notif_${it.id}_${it.timestampMs}" }) { item ->
                        NotificationHistoryCard(item)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun RibbonMultiplierTile(record: MultiplierRecord) {
    val tileColor = when {
        record.multiplier >= 100.0 -> Color(0xFF9333EA) // Vivid Purple >= 100x
        record.multiplier >= 10.0 -> Color(0xFFC026D3)  // Magenta >= 10x
        record.multiplier >= 2.0 -> Color(0xFF0284C7)   // Blue 2x..9.99x
        else -> Color(0xFF475569)                       // Slate < 2.00x
    }
    Surface(
        color = tileColor.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, tileColor.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${String.format(Locale.US, "%.2f", record.multiplier)}x${if (record.hasGapBefore) "*" else ""}",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = MultiplierHistoryAnalyzer.formatTimestamp(record.crashTimestamp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(20.dp)
                    .height(2.dp)
                    .background(tileColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun NotificationHistoryCard(record: NotificationHistoryRecord) {
    val typeColor = when (record.notificationType) {
        StrategyNotificationType.STRATEGY_APPROACHING -> ActionCashOutAmber
        StrategyNotificationType.STRATEGY_REACHED -> ActionStartEmerald
        StrategyNotificationType.STRATEGY_INVALIDATED -> MaterialTheme.colorScheme.onSurfaceVariant
        StrategyNotificationType.HISTORY_UNAVAILABLE,
        StrategyNotificationType.STRATEGY_PAUSED -> ActionStopRed
    }
    val statusColor = when (record.deliveryStatus) {
        NotificationDeliveryStatus.DELIVERED -> ActionStartEmerald
        NotificationDeliveryStatus.PERMISSION_DENIED,
        NotificationDeliveryStatus.FAILED -> ActionStopRed
        else -> ActionCashOutAmber
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_history_card_${record.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.strategyName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = typeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = record.notificationType.displayLabel,
                        color = typeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = record.message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append(MultiplierHistoryAnalyzer.formatFullTimestamp(record.timestampMs))
                        record.triggeringMultiplier?.let {
                            append(" · @ ${String.format(Locale.US, "%.2fx", it)}")
                        }
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = record.deliveryStatus.displayLabel,
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (record.interactionStatus == NotificationInteractionStatus.OPENED_BY_USER) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Opened",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniTile(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accent, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThresholdTimestampSection(
    title: String,
    records: List<MultiplierRecord>,
    badgeColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = badgeColor
        )
        if (records.isEmpty()) {
            Text(
                text = "None in selected window",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                records.take(12).forEach { rec ->
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%.2f", rec.multiplier)}x @ ${MultiplierHistoryAnalyzer.formatTimestamp(rec.crashTimestamp)}",
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (records.size > 12) {
                    Text(
                        text = "+${records.size - 12} more",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionLogCard(log: SessionLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (log.outcome == "WIN") ActionBetGreen else Color.Red,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = log.outcome,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Round #${log.roundNumber} (${log.platform})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "${log.triggerReason} • ${MultiplierHistoryAnalyzer.formatTimestamp(log.timestamp)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format(Locale.US, "%.2f", log.detectedMultiplier)}x",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Stake: ${log.stake} | Net: ${if (log.netProfit >= 0) "+" else ""}${String.format(Locale.US, "%.2f", log.netProfit)}",
                    fontSize = 11.sp,
                    color = if (log.netProfit >= 0) ActionBetGreen else Color.Red
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryMessage(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
