package com.example.data.database

import com.example.data.model.CustomPatternStrategy
import com.example.data.model.FloatingShortcut
import com.example.data.model.MultiplierRecord
import com.example.data.model.NotificationHistoryRecord
import com.example.data.model.NotificationInteractionStatus
import com.example.data.model.NotificationSettings
import com.example.data.model.PlatformProfile
import com.example.data.model.RiskLimits
import com.example.data.model.SessionLog
import com.example.data.model.ShortcutType
import com.example.data.model.StrategyConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ScreenEarnerRepository(private val dao: ScreenEarnerDao) {

    val shortcuts: Flow<List<FloatingShortcut>> = dao.getAllShortcuts()
    val profiles: Flow<List<PlatformProfile>> = dao.getAllProfiles()
    val strategyConfig: Flow<StrategyConfig?> = dao.getStrategyConfig()
    val riskLimits: Flow<RiskLimits?> = dao.getRiskLimits()
    val sessionLogs: Flow<List<SessionLog>> = dao.getRecentSessionLogs()
    val multiplierRecords: Flow<List<MultiplierRecord>> = dao.getAllMultiplierRecords()
    val customStrategies: Flow<List<CustomPatternStrategy>> = dao.getAllCustomStrategies()
    val notificationSettings: Flow<NotificationSettings?> = dao.getNotificationSettings()
    val notificationHistory: Flow<List<NotificationHistoryRecord>> = dao.getAllNotificationHistory()

    suspend fun seedDefaultsIfEmpty() {
        val currentShortcuts = dao.getAllShortcuts().first()
        if (currentShortcuts.isEmpty()) {
            dao.insertShortcut(
                FloatingShortcut(
                    label = "BET 1",
                    type = ShortcutType.BET,
                    panelId = 1,
                    visualX = 80f,
                    visualY = 320f,
                    backgroundColorHex = "#16A34A",
                    targetX = 400f,
                    targetY = 1200f,
                    isCalibrated = true
                )
            )
            dao.insertShortcut(
                FloatingShortcut(
                    label = "CASH OUT 1",
                    type = ShortcutType.CASH_OUT,
                    panelId = 1,
                    visualX = 220f,
                    visualY = 320f,
                    backgroundColorHex = "#F59E0B",
                    targetX = 650f,
                    targetY = 1200f,
                    isCalibrated = true
                )
            )
        }

        val currentProfiles = dao.getAllProfiles().first()
        if (currentProfiles.isEmpty()) {
            dao.insertProfile(
                PlatformProfile(
                    id = "betpawa_portrait",
                    platformName = "BetPawa Aviator",
                    orientation = "PORTRAIT",
                    isActive = true,
                    description = "Default portrait target layout for BetPawa web & native app."
                )
            )
            dao.insertProfile(
                PlatformProfile(
                    id = "sportybet_portrait",
                    platformName = "SportyBet Aviator",
                    orientation = "PORTRAIT",
                    isActive = false,
                    description = "Portrait target layout calibrated for SportyBet platform."
                )
            )
            dao.insertProfile(
                PlatformProfile(
                    id = "custom_landscape",
                    platformName = "Custom Browser",
                    orientation = "LANDSCAPE",
                    isActive = false,
                    description = "Custom layout for Chrome landscape mode."
                )
            )
        }

        if (dao.getStrategyConfig().first() == null) {
            dao.saveStrategyConfig(StrategyConfig())
        }

        if (dao.getRiskLimits().first() == null) {
            dao.saveRiskLimits(RiskLimits())
        }

        if (dao.getNotificationSettingsOnce() == null) {
            dao.saveNotificationSettings(NotificationSettings())
        }
        // NOTE: CustomPatternStrategy table intentionally starts empty with zero hardcoded example strategies.
    }

    suspend fun insertShortcut(shortcut: FloatingShortcut) = dao.insertShortcut(shortcut)
    suspend fun updateShortcut(shortcut: FloatingShortcut) = dao.updateShortcut(shortcut)
    suspend fun deleteShortcut(id: Long) = dao.deleteShortcutById(id)
    suspend fun resetShortcutsToDefault() {
        dao.deleteAllShortcuts()
        seedDefaultsIfEmpty()
    }

    suspend fun setActiveProfile(profileId: String) = dao.setActiveProfile(profileId)
    suspend fun insertProfile(profile: PlatformProfile) = dao.insertProfile(profile)

    suspend fun saveStrategyConfig(config: StrategyConfig) = dao.saveStrategyConfig(config)
    suspend fun saveRiskLimits(limits: RiskLimits) = dao.saveRiskLimits(limits)

    suspend fun addSessionLog(log: SessionLog) = dao.insertSessionLog(log)
    suspend fun clearSessionLogs() = dao.clearSessionLogs()

    suspend fun addMultiplierRecord(record: MultiplierRecord) = dao.insertMultiplierRecord(record)
    suspend fun addMultiplierRecords(records: List<MultiplierRecord>) {
        if (records.isNotEmpty()) {
            dao.insertMultiplierRecords(records)
        }
    }

    suspend fun getRecentChronologicalRecords(windowMinutes: Int = 30, nowMs: Long = System.currentTimeMillis()): List<MultiplierRecord> {
        val cutoff = nowMs - (windowMinutes.coerceAtLeast(1) * 60_000L)
        return dao.getMultiplierRecordsSinceChronological(cutoff)
    }

    suspend fun pruneMultiplierRecordsOlderThan(cutoffTimestamp: Long) =
        dao.pruneMultiplierRecordsOlderThan(cutoffTimestamp)

    suspend fun clearMultiplierRecords() = dao.clearMultiplierRecords()

    // User-defined custom pattern strategies
    suspend fun getCustomStrategiesOnce(): List<CustomPatternStrategy> = dao.getCustomStrategiesOnce()
    suspend fun insertCustomStrategy(strategy: CustomPatternStrategy): Long = dao.insertCustomStrategy(strategy)
    suspend fun updateCustomStrategy(strategy: CustomPatternStrategy) = dao.updateCustomStrategy(strategy)
    suspend fun deleteCustomStrategy(id: Long) = dao.deleteCustomStrategyById(id)

    // Notification settings & history
    suspend fun getNotificationSettingsOnce(): NotificationSettings =
        dao.getNotificationSettingsOnce() ?: NotificationSettings()

    suspend fun saveNotificationSettings(settings: NotificationSettings) =
        dao.saveNotificationSettings(settings)

    suspend fun addNotificationHistory(record: NotificationHistoryRecord): Long =
        dao.insertNotificationHistory(record)

    suspend fun updateNotificationInteraction(id: Long, status: NotificationInteractionStatus) =
        dao.updateNotificationInteraction(id, status)

    suspend fun clearNotificationHistory() = dao.clearNotificationHistory()
}
