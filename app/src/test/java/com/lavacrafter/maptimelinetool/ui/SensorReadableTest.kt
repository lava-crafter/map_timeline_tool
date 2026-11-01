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
    fun `heading is clockwise from north for flat and upright devices`() {
        // Flat, face up, top edge north: gravity +Z, field points north (+Y) and down (-Z).
        assertEquals(0f, deviceAzimuthDegrees(0f, 0f, 9.8f, 0f, 20f, -40f)!!, 0.01f)
        // Flat, top edge east: magnetic north is the device's -X.
        assertEquals(90f, deviceAzimuthDegrees(0f, 0f, 9.8f, -20f, 0f, -40f)!!, 0.01f)
        // Flat, top edge west.
        assertEquals(270f, deviceAzimuthDegrees(0f, 0f, 9.8f, 20f, 0f, -40f)!!, 0.01f)
        // Upright portrait, back camera north: gravity +Y, north is -Z, down is -Y.
        assertEquals(0f, deviceAzimuthDegrees(0f, 9.8f, 0f, 0f, -40f, -20f)!!, 0.01f)
        // Upright, back camera east: north is the device's -X.
        assertEquals(90f, deviceAzimuthDegrees(0f, 9.8f, 0f, -20f, -40f, 0f)!!, 0.01f)
        // Tilted 30 degrees towards the user while the top still faces north keeps the heading.
        val g = 9.8f
        val tiltedHeading = deviceAzimuthDegrees(0f, g * 0.5f, g * 0.866f, 0f, 20f * 0.866f + 40f * 0.5f, -40f * 0.866f + 20f * 0.5f)!!
        assertEquals(0f, if (tiltedHeading > 180f) tiltedHeading - 360f else tiltedHeading, 0.5f)
    }

    @Test
    fun `degenerate vectors give no heading`() {
        assertNull(deviceAzimuthDegrees(0f, 0f, 0f, 0f, 20f, -40f))
        assertNull(deviceAzimuthDegrees(0f, 0f, 9.8f, 0f, 0f, -40f))
    }

    @Test
    fun `accelerometer pitch conversion gives device tilt`() {        
        assertEquals(0f, accelerometerToPitchDegrees(0f, 9.8f), 0.001f) // Flat 
        assertEquals(89f, accelerometerToPitchDegrees(9.8f, 0f), 0.001f) // Upright, clamped to 89
    }

    @Test
    fun `look direction needs both accelerometer and magnetometer`() {
        val magnetometerOnly = PointEntity(
            timestamp = 1L,
            latitude = 39.9,
            longitude = 116.3,
            title = "p",
            note = "n",
            magnetometerX = 0f,
            magnetometerY = 1f,
            magnetometerZ = 0f
        )
        assertNull(magnetometerOnly.toLookDirection())

        val flatFacingEast = magnetometerOnly.copy(
            accelerometerX = 0f, accelerometerY = 0f, accelerometerZ = 9.8f,
            magnetometerX = -20f, magnetometerY = 0f, magnetometerZ = -40f
        )
        val direction = flatFacingEast.toLookDirection()
        assertNotNull(direction)
        assertEquals(90f, direction!!.azimuthDegrees, 0.01f)
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
