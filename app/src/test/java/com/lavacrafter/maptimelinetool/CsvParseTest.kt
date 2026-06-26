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

import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.export.CsvExporter
import com.lavacrafter.maptimelinetool.export.CsvImporter
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertEquals

class CsvParseTest {
    @Test
    fun testParse() {
        val csv = """
            name,description,latitude,longitude,time_utc
            MyName,MyNote,1.0,2.0,2024-01-01T00:00:00Z
        """.trimIndent()
        val points = CsvImporter.parseCsv(csv)
        val p = points.first()
        assertEquals("MyName", p.title)
        assertEquals("MyNote", p.note)
    }

    @Test
    fun parseCsv_handlesMultilineQuotesCommasEmptyFieldsAndMetadata() {
        val csv = CsvExporter.buildCsv(
            listOf(
                Point(
                    timestamp = 1704070923000L,
                    latitude = 1.5,
                    longitude = 2.5,
                    locationAccuracyMeters = 4.5f,
                    locationFixTimeMs = 1704070923000L,
                    locationProvider = "gps",
                    title = "Quoted, Title\"",
                    note = "Line1\nLine2, with comma",
                    pressureHpa = 1001.25f,
                    ambientLightLux = 200.5f,
                    accelerometerX = 1.0f,
                    accelerometerY = 2.0f,
                    accelerometerZ = 3.0f,
                    gyroscopeX = 4.0f,
                    gyroscopeY = 5.0f,
                    gyroscopeZ = 6.0f,
                    magnetometerX = 7.0f,
                    magnetometerY = 8.0f,
                    magnetometerZ = 9.0f,
                    noiseDb = 55.5f
                ),
                Point(
                    timestamp = 1704153600000L,
                    latitude = 3.5,
                    longitude = 4.5,
                    title = "Second",
                    note = ""
                )
            )
        ) { point -> if (point.title.startsWith("Quoted,")) "photos/p1.jpg" else null }

        val points = CsvImporter.parseCsv(csv)

        assertEquals(2, points.size)
        assertEquals("Quoted, Title\"", points[0].title)
        assertEquals("Line1\nLine2, with comma", points[0].note)
        assertEquals(4.5f, points[0].locationAccuracyMeters ?: 0f, 0.001f)
        assertEquals(1704070923000L, points[0].locationFixTimeMs)
        assertEquals("gps", points[0].locationProvider)
        assertEquals(1001.25f, points[0].pressureHpa ?: 0f, 0.001f)
        assertEquals(200.5f, points[0].ambientLightLux ?: 0f, 0.001f)
        assertEquals(6.0f, points[0].gyroscopeZ ?: 0f, 0.001f)
        assertEquals(9.0f, points[0].magnetometerZ ?: 0f, 0.001f)
        assertEquals(55.5f, points[0].noiseDb ?: 0f, 0.001f)
        assertEquals("photos/p1.jpg", points[0].photoPath)
        assertEquals("", points[1].note)
        assertNull(points[1].locationProvider)
        assertNull(points[1].photoPath)
    }
}
