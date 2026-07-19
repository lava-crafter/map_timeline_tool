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

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class GoogleFusedLocationProvider(
    context: Context
) : LocationProvider {
    private val appContext = context.applicationContext
    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    override fun getLastKnownLocation(): GeoPoint? = null

    override suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint? {
        val location = getCurrentLocation(timeoutMs) ?: return null
        val nowMs = System.currentTimeMillis()
        return location
            .takeIf {
                isLocationAcceptable(
                    location = it,
                    nowMs = nowMs,
                    maxAgeMs = MAX_POINT_LOCATION_AGE_MS,
                    maxAccuracyMeters = MAX_POINT_ACCURACY_METERS,
                    requireAccuracy = true
                )
            }
            ?.toGeoPointAndCache(appContext)
    }

    override suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? {
        val location = getCurrentLocation(timeoutMs) ?: return null
        val nowMs = System.currentTimeMillis()
        return location
            .takeIf {
                isLocationAcceptable(
                    location = it,
                    nowMs = nowMs,
                    maxAgeMs = MAX_POINT_LOCATION_AGE_MS,
                    maxAccuracyMeters = MAX_POINT_ACCURACY_METERS
                )
            }
            ?.toGeoPointAndCache(appContext)
    }

    override suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? {
        val deadline = LocationDeadline.after(timeoutMs)
        val recentLastKnown = getLastLocation(
            timeoutMs = deadline.remainingMs(),
            maxAgeMs = MAX_LAST_KNOWN_LOCATION_AGE_MS,
            maxAccuracyMeters = MAX_LAST_KNOWN_ACCURACY_METERS
        )
        if (recentLastKnown != null) {
            return recentLastKnown.toGeoPointAndCache(appContext)
        }

        val freshBudgetMs = deadline.remainingMs()
        val fresh = if (freshBudgetMs > 0L) getFreshLocation(freshBudgetMs) else null
        if (fresh != null) {
            return fresh
        }

        return getLastLocation(
            timeoutMs = deadline.remainingMs(),
            maxAgeMs = MAX_STALE_LAST_KNOWN_LOCATION_AGE_MS,
            maxAccuracyMeters = MAX_STALE_LAST_KNOWN_ACCURACY_METERS
        )?.toGeoPointAndCache(appContext)
    }

    private suspend fun getCurrentLocation(timeoutMs: Long): Location? {
        if (!isAvailable() || !hasLocationPermission()) {
            return null
        }
        val effectiveTimeoutMs = timeoutMs.coerceAtLeast(1L)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(effectiveTimeoutMs)
            .setMaxUpdateAgeMillis(0L)
            .build()
        val cancellationTokenSource = CancellationTokenSource()
        val task = try {
            client.getCurrentLocation(request, cancellationTokenSource.token)
        } catch (_: SecurityException) {
            return null
        }
        return task
            .awaitResult(effectiveTimeoutMs, cancellationTokenSource)
    }

    private suspend fun getLastLocation(
        timeoutMs: Long,
        maxAgeMs: Long,
        maxAccuracyMeters: Float
    ): Location? {
        if (!isAvailable() || !hasLocationPermission() || timeoutMs <= 0L) {
            return null
        }
        val task = try {
            client.lastLocation
        } catch (_: SecurityException) {
            return null
        }
        val location = task.awaitResult(timeoutMs.coerceAtLeast(1L)) ?: return null
        return location.takeIf {
            isLocationAcceptable(
                location = it,
                nowMs = System.currentTimeMillis(),
                maxAgeMs = maxAgeMs,
                maxAccuracyMeters = maxAccuracyMeters
            )
        }
    }

    private fun isAvailable(): Boolean {
        return runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }

    private fun hasLocationPermission(): Boolean {
        return appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
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

    companion object {
        private const val MAX_POINT_ACCURACY_METERS = 100f
        private const val MAX_POINT_LOCATION_AGE_MS = 15_000L
        private const val MAX_LAST_KNOWN_LOCATION_AGE_MS = 3_600_000L
        private const val MAX_LAST_KNOWN_ACCURACY_METERS = 150f
        private const val MAX_STALE_LAST_KNOWN_LOCATION_AGE_MS = 24 * 60 * 60 * 1000L
        private const val MAX_STALE_LAST_KNOWN_ACCURACY_METERS = 300f
    }
}

private suspend fun <T> Task<T>.awaitResult(
    timeoutMs: Long,
    cancellationTokenSource: CancellationTokenSource? = null
): T? {
    return withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) {
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result ->
                if (cont.isActive) {
                    cont.resume(result)
                }
            }
            addOnFailureListener {
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
            addOnCanceledListener {
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
            cont.invokeOnCancellation {
                cancellationTokenSource?.cancel()
            }
        }
    }
}

private fun Location.toGeoPointAndCache(context: Context): GeoPoint {
    LocationUtils.cacheLocation(context, this)
    return GeoPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        fixTimeMs = time.takeIf { it > 0L },
        provider = provider?.ifBlank { "fused" } ?: "fused"
    )
}
