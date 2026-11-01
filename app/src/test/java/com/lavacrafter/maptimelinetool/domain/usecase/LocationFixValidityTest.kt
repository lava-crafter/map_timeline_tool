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

package com.lavacrafter.maptimelinetool.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationFixValidityTest {
    private val nowMs = 1_700_000_000_000L
    private val elapsedNowNanos = 50_000_000_000L

    @Test
    fun `fix from a gnss clock ahead of a slow device clock is re-expressed on the device clock`() {
        // Device clock is 30 s behind satellite UTC; the fix happened 1.5 s ago by the monotonic clock.
        val gnssTime = nowMs + 30_000L - 1_500L
        val fixElapsed = elapsedNowNanos - 1_500_000_000L
        assertNull(LocationFixValidity.ageMs(1.0, 2.0, gnssTime, nowMs))

        val deviceTime = LocationFixValidity.deviceClockFixTimeMs(gnssTime, fixElapsed, nowMs, elapsedNowNanos)

        assertEquals(nowMs - 1_500L, deviceTime)
        assertEquals(1_500L, LocationFixValidity.ageMs(1.0, 2.0, deviceTime, nowMs))
    }

    @Test
    fun `missing or impossible monotonic time keeps the wall clock fix time`() {
        assertEquals(123L, LocationFixValidity.deviceClockFixTimeMs(123L, 0L, nowMs, elapsedNowNanos))
        assertEquals(123L, LocationFixValidity.deviceClockFixTimeMs(123L, elapsedNowNanos + 1, nowMs, elapsedNowNanos))
    }
}
