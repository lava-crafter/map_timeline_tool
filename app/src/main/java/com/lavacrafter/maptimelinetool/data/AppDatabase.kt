package com.lavacrafter.maptimelinetool.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PointEntity::class, TagEntity::class, PointTagCrossRef::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pointDao(): PointDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE points ADD COLUMN pressureHpa REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN ambientLightLux REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN accelerometerX REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN accelerometerY REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN accelerometerZ REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN gyroscopeX REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN gyroscopeY REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN gyroscopeZ REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN magnetometerX REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN magnetometerY REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN magnetometerZ REAL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE points ADD COLUMN photoPath TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE points ADD COLUMN noiseDb REAL")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "map_timeline.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}
