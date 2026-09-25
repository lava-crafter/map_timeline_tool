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

import com.lavacrafter.maptimelinetool.domain.model.Point
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Diagnostic assertions for archive identity and declared structure, not a substitute for DB/file recovery tests. */
class ArchiveBoundaryVerificationTest {
    private val pointCsv = "name,description,latitude,longitude,time_utc\n" +
        "P,,10.0,20.0,2024-01-01T00:00:00Z\n"

    @Test
    fun legacyArchiveWithoutManifestStillImportsItsPointsAndTags() {
        val restored = import(
            archive(
                "points.csv" to pointCsv.toByteArray(),
                "tags.csv" to "tag_id,name\n42,Work\n".toByteArray(),
                "point_tags.csv" to "point_index,tag_id\n0,42\n".toByteArray()
            )
        )

        assertEquals(1, restored.manifest.version)
        assertEquals(1, restored.points.size)
        assertEquals(listOf(ZipImporter.ImportedTag(42, "Work")), restored.tags)
        assertEquals(listOf(ZipImporter.ImportedPointTag(0, 42)), restored.pointTags)
    }

    @Test
    fun twoPointsSharingOnePhotoExportOneEntryAndRestoreBothReferences() {
        val directory = createTempDirectory("shared-photo-backup").toFile()
        try {
            val photo = File(directory, "shared.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val output = ByteArrayOutputStream()
            ZipExporter.export(
                points = listOf(point(1, photo.absolutePath), point(2, photo.absolutePath)),
                outputStream = output,
                resolvePhotoFile = { File(it) },
                options = ZipExporter.ExportOptions(includePhotos = true)
            )

            val names = mutableListOf<String>()
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
                while (true) {
                    names += zip.nextEntry?.name ?: break
                    zip.closeEntry()
                }
            }
            assertEquals(listOf("photos/shared.jpg"), names.filter { it.startsWith("photos/") })

            var saved = 0
            val restored = ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { _, input ->
                assertTrue(input.readBytes().contentEquals(photo.readBytes()))
                saved++
                "stored-photo.jpg"
            }
            assertEquals(1, saved)
            assertEquals(listOf("stored-photo.jpg", "stored-photo.jpg"), restored.points.map { it.photoPath })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun duplicatePointsEntryCannotSilentlyReplaceEarlierPoints() {
        val conflictingCsv = pointCsv.replace("P,,", "DIFFERENT,,")
        val bytes = archive(
            "points.csv" to pointCsv.toByteArray(),
            "POINTS.csv" to conflictingCsv.toByteArray()
        )

        assertThrows(IllegalArgumentException::class.java) { import(bytes) }
    }

    @Test
    fun declaredAbsentPointsCannotHidePresentPointData() {
        val manifest = """{"backup_version":2,"sections":{"points":false,"tags":false}}"""
        val bytes = archive(
            "backup_manifest.json" to manifest.toByteArray(),
            "points.csv" to pointCsv.toByteArray()
        )

        assertThrows(IllegalArgumentException::class.java) { import(bytes) }
    }

    @Test
    fun declaredPresentPointsRequireThePointsEntry() {
        val manifest = """{"backup_version":2,"sections":{"points":true,"tags":false},"counts":{"points":1}}"""
        val bytes = archive("backup_manifest.json" to manifest.toByteArray())

        assertThrows(IllegalArgumentException::class.java) { import(bytes) }
    }

    @Test
    fun duplicatePhotoEntryCannotReplacePhotoAfterItIsSaved() {
        val bytes = archive(
            "points.csv" to pointCsv.toByteArray(),
            "photos/a.jpg" to byteArrayOf(1, 2),
            "photos\\a.jpg" to byteArrayOf(3, 4)
        )
        var saves = 0

        assertThrows(IllegalArgumentException::class.java) {
            ZipImporter.importZip(ByteArrayInputStream(bytes)) { _, input ->
                input.readBytes()
                saves++
                "photo.jpg"
            }
        }
        assertTrue(saves <= 1)
    }

    @Test
    fun duplicatePhotoEntryWithDotSegmentCannotBeSavedTwice() {
        val bytes = archive(
            "photos/a.jpg" to byteArrayOf(1, 2),
            "photos/./a.jpg" to byteArrayOf(3, 4)
        )
        var saves = 0

        assertThrows(IllegalArgumentException::class.java) {
            ZipImporter.importZip(ByteArrayInputStream(bytes)) { _, input ->
                input.readBytes()
                saves++
                "photo.jpg"
            }
        }
        assertEquals(1, saves)
    }

    private fun point(id: Long, photoPath: String): Point = Point(
        id = id,
        timestamp = 1_710_000_000_000L + id * 1_000L,
        latitude = 10.0,
        longitude = 20.0,
        title = "P$id",
        note = "",
        photoPath = photoPath
    )

    private fun import(bytes: ByteArray): ZipImporter.ImportStats =
        ZipImporter.importZip(ByteArrayInputStream(bytes)) { _, input ->
            input.readBytes()
            "photo.jpg"
        }

    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
