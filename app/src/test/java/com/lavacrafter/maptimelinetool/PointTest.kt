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
import com.lavacrafter.maptimelinetool.data.toEntity
import com.lavacrafter.maptimelinetool.data.toDomain
import org.junit.Test
import org.junit.Assert.assertEquals

class PointTest {
    @Test
    fun testDomainEntityMapping() {
        val domain = Point(
            id = 1L,
            timestamp = 1000L,
            latitude = 1.0,
            longitude = 2.0,
            locationAccuracyMeters = 5.5f,
            locationFixTimeMs = 900L,
            locationProvider = "gps",
            title = "Test Name",
            note = "Test Note",
            pressureHpa = null,
            ambientLightLux = null,
            accelerometerX = null,
            accelerometerY = null,
            accelerometerZ = null,
            gyroscopeX = null,
            gyroscopeY = null,
            gyroscopeZ = null,
            magnetometerX = null,
            magnetometerY = null,
            magnetometerZ = null,
            noiseDb = null,
            photoPath = null
        )
        val entity = domain.toEntity()
        assertEquals("Test Name", entity.title)
        assertEquals("Test Note", entity.note)
        assertEquals(5.5f, entity.locationAccuracyMeters)
        assertEquals(900L, entity.locationFixTimeMs)
        assertEquals("gps", entity.locationProvider)
        
        val newDomain = entity.toDomain()
        assertEquals("Test Name", newDomain.title)
        assertEquals("Test Note", newDomain.note)
        assertEquals(5.5f, newDomain.locationAccuracyMeters)
        assertEquals(900L, newDomain.locationFixTimeMs)
        assertEquals("gps", newDomain.locationProvider)
    }
}
