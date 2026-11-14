package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.ScreenEarnerRepository
import com.example.data.engine.PatternAlertEvent
import com.example.data.model.CustomPatternStrategy
import com.example.data.model.NotificationDeliveryStatus
import com.example.data.model.NotificationHistoryRecord
import com.example.data.model.NotificationInteractionStatus
import com.example.data.model.NotificationPriorityLevel
import com.example.data.model.NotificationSettings
import com.example.data.model.StrategyNotificationType
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

interface SystemNotificationDispatcher {
    fun hasNotificationPermission(context: Context): Boolean
    fun postSystemNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        priorityLevel: NotificationPriorityLevel,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        historyRecordId: Long
    ): Boolean
}

object AndroidSystemNotificationDispatcher : SystemNotificationDispatcher {
    const val STRATEGY_CHANNEL_ID = "ScreenEarnerStrategyAlerts"
    private const val STRATEGY_CHANNEL_NAME = "Strategy Pattern Alerts"

    override fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureStrategyChannel(context: Context, settings: NotificationSettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                STRATEGY_CHANNEL_ID,
                STRATEGY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when user-defined multiplier patterns approach, trigger, or reset"
                enableVibration(settings.vibrationEnabled)
                if (!settings.soundEnabled) {
                    setSound(null, null)
                } else {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun postSystemNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        priorityLevel: NotificationPriorityLevel,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        historyRecordId: Long
    ): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                nm.getNotificationChannel(STRATEGY_CHANNEL_ID) == null
            ) {
                ensureStrategyChannel(context, NotificationSettings(soundEnabled = soundEnabled, vibrationEnabled = vibrationEnabled))
            }

            // Opens Screen Earner Strategy / Review screen without automatically placing any bet
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_STRATEGY)
                putExtra(MainActivity.EXTRA_NOTIFICATION_HISTORY_ID, historyRecordId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val compatPriority = when (priorityLevel) {
                NotificationPriorityLevel.URGENT -> NotificationCompat.PRIORITY_MAX
                NotificationPriorityLevel.HIGH -> NotificationCompat.PRIORITY_HIGH
                NotificationPriorityLevel.STANDARD -> NotificationCompat.PRIORITY_DEFAULT
            }

            val builder = NotificationCompat.Builder(context, STRATEGY_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(compatPriority)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            if (!soundEnabled) {
                builder.setSilent(true)
            }
            if (vibrationEnabled) {
                builder.setVibrate(longArrayOf(0L, 220L, 120L, 220L))
            }

            nm.notify(notificationId, builder.build())
            true
        } catch (t: Throwable) {
            Log.e("StrategyNotifMgr", "Failed posting Android notification", t)
            false
        }
    }
}

/**
 * Central strategy notification controller for Sections 16 & 17.
 * Enforces master toggle, per-type toggle, per-strategy toggle, quiet hours, repeat cooldown,
 * duplicate occurrence suppression, runtime permission checks, and persistent Room audit logging.
 */
