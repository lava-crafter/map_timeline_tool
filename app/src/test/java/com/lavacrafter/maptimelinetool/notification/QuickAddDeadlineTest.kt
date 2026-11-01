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

package com.lavacrafter.maptimelinetool.notification

import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider
import com.lavacrafter.maptimelinetool.quickadd.QuickAddLocation
import com.lavacrafter.maptimelinetool.quickadd.QuickAddLocationCache
import com.lavacrafter.maptimelinetool.quickadd.QuickAddResolver
import com.lavacrafter.maptimelinetool.quickadd.QuickAddResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickAddDeadlineTest {
    @Test
    fun timeoutBeforeCommitDoesNotReportSuccess() = runBlocking {
        val result = runQuickAddWithinDeadline(20L, { null as String? }) {
            delay(500L)
            "saved"
        }
        assertNull(result)
    }

    @Test
    fun expiredReceiverDeadlineNeverStartsPointWrite() = runBlocking {
        var started = false
        val result = runQuickAddWithinDeadline(0L, { null as String? }) {
            started = true
            "saved"
        }
        assertNull(result)
        assertEquals(false, started)
    }

    @Test
    fun timeoutAfterCoreCommitReportsSuccess() = runBlocking {
        var committed: String? = null
        val result = runQuickAddWithinDeadline(50L, { committed }) {
            committed = "saved"
            delay(500L) // Optional noise work is allowed to time out.
            "saved"
        }
        assertEquals("saved", result)
    }

    @Test
    fun timeoutInLocationStageDoesNotSaveEvenFromQualifiedCache() = runBlocking {
        val cache = QuickAddLocationCache(wallClockMs = { 100_000L }).apply {
            update(QuickAddLocation(1.0, 2.0, 5f, 99_000L, "gps"))
        }
        var writes = 0
        val resolver = QuickAddResolver(
            locationProvider = object : LocationProvider {
                override fun getLastKnownLocation(): GeoPoint? = null
                override suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint? {
                    delay(500L)
                    return null
                }
                override suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? = null
                override suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? = null
            },
            locationCache = cache,
            addPoint = { _, _, _, _ -> writes++ },
            wallClockMs = { 100_000L }
        )
        var committed: QuickAddResult? = null
        val result = runQuickAddWithinDeadline(20L, { committed }) {
            resolver.savePoint(5_000L, onCoreSaved = { committed = it })
        }

        assertNull(result)
        assertEquals(0, writes)
    }
}
