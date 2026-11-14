package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

enum class PatternComparator(val symbol: String, val label: String) {
    LESS_THAN("<", "Below (<)"),
    LESS_OR_EQUAL("<=", "At or Below (<=)"),
    GREATER_THAN(">", "Above (>)"),
    GREATER_OR_EQUAL(">=", "At or Above (>=)"),
    BETWEEN("between", "Between Range");

    fun matches(value: Double, threshold: Double, upperThreshold: Double? = null): Boolean {
        if (!value.isFinite() || !threshold.isFinite()) return false
        return when (this) {
            LESS_THAN -> value < threshold
            LESS_OR_EQUAL -> value <= threshold
            GREATER_THAN -> value > threshold
            GREATER_OR_EQUAL -> value >= threshold
            BETWEEN -> {
                val upper = upperThreshold?.takeIf { it.isFinite() && it >= threshold } ?: threshold
                value in threshold..upper
            }
        }
    }
}

enum class PatternLogicOperator(val label: String) {
    AND("ALL conditions match (AND)"),
    OR("ANY condition matches (OR)")
}

enum class NotificationPriorityLevel(val label: String) {
    STANDARD("Standard"),
    HIGH("High"),
    URGENT("Urgent")
}

enum class RepeatNotificationPolicy(val label: String) {
    ONCE_PER_OCCURRENCE("Once per occurrence"),
    COOLDOWN_MINUTES("Repeat after cooldown")
}

data class PatternCondition(
    val comparator: PatternComparator = PatternComparator.LESS_THAN,
    val threshold: Double = 2.00,
    val upperThreshold: Double? = null,
    val consecutiveRounds: Int = 5,
    val timeWindowMinutes: Int? = null,
    val sequenceMultipliers: List<Double> = emptyList()
) {
    fun summaryText(): String {
        val multFormatted = String.format(Locale.US, "%.2f", threshold)
        val rangeText = if (comparator == PatternComparator.BETWEEN && upperThreshold != null) {
            val upperFormatted = String.format(Locale.US, "%.2f", upperThreshold)
            "${multFormatted}x–${upperFormatted}x"
        } else {
            "${comparator.symbol} ${multFormatted}x"
        }
        val windowSuffix = if (timeWindowMinutes != null && timeWindowMinutes > 0) {
            " within ${timeWindowMinutes}m"
        } else {
            ""
        }
        return if (sequenceMultipliers.isNotEmpty()) {
            "Sequence [${sequenceMultipliers.joinToString(", ") { "${String.format(Locale.US, "%.2f", it)}x" }}]$windowSuffix"
        } else {
            "$consecutiveRounds consecutive rounds $rangeText$windowSuffix"
        }
    }
}

