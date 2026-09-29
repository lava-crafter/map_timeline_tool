package com.lavacrafter.maptimelinetool.export

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavacrafter.maptimelinetool.data.AppDatabase
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.data.PointRepository
import com.lavacrafter.maptimelinetool.data.PointTagCrossRef
import com.lavacrafter.maptimelinetool.data.TagEntity
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.PointSensorSnapshot
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider
import com.lavacrafter.maptimelinetool.domain.port.SensorSnapshotPort
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.usecase.PointWriteUseCase
import com.lavacrafter.maptimelinetool.domain.usecase.TagManagementUseCase
import com.lavacrafter.maptimelinetool.ui.AppViewModel
import com.lavacrafter.maptimelinetool.ui.SettingsStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only the debug test application's own files, preferences and in-memory Room database. */
@RunWith(AndroidJUnit4::class)
class ZipRestoreCoordinatorTest {
    @Test fun largeFullBackupRoundTrip() = runBlocking {
        fixture { context, database, repository, archive ->
            val dao = database.pointDao()
            val source = File(context.cacheDir, "source_${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val small = jpeg(8)
                val large = jpeg(800)
                val attached = dao.insertTag(TagEntity(name = "Attached"))
                dao.insertTag(TagEntity(name = "Unattached"))
                for (index in 0..1000) {
                    val photo = if (index < 302) File(source, "image_$index.jpg").apply {
                        writeBytes(if (index < 300) small else large)
                    }.name else null
                    val id = dao.insert(PointEntity(timestamp = 1_710_000_000_000L + index,
                        latitude = 10.0, longitude = 20.0, title = "Point $index", note = "notes",
                        pressureHpa = 1001f, photoPath = photo))
                    if (index % 2 == 0) dao.insertPointTag(PointTagCrossRef(id, attached))
                }
                SettingsStore.setMarkerScale(context, 1.2f)
                FullBackupAssembler(repository).assemble().writeZip(archive.outputStream(),
                    { name -> File(source, name) }, SettingsStore.exportBackupJson(context), "test")
                database.clearAllTables()
                SettingsStore.setMarkerScale(context, 0.7f)

                val state = MutableStateFlow<RestoreState>(RestoreState.Idle)
                val model = viewModel(repository)
                ZipRestoreCoordinator(context, repository).restore(Uri.fromFile(archive), state, model::importZipData)

                assertEquals(RestoreState.Success(1001), state.value)
                val restored = dao.getAll()
                assertEquals(1001, restored.size)
                assertEquals(302, restored.count { it.photoPath != null })
                assertEquals(setOf("Attached", "Unattached"), dao.getAllTags().map { it.name }.toSet())
                assertEquals(501, dao.getAllPointTagRelations().size)
                assertTrue(restored.all { it.pressureHpa == 1001f })
                assertTrue(restored.filter { it.photoPath != null }.all {
                    File(context.filesDir, "point_photos/${it.photoPath}").isFile
                })
                assertEquals(1.2f, SettingsStore.getMarkerScale(context), 0.01f)
            } finally { source.deleteRecursively() }
        }
    }

    @Test fun dbFailureRollsBackAndCleansStagedPhotos() = runBlocking {
        smallFixture { context, database, repository, archive ->
            val failing = object : PointRepositoryGateway by repository {
                override suspend fun <T> inTransaction(block: suspend () -> T) = repository.inTransaction(block)
                override suspend fun insert(point: Point): Long = throw IllegalStateException("Injected DB failure")
            }
            val state = MutableStateFlow<RestoreState>(RestoreState.Idle)
            val error = runCatching {
                ZipRestoreCoordinator(context, repository).restore(Uri.fromFile(archive), state,
                    viewModel(failing)::importZipData)
            }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEmpty(context, database)
        }
    }

    @Test fun failedPhotoMoveRollsBackAndCleansFinalPhotos() = runBlocking {
        smallFixture { context, database, repository, archive ->
            val error = runCatching {
                ZipRestoreCoordinator(context, repository, movePhoto = { _, _ -> false })
                    .restore(Uri.fromFile(archive), MutableStateFlow(RestoreState.Idle),
                        viewModel(repository)::importZipData)
            }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEmpty(context, database)
        }
    }

    @Test fun secondPhotoMoveFailureCleansFirstMovedPhoto() = runBlocking {
        fixture { context, database, repository, archive ->
            val first = File(context.cacheDir, "first_${UUID.randomUUID()}.jpg")
            val second = File(context.cacheDir, "second_${UUID.randomUUID()}.jpg")
            try {
                first.writeBytes(jpeg(8))
                second.writeBytes(jpeg(16))
                val points = listOf(first, second).mapIndexed { index, file ->
                    Point(timestamp = 1_710_000_000_000L + index, latitude = 10.0,
                        longitude = 20.0, title = "Point $index", note = "", photoPath = file.absolutePath)
                }
                ZipExporter.export(points, archive.outputStream(), { File(it) },
                    options = ZipExporter.ExportOptions(includePhotos = true))
                var moves = 0
                val error = runCatching {
                    ZipRestoreCoordinator(context, repository, movePhoto = { source, target ->
                        if (++moves == 1) source.renameTo(target) else false
                    }).restore(Uri.fromFile(archive), MutableStateFlow(RestoreState.Idle),
                        viewModel(repository)::importZipData)
                }.exceptionOrNull()
                assertTrue(error is IllegalStateException)
                assertEquals(2, moves)
                assertEmpty(context, database)
            } finally {
                first.delete()
                second.delete()
            }
        }
    }

