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

import android.app.Application
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lavacrafter.maptimelinetool.data.AppDatabase
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.data.PointRepository
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.model.PointSensorSnapshot
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider
import com.lavacrafter.maptimelinetool.domain.port.SensorSnapshotPort
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.usecase.PointWriteUseCase
import com.lavacrafter.maptimelinetool.domain.usecase.TagManagementUseCase
import com.lavacrafter.maptimelinetool.ui.AppViewModel
import com.lavacrafter.maptimelinetool.ui.SettingsStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real process death at restore commit boundaries. Driven from the host in two `am instrument` runs
 * (the first one is killed, the second runs startup recovery in a new process), so both methods are
 * skipped in the normal suite. See docs/bugfix-20261030-verification.md for the driver commands.
 *
 * Fixture: the live DB holds point K with photo OLD; the archive replaces K's photo and adds point N.
 */
@RunWith(AndroidJUnit4::class)
class RestoreProcessDeathTest {
    private val arguments = InstrumentationRegistry.getArguments()
    private val base = ApplicationProvider.getApplicationContext<Application>()
    private val sandbox = File(base.filesDir, "restore_process_death")
    private val context = object : ContextWrapper(base) {
        override fun getFilesDir(): File = sandbox
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(PREFERENCES, mode)
    }

    @Test fun killDuringRestore(): Unit = runBlocking {
        val boundary = arguments.getString("killBoundary")
        assumeTrue(boundary in BOUNDARIES)
        sandbox.deleteRecursively(); sandbox.mkdirs()
        val archive = buildArchive()
        val db = openDatabase()
        val dao = db.pointDao()
        File(sandbox, "point_photos").mkdirs()
        File(sandbox, "point_photos/$OLD_PHOTO").writeBytes(jpeg(4))
        dao.insert(PointEntity(timestamp = KEY_TIME, latitude = 10.0, longitude = 20.0, title = "old", note = "",
            photoPath = OLD_PHOTO))
        val repository = PointRepository(db, dao)
        val gateway = object : PointRepositoryGateway by repository {
            override suspend fun <T> inTransaction(block: suspend () -> T): T =
                repository.inTransaction(block).also { if (boundary == "committed") die() }
        }
        val coordinator = ZipRestoreCoordinator(context, gateway,
            beforeCommit = { if (boundary == "staging") die() },
            movePhoto = { source, destination ->
                source.renameTo(destination).also { if (boundary == "transaction") die() }
            })
        coordinator.restore(Uri.fromFile(archive), MutableStateFlow(RestoreState.Idle), viewModel(gateway)::importZipData)
        error("process should have been killed at $boundary")
    }

    @Test fun verifyAfterProcessDeath() = runBlocking {
        val boundary = arguments.getString("verifyBoundary")
        assumeTrue(boundary in BOUNDARIES)
        val db = openDatabase()
        try {
            val dao = db.pointDao()
            val stagingRoot = File(sandbox, "point_photo_imports")
            assertTrue("staging left by the killed run", stagingRoot.listFiles().orEmpty().isNotEmpty())
            if (boundary != "staging") assertTrue(stagingRoot.walkTopDown().any { it.name == "restore-journal.json" })
            // Same call as MapTimelineApp's startup recovery.
            ZipRestoreCoordinator(context, PointRepository(db, dao)).recover()

            assertTrue(stagingRoot.listFiles().isNullOrEmpty())
            val points = dao.getAll().sortedBy { it.timestamp }
            val photos = File(sandbox, "point_photos").listFiles().orEmpty().map { it.name }.toSet()
            if (boundary == "committed") {
                assertEquals(listOf(KEY_TIME, NEW_TIME), points.map { it.timestamp })
                assertEquals("restored", points.first().title)
                // Every committed reference resolves; the retired OLD photo is cleaned.
                assertEquals(points.mapNotNull { it.photoPath }.toSet(), photos)
                assertFalse(OLD_PHOTO in photos)
            } else {
                // Rolled back: original row and photo intact, moved/staged photos removed.
                assertEquals(listOf(KEY_TIME), points.map { it.timestamp })
                assertEquals("old", points.single().title)
                assertEquals(OLD_PHOTO, points.single().photoPath)
                assertEquals(setOf(OLD_PHOTO), photos)
            }
        } finally {
            db.close()
            base.deleteDatabase(DB_NAME)
            base.deleteSharedPreferences(PREFERENCES)
            sandbox.deleteRecursively()
        }
    }

    private fun openDatabase() = Room.databaseBuilder(base, AppDatabase::class.java, DB_NAME).build()

    private suspend fun buildArchive(): File {
        val source = File(sandbox, "source").apply { mkdirs() }
        val memory = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).build()
        try {
            listOf(KEY_TIME to "restored", NEW_TIME to "new").forEachIndexed { index, (time, title) ->
                val photo = File(source, "archive_$index.jpg").apply { writeBytes(jpeg(8 + index)) }
                memory.pointDao().insert(PointEntity(timestamp = time, latitude = 10.0, longitude = 20.0,
                    title = title, note = "", photoPath = photo.name))
            }
            val archive = File(sandbox, "backup.zip")
            FullBackupAssembler(PointRepository(memory, memory.pointDao())).assemble().writeZip(archive.outputStream(),
                { name -> File(source, name) }, SettingsStore.exportBackupJson(context), "test")
            return archive
        } finally {
            memory.close()
            source.deleteRecursively()
        }
    }

    private fun die(): Nothing {
        Process.killProcess(Process.myPid())
        error("unreachable")
    }

    private fun viewModel(repo: PointRepositoryGateway): AppViewModel {
        val sensor = object : SensorSnapshotPort { override suspend fun readSnapshot() = PointSensorSnapshot() }
        val location = object : LocationProvider {
            override fun getLastKnownLocation(): GeoPoint? = null
            override suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint? = null
            override suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? = null
            override suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? = null
        }
        return AppViewModel(base, repo, PointWriteUseCase(repo, sensor, { }), TagManagementUseCase(repo), location)
    }

    private fun jpeg(side: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        try {
            return java.io.ByteArrayOutputStream().use { bytes ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes))
                bytes.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private companion object {
        val BOUNDARIES = setOf("staging", "transaction", "committed")
        const val DB_NAME = "restore_process_death.db"
        const val PREFERENCES = "restore_process_death"
        const val OLD_PHOTO = "point_photo_old.jpg"
        const val KEY_TIME = 1_710_000_000_000L
        const val NEW_TIME = 1_710_000_001_000L
    }
}
