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

package com.lavacrafter.maptimelinetool.export

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavacrafter.maptimelinetool.data.AppDatabase
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.data.PointRepository
import com.lavacrafter.maptimelinetool.data.PointTagCrossRef
import com.lavacrafter.maptimelinetool.data.TagEntity
import com.lavacrafter.maptimelinetool.ui.SettingsStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullBackupRoomTest {
    @Test
    fun fullBackupReadsRoomRelationsAndRestoresAllLogicalRecords() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val preferencesName = "full_backup_${UUID.randomUUID()}_map_timeline_settings"
        val settingsContext = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences(preferencesName, mode)
        }
        val photoDir = File(base.cacheDir, "full_backup_${UUID.randomUUID()}").apply { mkdirs() }
        val photo = File(photoDir, "saved.jpg").apply { writeBytes(byteArrayOf(10, 20, 30)) }
        val database = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).build()
        try {
            SettingsStore.setMarkerScale(settingsContext, 1.2f)
            SettingsStore.setQuickAddNotificationEnabled(settingsContext, true)
            val dao = database.pointDao()
            val first = dao.insert(PointEntity(timestamp = 1_710_000_000_000L, latitude = 10.0,
                longitude = 20.0, title = "First", note = "a", pressureHpa = 1_002f, photoPath = photo.name))
            val second = dao.insert(PointEntity(timestamp = 1_710_000_001_000L, latitude = 11.0,
                longitude = 21.0, title = "Second", note = "b"))
            val attached = dao.insertTag(TagEntity(name = "Attached"))
            dao.insertTag(TagEntity(name = "Unattached"))
            dao.insertPointTag(PointTagCrossRef(first, attached))
            dao.insertPointTag(PointTagCrossRef(second, attached))

            val output = ByteArrayOutputStream()
            val payload = FullBackupAssembler(PointRepository(database, dao)).assemble()
            payload.writeZip(output, { path -> File(photoDir, path) }, SettingsStore.exportBackupJson(settingsContext), "test")
            val imported = ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { entryName, input ->
                File(photoDir, "restored.jpg").apply { writeBytes(input.readBytes()) }.name
            }

            assertEquals(2, imported.points.size)
            assertEquals(setOf("Attached", "Unattached"), imported.tags.map { it.name }.toSet())
            assertEquals(2, imported.pointTags.size)
            assertEquals(setOf(0, 1), imported.pointTags.map { it.pointIndex }.toSet())
            assertEquals(1_002f, imported.points.first().pressureHpa)
            assertEquals("restored.jpg", imported.points.first().photoPath)
            assertEquals(listOf(10, 20, 30), File(photoDir, "restored.jpg").readBytes().map { it.toInt() })
            val portable = JSONObject(requireNotNull(imported.settingsJson))
            assertEquals(1.2, portable.getDouble("marker_scale"), 0.001)
            assertFalse(portable.has("quick_add_notification_enabled"))
            assertTrue(imported.manifest.sections.settings)
            assertEquals(2, imported.manifest.counts.tags)
        } finally {
            database.close()
            base.deleteSharedPreferences(preferencesName)
            photoDir.deleteRecursively()
        }
    }
}
