package com.lavacrafter.maptimelinetool.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeoJsonExporterTest {
    @Test
    fun `missing timestamp is rejected instead of using current time`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoJsonExporter.parsePointsFromGeoJson(feature("\"latitude\":0,\"longitude\":0"))
        }
    }

    @Test
    fun `datetime must be a complete non lenient value`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoJsonExporter.parsePointsFromGeoJson(
                feature("\"time_utc\":\"2024-02-30T00:00:00Z\",\"latitude\":0,\"longitude\":0")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeoJsonExporter.parsePointsFromGeoJson(
                feature("\"time_utc\":\"2024-01-01T00:00:00Z trailing\",\"latitude\":0,\"longitude\":0")
            )
        }
    }

    @Test
    fun `legacy numeric timestamp and valid coordinates are preserved`() {
        val points = GeoJsonExporter.parsePointsFromGeoJson(
            feature("\"timestamp_ms\":1710000000000,\"latitude\":90,\"longitude\":-180")
        )

        assertEquals(1, points.size)
        assertEquals(1710000000000L, points.single().timestamp)
        assertEquals(90.0, points.single().latitude, 0.0)
        assertEquals(-180.0, points.single().longitude, 0.0)
    }

    @Test
    fun `non finite and out of bounds coordinates are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoJsonExporter.parsePointsFromGeoJson(feature("\"timestamp_ms\":1,\"latitude\":91,\"longitude\":0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeoJsonExporter.parsePointsFromGeoJson(feature("\"timestamp_ms\":1,\"latitude\":0,\"longitude\":181"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeoJsonExporter.parsePointsFromGeoJson(feature("\"timestamp_ms\":1,\"latitude\":\"NaN\",\"longitude\":0"))
        }
    }

    private fun feature(properties: String): String =
        "{\"type\":\"FeatureCollection\",\"features\":[{" +
            "\"type\":\"Feature\",\"properties\":{" + properties + "}}]}"
}
