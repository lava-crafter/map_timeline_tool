package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.domain.model.Point
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
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
        assertEquals("stored/photos/p.jpg", imported.points.first().photoPath)
        assertEquals(1, imported.importedPhotoCount)
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
            val tagsCsv = "\"tag_id\",\"name\"\n\"10\",\"Work\"\n"
            zip.write(tagsCsv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("point_tags.csv"))
            val pointTagsCsv = "\"point_index\",\"tag_id\"\n\"0\",\"10\"\n"
            zip.write(pointTagsCsv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val imported = ZipImporter.importZip(ByteArrayInputStream(actualZip.toByteArray())) { _, _ -> null }
        assertEquals(1, imported.tags.size)
        assertEquals("Work", imported.tags.first().name)
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
}
