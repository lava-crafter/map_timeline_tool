package com.lavacrafter.maptimelinetool.ui

import com.lavacrafter.maptimelinetool.data.PointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SensorReadableTest {
    @Test
    fun `pressure converts to near sea level altitude`() {
        val altitude = pressureToAltitudeMeters(1013.25f)
        assertEquals(0f, altitude, 0.01f)
    }

    @Test
    fun `ambient light is grouped into three levels`() {
        assertEquals(AmbientLightLevel.LOW, toAmbientLightLevel(20f))
        assertEquals(AmbientLightLevel.MEDIUM, toAmbientLightLevel(300f))
        assertEquals(AmbientLightLevel.HIGH, toAmbientLightLevel(3000f))
    }

    @Test
    fun `magnetometer heading is normalized to 0 to 360`() {
        assertEquals(0f, magnetometerToAzimuthDegrees(1f, 0f), 0.001f)
        assertEquals(90f, magnetometerToAzimuthDegrees(0f, 1f), 0.001f)
        assertEquals(270f, magnetometerToAzimuthDegrees(0f, -1f), 0.001f)
    }

    @Test
    fun `gyroscope pitch conversion integrates over snapshot window`() {
        assertEquals(0f, gyroscopeRateToPitchDegrees(0f), 0.001f)
        assertEquals(28.6f, gyroscopeRateToPitchDegrees(1f), 0.2f)
    }

    @Test
    fun `look direction falls back to zero pitch when gyroscope missing`() {
        val point = PointEntity(
            timestamp = 1L,
            latitude = 39.9,
            longitude = 116.3,
            title = "p",
            note = "n",
            magnetometerX = 0f,
            magnetometerY = 1f
        )

        val direction = point.toLookDirection()

        assertNotNull(direction)
        assertEquals(90f, direction!!.azimuthDegrees, 0.001f)
        assertEquals(0f, direction.pitchDegrees, 0.001f)
    }

    @Test
    fun `look direction is unavailable without magnetometer`() {
        val point = PointEntity(
            timestamp = 1L,
            latitude = 39.9,
            longitude = 116.3,
            title = "p",
            note = "n",
            gyroscopeX = 0.3f
        )

        assertNull(point.toLookDirection())
    }

    @Test
    fun `view sector starts from origin and has fan points`() {
        val sector = buildViewSector(
            latitude = 39.9,
            longitude = 116.3,
            azimuthDegrees = 45f,
            pitchDegrees = 10f
        )

        assertEquals(4, sector.size)
        assertEquals(39.9, sector[0].first, 0.0)
        assertEquals(116.3, sector[0].second, 0.0)
    }
}
