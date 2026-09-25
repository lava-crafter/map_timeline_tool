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

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.PointSensorSnapshot
import com.lavacrafter.maptimelinetool.domain.model.Tag
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider
import com.lavacrafter.maptimelinetool.domain.port.SensorSnapshotPort
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.usecase.PointWriteUseCase
import com.lavacrafter.maptimelinetool.domain.usecase.TagManagementUseCase
import com.lavacrafter.maptimelinetool.export.BackupManifest
import com.lavacrafter.maptimelinetool.export.BackupSections
import com.lavacrafter.maptimelinetool.export.CsvImporter
import com.lavacrafter.maptimelinetool.export.ZipImporter
import com.lavacrafter.maptimelinetool.ui.AppViewModel
import com.lavacrafter.maptimelinetool.ui.SettingsStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory Room tests for the persistence side of known import and relationship defects. */
@RunWith(AndroidJUnit4::class)
class DataIntegrityVerificationTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: PointRepository

    @Before
    fun createDatabase() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        repository = PointRepository(database, database.pointDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun importingLegacyCsvDoesNotEraseExistingPhotoAndMetadata() = runBlocking {
        val id = database.pointDao().insert(
            pointEntity(timestamp = 1_704_067_200_000L).copy(
                photoPath = "original.jpg",
                locationAccuracyMeters = 8f,
                pressureHpa = 1_002f
            )
        )
        val imported = CsvImporter.parseCsv(
            "name,description,latitude,longitude,time_utc\n" +
                "Point,,10.0,20.0,2024-01-01T00:00:00Z\n"
        )

        pointWriter().importPoints(imported)

        val afterImport = database.pointDao().getAll().single { it.id == id }
        assertEquals("original.jpg", afterImport.photoPath)
        assertEquals(8f, afterImport.locationAccuracyMeters)
        assertEquals(1_002f, afterImport.pressureHpa)
    }

    @Test
    fun failedCsvImportDoesNotCommitEarlierRows() = runBlocking {
        val failingRepository = object : PointRepositoryGateway by repository {
            override suspend fun <T> inTransaction(block: suspend () -> T): T = repository.inTransaction(block)

            override suspend fun insert(point: Point): Long {
                if (point.title == "Second") throw IllegalStateException("Injected DB failure")
                return repository.insert(point)
            }
        }

        val failure = runCatching {
            pointWriter(failingRepository).importPoints(
                listOf(point(timestamp = 1_000L, title = "First"), point(timestamp = 2_000L, title = "Second"))
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(database.pointDao().getAll().isEmpty())
    }

    @Test
    fun importingTheSameLegacyCsvTwiceDoesNotCreateExtraRows() = runBlocking {
        val imported = CsvImporter.parseCsv(
            "name,description,latitude,longitude,time_utc\n" +
                "Old format,Some notes,10.0,20.0,2024-01-01T00:00:00Z\n"
        )
        val writer = pointWriter()

        writer.importPoints(imported)
        writer.importPoints(imported)

        val saved = database.pointDao().getAll().single()
        assertEquals("Old format", saved.title)
        assertEquals("Some notes", saved.note)
        assertEquals(null, saved.pressureHpa)
        assertEquals(null, saved.photoPath)
    }

    @Test
    fun failedTagInsertDoesNotLeavePointOrPartialTags() = runBlocking {
        val failingRepository = object : PointRepositoryGateway by repository {
            override suspend fun <T> inTransaction(block: suspend () -> T): T = repository.inTransaction(block)

            override suspend fun insertPointTag(pointId: Long, tagId: Long) {
                if (tagId == 2L) throw IllegalStateException("Injected tag failure")
                repository.insertPointTag(pointId, tagId)
            }
        }

        val failure = runCatching {
            pointWriter(failingRepository).addPointWithTags(
                title = "Point",
                note = "",
                location = GeoPoint(10.0, 20.0),
                timestamp = 1_000L,
                tagIds = linkedSetOf(1L, 2L)
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(database.pointDao().getAll().isEmpty())
    }

    @Test
    fun deletingPointDoesNotLeavePointTagRows() = runBlocking {
        val id = database.pointDao().insert(pointEntity())
        val tagId = database.pointDao().insertTag(TagEntity(name = "Work"))
        repository.insertPointTag(id, tagId)

        repository.delete(point(timestamp = 1_000L).copy(id = id))

        assertTrue(database.pointDao().getTagIdsForPoint(id).isEmpty())
    }

    @Test
    fun deletingTagDoesNotLeavePointTagRows() = runBlocking {
        val id = database.pointDao().insert(pointEntity())
        val tagId = database.pointDao().insertTag(TagEntity(name = "Work"))
        repository.insertPointTag(id, tagId)

        repository.deleteTag(tagId)

        assertTrue(database.pointDao().getTagIdsForPoint(id).isEmpty())
    }

    @Test
    fun deletingTagDoesNotLeaveItsIdInPinnedRecentOrDefaultSettings() = runBlocking {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val prefix = "tag_deletion_${UUID.randomUUID()}_"
        val settingsContext = object : ContextWrapper(testContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                testContext.getSharedPreferences(prefix + name, mode)
        }
        val deleted = database.pointDao().insertTag(TagEntity(name = "Delete me"))
        val kept = database.pointDao().insertTag(TagEntity(name = "Keep me"))
        SettingsStore.setPinnedTagIds(settingsContext, listOf(deleted, kept))
        SettingsStore.setDefaultTagIds(settingsContext, listOf(deleted, kept))
        SettingsStore.addRecentTagId(settingsContext, kept)
        SettingsStore.addRecentTagId(settingsContext, deleted)

        TagManagementUseCase(repository, onTagDeleted = { SettingsStore.removeTagId(settingsContext, it) }).deleteTag(deleted)

        assertEquals(listOf(kept), database.pointDao().getAllTags().map { it.id })
        assertEquals(listOf(kept), SettingsStore.getPinnedTagIds(settingsContext))
        assertEquals(listOf(kept), SettingsStore.getDefaultTagIds(settingsContext))
        assertEquals(listOf(kept), SettingsStore.getRecentTagIds(settingsContext))
    }

    @Test
    fun zipRestoreKeepsTwoDistinctPointsWithTheSameImportKey() = runBlocking {
        val imported = ZipImporter.ImportStats(
            points = listOf(point(title = "First"), point(title = "Second")),
            tags = emptyList(),
            pointTags = emptyList(),
            importedPhotoCount = 0,
            missingPhotoCount = 0,
            manifest = BackupManifest(version = 2, sections = BackupSections(tags = false))
        )

        viewModel().importZipData(imported)

        assertEquals(2, database.pointDao().getAll().size)
    }

    @Test
    fun zipRestoreKeepsTwoSeparateTagsWithTheSameName() = runBlocking {
        val imported = ZipImporter.ImportStats(
            points = emptyList(),
            tags = listOf(ZipImporter.ImportedTag(1L, "Work"), ZipImporter.ImportedTag(2L, "Work")),
            pointTags = emptyList(),
            importedPhotoCount = 0,
            missingPhotoCount = 0,
            manifest = BackupManifest(version = 2, sections = BackupSections(tags = true))
        )

        viewModel().importZipData(imported)

        assertEquals(2, database.pointDao().getAllTags().size)
    }

    @Test
    fun failingZipTagInsertRollsBackPointAndPreviouslyInsertedTag() = runBlocking {
        val failingRepository = object : PointRepositoryGateway by repository {
            override suspend fun insertTag(tag: Tag): Long {
                if (tag.name == "Second") throw IllegalStateException("Injected tag insert failure")
                return repository.insertTag(tag)
            }
        }
        val imported = ZipImporter.ImportStats(
            points = listOf(point()),
            tags = listOf(ZipImporter.ImportedTag(1L, "First"), ZipImporter.ImportedTag(2L, "Second")),
            pointTags = listOf(ZipImporter.ImportedPointTag(0, 1L)),
            importedPhotoCount = 0,
            missingPhotoCount = 0,
            manifest = BackupManifest(version = 2, sections = BackupSections(tags = true))
        )

        val failure = runCatching { viewModel(failingRepository).importZipData(imported) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(database.pointDao().getAll().isEmpty())
        assertTrue(database.pointDao().getAllTags().isEmpty())
    }

    @Test
    fun restoringTheSameZipDataTwiceDoesNotMultiplyItsPointOrTagRelations() = runBlocking {
        val imported = ZipImporter.ImportStats(
            points = listOf(point()),
            tags = listOf(ZipImporter.ImportedTag(7L, "Work")),
            pointTags = listOf(ZipImporter.ImportedPointTag(0, 7L)),
            importedPhotoCount = 0,
            missingPhotoCount = 0,
            manifest = BackupManifest(version = 2, sections = BackupSections(tags = true))
        )
        val model = viewModel()

        model.importZipData(imported)
        model.importZipData(imported)

        val saved = database.pointDao().getAll().single()
        val tag = database.pointDao().getAllTags().single()
        assertEquals("Work", tag.name)
        assertEquals(listOf(tag.id), database.pointDao().getTagIdsForPoint(saved.id))
    }

    @Test
    fun validArchivePointTagIndexReachesTheCorrectRoomRow() = runBlocking {
        val output = ByteArrayOutputStream()
        val csv = "name,description,latitude,longitude,time_utc\n" +
            "First,,10.0,20.0,2024-01-01T00:00:00Z\n" +
            "Second,,11.0,21.0,2024-01-01T00:00:01Z\n"
        ZipOutputStream(output).use { zip ->
            listOf(
                "points.csv" to csv,
                "tags.csv" to "tag_id,name\n5,Work\n",
                "point_tags.csv" to "point_index,tag_id\n1,5\n"
            ).forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        val parsed = ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { _, _ -> null }
        viewModel().importZipData(parsed)

        val saved = database.pointDao().getAll().associateBy { it.title }
        val tag = database.pointDao().getAllTags().single()
        assertTrue(database.pointDao().getTagIdsForPoint(saved.getValue("First").id).isEmpty())
        assertEquals(listOf(tag.id), database.pointDao().getTagIdsForPoint(saved.getValue("Second").id))
    }

    @Test
    fun skippedInvalidArchiveRowCannotReassignItsTagToTheNextRoomPoint() = runBlocking {
        val csv = "name,description,latitude,longitude,time_utc\n" +
            "First,,10.0,20.0,2024-01-01T00:00:00Z\n" +
            "Invalid,,bad,20.0,2024-01-01T00:00:01Z\n" +
            "Third,,11.0,21.0,2024-01-01T00:00:02Z\n"
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            listOf(
                "points.csv" to csv,
                "tags.csv" to "tag_id,name\n5,Work\n",
                "point_tags.csv" to "point_index,tag_id\n1,5\n"
            ).forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        val failure = runCatching {
            val parsed = ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { _, _ -> null }
            viewModel().importZipData(parsed)
        }.exceptionOrNull()

        // Either reject the archive or leave the valid third point untagged; never move row 1's tag to it.
        if (failure != null) {
            assertTrue(failure is IllegalArgumentException)
        } else {
            val third = database.pointDao().getAll().single { it.title == "Third" }
            assertTrue(database.pointDao().getTagIdsForPoint(third.id).isEmpty())
        }
    }

    @Test
    fun deletingOnePointDoesNotDeletePhotoUsedByAnotherPoint() = runBlocking {
        val sharedPhotoPath = "shared.jpg"
        val firstId = database.pointDao().insert(pointEntity(timestamp = 1_000L).copy(photoPath = sharedPhotoPath))
        val secondId = database.pointDao().insert(pointEntity(timestamp = 2_000L).copy(photoPath = sharedPhotoPath))
        val deletedPhotos = mutableListOf<String?>()

        pointWriter(deletePhoto = { deletedPhotos += it }).deletePoint(point(timestamp = 1_000L).copy(id = firstId, photoPath = sharedPhotoPath))

        assertEquals(sharedPhotoPath, database.pointDao().getAll().single { it.id == secondId }.photoPath)
        assertTrue(deletedPhotos.isEmpty())
    }

    @Test
    fun versionTwoManifestWithWrongPointCountCannotBeRestored() {
        val manifest = """{"backup_version":2,"sections":{"points":true},"counts":{"points":2,"tags":0,"photos":0}}"""
        val csv = "name,description,latitude,longitude,time_utc\nPoint,,10.0,20.0,2024-01-01T00:00:00Z\n"
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            listOf("backup_manifest.json" to manifest, "points.csv" to csv).forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { _, _ -> null }
        }
    }

    private fun viewModel(gateway: PointRepositoryGateway = repository): AppViewModel = AppViewModel(
        app = ApplicationProvider.getApplicationContext(),
        repo = gateway,
        pointWriteUseCase = pointWriter(gateway),
        tagManagementUseCase = TagManagementUseCase(gateway),
        locationProvider = object : LocationProvider {
            override fun getLastKnownLocation(): GeoPoint? = null
            override suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint? = null
            override suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? = null
            override suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? = null
        }
    )

    private fun pointWriter(
        gateway: PointRepositoryGateway = repository,
        deletePhoto: suspend (String?) -> Unit = {}
    ) = PointWriteUseCase(
        repository = gateway,
        sensorSnapshotPort = object : SensorSnapshotPort {
            override suspend fun readSnapshot(): PointSensorSnapshot = PointSensorSnapshot()
        },
        deletePhoto = deletePhoto
    )

    private fun point(timestamp: Long = 1_000L, title: String = "Point") = Point(
        timestamp = timestamp,
        latitude = 10.0,
        longitude = 20.0,
        title = title,
        note = ""
    )

    private fun pointEntity(timestamp: Long = 1_000L) = PointEntity(
        timestamp = timestamp,
        latitude = 10.0,
        longitude = 20.0,
        title = "Point",
        note = ""
    )
}
