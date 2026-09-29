package com.lavacrafter.maptimelinetool.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class ZipRestorePreflightTest {
    private val csv = "name,description,latitude,longitude,time_utc\nP,,10,20,2024-01-01T00:00:00Z\n"
    private val manifest = """{"backup_version":2,"sections":{"points":true,"tags":true,"photos":false,"settings":false}}"""

    @Test fun rejectsTooManyEmptyEntries() {
        val entries = (0..10).map { "unknown_$it" to "" }
        assertThrows(ZipImportLimitExceededException::class.java) {
            restore(zip(entries), ZipImportLimits(maxEntries = 10))
        }
    }

    @Test fun rejectsTooManyPhotoEntries() {
        val entries = (0..3).map { "photos/$it.jpg" to "bytes" }
        assertThrows(ZipImportLimitExceededException::class.java) {
            restore(zip(entries), ZipImportLimits(maxPhotos = 3))
        }
    }

    @Test fun rejectsTooManyPointRows() {
        val entry = csv + "Q,,10,20,2024-01-01T00:00:01Z\n"
        assertThrows(ZipImportLimitExceededException::class.java) {
            restore(zip(listOf("points.csv" to entry)), ZipImportLimits(maxPoints = 1))
        }
    }

    @Test fun rejectsBrokenRelations() {
        assertThrows(IllegalArgumentException::class.java) {
            restore(zip(listOf("backup_manifest.json" to manifest, "points.csv" to csv,
                "tags.csv" to "tag_id,name\n1,Work\n",
                "point_tags.csv" to "point_index,tag_id\n0,2\n")))
        }
    }

    @Test fun rejectsMalformedCanonicalPointField() {
        assertThrows(IllegalArgumentException::class.java) {
            restore(zip(listOf("points.csv" to csv.replace("10,20,", "10,20,")
                .replace("2024-01-01T00:00:00Z", "bad time"))))
        }
    }

    @Test fun rejectsTraversalAndOversizedLegacyGeoJson() {
        assertThrows(IllegalArgumentException::class.java) {
            restore(zip(listOf("../points.csv" to csv)))
        }
        assertThrows(ZipImportLimitExceededException::class.java) {
            restore(zip(listOf("points.geojson" to "a".repeat(100))), ZipImportLimits(maxGeoJsonBytes = 64))
        }
    }

    private fun restore(bytes: ByteArray, limits: ZipImportLimits = ZipImportLimits()) =
        ZipImporter.importZip(ByteArrayInputStream(bytes), limits) { _, input ->
            input.readBytes()
            "saved.jpg"
        }

    private fun zip(entries: List<Pair<String, String>>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }
}
