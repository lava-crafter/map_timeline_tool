package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvDomainBoundaryTest {
    @Test
    fun `export csv includes sensor and photo_rel_path columns`() {
        val points = listOf(
            Point(
                timestamp = 1710000000000L,
                latitude = 10.1,
                longitude = 20.2,
                title = "A",
                note = "B",
                pressureHpa = 1013.2f,
                ambientLightLux = 66.6f,
                accelerometerX = 1.1f,
                accelerometerY = 2.2f,
                accelerometerZ = 3.3f,
                gyroscopeX = 4.4f,
                gyroscopeY = 5.5f,
                gyroscopeZ = 6.6f,
                magnetometerX = 7.7f,
                magnetometerY = 8.8f,
                magnetometerZ = 9.9f,
                noiseDb = 42.4f
            )
        )

        val csv = CsvExporter.buildCsv(points) { "photos/a.jpg" }
        val lines = csv.lineSequence().toList()
        assertTrue(lines.first().contains("pressure_hpa"))
        assertTrue(lines.first().contains("photo_rel_path"))
        assertTrue(lines[1].contains("\"photos/a.jpg\""))
    }

    @Test
    fun `import csv supports legacy 5 columns and extended columns`() {
        val legacyCsv = """
            name,description,latitude,longitude,time_utc
            "Old","No sensor",10.0,20.0,2024-01-01T00:00:00Z
        """.trimIndent()
        val importedLegacy = CsvImporter.parseCsv(legacyCsv)
        assertEquals(1, importedLegacy.size)
        assertNull(importedLegacy.first().pressureHpa)
        assertNull(importedLegacy.first().photoPath)

        val extended = CsvExporter.buildCsv(
            listOf(
                Point(
                    timestamp = 1710000000000L,
                    latitude = 11.1,
                    longitude = 22.2,
                    title = "New",
                    note = "Line1\nLine2",
                    pressureHpa = 1001.5f,
                    accelerometerX = 1.0f
                )
            )
        ) { "photos/new.jpg" }
        val imported = CsvImporter.parseCsv(extended)

        assertEquals(1, imported.size)
        assertEquals("New", imported.first().title)
        assertEquals(1001.5f, imported.first().pressureHpa ?: 0f, 0.001f)
        assertEquals("photos/new.jpg", imported.first().photoPath)
        assertEquals("Line1\nLine2", imported.first().note)
    }

    @Test
    fun `import csv tolerates empty and invalid sensor values`() {
        val csv = """
            name,description,latitude,longitude,time_utc,pressure_hpa,ambient_light_lux,accelerometer_x,photo_rel_path
            "P","Bad",30.0,120.0,2024-01-01T00:00:00Z,abc,,nan,photos/p.jpg
        """.trimIndent()

        val imported = CsvImporter.parseCsv(csv)
        assertEquals(1, imported.size)
        assertNull(imported.first().pressureHpa)
        assertNull(imported.first().ambientLightLux)
        assertNull(imported.first().accelerometerX)
        assertEquals("photos/p.jpg", imported.first().photoPath)
    }
}
