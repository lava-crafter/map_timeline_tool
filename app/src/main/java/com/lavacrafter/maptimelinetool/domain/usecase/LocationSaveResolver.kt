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
import com.lavacrafter.maptimelinetool.LocationDeadline
import kotlinx.coroutines.CancellationException

class LocationSaveResolver(
    private val locationProvider: LocationProvider,
    private val policy: LocationSavePolicy = LocationSavePolicy()
) {
    suspend fun resolve(
        flow: LocationSaveFlow,
        timeoutMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): LocationSaveDecision {
        val deadline = LocationDeadline.after(timeoutMs)

        val preciseLocation = locationOrNull {
            locationProvider.getPreciseLocation(deadline.preferredBudgetMs())
        }
        if (preciseLocation != null) {
            return policy.evaluate(preciseLocation, null, flow, nowMs)
        }

        val fallbackBudgetMs = deadline.remainingMs()
        val fallbackLocation = if (fallbackBudgetMs > 0L) {
            locationOrNull { locationProvider.getBestEffortLocation(fallbackBudgetMs) }
        } else {
            null
        }
        return policy.evaluate(null, fallbackLocation, flow, nowMs)
    }

    private suspend fun locationOrNull(block: suspend () -> com.lavacrafter.maptimelinetool.domain.model.GeoPoint?): com.lavacrafter.maptimelinetool.domain.model.GeoPoint? {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}
