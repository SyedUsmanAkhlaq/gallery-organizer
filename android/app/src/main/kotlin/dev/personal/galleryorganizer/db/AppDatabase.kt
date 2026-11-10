package dev.personal.galleryorganizer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema history:
 *  v1 (M2): media_assets
 *  v2: clothing_results, face_scans, faces, persons, face_exclusions.
 *      v1 held only the rebuildable MediaStore index, so 1→2 is destructive.
 *
 * From v2 on, every bump needs a real Migration: persons/names/exclusions
 * are user corrections and must never be dropped.
 */
@Database(
    entities = [
        MediaAssetEntity::class,
        ClothingResultEntity::class,
        FaceScanEntity::class,
        FaceEntity::class,
        PersonEntity::class,
        FaceExclusionEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaAssets(): MediaAssetDao
    abstract fun clothing(): ClothingDao
    abstract fun faces(): FaceDao
    abstract fun persons(): PersonDao
    abstract fun maintenance(): MaintenanceDao

    companion object {
        const val FILE_NAME = "analysis.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    FILE_NAME,
                ).fallbackToDestructiveMigrationFrom(true, 1)
                    .build().also { instance = it }
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
