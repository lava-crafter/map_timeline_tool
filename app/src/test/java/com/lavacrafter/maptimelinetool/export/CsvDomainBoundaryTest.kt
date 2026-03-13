package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvDomainBoundaryTest {
    @Test
    fun `export and import work with domain point model`() {
        val points = listOf(
            Point(
                timestamp = 1710000000000L,
                latitude = 10.1,
                longitude = 20.2,
                title = "A",
                note = "B"
            )
        )

        val csv = CsvExporter.buildCsv(points)
        val imported = CsvImporter.parseCsv(csv)

        assertEquals(1, imported.size)
        assertEquals(points.first().title, imported.first().title)
        assertEquals(points.first().latitude, imported.first().latitude, 0.0)
    }
}
