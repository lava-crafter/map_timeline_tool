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

        ZipExporter.export(listOf(point), output) { path -> File(path) }

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
}
