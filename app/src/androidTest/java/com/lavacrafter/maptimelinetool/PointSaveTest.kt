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

package com.lavacrafter.maptimelinetool

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.lavacrafter.maptimelinetool.data.AppDatabase
import com.lavacrafter.maptimelinetool.data.PointEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class PointSaveTest {
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadPoint() = runBlocking {
        val point = PointEntity(
            timestamp = 1000L,
            latitude = 1.0,
            longitude = 1.0,
            locationAccuracyMeters = 9f,
            locationFixTimeMs = 900L,
            locationProvider = "gps",
            title = "My Point",
            note = "My Note"
        )
        val id = db.pointDao().insert(point)
        val loaded = db.pointDao().getAll().first { it.id == id }
        assertEquals("My Point", loaded.title)
        assertEquals("My Note", loaded.note)
        assertEquals(9f, loaded.locationAccuracyMeters)
        assertEquals(900L, loaded.locationFixTimeMs)
        assertEquals("gps", loaded.locationProvider)
        
        db.pointDao().update(loaded.copy(title = "New Name", note = "New Note"))
        val updated = db.pointDao().getAll().first { it.id == id }
        assertEquals("New Name", updated.title)
        assertEquals("New Note", updated.note)
        assertEquals(9f, updated.locationAccuracyMeters)
        assertEquals(900L, updated.locationFixTimeMs)
        assertEquals("gps", updated.locationProvider)
    }
}
