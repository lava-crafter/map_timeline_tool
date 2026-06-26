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
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipExportImportTest {
    @Test
    fun `zip export includes points csv and photos`() {
        val tempDir = createTempDirectory("zip-export-test").toFile()
        val photo = File(tempDir, "a.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val point = Point(
            timestamp = 1710000000000L,
            latitude = 10.0,
            longitude = 20.0,
            title = "A",
            note = "B",
            pressureHpa = 1000f,
            photoPath = photo.absolutePath
        )
        val output = ByteArrayOutputStream()

        ZipExporter.export(
            points = listOf(point),
            outputStream = output,
            resolvePhotoFile = { path -> File(path) },
            options = ZipExporter.ExportOptions(includePoints = true, includeTags = false, includeSensors = false, includePhotos = true),
            tags = emptyList(),
            pointTagIdsByPointId = emptyMap()
        )

        val entryNames = mutableSetOf<String>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryNames.add(entry.name)
                zip.closeEntry()
            }
        }
        assertTrue(entryNames.contains("points.csv"))
        assertTrue(entryNames.any { it.startsWith("photos/") })
        assertTrue(entryNames.contains("backup_manifest.json"))
    }

    @Test
    fun `zip export can include tags and point tags csv`() {
        val point = Point(
            id = 10L,
            timestamp = 1710000000000L,
            latitude = 10.0,
            longitude = 20.0,
            title = "A",
            note = "B"
        )
        val output = ByteArrayOutputStream()

        ZipExporter.export(
            points = listOf(point),
            outputStream = output,
            resolvePhotoFile = { null },
            options = ZipExporter.ExportOptions(includePoints = true, includeTags = true, includePhotos = false),
            tags = listOf(ZipExporter.TagRecord(id = 100L, name = "Office")),
            pointTagIdsByPointId = mapOf(10L to listOf(100L))
        )

        val entryNames = mutableSetOf<String>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryNames.add(entry.name)
                zip.closeEntry()
            }
        }
        assertTrue(entryNames.contains("tags.csv"))
        assertTrue(entryNames.contains("point_tags.csv"))
        assertTrue(entryNames.contains("backup_manifest.json"))
    }

    @Test
    fun `zip export manifest reports filtered tag count`() {
        val point = Point(
            id = 10L,
            timestamp = 1710000000000L,
            latitude = 10.0,
            longitude = 20.0,
            title = "A",
            note = "B"
        )
        val output = ByteArrayOutputStream()

        ZipExporter.export(
            points = listOf(point),
            outputStream = output,
            resolvePhotoFile = { null },
            options = ZipExporter.ExportOptions(includePoints = true, includeTags = true, includePhotos = false),
            tags = listOf(
                ZipExporter.TagRecord(id = 100L, name = "Office"),
                ZipExporter.TagRecord(id = 200L, name = "Unused")
            ),
            pointTagIdsByPointId = mapOf(10L to listOf(100L))
        )

        var manifestJson: String? = null
        java.util.zip.ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "backup_manifest.json") {
                    manifestJson = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }

        val manifest = manifestJson ?: error("manifest missing")
        assertTrue(manifest.contains("\"counts\":{\"points\":1,\"tags\":1,\"photos\":0}"))
    }

    @Test
    fun `zip export with include tags false only writes points and photos`() {
        val tempDir = createTempDirectory("zip-export-no-tags-test").toFile()
        val photo = File(tempDir, "a.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val output = ByteArrayOutputStream()
        val point = Point(
            id = 10L,
            timestamp = 1710000000000L,
            latitude = 10.0,
            longitude = 20.0,
            title = "A",
            note = "B",
            photoPath = photo.absolutePath
        )
        ZipExporter.export(
            points = listOf(point),
            outputStream = output,
            resolvePhotoFile = { path -> File(path) },
            options = ZipExporter.ExportOptions(includePoints = true, includeTags = false, includePhotos = true),
            tags = listOf(ZipExporter.TagRecord(id = 100L, name = "Office")),
            pointTagIdsByPointId = mapOf(10L to listOf(100L))
        )

        val entryNames = mutableSetOf<String>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryNames.add(entry.name)
                zip.closeEntry()
            }
        }
        assertTrue(entryNames.contains("points.csv"))
        assertTrue(entryNames.any { it.startsWith("photos/") })
        assertTrue(entryNames.none { it == "tags.csv" || it == "point_tags.csv" })
        assertTrue(entryNames.contains("backup_manifest.json"))
    }

    @Test
    fun `zip import restores sensors and photo binding`() {
        val point = Point(
            timestamp = 1710000000000L,
            latitude = 1.2,
            longitude = 3.4,
            locationAccuracyMeters = 6.5f,
            locationFixTimeMs = 1709999999000L,
            locationProvider = "gps",
            title = "P",
            note = "N",
            gyroscopeX = 9.9f
        )
        val actualZip = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(actualZip).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("points.csv"))
            val csv = CsvExporter.buildCsv(listOf(point)) { "photos/p.jpg" }
            zip.write(csv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("photos/p.jpg"))
            zip.write(byteArrayOf(7, 8, 9))
            zip.closeEntry()
        }

        val imported = ZipImporter.importZip(ByteArrayInputStream(actualZip.toByteArray())) { entryName, photoInput ->
            val bytes = photoInput.readBytes()
            if (bytes.isEmpty()) null else "stored/$entryName"
        }

        assertEquals(1, imported.points.size)
        assertEquals(9.9f, imported.points.first().gyroscopeX ?: 0f, 0.001f)
        assertEquals(6.5f, imported.points.first().locationAccuracyMeters ?: 0f, 0.001f)
        assertEquals(1709999999000L, imported.points.first().locationFixTimeMs)
        assertEquals("gps", imported.points.first().locationProvider)
        assertEquals("stored/photos/p.jpg", imported.points.first().photoPath)
        assertEquals(1, imported.importedPhotoCount)
    }

    @Test
    fun `kml export includes location metadata`() {
        val point = Point(
            timestamp = 1710000000000L,
            latitude = 1.2,
            longitude = 3.4,
            locationAccuracyMeters = 18f,
            locationFixTimeMs = 1709999998000L,
            locationProvider = "cached_overlay",
            title = "P",
            note = "N"
        )

        val kml = KmlExporter.buildKml(listOf(point))

        assertTrue(kml.contains("Location provider: cached_overlay"))
        assertTrue(kml.contains("Accuracy(m): 18.0"))
        assertTrue(kml.contains("Fix time(ms): 1709999998000"))
    }

    @Test
    fun `zip import parses tags and point tag relations`() {
        val actualZip = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(actualZip).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("points.csv"))
            val csv = CsvExporter.buildCsv(
                listOf(
                    Point(
                        timestamp = 1710000000000L,
                        latitude = 1.2,
                        longitude = 3.4,
                        title = "P",
                        note = "N"
                    )
                )
            )
            zip.write(csv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("tags.csv"))
            val tagsCsv = "\"tag_id\",\"name\"\n\"10\",\"  Work\tTeam\u0007 \"\n"
            zip.write(tagsCsv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("point_tags.csv"))
            val pointTagsCsv = "\"point_index\",\"tag_id\"\n\"0\",\"10\"\n"
            zip.write(pointTagsCsv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val imported = ZipImporter.importZip(ByteArrayInputStream(actualZip.toByteArray())) { _, _ -> null }
        assertEquals(1, imported.tags.size)
        assertEquals("Work Team", imported.tags.first().name)
        assertEquals(1, imported.pointTags.size)
        assertEquals(0, imported.pointTags.first().pointIndex)
        assertEquals(10L, imported.pointTags.first().legacyTagId)
    }

    @Test
    fun `zip export photos only writes photos folder entries`() {
        val tempDir = createTempDirectory("zip-export-photos-only-test").toFile()
        val photo = File(tempDir, "a.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val output = ByteArrayOutputStream()

        ZipExporter.export(
            points = listOf(
                Point(
                    timestamp = 1710000000000L,
                    latitude = 10.0,
                    longitude = 20.0,
                    title = "A",
                    note = "B",
                    photoPath = photo.absolutePath
                )
            ),
            outputStream = output,
            resolvePhotoFile = { path -> File(path) },
            options = ZipExporter.ExportOptions(includePoints = false, includeTags = false, includeSensors = false, includePhotos = true)
        )

        val entryNames = mutableSetOf<String>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryNames.add(entry.name)
                zip.closeEntry()
            }
        }
        assertTrue(entryNames.none { it == "points.csv" })
        assertTrue(entryNames.any { it.startsWith("photos/") })
        assertTrue(entryNames.contains("backup_manifest.json"))
    }

    @Test
    fun `zip import tolerates missing photos`() {
        val zipBytes = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zipBytes).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("points.csv"))
            val csv = CsvExporter.buildCsv(
                listOf(
                    Point(
                        timestamp = 1710000000000L,
                        latitude = 1.0,
                        longitude = 2.0,
                        title = "P",
                        note = "N"
                    )
                )
            ) { "photos/missing.jpg" }
            zip.write(csv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val imported = ZipImporter.importZip(ByteArrayInputStream(zipBytes.toByteArray())) { _, _ -> null }
        assertEquals(1, imported.points.size)
        assertNull(imported.points.first().photoPath)
        assertEquals(1, imported.missingPhotoCount)
        assertNotNull(imported.points.first().title)
    }

    @Test
    fun `zip export writes settings json when provided`() {
        val output = ByteArrayOutputStream()

        ZipExporter.export(
            points = emptyList(),
            outputStream = output,
            resolvePhotoFile = { null },
            options = ZipExporter.ExportOptions(includePoints = false, includeTags = false, includeSensors = false, includePhotos = false),
            settingsJsonProvider = { """{"schema_version":1,"timeout_seconds":30}""" },
            appVersion = "1.2.3"
        )

        val entryNames = mutableSetOf<String>()
        var settingsContent: String? = null
        java.util.zip.ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryNames.add(entry.name)
                if (entry.name == "settings.json") {
                    settingsContent = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        assertTrue(entryNames.contains("backup_manifest.json"))
        assertTrue(entryNames.contains("settings.json"))
        assertEquals("""{"schema_version":1,"timeout_seconds":30}""", settingsContent)
    }

    @Test
    fun `zip import reads settings json and keeps legacy zip compatibility`() {
        val zipBytes = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zipBytes).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("points.csv"))
            val csv = CsvExporter.buildCsv(
                listOf(
                    Point(
                        timestamp = 1710000000000L,
                        latitude = 1.0,
                        longitude = 2.0,
                        title = "P",
                        note = "N"
                    )
                )
            )
            zip.write(csv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("settings.json"))
            zip.write("""{"schema_version":1,"follow_system_theme":false}""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val imported = ZipImporter.importZip(ByteArrayInputStream(zipBytes.toByteArray())) { _, _ -> null }
        assertEquals(1, imported.points.size)
        assertEquals("""{"schema_version":1,"follow_system_theme":false}""", imported.settingsJson)
    }

    @Test
    fun `zip export and import round trip keeps points tags photos and settings`() {
        val tempDir = createTempDirectory("zip-round-trip-test").toFile()
        val firstPhoto = File(tempDir, "first.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val secondPhoto = File(tempDir, "second.jpg").apply { writeBytes(byteArrayOf(5, 6, 7, 8)) }
        val points = listOf(
            Point(
                id = 10L,
                timestamp = 1710000000000L,
                latitude = 10.0,
                longitude = 20.0,
                locationAccuracyMeters = 5.5f,
                locationFixTimeMs = 1709999999000L,
                locationProvider = "gps",
                title = "Alpha",
                note = "First point",
                pressureHpa = 1000.5f,
                ambientLightLux = 44.4f,
                accelerometerX = 1.1f,
                gyroscopeY = 2.2f,
                magnetometerZ = 3.3f,
                noiseDb = 40.4f,
                photoPath = firstPhoto.absolutePath
            ),
            Point(
                id = 20L,
                timestamp = 1710000005000L,
                latitude = 11.0,
                longitude = 21.0,
                title = "Beta",
                note = "Second point",
                photoPath = secondPhoto.absolutePath
            )
        )
        val output = ByteArrayOutputStream()

        ZipExporter.export(
            points = points,
            outputStream = output,
            resolvePhotoFile = { path -> File(path) },
            options = ZipExporter.ExportOptions(includePoints = true, includeTags = true, includeSensors = true, includePhotos = true),
            tags = listOf(
                ZipExporter.TagRecord(id = 100L, name = "Work"),
                ZipExporter.TagRecord(id = 200L, name = "Travel")
            ),
            pointTagIdsByPointId = mapOf(10L to listOf(100L, 200L), 20L to listOf(200L)),
            settingsJsonProvider = { """{"schema_version":1,"timeout_seconds":30}""" }
        )

        val imported = ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { entryName, photoInput ->
            val target = File(tempDir, entryName.substringAfterLast('/'))
            target.writeBytes(photoInput.readBytes())
            "stored/${target.name}"
        }

        assertEquals(2, imported.points.size)
        assertEquals(2, imported.tags.size)
        assertEquals(3, imported.pointTags.size)
        assertEquals(2, imported.importedPhotoCount)
        assertEquals(0, imported.missingPhotoCount)
        assertEquals("""{"schema_version":1,"timeout_seconds":30}""", imported.settingsJson)
        assertEquals("gps", imported.points.first().locationProvider)
        assertEquals(40.4f, imported.points.first().noiseDb ?: 0f, 0.001f)
        assertEquals("stored/first.jpg", imported.points.first().photoPath)
        assertEquals("stored/second.jpg", imported.points[1].photoPath)
    }

    @Test
    fun `zip photo paths stay safe across duplicates traversal backslashes and blanks`() {
        val tempDir = createTempDirectory("zip-photo-safety-test").toFile()
        val duplicateA = File(tempDir, "photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val duplicateDir = File(tempDir, "nested").apply { mkdirs() }
        val duplicateB = File(duplicateDir, "photo.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val weirdName = File(tempDir, "..\\unsafe name.jpg").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val output = ByteArrayOutputStream()

        ZipExporter.export(
            points = listOf(
                Point(timestamp = 1L, latitude = 1.0, longitude = 1.0, title = "A", note = "A", photoPath = duplicateA.absolutePath),
                Point(timestamp = 2L, latitude = 2.0, longitude = 2.0, title = "B", note = "B", photoPath = duplicateB.absolutePath),
                Point(timestamp = 3L, latitude = 3.0, longitude = 3.0, title = "C", note = "C", photoPath = weirdName.absolutePath),
                Point(timestamp = 4L, latitude = 4.0, longitude = 4.0, title = "D", note = "D", photoPath = ""),
                Point(timestamp = 5L, latitude = 5.0, longitude = 5.0, title = "E", note = "E", photoPath = File(tempDir, "missing.jpg").absolutePath)
            ),
            outputStream = output,
            resolvePhotoFile = { path -> File(path) },
            options = ZipExporter.ExportOptions(includePoints = true, includeTags = false, includeSensors = false, includePhotos = true)
        )

        val entryNames = mutableListOf<String>()
        var csvContent = ""
        java.util.zip.ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryNames.add(entry.name)
                if (entry.name == "points.csv") {
                    csvContent = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }

        assertTrue(entryNames.contains("photos/photo.jpg"))
        assertTrue(entryNames.contains("photos/photo_1.jpg"))
        assertTrue(entryNames.any { it.startsWith("photos/_") && it.endsWith("unsafe name.jpg") })
        assertFalse(entryNames.any { it.contains("..") || it.contains('\\') })
        assertTrue(csvContent.contains("photos/photo.jpg"))
        assertTrue(csvContent.contains("photos/photo_1.jpg"))

        val imported = ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { entryName, photoInput ->
            photoInput.readBytes()
            "saved/$entryName"
        }
        assertEquals(5, imported.points.size)
        assertEquals(0, imported.missingPhotoCount)
        assertNull(imported.points[3].photoPath)
        assertNull(imported.points[4].photoPath)
    }

    @Test
    fun `zip import normalizes backslashes and rejects parent traversal photo entries`() {
        val zipBytes = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zipBytes).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("points.csv"))
            val csv = CsvExporter.buildCsv(
                listOf(
                    Point(timestamp = 1710000000000L, latitude = 1.0, longitude = 2.0, title = "P1", note = "N1"),
                    Point(timestamp = 1710000001000L, latitude = 3.0, longitude = 4.0, title = "P2", note = "N2")
                )
            ) { point -> if (point.title == "P1") "photos\\safe.jpg" else "photos/../blocked.jpg" }
            zip.write(csv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("photos\\safe.jpg"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("photos/../blocked.jpg"))
            zip.write(byteArrayOf(4, 5, 6))
            zip.closeEntry()
        }

        val imported = ZipImporter.importZip(ByteArrayInputStream(zipBytes.toByteArray())) { entryName, photoInput ->
            photoInput.readBytes()
            "stored/$entryName"
        }

        assertEquals(2, imported.points.size)
        assertEquals("stored/photos/safe.jpg", imported.points[0].photoPath)
        assertNull(imported.points[1].photoPath)
        assertEquals(1, imported.importedPhotoCount)
        assertEquals(1, imported.missingPhotoCount)
    }
}
