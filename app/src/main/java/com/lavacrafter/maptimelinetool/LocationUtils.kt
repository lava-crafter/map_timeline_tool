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

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import com.lavacrafter.maptimelinetool.ui.HeadingLocationOverlay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LocationUtils {
    private const val MAX_POINT_ACCURACY_METERS = 100f
    private const val MAX_POINT_LOCATION_AGE_MS = 15_000L
    private const val MAX_LAST_KNOWN_LOCATION_AGE_MS = 3_600_000L
    private const val MAX_LAST_KNOWN_ACCURACY_METERS = 150f
    private const val MAX_STALE_LAST_KNOWN_LOCATION_AGE_MS = 24 * 60 * 60 * 1000L
    private const val MAX_STALE_LAST_KNOWN_ACCURACY_METERS = 300f
    private const val CACHED_PROVIDER = "cached_overlay"

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(
        context: Context,
        maxAgeMs: Long = MAX_LAST_KNOWN_LOCATION_AGE_MS,
        maxAccuracyMeters: Float = MAX_LAST_KNOWN_ACCURACY_METERS
    ): Location? {
        val lm = context.getSystemService(LocationManager::class.java)
        val systemCandidates = lm?.let { locationManager ->
            runCatching { locationManager.getProviders(true) }
                .getOrDefault(emptyList())
                .mapNotNull { provider ->
                    runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                }
        }.orEmpty()

        val cached = readCachedLocation(context)
        val best = pickBestLocation(systemCandidates + listOfNotNull(cached), maxAgeMs, maxAccuracyMeters)
        if (best != null) {
            cacheLocation(context, best)
        }
        return best
    }

    fun cacheLocation(context: Context, location: Location) {
        val editor = context.getSharedPreferences(HeadingLocationOverlay.LOCATION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(HeadingLocationOverlay.KEY_LAT, location.latitude.toFloat())
            .putFloat(HeadingLocationOverlay.KEY_LON, location.longitude.toFloat())
            .putLong(HeadingLocationOverlay.KEY_TIME, location.time)
            .putString(HeadingLocationOverlay.KEY_PROVIDER, location.provider)

        if (location.hasAccuracy()) {
            editor.putFloat(HeadingLocationOverlay.KEY_ACCURACY, location.accuracy)
        } else {
            editor.remove(HeadingLocationOverlay.KEY_ACCURACY)
        }
        editor.apply()
    }

    private fun readCachedLocation(context: Context): Location? {
        val prefs = context.getSharedPreferences(HeadingLocationOverlay.LOCATION_PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(HeadingLocationOverlay.KEY_LAT) || !prefs.contains(HeadingLocationOverlay.KEY_LON)) {
            return null
        }
        val provider = prefs.getString(HeadingLocationOverlay.KEY_PROVIDER, CACHED_PROVIDER)
            ?.ifBlank { CACHED_PROVIDER }
            ?: CACHED_PROVIDER
        return Location(provider).apply {
            latitude = prefs.getFloat(HeadingLocationOverlay.KEY_LAT, 0f).toDouble()
            longitude = prefs.getFloat(HeadingLocationOverlay.KEY_LON, 0f).toDouble()
            time = prefs.getLong(HeadingLocationOverlay.KEY_TIME, System.currentTimeMillis())
            if (prefs.contains(HeadingLocationOverlay.KEY_ACCURACY)) {
                val cachedAccuracy = prefs.getFloat(HeadingLocationOverlay.KEY_ACCURACY, -1f)
                if (cachedAccuracy > 0f) {
                    accuracy = cachedAccuracy
                }
            }
        }
    }

    suspend fun getFreshLocation(
        context: Context,
        timeoutMs: Long,
        maxAgeMs: Long = MAX_POINT_LOCATION_AGE_MS,
        maxAccuracyMeters: Float = MAX_POINT_ACCURACY_METERS,
        requireAccuracy: Boolean = false
    ): Location? {
        val fresh = withTimeoutOrNull(timeoutMs) {
            try {
                getCurrentLocationOnce(context)
            } catch (_: Exception) {
                null
            }
        } ?: return null

        return fresh.takeIf {
            isLocationAcceptable(it, System.currentTimeMillis(), maxAgeMs, maxAccuracyMeters, requireAccuracy)
        }?.also { cacheLocation(context, it) }
    }

    suspend fun getPreciseLocation(
        context: Context,
        timeoutMs: Long,
        maxAgeMs: Long = MAX_POINT_LOCATION_AGE_MS,
        maxAccuracyMeters: Float = MAX_POINT_ACCURACY_METERS
    ): Location? {
        return getFreshLocation(
            context = context,
            timeoutMs = timeoutMs,
            maxAgeMs = maxAgeMs,
            maxAccuracyMeters = maxAccuracyMeters,
            requireAccuracy = true
        )
    }

    suspend fun getBestEffortLocation(
        context: Context,
        timeoutMs: Long,
        maxAgeMs: Long = MAX_LAST_KNOWN_LOCATION_AGE_MS,
        maxAccuracyMeters: Float = MAX_LAST_KNOWN_ACCURACY_METERS
    ): Location? {
        return getLastKnownLocation(context, maxAgeMs, maxAccuracyMeters)
            ?: getFreshLocation(context, timeoutMs, MAX_POINT_LOCATION_AGE_MS, MAX_POINT_ACCURACY_METERS)
            ?: getLastKnownLocation(
                context,
                MAX_STALE_LAST_KNOWN_LOCATION_AGE_MS,
                MAX_STALE_LAST_KNOWN_ACCURACY_METERS
            )
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocationOnce(context: Context): Location = suspendCancellableCoroutine { cont ->
        val lm = context.getSystemService(LocationManager::class.java) ?: run {
            cont.resumeWithException(IllegalStateException("No location manager"))
            return@suspendCancellableCoroutine
        }
        val providers = lm.getProviders(true)
        val provider = pickSingleUpdateProvider(providers)
            ?: run {
                cont.resumeWithException(IllegalStateException("No location provider"))
                return@suspendCancellableCoroutine
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, { runnable -> runnable.run() }) { location ->
                if (location != null) {
                    cont.resume(location)
                } else {
                    cont.resumeWithException(IllegalStateException("Location unavailable"))
                }
            }
            return@suspendCancellableCoroutine
        }

        lateinit var listener: android.location.LocationListener
        listener = android.location.LocationListener { location ->
            lm.removeUpdates(listener)
            cont.resume(location)
        }
        cont.invokeOnCancellation { lm.removeUpdates(listener) }
            requestSingleUpdateCompat(lm, provider, listener)
    }

    private fun pickSingleUpdateProvider(providers: List<String>): String? {
        return providers.firstOrNull { it == LocationManager.FUSED_PROVIDER }
            ?: providers.firstOrNull { it == LocationManager.GPS_PROVIDER }
            ?: providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
            ?: providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
    }

    private fun pickBestLocation(
        candidates: List<Location>,
        maxAgeMs: Long,
        maxAccuracyMeters: Float
    ): Location? {
        val now = System.currentTimeMillis()
        return candidates
            .asSequence()
            .filter { isLocationAcceptable(it, now, maxAgeMs, maxAccuracyMeters) }
            .maxByOrNull { scoreLocation(it, now) }
    }

    private fun isLocationAcceptable(
        location: Location,
        nowMs: Long,
        maxAgeMs: Long,
        maxAccuracyMeters: Float,
        requireAccuracy: Boolean = false
    ): Boolean {
        if (location.latitude.isNaN() || location.longitude.isNaN()) {
            return false
        }

        val fixTime = location.time.takeIf { it > 0L } ?: return false
        val ageMs = (nowMs - fixTime).coerceAtLeast(0L)
        if (ageMs > maxAgeMs) {
            return false
        }

        if (requireAccuracy && !location.hasAccuracy()) {
            return false
        }

        if (location.hasAccuracy() && location.accuracy > maxAccuracyMeters) {
            return false
        }

        return true
    }

    private fun scoreLocation(location: Location, nowMs: Long): Double {
        val accuracyScore = if (location.hasAccuracy()) {
            10_000.0 - location.accuracy.toDouble()
        } else {
            0.0
        }
        val ageMs = (nowMs - location.time).coerceAtLeast(0L).toDouble()
        val recencyScore = 1_000.0 - (ageMs / 1000.0)
        val providerScore = when (location.provider) {
            LocationManager.FUSED_PROVIDER -> 35.0
            LocationManager.GPS_PROVIDER -> 30.0
            LocationManager.NETWORK_PROVIDER -> 20.0
            CACHED_PROVIDER -> 15.0
            else -> 10.0
        }
        return accuracyScore + recencyScore + providerScore
    }

    @Suppress("DEPRECATION")
    private fun requestSingleUpdateCompat(
        locationManager: android.location.LocationManager,
        provider: String,
        listener: android.location.LocationListener,
    ) {
        locationManager.requestSingleUpdate(provider, listener, null)
    }
}
