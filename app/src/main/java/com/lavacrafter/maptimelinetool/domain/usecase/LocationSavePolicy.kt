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

enum class LocationSaveQuality {
    PRECISE_FRESH,
    FRESH_BUT_LOW_ACCURACY,
    LAST_KNOWN_RECENT,
    LAST_KNOWN_STALE,
    UNAVAILABLE
}

enum class LocationSaveFlow {
    MANUAL_ADD,
    AUTO_SAVE,
    QUICK_ADD
}

data class LocationSaveDecision(
    val quality: LocationSaveQuality,
    val location: GeoPoint?,
    val canSave: Boolean,
    val requiresManualConfirmation: Boolean
)

class LocationSavePolicy(
    private val freshAgeMs: Long = 15_000L,
    private val recentLastKnownAgeMs: Long = 3_600_000L,
    private val staleLastKnownAgeMs: Long = 24 * 60 * 60 * 1000L
) {
    fun evaluate(
        preciseLocation: GeoPoint?,
        fallbackLocation: GeoPoint?,
        flow: LocationSaveFlow,
        nowMs: Long = System.currentTimeMillis()
    ): LocationSaveDecision {
        val quality = when {
            preciseLocation != null -> LocationSaveQuality.PRECISE_FRESH
            fallbackLocation == null -> LocationSaveQuality.UNAVAILABLE
            else -> classifyFallback(fallbackLocation, nowMs)
        }
        val location = preciseLocation ?: fallbackLocation
        return LocationSaveDecision(
            quality = quality,
            location = location.takeIf { quality != LocationSaveQuality.UNAVAILABLE },
            canSave = quality != LocationSaveQuality.UNAVAILABLE,
            requiresManualConfirmation = requiresManualConfirmation(quality, flow)
        )
    }

    private fun classifyFallback(location: GeoPoint, nowMs: Long): LocationSaveQuality {
        val fixTimeMs = location.fixTimeMs ?: return LocationSaveQuality.LAST_KNOWN_STALE
        val ageMs = (nowMs - fixTimeMs).coerceAtLeast(0L)
        return when {
            ageMs <= freshAgeMs -> LocationSaveQuality.FRESH_BUT_LOW_ACCURACY
            ageMs <= recentLastKnownAgeMs -> LocationSaveQuality.LAST_KNOWN_RECENT
            ageMs <= staleLastKnownAgeMs -> LocationSaveQuality.LAST_KNOWN_STALE
            else -> LocationSaveQuality.UNAVAILABLE
        }
    }

    private fun requiresManualConfirmation(
        quality: LocationSaveQuality,
        flow: LocationSaveFlow
    ): Boolean {
        if (flow != LocationSaveFlow.MANUAL_ADD) {
            return false
        }
        return quality == LocationSaveQuality.FRESH_BUT_LOW_ACCURACY ||
            quality == LocationSaveQuality.LAST_KNOWN_STALE
    }
}
