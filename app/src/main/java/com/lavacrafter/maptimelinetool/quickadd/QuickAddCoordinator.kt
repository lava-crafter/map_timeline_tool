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
import com.lavacrafter.maptimelinetool.domain.model.PointCaptureMode
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider
import com.lavacrafter.maptimelinetool.domain.usecase.PointWriteUseCase

enum class QuickAddLocationAvailability {
    AVAILABLE,
    SERVICES_DISABLED,
    NO_PROVIDER
}

class QuickAddCoordinator(
    private val locationProvider: LocationProvider,
    private val pointWriteUseCase: PointWriteUseCase,
    private val stateStore: QuickAddExecutionState,
    private val getDefaultTagIds: () -> Set<Long>,
    private val getExistingTagIds: suspend () -> Set<Long>,
    private val locationAvailability: () -> QuickAddLocationAvailability,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val locationTimeoutMs: Long = LOCATION_TIMEOUT_MS
) {
    suspend fun execute(): QuickAddOutcome {
        if (!stateStore.tryStart()) return QuickAddOutcome.AlreadyRunning

        val outcome = try {
            when (locationAvailability()) {
                QuickAddLocationAvailability.SERVICES_DISABLED -> QuickAddOutcome.LocationServicesDisabled
                QuickAddLocationAvailability.NO_PROVIDER -> QuickAddOutcome.NoLocationProvider
                QuickAddLocationAvailability.AVAILABLE -> captureAndSave()
            }
        } catch (_: Exception) {
            QuickAddOutcome.UnexpectedFailure
        }
        stateStore.record(outcome)
        return outcome
    }

    private suspend fun captureAndSave(): QuickAddOutcome {
        val clickTimeMs = wallClockMs()
        val location = try {
            locationProvider.getPreciseLocation(locationTimeoutMs)
        } catch (_: Exception) {
            return QuickAddOutcome.UnexpectedFailure
        } ?: return QuickAddOutcome.LocationTimeout

        validateLocation(location, wallClockMs())?.let { return it }

        val tagIds = try {
            getDefaultTagIds().intersect(getExistingTagIds())
        } catch (_: Exception) {
            return QuickAddOutcome.StorageFailure
        }

        return try {
            val pointId = pointWriteUseCase.addPointWithTags(
                title = "",
                note = "",
                location = location,
                timestamp = clickTimeMs,
                tagIds = tagIds,
                captureMode = PointCaptureMode.QUICK
            )
            QuickAddOutcome.Success(pointId)
        } catch (_: Exception) {
            QuickAddOutcome.StorageFailure
        }
    }

    private fun validateLocation(location: GeoPoint, nowMs: Long): QuickAddOutcome? {
        if (!location.latitude.isFinite() || !location.longitude.isFinite() ||
            location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0
        ) {
            return QuickAddOutcome.UnexpectedFailure
        }
        val accuracy = location.accuracyMeters
        if (accuracy == null || !accuracy.isFinite() || accuracy <= 0f || accuracy > MAX_ACCURACY_METERS) {
            return QuickAddOutcome.LocationTooInaccurate
        }
        val fixTimeMs = location.fixTimeMs ?: return QuickAddOutcome.LocationTimeout
        val ageMs = nowMs - fixTimeMs
        if (fixTimeMs <= 0L || ageMs < 0L || ageMs > MAX_LOCATION_AGE_MS) {
            return QuickAddOutcome.LocationTimeout
        }
        if (location.provider.isNullOrBlank()) {
            return QuickAddOutcome.UnexpectedFailure
        }
        return null
    }

    companion object {
        const val LOCATION_TIMEOUT_MS = 10_000L
        const val MAX_LOCATION_AGE_MS = 15_000L
        const val MAX_ACCURACY_METERS = 50f
    }
}
