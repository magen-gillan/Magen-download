package com.magen.download.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.magen.download.database.objects.CommandTemplate
import com.magen.download.database.objects.CookieProfile
import com.magen.download.database.objects.DownloadedVideoInfo
import com.magen.download.database.objects.OptionShortcut

@Database(
    entities =
        [
            DownloadedVideoInfo::class,
            CommandTemplate::class,
            CookieProfile::class,
            OptionShortcut::class,
        ],
    version = 6,
    autoMigrations =
        [
            AutoMigration(from = 1, to = 2),
            AutoMigration(from = 2, to = 3),
            AutoMigration(from = 3, to = 4),
            AutoMigration(from = 4, to = 5),
            // NOTE: 5 -> 6 is a MANUAL migration (see MIGRATION_5_6) because a
            // plain auto-migration would try to create the UNIQUE videoPath
            // index while duplicate rows may still exist, crashing the app on
            // first open after the update.
        ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoInfoDao(): VideoInfoDao

    companion object {
        /**
         * 5 -> 6: deduplicate history rows by videoPath (older versions
         * inserted blindly), then add the UNIQUE index that keeps inserts
         * deduplicated atomically from now on.
         */
        val MIGRATION_5_6: Migration =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "DELETE FROM DownloadedVideoInfo WHERE id NOT IN " +
                            "(SELECT MIN(id) FROM DownloadedVideoInfo GROUP BY videoPath)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_DownloadedVideoInfo_videoPath` " +
                            "ON `DownloadedVideoInfo` (`videoPath`)"
                    )
                }
            }
    }
}
