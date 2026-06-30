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

import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickAddResolverTest {
    @Test
    fun savePoint_prefersFreshPreciseLocationOverQualifiedCacheAndUsesClickTimestamp() = runBlocking {
        val cachedLocation = QuickAddLocation(
            latitude = 1.0,
            longitude = 2.0,
            accuracyMeters = 12f,
            fixTimeMs = 90_000L,
            provider = "gps"
        )
        val cache = QuickAddLocationCache(wallClockMs = { 100_000L }).apply { update(cachedLocation) }
        val provider = FakeLocationProvider(
            preciseLocation = GeoPoint(
                latitude = 3.0,
                longitude = 4.0,
                accuracyMeters = 10f,
                fixTimeMs = 99_000L,
                provider = "fused"
            )
        )
        var savedTitle: String? = null
        var savedLocation: GeoPoint? = null
        var savedTimestamp: Long? = null

        val result = QuickAddResolver(
            locationProvider = provider,
            locationCache = cache,
            addPoint = { title, location, timestamp ->
                savedTitle = title
                savedLocation = location
                savedTimestamp = timestamp
            },
            wallClockMs = { 100_000L },
            titleFormatter = { "title-$it" }
        ).savePoint(timeoutMs = 5_000L, clickTimeMs = 123_456L)

        assertEquals(QuickAddResult.SAVED_FROM_FRESH_REQUEST, result)
        assertEquals(1, provider.preciseCalls)
        assertEquals("title-123456", savedTitle)
        assertEquals(provider.preciseLocation, savedLocation)
        assertEquals(123_456L, savedTimestamp)
        assertEquals(provider.preciseLocation?.accuracyMeters, cache.getQualifiedLocation()?.accuracyMeters)
    }

    @Test
    fun savePoint_requestsOnePreciseFixWithoutBestEffortFallback() = runBlocking {
        val provider = FakeLocationProvider(
            preciseLocation = GeoPoint(
                latitude = 1.0,
                longitude = 2.0,
                accuracyMeters = 15f,
                fixTimeMs = 99_500L,
                provider = "fused"
            )
        )
        val cache = QuickAddLocationCache(wallClockMs = { 100_000L })
        var savedLocation: GeoPoint? = null

        val result = QuickAddResolver(
            locationProvider = provider,
            locationCache = cache,
            addPoint = { _, location, _ ->
                savedLocation = location
            },
            wallClockMs = { 100_000L }
        ).savePoint(timeoutMs = 5_000L, clickTimeMs = 101_000L)

        assertEquals(QuickAddResult.SAVED_FROM_FRESH_REQUEST, result)
        assertEquals(1, provider.preciseCalls)
        assertEquals(0, provider.bestEffortCalls)
        assertEquals(provider.preciseLocation, savedLocation)
        assertEquals(15f, cache.getQualifiedLocation()?.accuracyMeters)
    }

    @Test
    fun savePoint_failsWhenPreciseFixIsNotStrictEnough() = runBlocking {
        val provider = FakeLocationProvider(
            preciseLocation = GeoPoint(
                latitude = 1.0,
                longitude = 2.0,
                accuracyMeters = 75f,
                fixTimeMs = 99_000L,
                provider = "gps"
            )
        )
        var saved = false

        val result = QuickAddResolver(
            locationProvider = provider,
            locationCache = QuickAddLocationCache(wallClockMs = { 100_000L }),
            addPoint = { _, _, _ -> saved = true },
            wallClockMs = { 100_000L }
        ).savePoint(timeoutMs = 5_000L, clickTimeMs = 101_000L)

        assertEquals(QuickAddResult.FAILED_NO_FRESH_ACCURATE_LOCATION, result)
        assertEquals(1, provider.preciseCalls)
        assertEquals(0, provider.bestEffortCalls)
        assertEquals(false, saved)
    }

    @Test
    fun savePoint_fallsBackToQualifiedCacheWhenNoStrictLocationCanBeResolved() = runBlocking {
        val provider = FakeLocationProvider(preciseLocation = null)
        var savedTitle: String? = null
        var savedLocation: GeoPoint? = null
        val cachedLocation = QuickAddLocation(
            latitude = 8.0,
            longitude = 9.0,
            accuracyMeters = 18f,
            fixTimeMs = 95_000L,
            provider = "gps"
        )
        val cache = QuickAddLocationCache(wallClockMs = { 100_000L }).apply { update(cachedLocation) }

        val result = QuickAddResolver(
            locationProvider = provider,
            locationCache = cache,
            addPoint = { title, location, _ ->
                savedTitle = title
                savedLocation = location
            },
            wallClockMs = { 100_000L },
            titleFormatter = { "title-$it" }
        ).savePoint(timeoutMs = 5_000L, clickTimeMs = 101_000L)

        assertEquals(QuickAddResult.SAVED_FROM_RECENT_CACHE, result)
        assertEquals("title-101000", savedTitle)
        assertEquals(cachedLocation.toGeoPoint(), savedLocation)
        assertEquals(1, provider.preciseCalls)
    }

    private class FakeLocationProvider(
        val preciseLocation: GeoPoint? = null
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
            return null
        }
    }
}
