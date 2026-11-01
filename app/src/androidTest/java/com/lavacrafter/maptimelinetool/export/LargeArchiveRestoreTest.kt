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
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
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
import com.lavacrafter.maptimelinetool.domain.usecase.PointWriteUseCase
import com.lavacrafter.maptimelinetool.domain.usecase.TagManagementUseCase
import com.lavacrafter.maptimelinetool.ui.AppViewModel
import com.lavacrafter.maptimelinetool.ui.SettingsStore
import java.io.File
import java.security.MessageDigest
import java.util.Random
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a > 2 GiB full backup on the device and restores it (no fixture is committed).
 * Opt-in because it writes several GiB: `-Pandroid.testInstrumentationRunnerArguments.largeArchive=true`.
 */
@RunWith(AndroidJUnit4::class)
class LargeArchiveRestoreTest {
    @Test fun archiveLargerThanTwoGibRestoresAndCleansStaging() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("largeArchive") == "true")
        val base = ApplicationProvider.getApplicationContext<Application>()
        val sandbox = File(base.filesDir, "large_restore_${UUID.randomUUID()}").apply { mkdirs() }
        val preferences = "large_restore_${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = sandbox
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(preferences, mode)
        }
        val freeBefore = sandbox.usableSpace
        assumeTrue("Needs ~${3 * TOTAL_BYTES / GIB} GiB free", freeBefore > 3 * TOTAL_BYTES + 2 * GIB)
        val db = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).build()
        val repository = PointRepository(db, db.pointDao())
        try {
            val source = File(sandbox, "source").apply { mkdirs() }
            val header = jpeg(16)
            val expected = HashMap<Long, String>()
            for (index in 0 until PHOTO_COUNT) {
                val photo = File(source, "large_$index.jpg")
                // Bytes after EOI are ignored by decoders; random padding keeps DEFLATE from shrinking the archive.
                val digest = MessageDigest.getInstance("SHA-256")
                photo.outputStream().buffered(1 shl 20).use { output ->
                    output.write(header); digest.update(header)
                    val random = Random(index.toLong()); val chunk = ByteArray(1 shl 20)
                    repeat((PADDING_BYTES / chunk.size).toInt()) {
                        random.nextBytes(chunk); output.write(chunk); digest.update(chunk)
                    }
                }
                val timestamp = 1_710_000_000_000L + index
                db.pointDao().insert(PointEntity(timestamp = timestamp, latitude = 10.0, longitude = 20.0,
                    title = "Large $index", note = "", photoPath = photo.name))
                expected[timestamp] = digest.digest().toHex()
            }
            val archive = File(sandbox, "backup.zip")
            val exportStart = System.nanoTime()
            FullBackupAssembler(repository).assemble().writeZip(archive.outputStream(),
                { name -> File(source, name) }, SettingsStore.exportBackupJson(context), "test")
            val exportMs = (System.nanoTime() - exportStart) / 1_000_000
            assertTrue("archive is ${archive.length()} bytes", archive.length() > 2L * GIB)
            source.deleteRecursively()
            db.clearAllTables()

            val state = MutableStateFlow<RestoreState>(RestoreState.Idle)
            val restoreStart = System.nanoTime()
            ZipRestoreCoordinator(context, repository).restore(Uri.fromFile(archive), state, viewModel(repository)::importZipData)
            val restoreMs = (System.nanoTime() - restoreStart) / 1_000_000
            Log.i(TAG, "archive=${archive.length()} bytes export=${exportMs}ms restore=${restoreMs}ms")

            assertEquals(RestoreState.Success(PHOTO_COUNT), state.value)
            val restored = db.pointDao().getAll()
            assertEquals(PHOTO_COUNT, restored.size)
            restored.forEach { point ->
                val file = File(sandbox, "point_photos/${requireNotNull(point.photoPath)}")
                assertEquals(header.size + PADDING_BYTES, file.length())
                assertEquals(expected[point.timestamp], file.sha256())
            }
            assertTrue(File(sandbox, "point_photo_imports").listFiles().isNullOrEmpty())
            archive.delete()
            // Only the restored photos should remain: no staged copies were left behind.
            val remaining = sandbox.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            assertEquals(PHOTO_COUNT * (header.size + PADDING_BYTES), remaining)
        } finally {
            db.close()
            base.deleteSharedPreferences(preferences)
            sandbox.deleteRecursively()
        }
    }

    private fun viewModel(repo: PointRepository): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val sensor = object : SensorSnapshotPort { override suspend fun readSnapshot() = PointSensorSnapshot() }
        val location = object : LocationProvider {
            override fun getLastKnownLocation(): GeoPoint? = null
            override suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint? = null
            override suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? = null
            override suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? = null
        }
        return AppViewModel(app, repo, PointWriteUseCase(repo, sensor, { }), TagManagementUseCase(repo), location)
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

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered(1 shl 20).use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "LargeArchiveRestoreTest"
        const val GIB = 1024L * 1024L * 1024L
        const val PHOTO_COUNT = 20
        // 20 × 115 MiB ≈ 2.25 GiB, each under the 128 MiB per-photo limit.
        const val PADDING_BYTES = 115L * 1024L * 1024L
        const val TOTAL_BYTES = PHOTO_COUNT * PADDING_BYTES
    }
}
