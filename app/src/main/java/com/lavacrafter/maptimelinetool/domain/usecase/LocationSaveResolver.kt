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

import com.lavacrafter.maptimelinetool.domain.port.LocationProvider

class LocationSaveResolver(
    private val locationProvider: LocationProvider,
    private val policy: LocationSavePolicy = LocationSavePolicy()
) {
    suspend fun resolve(
        flow: LocationSaveFlow,
        timeoutMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): LocationSaveDecision {
        val preciseTimeoutMs = preciseTimeout(timeoutMs)
        val fallbackTimeoutMs = (timeoutMs - preciseTimeoutMs).coerceAtLeast(1L)

        val preciseLocation = runCatching {
            locationProvider.getPreciseLocation(preciseTimeoutMs)
        }.getOrNull()
        if (preciseLocation != null) {
            return policy.evaluate(preciseLocation, null, flow, nowMs)
        }

        val fallbackLocation = runCatching {
            locationProvider.getBestEffortLocation(fallbackTimeoutMs)
        }.getOrNull()
        return policy.evaluate(null, fallbackLocation, flow, nowMs)
    }

    private fun preciseTimeout(timeoutMs: Long): Long {
        if (timeoutMs <= 1L) {
            return 1L
        }
        if (timeoutMs < 2_000L) {
            return (timeoutMs / 2L).coerceAtLeast(1L)
        }
        return ((timeoutMs * 3L) / 5L).coerceAtLeast(1L)
    }
}
