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

import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider

class CompositeLocationProvider(
    private val preferred: LocationProvider,
    private val fallback: LocationProvider
) : LocationProvider {
    override fun getLastKnownLocation(): GeoPoint? =
        preferred.getLastKnownLocation() ?: fallback.getLastKnownLocation()

    override suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint? =
        firstResult(timeoutMs) { provider, budget -> provider.getPreciseLocation(budget) }

    override suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? =
        firstResult(timeoutMs) { provider, budget -> provider.getFreshLocation(budget) }

    override suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? =
        firstResult(timeoutMs) { provider, budget -> provider.getBestEffortLocation(budget) }

    private suspend fun firstResult(
        timeoutMs: Long,
        block: suspend (LocationProvider, Long) -> GeoPoint?
    ): GeoPoint? {
        val preferredTimeoutMs = preferredTimeout(timeoutMs)
        val fallbackTimeoutMs = (timeoutMs - preferredTimeoutMs).coerceAtLeast(1L)
        val preferredResult = runCatching {
            block(preferred, preferredTimeoutMs)
        }.getOrNull()
        if (preferredResult != null) {
            return preferredResult
        }
        return runCatching {
            block(fallback, fallbackTimeoutMs)
        }.getOrNull()
    }

    private fun preferredTimeout(timeoutMs: Long): Long {
        if (timeoutMs <= 1L) {
            return 1L
        }
        if (timeoutMs < 2_000L) {
            return (timeoutMs / 2L).coerceAtLeast(1L)
        }
        return ((timeoutMs * 3L) / 5L).coerceAtLeast(1L)
    }
}
