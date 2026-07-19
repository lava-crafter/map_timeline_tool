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

package com.lavacrafter.maptimelinetool.quickadd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAddLocationCacheTest {
    @Test
    fun update_storesQualifiedLocation() {
        val cache = QuickAddLocationCache(
            wallClockMs = { 100_000L },
            elapsedRealtimeNanos = { 200_000_000_000L }
        )
        val location = quickAddLocation(
            fixTimeMs = 95_000L,
            elapsedRealtimeNanos = 195_000_000_000L
        )

        assertTrue(cache.update(location))
        assertEquals(location, cache.getQualifiedLocation())
    }

    @Test
    fun getQualifiedLocation_expiresWhenTooOld() {
        var nowMs = 100_000L
        val cache = QuickAddLocationCache(wallClockMs = { nowMs })
        val location = quickAddLocation(fixTimeMs = 90_000L)

        assertTrue(cache.update(location))
        nowMs = 120_001L

        assertNull(cache.getQualifiedLocation())
    }

    @Test
    fun update_rejectsInvalidInputs() {
        val cache = QuickAddLocationCache(wallClockMs = { 100_000L })

        assertFalse(cache.update(quickAddLocation(accuracyMeters = 80f)))
        assertFalse(cache.update(quickAddLocation(provider = "   ")))
        assertFalse(cache.update(quickAddLocation(isMock = true)))
        assertFalse(cache.update(quickAddLocation(latitude = 100.0)))
        assertNull(cache.getQualifiedLocation())
    }

    @Test
    fun update_doesNotEvictQualifiedEntryWhenCandidateIsRejected() {
        val cache = QuickAddLocationCache(wallClockMs = { 100_000L })
        val qualified = quickAddLocation(accuracyMeters = 20f, fixTimeMs = 95_000L)

        assertTrue(cache.update(qualified))
        assertFalse(cache.update(quickAddLocation(accuracyMeters = 75f, fixTimeMs = 99_000L)))
        assertEquals(qualified, cache.getQualifiedLocation())
    }

    @Test
    fun qualification_prefersElapsedRealtimeAgeWhenAvailable() {
        val cache = QuickAddLocationCache(
            wallClockMs = { 1_000_000L },
            elapsedRealtimeNanos = { 500_000_000_000L }
        )
        val location = quickAddLocation(
            fixTimeMs = 900_000L,
            elapsedRealtimeNanos = 490_000_000_000L
        )

        assertTrue(cache.update(location))
        assertEquals(location, cache.getQualifiedLocation())
    }

    private fun quickAddLocation(
        latitude: Double = 1.0,
        longitude: Double = 2.0,
        accuracyMeters: Float = 20f,
        fixTimeMs: Long = 95_000L,
        provider: String = "gps",
        elapsedRealtimeNanos: Long? = null,
        isMock: Boolean = false
    ): QuickAddLocation {
        return QuickAddLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            fixTimeMs = fixTimeMs,
            provider = provider,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            isMock = isMock
        )
    }
}
