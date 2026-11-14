package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CustomPatternStrategy
import com.example.data.model.FloatingShortcut
import com.example.data.model.MultiplierRecord
import com.example.data.model.NotificationHistoryRecord
import com.example.data.model.NotificationInteractionStatus
import com.example.data.model.NotificationSettings
import com.example.data.model.PlatformProfile
import com.example.data.model.RiskLimits
import com.example.data.model.SessionLog
import com.example.data.model.StrategyConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenEarnerDao {
    // Shortcuts
    @Query("SELECT * FROM floating_shortcuts ORDER BY id ASC")
    fun getAllShortcuts(): Flow<List<FloatingShortcut>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: FloatingShortcut): Long

    @Update
    suspend fun updateShortcut(shortcut: FloatingShortcut)

    @Query("DELETE FROM floating_shortcuts WHERE id = :id")
    suspend fun deleteShortcutById(id: Long)

    @Query("DELETE FROM floating_shortcuts")
    suspend fun deleteAllShortcuts()

    // Profiles
    @Query("SELECT * FROM platform_profiles")
    fun getAllProfiles(): Flow<List<PlatformProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: PlatformProfile)

    @Query("UPDATE platform_profiles SET isActive = (id = :activeId)")
    suspend fun setActiveProfile(activeId: String)

    // Strategy Config
    @Query("SELECT * FROM strategy_configs WHERE id = 1")
    fun getStrategyConfig(): Flow<StrategyConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStrategyConfig(config: StrategyConfig)

    // Risk Limits
    @Query("SELECT * FROM risk_limits WHERE id = 1")
    fun getRiskLimits(): Flow<RiskLimits?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRiskLimits(limits: RiskLimits)

    // Session Logs
    @Query("SELECT * FROM session_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentSessionLogs(): Flow<List<SessionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionLog(log: SessionLog)

    @Query("DELETE FROM session_logs")
    suspend fun clearSessionLogs()

    // Multiplier Records (observed round & ribbon history)
    @Query("SELECT * FROM multiplier_records ORDER BY crashTimestamp DESC, sequenceIndex ASC, id DESC LIMIT 2000")
    fun getAllMultiplierRecords(): Flow<List<MultiplierRecord>>

    @Query("SELECT * FROM multiplier_records WHERE crashTimestamp >= :sinceTimestamp ORDER BY crashTimestamp DESC, sequenceIndex ASC, id DESC")
    fun getMultiplierRecordsSince(sinceTimestamp: Long): Flow<List<MultiplierRecord>>

    @Query("SELECT * FROM multiplier_records WHERE crashTimestamp >= :sinceTimestamp ORDER BY crashTimestamp ASC, sequenceIndex DESC, id ASC")
    suspend fun getMultiplierRecordsSinceChronological(sinceTimestamp: Long): List<MultiplierRecord>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMultiplierRecord(record: MultiplierRecord)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMultiplierRecords(records: List<MultiplierRecord>)

    @Query("DELETE FROM multiplier_records WHERE crashTimestamp < :cutoffTimestamp")
    suspend fun pruneMultiplierRecordsOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM multiplier_records")
    suspend fun clearMultiplierRecords()

    // Custom Pattern Strategies (User-defined, zero hardcoded examples)
    @Query("SELECT * FROM custom_pattern_strategies ORDER BY createdAtMs ASC, id ASC")
    fun getAllCustomStrategies(): Flow<List<CustomPatternStrategy>>

    @Query("SELECT * FROM custom_pattern_strategies ORDER BY createdAtMs ASC, id ASC")
    suspend fun getCustomStrategiesOnce(): List<CustomPatternStrategy>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomStrategy(strategy: CustomPatternStrategy): Long

    @Update
    suspend fun updateCustomStrategy(strategy: CustomPatternStrategy)

    @Query("DELETE FROM custom_pattern_strategies WHERE id = :id")
    suspend fun deleteCustomStrategyById(id: Long)

    // Notification Settings
    @Query("SELECT * FROM notification_settings WHERE id = 1")
    fun getNotificationSettings(): Flow<NotificationSettings?>

    @Query("SELECT * FROM notification_settings WHERE id = 1")
    suspend fun getNotificationSettingsOnce(): NotificationSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNotificationSettings(settings: NotificationSettings)

    // Notification History
    @Query("SELECT * FROM notification_history ORDER BY timestampMs DESC, id DESC LIMIT 500")
    fun getAllNotificationHistory(): Flow<List<NotificationHistoryRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationHistory(record: NotificationHistoryRecord): Long

    @Query("UPDATE notification_history SET interactionStatus = :status WHERE id = :id")
    suspend fun updateNotificationInteraction(id: Long, status: NotificationInteractionStatus)

    @Query("DELETE FROM notification_history")
    suspend fun clearNotificationHistory()
}