    @Test fun settingsCommitFailureReturnsWarningWithoutRollingBackData() = runBlocking {
        fixture { context, database, repository, archive ->
            ZipExporter.export(listOf(Point(timestamp = 1_710_000_000_000L, latitude = 10.0,
                longitude = 20.0, title = "Saved", note = "")), archive.outputStream(), { null },
                settingsJsonProvider = { SettingsStore.exportBackupJson(context) })
            val state = MutableStateFlow<RestoreState>(RestoreState.Idle)
            ZipRestoreCoordinator(context, repository, applySettings = { _, _, _ -> false })
                .restore(Uri.fromFile(archive), state, viewModel(repository)::importZipData)
            assertTrue(state.value is RestoreState.SuccessWithWarning)
            assertEquals("Saved", database.pointDao().getAll().single().title)
        }
    }

    @Test fun corruptSettingsFailBeforeTouchingDatabase() = runBlocking {
        fixture { context, database, repository, archive ->
            ZipExporter.export(listOf(Point(timestamp = 1_710_000_000_000L, latitude = 10.0,
                longitude = 20.0, title = "Not saved", note = "")), archive.outputStream(), { null },
                settingsJsonProvider = { "{\"timeout_seconds\":[]}" })
            val failure = runCatching {
                ZipRestoreCoordinator(context, repository).restore(Uri.fromFile(archive),
                    MutableStateFlow(RestoreState.Idle), viewModel(repository)::importZipData)
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEmpty(context, database)
        }
    }

    @Test fun recoveryKeepsReferencedPhotoAndDeletesUnreferencedPhoto() = runBlocking {
        fixture { context, database, repository, _ ->
            val dir = File(context.filesDir, "point_photos").apply { mkdirs() }
            val committed = File(dir, "point_photo_committed.jpg").apply { writeBytes(jpeg(8)) }
            val orphan = File(dir, "point_photo_orphan.jpg").apply { writeBytes(jpeg(8)) }
            val staging = File(context.filesDir, "point_photo_imports/import_crashed").apply { mkdirs() }
            File(staging, "restore-journal.json").writeText(
                "[\"${committed.name}\",\"${orphan.name}\"]")
            database.pointDao().insert(PointEntity(timestamp = 1_710_000_000_000L, latitude = 10.0,
                longitude = 20.0, title = "Saved", note = "", photoPath = committed.name))

            ZipRestoreCoordinator(context, repository).recover()

            assertTrue(committed.exists())
            assertFalse(orphan.exists())
            assertFalse(staging.exists())
        }
    }

    @Test fun cancellationAfterStagingDoesNotTouchFinalData() = runBlocking {
        smallFixture { context, database, repository, archive ->
            val error = runCatching {
                ZipRestoreCoordinator(context, repository, beforeCommit = { throw CancellationException("cancel") })
                    .restore(Uri.fromFile(archive), MutableStateFlow(RestoreState.Idle),
                        viewModel(repository)::importZipData)
            }.exceptionOrNull()
            assertTrue(error is CancellationException)
            assertEmpty(context, database)
        }
    }

    private suspend fun smallFixture(test: suspend (Context, AppDatabase, PointRepository, File) -> Unit) {
        fixture { context, db, repo, archive ->
            val image = File(context.cacheDir, "small_${UUID.randomUUID()}.jpg")
            try {
                image.writeBytes(jpeg(8))
                ZipExporter.export(listOf(Point(timestamp = 1_710_000_000_000L, latitude = 10.0,
                    longitude = 20.0, title = "Point", note = "", photoPath = image.absolutePath)),
                    archive.outputStream(), { File(it) },
                    options = ZipExporter.ExportOptions(includePhotos = true))
                test(context, db, repo, archive)
            } finally { image.delete() }
        }
    }

    private suspend fun fixture(test: suspend (Context, AppDatabase, PointRepository, File) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val sandbox = File(base.filesDir, "restore_test_${UUID.randomUUID()}").apply { mkdirs() }
        val preferences = "restore_test_${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = sandbox
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(preferences, mode)
        }
        val archive = File(sandbox, "backup.zip")
        val db = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).build()
        try { test(context, db, PointRepository(db, db.pointDao()), archive) }
        finally {
            db.close()
            base.deleteSharedPreferences(preferences)
            sandbox.deleteRecursively()
        }
    }

    private suspend fun assertEmpty(context: Context, db: AppDatabase) {
        assertTrue(db.pointDao().getAll().isEmpty())
        assertTrue(db.pointDao().getAllTags().isEmpty())
        assertFalse(File(context.filesDir, "point_photos").listFiles()?.isNotEmpty() == true)
        assertFalse(File(context.filesDir, "point_photo_imports").listFiles()?.isNotEmpty() == true)
    }

    private fun jpeg(side: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        try {
            val colors = IntArray(side * side) { index ->
                val n = index * 1664525 + 1013904223
                0xff000000.toInt() or (n and 0x00ffffff)
            }
            bitmap.setPixels(colors, 0, side, 0, 0, side, side)
            return java.io.ByteArrayOutputStream().use { bytes ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes))
                bytes.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun viewModel(repo: PointRepositoryGateway): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val sensor = object : SensorSnapshotPort {
            override suspend fun readSnapshot() = PointSensorSnapshot()
        }
        val location = object : LocationProvider {
            override fun getLastKnownLocation(): GeoPoint? = null
            override suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint? = null
            override suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? = null
            override suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? = null
        }
        return AppViewModel(app, repo, PointWriteUseCase(repo, sensor, { }), TagManagementUseCase(repo), location)
    }
}
