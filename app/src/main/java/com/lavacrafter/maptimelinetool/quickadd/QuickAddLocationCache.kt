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

class QuickAddLocationCache(
    private val maxAgeMs: Long = 15_000L,
    private val maxAccuracyMeters: Float = 50f,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeNanos: () -> Long? = { null }
) {
    @Volatile
    private var cachedLocation: QuickAddLocation? = null

    fun update(location: QuickAddLocation): Boolean {
        if (!isQualified(location)) {
            return false
        }
        cachedLocation = location
        return true
    }

    fun getQualifiedLocation(): QuickAddLocation? {
        val location = cachedLocation ?: return null
        return if (isQualified(location)) {
            location
        } else {
            cachedLocation = null
            null
        }
    }

    fun clear() {
        cachedLocation = null
    }

    fun isQualified(location: QuickAddLocation): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) {
            return false
        }
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
            return false
        }
        if (location.isMock) {
            return false
        }
        if (location.fixTimeMs <= 0L) {
            return false
        }
        if (location.accuracyMeters <= 0f || location.accuracyMeters > maxAccuracyMeters) {
            return false
        }
        val provider = location.provider.trim()
        if (provider.isBlank()) {
            return false
        }
        if (provider.equals("unknown", ignoreCase = true) || provider == "?") {
            return false
        }
        val ageMs = calculateAgeMs(location) ?: return false
        return ageMs <= maxAgeMs
    }

    private fun calculateAgeMs(location: QuickAddLocation): Long? {
        val nowElapsedRealtimeNanos = elapsedRealtimeNanos()?.takeIf { it > 0L }
        val fixElapsedRealtimeNanos = location.elapsedRealtimeNanos?.takeIf { it > 0L }
        if (nowElapsedRealtimeNanos != null && fixElapsedRealtimeNanos != null) {
            val ageNanos = nowElapsedRealtimeNanos - fixElapsedRealtimeNanos
            if (ageNanos < 0L) {
                return null
            }
            return ageNanos / 1_000_000L
        }

        val ageMs = wallClockMs() - location.fixTimeMs
        if (ageMs < 0L) {
            return null
        }
        return ageMs
    }
}
