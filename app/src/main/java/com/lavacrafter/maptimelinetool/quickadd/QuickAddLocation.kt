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

import android.location.Location
import android.os.Build
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint

data class QuickAddLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val fixTimeMs: Long,
    val provider: String,
    val elapsedRealtimeNanos: Long? = null,
    val isMock: Boolean = false
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        fixTimeMs = fixTimeMs,
        provider = provider
    )
}

internal fun GeoPoint.toQuickAddLocation(
    observedAtMs: Long,
    observedElapsedRealtimeNanos: Long? = null
): QuickAddLocation? {
    val accuracy = accuracyMeters ?: return null
    val fixTime = fixTimeMs ?: return null
    val rawProvider = provider?.trim().orEmpty()
    if (rawProvider.isBlank()) {
        return null
    }
    val derivedElapsedRealtimeNanos = observedElapsedRealtimeNanos?.takeIf { it > 0L }?.let { nowElapsed ->
        val wallClockAgeMs = observedAtMs - fixTime
        if (wallClockAgeMs < 0L) {
            null
        } else {
            (nowElapsed - (wallClockAgeMs * 1_000_000L)).takeIf { it > 0L }
        }
    }
    return QuickAddLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        fixTimeMs = fixTime,
        provider = rawProvider,
        elapsedRealtimeNanos = derivedElapsedRealtimeNanos
    )
}

internal fun Location.toQuickAddLocation(): QuickAddLocation {
    return QuickAddLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        fixTimeMs = time,
        provider = provider?.trim().orEmpty(),
        elapsedRealtimeNanos = elapsedRealtimeNanos.takeIf { it > 0L },
        isMock = isMockCompat()
    )
}

private fun Location.isMockCompat(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        isMock
    } else {
        @Suppress("DEPRECATION")
        isFromMockProvider
    }
}
