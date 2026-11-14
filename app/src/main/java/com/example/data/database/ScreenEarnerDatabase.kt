package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.CustomPatternStrategy
import com.example.data.model.FloatingShortcut
import com.example.data.model.MultiplierRecord
import com.example.data.model.NotificationHistoryRecord
import com.example.data.model.NotificationSettings
import com.example.data.model.PlatformProfile
import com.example.data.model.RiskLimits
import com.example.data.model.SessionLog
import com.example.data.model.StrategyConfig

@Database(
    entities = [
        FloatingShortcut::class,
        PlatformProfile::class,
        StrategyConfig::class,
        RiskLimits::class,
        SessionLog::class,
        MultiplierRecord::class,
        CustomPatternStrategy::class,
        NotificationSettings::class,
        NotificationHistoryRecord::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(ScreenEarnerTypeConverters::class)
abstract class ScreenEarnerDatabase : RoomDatabase() {
    abstract fun dao(): ScreenEarnerDao

    companion object {
        @Volatile
        private var INSTANCE: ScreenEarnerDatabase? = null

        fun getDatabase(context: Context): ScreenEarnerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScreenEarnerDatabase::class.java,
                    "screen_earner_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
