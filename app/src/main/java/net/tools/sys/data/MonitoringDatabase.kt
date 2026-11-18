package net.tools.sys.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ServerMonitoringStateEntity::class,
        MonitoringHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(MonitoringConverters::class)
abstract class MonitoringDatabase : RoomDatabase() {
    abstract fun monitoringDao(): MonitoringDao

    companion object {
        const val NAME = "monitoring.db"

        @Volatile
        private var instance: MonitoringDatabase? = null

        fun get(context: Context): MonitoringDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }
        }

        private fun build(context: Context): MonitoringDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MonitoringDatabase::class.java,
                NAME,
            )
                // Version 1 is the first schema. Add a Migration and raise [version]
                // before changing tables. Destructive fallback would erase history.
                .build()
        }
    }
}
