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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class QuickAddResult {
    SAVED_FROM_RECENT_CACHE,
    SAVED_FROM_FRESH_REQUEST,
    FAILED_NO_FRESH_ACCURATE_LOCATION
}

class QuickAddResolver(
    private val locationProvider: LocationProvider,
    private val locationCache: QuickAddLocationCache,
    private val addPoint: suspend (title: String, location: GeoPoint, timestamp: Long) -> Unit,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeNanos: () -> Long? = { null },
    private val titleFormatter: (Long) -> String = { timestampMs ->
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
    }
) {
    suspend fun savePoint(timeoutMs: Long, clickTimeMs: Long = wallClockMs()): QuickAddResult {
        locationCache.getQualifiedLocation()?.let { cachedLocation ->
            addPoint(titleFormatter(clickTimeMs), cachedLocation.toGeoPoint(), clickTimeMs)
            return QuickAddResult.SAVED_FROM_RECENT_CACHE
        }

        val preciseLocation = runCatching {
            locationProvider.getPreciseLocation(timeoutMs)
        }.getOrNull()

        val strictLocation = preciseLocation
            ?.toQuickAddLocation(
                observedAtMs = wallClockMs(),
                observedElapsedRealtimeNanos = elapsedRealtimeNanos()
            )
            ?.takeIf { locationCache.isQualified(it) }
            ?: return QuickAddResult.FAILED_NO_FRESH_ACCURATE_LOCATION

        addPoint(titleFormatter(clickTimeMs), strictLocation.toGeoPoint(), clickTimeMs)
        locationCache.update(strictLocation)
        return QuickAddResult.SAVED_FROM_FRESH_REQUEST
    }
}
