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
            title = "My Point",
            note = "My Note"
        )
        val id = db.pointDao().insert(point)
        val loaded = db.pointDao().getAll().first { it.id == id }
        assertEquals("My Point", loaded.title)
        assertEquals("My Note", loaded.note)
        
        db.pointDao().update(loaded.copy(title = "New Name", note = "New Note"))
        val updated = db.pointDao().getAll().first { it.id == id }
        assertEquals("New Name", updated.title)
        assertEquals("New Note", updated.note)
    }
}
