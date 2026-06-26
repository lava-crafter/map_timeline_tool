/*
Copyright 2026 Muchen Jiang (lava-crafter)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.lavacrafter.maptimelinetool.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun migrate3To7_preservesCoreDataAndTagLinks() {
        runMigrationTest(startVersion = 3, dbName = "migration-test-v3.db") { db ->
            createLegacyTagsSchema(db)
            createPointsTableV3(db)
            insertTagData(db)
            insertPointTagLink(db)
            db.insertOrThrow(
                "points",
                null,
                ContentValues().apply {
                    put("id", 1L)
                    put("timestamp", 1710000000000L)
                    put("latitude", 10.1)
                    put("longitude", 20.2)
                    put("title", "Legacy V3")
                    put("note", "core only")
                }
            )
        }

        migrateAndAssert("migration-test-v3.db") { point, db ->
            assertEquals("Legacy V3", point.title)
            assertEquals("core only", point.note)
            assertEquals(1710000000000L, point.timestamp)
            assertEquals(10.1, point.latitude, 0.0)
            assertEquals(20.2, point.longitude, 0.0)
            assertNull(point.locationAccuracyMeters)
            assertNull(point.locationFixTimeMs)
            assertNull(point.locationProvider)
            assertNull(point.photoPath)
            assertNull(point.noiseDb)
            assertNull(point.pressureHpa)
            assertTagLink(db, point.id, 10L, "Work")
        }
    }

    @Test
    fun migrate4To7_preservesSensorColumns() {
        runMigrationTest(startVersion = 4, dbName = "migration-test-v4.db") { db ->
            createLegacyTagsSchema(db)
            createPointsTableV4(db)
            insertTagData(db)
            insertPointTagLink(db)
            db.insertOrThrow(
                "points",
                null,
                ContentValues().apply {
                    put("id", 1L)
                    put("timestamp", 1710000001000L)
                    put("latitude", 11.1)
                    put("longitude", 21.2)
                    put("title", "Legacy V4")
                    put("note", "sensor data")
                    put("pressureHpa", 1008.5f)
                    put("ambientLightLux", 345.6f)
                    put("accelerometerX", 1.1f)
                    put("accelerometerY", 2.2f)
                    put("accelerometerZ", 3.3f)
                    put("gyroscopeX", 4.4f)
                    put("gyroscopeY", 5.5f)
                    put("gyroscopeZ", 6.6f)
                    put("magnetometerX", 7.7f)
                    put("magnetometerY", 8.8f)
                    put("magnetometerZ", 9.9f)
                }
            )
        }

        migrateAndAssert("migration-test-v4.db") { point, db ->
            assertEquals("Legacy V4", point.title)
            assertEquals(1008.5f, point.pressureHpa ?: 0f, 0.001f)
            assertEquals(345.6f, point.ambientLightLux ?: 0f, 0.001f)
            assertEquals(1.1f, point.accelerometerX ?: 0f, 0.001f)
            assertEquals(2.2f, point.accelerometerY ?: 0f, 0.001f)
            assertEquals(3.3f, point.accelerometerZ ?: 0f, 0.001f)
            assertEquals(4.4f, point.gyroscopeX ?: 0f, 0.001f)
            assertEquals(5.5f, point.gyroscopeY ?: 0f, 0.001f)
            assertEquals(6.6f, point.gyroscopeZ ?: 0f, 0.001f)
            assertEquals(7.7f, point.magnetometerX ?: 0f, 0.001f)
            assertEquals(8.8f, point.magnetometerY ?: 0f, 0.001f)
            assertEquals(9.9f, point.magnetometerZ ?: 0f, 0.001f)
            assertNull(point.photoPath)
            assertNull(point.noiseDb)
            assertNull(point.locationAccuracyMeters)
            assertTagLink(db, point.id, 10L, "Work")
        }
    }

    @Test
    fun migrate5To7_preservesPhotoPath() {
        runMigrationTest(startVersion = 5, dbName = "migration-test-v5.db") { db ->
            createLegacyTagsSchema(db)
            createPointsTableV5(db)
            insertTagData(db)
            insertPointTagLink(db)
            db.insertOrThrow(
                "points",
                null,
                ContentValues().apply {
                    put("id", 1L)
                    put("timestamp", 1710000002000L)
                    put("latitude", 12.1)
                    put("longitude", 22.2)
                    put("title", "Legacy V5")
                    put("note", "photo path")
                    put("pressureHpa", 1001.2f)
                    put("photoPath", "point_photo_v5.jpg")
                }
            )
        }

        migrateAndAssert("migration-test-v5.db") { point, db ->
            assertEquals("Legacy V5", point.title)
            assertEquals("point_photo_v5.jpg", point.photoPath)
            assertEquals(1001.2f, point.pressureHpa ?: 0f, 0.001f)
            assertNull(point.noiseDb)
            assertNull(point.locationProvider)
            assertTagLink(db, point.id, 10L, "Work")
        }
    }

    @Test
    fun migrate6To7_preservesNoiseAndAddsLocationColumns() {
        runMigrationTest(startVersion = 6, dbName = "migration-test-v6.db") { db ->
            createLegacyTagsSchema(db)
            createPointsTableV6(db)
            insertTagData(db)
            insertPointTagLink(db)
            db.insertOrThrow(
                "points",
                null,
                ContentValues().apply {
                    put("id", 1L)
                    put("timestamp", 1710000003000L)
                    put("latitude", 13.1)
                    put("longitude", 23.2)
                    put("title", "Legacy V6")
                    put("note", "noise data")
                    put("pressureHpa", 999.9f)
                    put("photoPath", "point_photo_v6.jpg")
                    put("noiseDb", 52.3f)
                }
            )
        }

        migrateAndAssert("migration-test-v6.db") { point, db ->
            assertEquals("Legacy V6", point.title)
            assertEquals("point_photo_v6.jpg", point.photoPath)
            assertEquals(52.3f, point.noiseDb ?: 0f, 0.001f)
            assertEquals(999.9f, point.pressureHpa ?: 0f, 0.001f)
            assertNull(point.locationAccuracyMeters)
            assertNull(point.locationFixTimeMs)
            assertNull(point.locationProvider)
            assertHasColumn(db, "locationAccuracyMeters")
            assertHasColumn(db, "locationFixTimeMs")
            assertHasColumn(db, "locationProvider")
            assertTagLink(db, point.id, 10L, "Work")
        }
    }

    private fun runMigrationTest(
        startVersion: Int,
        dbName: String,
        populate: (SQLiteDatabase) -> Unit
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.beginTransaction()
            populate(db)
            db.version = startVersion
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    private fun migrateAndAssert(
        dbName: String,
        assertions: suspend (PointEntity, AppDatabase) -> Unit
    ) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val point = db.pointDao().getAll().single()
            assertions(point, db)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun createLegacyTagsSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS point_tags (pointId INTEGER NOT NULL, tagId INTEGER NOT NULL, PRIMARY KEY(pointId, tagId))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_point_tags_tagId ON point_tags(tagId)")
    }

    private fun createPointsTableV3(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL)"
        )
    }

    private fun createPointsTableV4(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, pressureHpa REAL, ambientLightLux REAL, accelerometerX REAL, accelerometerY REAL, accelerometerZ REAL, gyroscopeX REAL, gyroscopeY REAL, gyroscopeZ REAL, magnetometerX REAL, magnetometerY REAL, magnetometerZ REAL)"
        )
    }

    private fun createPointsTableV5(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, pressureHpa REAL, ambientLightLux REAL, accelerometerX REAL, accelerometerY REAL, accelerometerZ REAL, gyroscopeX REAL, gyroscopeY REAL, gyroscopeZ REAL, magnetometerX REAL, magnetometerY REAL, magnetometerZ REAL, photoPath TEXT DEFAULT NULL)"
        )
    }

    private fun createPointsTableV6(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, pressureHpa REAL, ambientLightLux REAL, accelerometerX REAL, accelerometerY REAL, accelerometerZ REAL, gyroscopeX REAL, gyroscopeY REAL, gyroscopeZ REAL, magnetometerX REAL, magnetometerY REAL, magnetometerZ REAL, photoPath TEXT DEFAULT NULL, noiseDb REAL)"
        )
    }

    private fun insertTagData(db: SQLiteDatabase) {
        db.insertOrThrow(
            "tags",
            null,
            ContentValues().apply {
                put("id", 10L)
                put("name", "Work")
            }
        )
    }

    private fun insertPointTagLink(db: SQLiteDatabase) {
        db.insertOrThrow(
            "point_tags",
            null,
            ContentValues().apply {
                put("pointId", 1L)
                put("tagId", 10L)
            }
        )
    }

    private suspend fun assertTagLink(db: AppDatabase, pointId: Long, expectedTagId: Long, expectedTagName: String) {
        val tagIds = db.pointDao().getTagIdsForPoint(pointId)
        assertEquals(listOf(expectedTagId), tagIds)
        db.openHelper.readableDatabase.query("SELECT name FROM tags WHERE id = $expectedTagId").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expectedTagName, cursor.getString(0))
        }
    }

    private fun assertHasColumn(db: AppDatabase, columnName: String) {
        db.openHelper.readableDatabase.query("SELECT * FROM points LIMIT 1").use { cursor ->
            assertTrue(cursor.getColumnIndex(columnName) >= 0)
        }
    }
}
