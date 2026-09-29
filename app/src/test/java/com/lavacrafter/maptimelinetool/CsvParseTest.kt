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
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.StringReader

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

    @Test
    fun forEachPoint_streamsRecordsAndRejectsOversizedFields() {
        val titles = mutableListOf<String>()
        CsvImporter.forEachPoint(
            StringReader("name,latitude,longitude,time_utc\nOne,1,2,1000\nTwo,3,4,2000\n")
        ) { point ->
            titles += point.title
        }
        assertEquals(listOf("One", "Two"), titles)

        try {
            CsvImporter.forEachPoint(
                StringReader("name,latitude,longitude,time_utc\n${"a".repeat(20)},1,2,1000\n"),
                limits = CsvImporter.Limits(maxFieldChars = 10)
            ) { }
            throw AssertionError("Expected CSV field budget rejection")
        } catch (_: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test
    fun ordinaryCsvReportsInvalidRowsAndEnforcesTotalRecordBudget() {
        val csv = "name,description,latitude,longitude,time_utc\n" +
            "Good,,1,2,1000\n" +
            "Bad coordinate,,NaN,2,1000\n" +
            "Bad time,,1,2,not-a-date\n" +
            "Missing time,,1,2\n"
        val parsed = CsvImporter.parseOrdinaryCsv(csv.reader())

        assertEquals(listOf("Good"), parsed.points.map { it.title })
        assertEquals(3, parsed.skipped)
        assertEquals(listOf(2, 3, 4), parsed.warnings.map { it.row })
        assertEquals(
            listOf(CsvImporter.SkipReason.INVALID_COORDINATES, CsvImporter.SkipReason.INVALID_TIMESTAMP,
                CsvImporter.SkipReason.INVALID_TIMESTAMP), parsed.warnings.map { it.reason }
        )
        assertThrows(IllegalArgumentException::class.java) {
            CsvImporter.parseOrdinaryCsv(csv.reader(), maxRecords = 3)
        }
    }

    @Test
    fun generatedOrdinaryCsvWithMoreThanOneThousandRowsParsesCompletely() {
        val csv = buildString {
            append("name,description,latitude,longitude,time_utc\n")
            repeat(1_201) { index -> append("Point $index,,1,2,1000\n") }
        }
        val parsed = CsvImporter.parseOrdinaryCsv(csv.reader())

        assertEquals(1_201, parsed.points.size)
        assertEquals(0, parsed.skipped)
    }

    @Test
    fun ordinaryCsvFormulaSafeTextRoundTripsExactlyWithoutChangingNegativeCoordinates() {
        val titles = listOf("=1+1", "+SUM(1,2)", "-1+2", "@SUM(1,2)", "  =1+1", "MTTCSV1:abcd", "Safe")
        val points = titles.mapIndexed { index, title ->
            Point(timestamp = 1_700_000_000_000L + index, latitude = -12.5, longitude = -3.25,
                title = title, note = if (index == 6) "MTTCSV1:abc" else "=HYPERLINK(\"bad\")",
                locationProvider = "@provider")
        }
        val exported = CsvExporter.buildCsv(points)
        val imported = CsvImporter.parseOrdinaryCsv(exported.reader())

        assertTrue(exported.contains("\"-12.5\",\"-3.25\""))
        assertTrue(!exported.contains("\"=1+1\""))
        assertTrue(!exported.contains("\"+SUM(1,2)\""))
        assertTrue(!exported.contains("\"-1+2\""))
        assertTrue(!exported.contains("\"@SUM(1,2)\""))
        assertEquals(0, imported.skipped)
        // The parser applies the app's existing title normalization on import.
        assertEquals(titles.map(String::trim), imported.points.map { it.title })
        assertEquals(points.map { it.note }, imported.points.map { it.note })
        assertEquals(points.map { it.locationProvider }, imported.points.map { it.locationProvider })
        assertEquals(points.map { it.latitude }, imported.points.map { it.latitude })
    }

    @Test
    fun malformedEncodedTextIsSkippedButUnmarkedExternalCsvIsNotDecoded() {
        val marked = "name,description,latitude,longitude,time_utc,mtt_text_encoding\n" +
            "MTTCSV1:qq,,1,2,1000,hex-v1\n"
        val parsed = CsvImporter.parseOrdinaryCsv(marked.reader())
        assertEquals(1, parsed.skipped)
        assertEquals(CsvImporter.SkipReason.INVALID_TEXT_ENCODING, parsed.warnings.single().reason)

        val external = "name,description,latitude,longitude,time_utc\nMTTCSV1:qq,,1,2,1000\n"
        assertEquals("MTTCSV1:qq", CsvImporter.parseOrdinaryCsv(external.reader()).points.single().title)
    }
}
