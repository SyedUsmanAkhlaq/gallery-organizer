package dev.personal.galleryorganizer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema history (add a Migration for every bump; never destructive, since
 * user corrections from milestone 4 must survive upgrades):
 *  v1 (M2): media_assets
 */
@Database(entities = [MediaAssetEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaAssets(): MediaAssetDao

    companion object {
        const val FILE_NAME = "analysis.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    FILE_NAME,
                ).build().also { instance = it }
            }

        /** Bytes on disk including WAL/SHM side files. */
        fun sizeOnDisk(context: Context): Long {
            val main = context.getDatabasePath(FILE_NAME)
            return listOf("", "-wal", "-shm").sumOf { suffix ->
                java.io.File(main.path + suffix).takeIf { it.exists() }?.length() ?: 0L
            }
        }
    }
}
