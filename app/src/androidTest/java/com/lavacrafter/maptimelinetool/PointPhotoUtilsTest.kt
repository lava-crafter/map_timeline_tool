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
import java.io.File
import java.util.UUID
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
}
