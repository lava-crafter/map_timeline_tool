package com.lavacrafter.maptimelinetool

import com.lavacrafter.maptimelinetool.export.CsvImporter
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
}
