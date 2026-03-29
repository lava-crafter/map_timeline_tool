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
        
        val newDomain = entity.toDomain()
        assertEquals("Test Name", newDomain.title)
        assertEquals("Test Note", newDomain.note)
    }
}
