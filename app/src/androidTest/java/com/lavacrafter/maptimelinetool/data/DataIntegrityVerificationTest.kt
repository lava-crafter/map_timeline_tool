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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.PointSensorSnapshot
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

    private fun viewModel(): AppViewModel = AppViewModel(
        app = ApplicationProvider.getApplicationContext(),
        repo = repository,
        pointWriteUseCase = pointWriter(),
        tagManagementUseCase = TagManagementUseCase(repository),
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