class StrategyNotificationManager(
    private val context: Context?,
    private val repository: ScreenEarnerRepository,
    private val dispatcher: SystemNotificationDispatcher = AndroidSystemNotificationDispatcher
) {
    private val lastDeliveredOccurrences = ConcurrentHashMap<String, Long>()
    private val lastStrategyDispatchTimes = ConcurrentHashMap<Long, Long>()

    companion object {
        fun isWithinQuietHours(settings: NotificationSettings, nowMs: Long): Boolean {
            if (!settings.quietHoursEnabled) return false
            val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
            val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val startMinutes = settings.quietStartHour.coerceIn(0, 23) * 60 + settings.quietStartMinute.coerceIn(0, 59)
            val endMinutes = settings.quietEndHour.coerceIn(0, 23) * 60 + settings.quietEndMinute.coerceIn(0, 59)

            return if (startMinutes == endMinutes) {
                false
            } else if (startMinutes < endMinutes) {
                currentMinutes in startMinutes until endMinutes
            } else {
                // Overnight quiet period (e.g. 23:00 to 07:00)
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            }
        }
    }

    fun evaluateSuppressionReason(
        event: PatternAlertEvent,
        settings: NotificationSettings,
        nowMs: Long
    ): NotificationDeliveryStatus? {
        if (!settings.masterEnabled) {
            return NotificationDeliveryStatus.SUPPRESSED_MASTER_OFF
        }
        val typeEnabled = when (event.notificationType) {
            StrategyNotificationType.STRATEGY_APPROACHING -> settings.approachingEnabled
            StrategyNotificationType.STRATEGY_REACHED -> settings.reachedEnabled
            StrategyNotificationType.STRATEGY_INVALIDATED -> settings.invalidatedEnabled
            StrategyNotificationType.HISTORY_UNAVAILABLE -> settings.historyUnavailableEnabled
            StrategyNotificationType.STRATEGY_PAUSED -> settings.pausedEnabled
        }
        if (!typeEnabled) {
            return NotificationDeliveryStatus.SUPPRESSED_TYPE_OFF
        }
        if (!event.strategy.notificationEnabled) {
            return NotificationDeliveryStatus.SUPPRESSED_STRATEGY_OFF
        }
        if (isWithinQuietHours(settings, nowMs)) {
            return NotificationDeliveryStatus.SUPPRESSED_QUIET_HOURS
        }
        if (lastDeliveredOccurrences.containsKey(event.occurrenceKey)) {
            return NotificationDeliveryStatus.SUPPRESSED_DUPLICATE
        }
        val effectiveCooldownMin = maxOf(
            settings.repeatIntervalMinutes.coerceAtLeast(0),
            event.strategy.repeatCooldownMinutes.coerceAtLeast(0)
        )
        // Allow transition from APPROACHING -> REACHED -> INVALIDATED on the same strategy without cooldown block,
        // while enforcing cooldown against rapid repeated REACHED alerts for the same strategy.
        if (event.notificationType == StrategyNotificationType.STRATEGY_REACHED && effectiveCooldownMin > 0) {
            val lastTime = lastStrategyDispatchTimes[event.strategy.id] ?: 0L
            if (lastTime > 0L && (nowMs - lastTime) < effectiveCooldownMin * 60_000L) {
                return NotificationDeliveryStatus.SUPPRESSED_COOLDOWN
            }
        }
        return null
    }

    suspend fun dispatchPatternAlert(
        event: PatternAlertEvent,
        settings: NotificationSettings,
        nowMs: Long = System.currentTimeMillis()
    ): NotificationHistoryRecord {
        val suppression = evaluateSuppressionReason(event, settings, nowMs)
        if (suppression != null) {
            val suppressedRecord = NotificationHistoryRecord(
                strategyId = event.strategy.id,
                strategyName = event.strategy.name,
                notificationType = event.notificationType,
                title = event.title,
                message = event.message,
                triggeringRecordKey = event.triggeringRecordKey,
                triggeringMultiplier = event.triggeringMultiplier,
                timestampMs = nowMs,
                deliveryStatus = suppression,
                interactionStatus = NotificationInteractionStatus.UNREAD
            )
            val savedId = repository.addNotificationHistory(suppressedRecord)
            return suppressedRecord.copy(id = savedId)
        }

        val appCtx = context
        val hasPermission = appCtx != null && dispatcher.hasNotificationPermission(appCtx)
        if (!hasPermission) {
            val deniedRecord = NotificationHistoryRecord(
                strategyId = event.strategy.id,
                strategyName = event.strategy.name,
                notificationType = event.notificationType,
                title = event.title,
                message = event.message,
                triggeringRecordKey = event.triggeringRecordKey,
                triggeringMultiplier = event.triggeringMultiplier,
                timestampMs = nowMs,
                deliveryStatus = NotificationDeliveryStatus.PERMISSION_DENIED,
                interactionStatus = NotificationInteractionStatus.UNREAD
            )
            val savedId = repository.addNotificationHistory(deniedRecord)
            return deniedRecord.copy(id = savedId)
        }

        // Insert record first so PendingIntent carries the real history ID for interaction tracking
        val pendingRecord = NotificationHistoryRecord(
            strategyId = event.strategy.id,
            strategyName = event.strategy.name,
            notificationType = event.notificationType,
            title = event.title,
            message = event.message,
            triggeringRecordKey = event.triggeringRecordKey,
            triggeringMultiplier = event.triggeringMultiplier,
            timestampMs = nowMs,
            deliveryStatus = NotificationDeliveryStatus.DELIVERED,
            interactionStatus = NotificationInteractionStatus.UNREAD
        )
        val recordId = repository.addNotificationHistory(pendingRecord)

        val notifId = ((event.strategy.id * 31L + event.notificationType.ordinal).toInt() and 0x7FFFFFFF) + 2000
        if (dispatcher === AndroidSystemNotificationDispatcher) {
            AndroidSystemNotificationDispatcher.ensureStrategyChannel(appCtx, settings)
        }
        val posted = dispatcher.postSystemNotification(
            context = appCtx,
            notificationId = notifId,
            title = event.title,
            message = event.message,
            priorityLevel = event.strategy.notificationLevel,
            soundEnabled = settings.soundEnabled,
            vibrationEnabled = settings.vibrationEnabled,
            historyRecordId = recordId
        )

        val finalStatus = if (posted) {
            lastDeliveredOccurrences[event.occurrenceKey] = nowMs
            if (event.notificationType == StrategyNotificationType.STRATEGY_REACHED) {
                lastStrategyDispatchTimes[event.strategy.id] = nowMs
            }
            NotificationDeliveryStatus.DELIVERED
        } else {
            NotificationDeliveryStatus.FAILED
        }

        val finalRecord = pendingRecord.copy(id = recordId, deliveryStatus = finalStatus)
        if (!posted) {
            repository.addNotificationHistory(finalRecord)
        }
        return finalRecord
    }

    suspend fun dispatchSystemNotice(
        strategyName: String = "History Monitor",
        type: StrategyNotificationType,
        title: String,
        message: String,
        occurrenceKey: String,
        settings: NotificationSettings,
        nowMs: Long = System.currentTimeMillis()
    ): NotificationHistoryRecord {
        val dummyStrategy = CustomPatternStrategy(id = 0L, name = strategyName)
        val event = PatternAlertEvent(
            strategy = dummyStrategy,
            notificationType = type,
            title = title,
            message = message,
            occurrenceKey = occurrenceKey,
            timestampMs = nowMs
        )
        return dispatchPatternAlert(event, settings, nowMs)
    }

    fun clearSessionCache() {
        lastDeliveredOccurrences.clear()
        lastStrategyDispatchTimes.clear()
    }
}
