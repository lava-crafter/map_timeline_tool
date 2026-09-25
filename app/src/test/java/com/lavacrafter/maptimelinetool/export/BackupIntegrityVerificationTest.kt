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
import org.junit.Test

/** Desired backup invariants. Each failing assertion identifies a distinct restoration defect. */
class BackupIntegrityVerificationTest {
    @Test
    fun csvRoundTripPreservesPointTimestampMilliseconds() {
        val original = point(timestamp = 1_710_000_000_123L)

        val restored = CsvImporter.parseCsv(CsvExporter.buildCsv(listOf(original)))

        assertEquals(original.timestamp, restored.single().timestamp)
    }

    @Test
    fun invalidCsvTimestampDoesNotBecomeTheCurrentTime() {
        val csv = "name,description,latitude,longitude,time_utc\n" +
            "Bad time,invalid,10.0,20.0,this-is-not-a-time\n"

        assertEquals(emptyList<Point>(), CsvImporter.parseCsv(csv))
    }

    @Test
    fun zipRoundTripPreservesPointTimestampMilliseconds() {
        val original = point(timestamp = 1_710_000_000_987L)
        val zip = exportZip(listOf(original))

        val restored = ZipImporter.importZip(ByteArrayInputStream(zip)) { _, _ -> null }

        assertEquals(original.timestamp, restored.points.single().timestamp)
    }

    @Test
    fun fullZipBackupIncludesTagsNotYetAttachedToPoints() {
        val zip = exportZip(
            points = listOf(point(id = 10L)),
            tags = listOf(
                ZipExporter.TagRecord(1L, "Attached"),
                ZipExporter.TagRecord(2L, "Unused but saved")
            ),
            pointTagIdsByPointId = mapOf(10L to listOf(1L))
        )

        val restored = ZipImporter.importZip(ByteArrayInputStream(zip)) { _, _ -> null }

        assertEquals(setOf("Attached", "Unused but saved"), restored.tags.map { it.name }.toSet())
    }

    @Test
    fun malformedManifestIsNotMistakenForAnOldArchive() {
        val zip = archive(
            "backup_manifest.json" to "not-json",
            "points.csv" to CsvExporter.buildCsv(listOf(point()))
        )

        assertThrows(IllegalArgumentException::class.java) {
            ZipImporter.importZip(ByteArrayInputStream(zip)) { _, _ -> null }
        }
    }

    @Test
    fun invalidPointRowCannotShiftPointTagIndexes() {
        val csv = "name,description,latitude,longitude,time_utc\n" +
            "A,,10.0,20.0,2024-01-01T00:00:00Z\n" +
            "B,,invalid,20.0,2024-01-01T00:00:01Z\n" +
            "C,,11.0,21.0,2024-01-01T00:00:02Z\n"
        val zip = archive(
            "points.csv" to csv,
            "tags.csv" to "tag_id,name\n10,Work\n",
            "point_tags.csv" to "point_index,tag_id\n1,10\n"
        )

        assertThrows(IllegalArgumentException::class.java) {
            ZipImporter.importZip(ByteArrayInputStream(zip)) { _, _ -> null }
        }
    }

    @Test
    fun restoringPhotoVerifiesTheHashDeclaredInPointsCsv() {
        val directory = createTempDirectory("backup-integrity-photo").toFile()
        try {
            val photo = File(directory, "photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val exported = exportZip(listOf(point(photoPath = photo.absolutePath)), includePhotos = true)

            // Rebuild the archive so its ZIP CRC is valid: only the CSV's SHA-256 is stale.
            val altered = ByteArrayOutputStream()
            ZipInputStream(ByteArrayInputStream(exported)).use { input ->
                ZipOutputStream(altered).use { output ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        val contents = input.readBytes()
                        output.putNextEntry(ZipEntry(entry.name))
                        output.write(if (entry.name.startsWith("photos/")) byteArrayOf(4, 5, 6) else contents)
                        output.closeEntry()
                        input.closeEntry()
                    }
                }
            }

            assertThrows(IllegalArgumentException::class.java) {
                ZipImporter.importZip(ByteArrayInputStream(altered.toByteArray())) { _, input ->
                    input.readBytes()
                    "restored.jpg"
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun point(
        id: Long = 0L,
        timestamp: Long = 1_710_000_000_000L,
        photoPath: String? = null
    ) = Point(
        id = id,
        timestamp = timestamp,
        latitude = 10.0,
        longitude = 20.0,
        title = "Point",
        note = "",
        photoPath = photoPath
    )

    private fun exportZip(
        points: List<Point>,
        tags: List<ZipExporter.TagRecord> = emptyList(),
        pointTagIdsByPointId: Map<Long, List<Long>> = emptyMap(),
        includePhotos: Boolean = false
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipExporter.export(
            points = points,
            outputStream = output,
            resolvePhotoFile = { File(it) },
            options = ZipExporter.ExportOptions(includePoints = true, includeTags = true, includePhotos = includePhotos),
            tags = tags,
            pointTagIdsByPointId = pointTagIdsByPointId
        )
        return output.toByteArray()
    }

    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
