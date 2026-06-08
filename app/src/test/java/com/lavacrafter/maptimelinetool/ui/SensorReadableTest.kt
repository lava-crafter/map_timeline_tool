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
    fun `magnetometer heading is normalized to 0 to 360`() {
        assertEquals(0f, magnetometerToAzimuthDegrees(1f, 0f), 0.001f)
        assertEquals(90f, magnetometerToAzimuthDegrees(0f, 1f), 0.001f)
        assertEquals(270f, magnetometerToAzimuthDegrees(0f, -1f), 0.001f)
    }

    @Test
    fun `accelerometer pitch conversion gives device tilt`() {        
        assertEquals(0f, accelerometerToPitchDegrees(0f, 9.8f), 0.001f) // Flat 
        assertEquals(89f, accelerometerToPitchDegrees(9.8f, 0f), 0.001f) // Upright, clamped to 89
    }

    @Test
    fun `look direction falls back to zero pitch when accelerometer missing`() {
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
