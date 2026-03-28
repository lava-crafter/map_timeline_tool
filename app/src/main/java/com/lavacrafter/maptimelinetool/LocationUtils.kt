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
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true)
        return providers
            .mapNotNull { lm.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?: readCachedLocation(context)
    }

    private fun readCachedLocation(context: Context): Location? {
        val prefs = context.getSharedPreferences(HeadingLocationOverlay.LOCATION_PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(HeadingLocationOverlay.KEY_LAT) || !prefs.contains(HeadingLocationOverlay.KEY_LON)) {
            return null
        }
        return Location("cached").apply {
            latitude = prefs.getFloat(HeadingLocationOverlay.KEY_LAT, 0f).toDouble()
            longitude = prefs.getFloat(HeadingLocationOverlay.KEY_LON, 0f).toDouble()
            time = prefs.getLong(HeadingLocationOverlay.KEY_TIME, System.currentTimeMillis())
        }
    }

    suspend fun getFreshLocation(context: Context, timeoutMs: Long): Location? {
        return withTimeoutOrNull(timeoutMs) {
            try {
                getCurrentLocationOnce(context)
            } catch (_: Exception) {
                null
            }
        } ?: getLastKnownLocation(context)
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocationOnce(context: Context): Location = suspendCancellableCoroutine { cont ->
        val lm = context.getSystemService(LocationManager::class.java) ?: run {
            cont.resumeWithException(IllegalStateException("No location manager"))
            return@suspendCancellableCoroutine
        }
        val providers = lm.getProviders(true)
        val provider = providers.firstOrNull { it == LocationManager.FUSED_PROVIDER }
            ?: providers.firstOrNull { it == LocationManager.GPS_PROVIDER }
            ?: providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
            ?: providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
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

    @Suppress("DEPRECATION")
    private fun requestSingleUpdateCompat(
        locationManager: android.location.LocationManager,
        provider: String,
        listener: android.location.LocationListener,
    ) {
        locationManager.requestSingleUpdate(provider, listener, null)
    }
}
