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

import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationSaveResolverTest {
    @Test
    fun resolve_prefersPreciseLocation() = runBlocking {
        val precise = GeoPoint(1.0, 2.0, accuracyMeters = 5f, fixTimeMs = 10L)
        val provider = FakeLocationProvider(
            preciseLocation = precise,
            bestEffortLocation = GeoPoint(3.0, 4.0, accuracyMeters = 50f, fixTimeMs = 20L)
        )

        val decision = LocationSaveResolver(provider).resolve(LocationSaveFlow.MANUAL_ADD, 5_000L, nowMs = 1_000L)

        assertEquals(LocationSaveQuality.PRECISE_FRESH, decision.quality)
        assertEquals(1, provider.preciseCalls)
        assertEquals(0, provider.bestEffortCalls)
        assertEquals(precise, decision.location)
    }

    @Test
    fun resolve_fallsBackToBestEffortWhenPreciseFails() = runBlocking {
        val fallback = GeoPoint(1.0, 2.0, accuracyMeters = 150f, fixTimeMs = 995_000L)
        val provider = FakeLocationProvider(
            preciseLocation = null,
            bestEffortLocation = fallback
        )

        val decision = LocationSaveResolver(provider).resolve(LocationSaveFlow.MANUAL_ADD, 5_000L, nowMs = 1_000_000L)

        assertEquals(LocationSaveQuality.FRESH_BUT_LOW_ACCURACY, decision.quality)
        assertEquals(1, provider.preciseCalls)
        assertEquals(1, provider.bestEffortCalls)
        assertEquals(fallback, decision.location)
    }

    @Test
    fun resolve_returnsUnavailableWhenNoLocationCanBeResolved() = runBlocking {
        val provider = FakeLocationProvider()

        val decision = LocationSaveResolver(provider).resolve(LocationSaveFlow.AUTO_SAVE, 5_000L, nowMs = 1_000_000L)

        assertEquals(LocationSaveQuality.UNAVAILABLE, decision.quality)
        assertNull(decision.location)
    }

    private class FakeLocationProvider(
        private val preciseLocation: GeoPoint? = null,
        private val bestEffortLocation: GeoPoint? = null
    ) : LocationProvider {
        var preciseCalls: Int = 0
        var bestEffortCalls: Int = 0

        override fun getLastKnownLocation(): GeoPoint? = null

        override suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint? {
            preciseCalls += 1
            return preciseLocation
        }

        override suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? = null

        override suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? {
            bestEffortCalls += 1
            return bestEffortLocation
        }
    }
}
