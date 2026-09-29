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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavacrafter.maptimelinetool.ui.PhotoCompressFormat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PointPhotoUtilsTest {
    @Test
    fun largePhotoUsesBoundedSampledDecode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(getPointPhotoDir(context), "large_${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(4096, 3072, Bitmap.Config.ARGB_8888)
        var generated: File? = null
        try {
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            val prepared = preparePhotoForPersist(
                context, source.name, PhotoPersistOptions(false, PhotoCompressFormat.JPEG, 80)
            )
            generated = File(getPointPhotoDir(context), requireNotNull(prepared.generatedPath))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(generated.absolutePath, bounds)
            assertTrue(bounds.outWidth <= MAX_WORKING_PHOTO_SIDE)
            assertTrue(bounds.outHeight <= MAX_WORKING_PHOTO_SIDE)
            assertTrue(bounds.outWidth.toLong() * bounds.outHeight <= MAX_WORKING_PHOTO_PIXELS)
            prepared.rollback(context)
            assertTrue(source.exists())
            assertFalse(generated.exists())
        } finally {
            bitmap.recycle()
            source.delete()
            generated?.delete()
        }
    }

    @Test
    fun sampleSizeCapsLongEdgeAndPixelCount() {
        val sample = photoDecodeSampleSize(30_000, 8_000)
        assertTrue((30_000L + sample - 1) / sample <= MAX_WORKING_PHOTO_SIDE)
        assertTrue(((30_000L + sample - 1) / sample) * ((8_000L + sample - 1) / sample) <= MAX_WORKING_PHOTO_PIXELS)
    }

    @Test
    fun invalidImageFailsWithoutDeletingTheOriginal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(getPointPhotoDir(context), "invalid_${UUID.randomUUID()}.jpg")
        try {
            source.writeBytes(byteArrayOf(1, 2, 3))
            val failure = runCatching {
                preparePhotoForPersist(context, source.name,
                    PhotoPersistOptions(false, PhotoCompressFormat.JPEG, 80))
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(source.exists())
        } finally {
            source.delete()
        }
    }

    @Test
    fun resolvePointPhotoFile_onlyAllowsTheDedicatedPhotoDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photoDir = getPointPhotoDir(context)
        val valid = java.io.File(photoDir, "photo-test.jpg")

        assertEquals(valid.canonicalFile, requireNotNull(resolvePointPhotoFile(context, valid.name)))
        assertNull(resolvePointPhotoFile(context, valid.absolutePath))
        assertNull(resolvePointPhotoFile(context, "../shared_prefs/map_timeline_settings.xml"))
        assertNull(resolvePointPhotoFile(context, "nested/photo-test.jpg"))
        assertNull(resolvePointPhotoFile(context, context.filesDir.absolutePath))
    }

    @Test
    fun committingStagedPhotoMovesItIntoTheDedicatedDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val staging = createPointPhotoImportStagingDir(context)
        val staged = File(staging, "verification_${UUID.randomUUID()}.jpg")
        val destination = File(getPointPhotoDir(context), staged.name)
        try {
            staged.writeBytes(byteArrayOf(1, 2, 3))

            assertEquals(listOf(staged.name), commitPointPhotoImport(context, listOf(staged)))

            assertFalse(staged.exists())
            assertTrue(destination.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        } finally {
            destination.delete()
            deletePointPhotoImportStagingDir(staging)
        }
    }

    @Test
    fun invalidSecondStagedPhotoRollsBackFirstCommittedPhoto() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val staging = createPointPhotoImportStagingDir(context)
        val staged = File(staging, "verification_${UUID.randomUUID()}.jpg")
        val destination = File(getPointPhotoDir(context), staged.name)
        try {
            staged.writeBytes(byteArrayOf(1, 2, 3))
            val invalid = File(staging, "missing.jpg")

            assertThrows(IllegalArgumentException::class.java) {
                commitPointPhotoImport(context, listOf(staged, invalid))
            }

            assertFalse(destination.exists())
            assertFalse(staged.exists())
        } finally {
            destination.delete()
            deletePointPhotoImportStagingDir(staging)
        }
    }

    @Test
    fun preparingPhotoKeepsSourceUntilCommitCleanup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(getPointPhotoDir(context), "source_${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        var generated: File? = null
        try {
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            val prepared = preparePhotoForPersist(
                context,
                source.name,
                PhotoPersistOptions(false, PhotoCompressFormat.PNG, 100)
            )

            assertEquals(source.name, prepared.sourcePath)
            assertTrue(source.exists())
            generated = File(getPointPhotoDir(context), requireNotNull(prepared.generatedPath))
            assertTrue(generated.exists())
            prepared.commitCleanup(context) { false }
            assertFalse(source.exists())
        } finally {
            bitmap.recycle()
            source.delete()
            generated?.delete()
        }
    }

    @Test
    fun rollbackRemovesGeneratedPhotoAndRetainsSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(getPointPhotoDir(context), "source_${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        var generated: File? = null
        try {
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            val prepared = preparePhotoForPersist(
                context,
                source.name,
                PhotoPersistOptions(false, PhotoCompressFormat.PNG, 100)
            )
            generated = File(getPointPhotoDir(context), requireNotNull(prepared.generatedPath))
            prepared.rollback(context)

            assertTrue(source.exists())
            assertFalse(generated.exists())
        } finally {
            bitmap.recycle()
            source.delete()
            generated?.delete()
        }
    }

    @Test
    fun commitCleanupDoesNotDeleteSourceStillReferencedByAnotherPoint() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(getPointPhotoDir(context), "source_${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        var generated: File? = null
        try {
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            val prepared = preparePhotoForPersist(
                context, source.name, PhotoPersistOptions(false, PhotoCompressFormat.PNG, 100)
            )
            generated = File(getPointPhotoDir(context), requireNotNull(prepared.generatedPath))

            prepared.commitCleanup(context) { it == source.name }

            assertTrue(source.exists())
            assertTrue(generated.exists())
        } finally {
            bitmap.recycle()
            source.delete()
            generated?.delete()
        }
    }

    @Test
    fun losslessPreparationNeverGeneratesOrDeletesPhoto() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(getPointPhotoDir(context), "source_${UUID.randomUUID()}.jpg")
        try {
            source.writeBytes(byteArrayOf(1, 2, 3))
            val prepared = preparePhotoForPersist(
                context, source.name, PhotoPersistOptions(true, PhotoCompressFormat.JPEG, 80)
            )

            assertEquals(source.name, prepared.storedPath)
            assertNull(prepared.generatedPath)
            prepared.rollback(context)
            prepared.commitCleanup(context) { false }
            assertTrue(source.exists())
        } finally {
            source.delete()
        }
    }
}