@Entity(tableName = "custom_pattern_strategies")
data class CustomPatternStrategy(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val logicOperator: PatternLogicOperator = PatternLogicOperator.AND,
    val conditionsJson: String = serializeConditions(emptyList()),
    val waitingPeriodSeconds: Int = 0,
    val notificationLevel: NotificationPriorityLevel = NotificationPriorityLevel.HIGH,
    val approachingEnabled: Boolean = true,
    val customMessage: String = "",
    val repeatPolicy: RepeatNotificationPolicy = RepeatNotificationPolicy.ONCE_PER_OCCURRENCE,
    val repeatCooldownMinutes: Int = 5,
    val notificationEnabled: Boolean = true,
    val lastEvaluatedRecordKey: String? = null,
    val lastTriggeredOccurrenceKey: String? = null,
    val lastApproachedOccurrenceKey: String? = null,
    val lastNotifiedAtMs: Long = 0L,
    val currentlyApproaching: Boolean = false,
    val currentlyReached: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun getConditions(): List<PatternCondition> = deserializeConditions(conditionsJson)

    fun withConditions(conditions: List<PatternCondition>): CustomPatternStrategy {
        return copy(conditionsJson = serializeConditions(conditions))
    }

    fun summaryDescription(): String {
        val conds = getConditions()
        if (conds.isEmpty()) return "No conditions configured"
        val joined = conds.joinToString(if (logicOperator == PatternLogicOperator.AND) " AND " else " OR ") {
            it.summaryText()
        }
        return if (waitingPeriodSeconds > 0) {
            "$joined · Wait ${waitingPeriodSeconds}s"
        } else {
            joined
        }
    }

    companion object {
        fun serializeConditions(conditions: List<PatternCondition>): String {
            if (conditions.isEmpty()) return ""
            return conditions.joinToString(";") { c ->
                val seqStr = c.sequenceMultipliers.joinToString(",") {
                    String.format(Locale.US, "%.2f", it)
                }
                listOf(
                    c.comparator.name,
                    c.threshold.toString(),
                    c.upperThreshold?.toString() ?: "",
                    c.consecutiveRounds.coerceAtLeast(1).toString(),
                    c.timeWindowMinutes?.toString() ?: "",
                    seqStr
                ).joinToString("|")
            }
        }

        fun deserializeConditions(encoded: String?): List<PatternCondition> {
            if (encoded.isNullOrBlank()) return emptyList()
            return encoded.split(";").mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size < 4) return@mapNotNull null
                val comp = runCatching { PatternComparator.valueOf(parts[0]) }.getOrNull()
                    ?: return@mapNotNull null
                val threshold = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 1.0 }
                    ?: return@mapNotNull null
                val upper = parts.getOrNull(2)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= threshold }
                val consecutive = parts.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val timeWindow = parts.getOrNull(4)?.toIntOrNull()?.takeIf { it > 0 }
                val seq = parts.getOrNull(5)
                    ?.split(",")
                    ?.mapNotNull { it.trim().toDoubleOrNull()?.takeIf { v -> v.isFinite() && v >= 1.0 } }
                    ?: emptyList()
                PatternCondition(
                    comparator = comp,
                    threshold = threshold,
                    upperThreshold = upper,
                    consecutiveRounds = consecutive,
                    timeWindowMinutes = timeWindow,
                    sequenceMultipliers = seq
                )
            }
        }
    }
}

@Entity(tableName = "notification_settings")
data class NotificationSettings(
    @PrimaryKey
    val id: Int = 1,
    val masterEnabled: Boolean = true,
    val approachingEnabled: Boolean = true,
    val reachedEnabled: Boolean = true,
    val invalidatedEnabled: Boolean = true,
    val historyUnavailableEnabled: Boolean = true,
    val pausedEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val repeatIntervalMinutes: Int = 5,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 23,
    val quietStartMinute: Int = 0,
    val quietEndHour: Int = 7,
    val quietEndMinute: Int = 0
)

enum class StrategyNotificationType(val displayLabel: String) {
    STRATEGY_APPROACHING("Approaching"),
    STRATEGY_REACHED("Strategy Reached"),
    STRATEGY_INVALIDATED("Invalidated"),
    HISTORY_UNAVAILABLE("History Unavailable"),
    STRATEGY_PAUSED("Strategy Paused")
}

enum class NotificationDeliveryStatus(val displayLabel: String) {
    DELIVERED("Delivered"),
    PERMISSION_DENIED("Permission Denied"),
    SUPPRESSED_MASTER_OFF("Suppressed (Master Off)"),
    SUPPRESSED_TYPE_OFF("Suppressed (Type Disabled)"),
    SUPPRESSED_STRATEGY_OFF("Suppressed (Strategy Notification Off)"),
    SUPPRESSED_COOLDOWN("Suppressed (Cooldown)"),
    SUPPRESSED_QUIET_HOURS("Suppressed (Quiet Hours)"),
    SUPPRESSED_DUPLICATE("Suppressed (Duplicate)"),
    FAILED("Delivery Failed")
}

enum class NotificationInteractionStatus(val displayLabel: String) {
    UNREAD("Unread"),
    OPENED_BY_USER("Opened by User"),
    DISMISSED("Dismissed")
}

@Entity(tableName = "notification_history")
data class NotificationHistoryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val strategyId: Long = 0,
    val strategyName: String,
    val notificationType: StrategyNotificationType,
    val title: String,
    val message: String,
    val triggeringRecordKey: String? = null,
    val triggeringMultiplier: Double? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val deliveryStatus: NotificationDeliveryStatus,
    val interactionStatus: NotificationInteractionStatus = NotificationInteractionStatus.UNREAD
)
