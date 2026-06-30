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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat

class QuickAddPassiveLocationUpdater(
    context: Context,
    private val locationCache: QuickAddLocationCache
) {
    private val appContext = context.applicationContext
    private val locationManager: LocationManager? = appContext.getSystemService(LocationManager::class.java)
    private val listener = LocationListener { location ->
        locationCache.update(location.toQuickAddLocation())
    }

    @Volatile
    private var registered = false

    fun refreshRegistration(enabled: Boolean) {
        val shouldRegister = shouldRegisterQuickAddPassiveLocationUpdater(
            enabled = enabled,
            sdkInt = Build.VERSION.SDK_INT,
            hasFinePermission = hasPreciseLocationPermission(),
            hasBackgroundPermission = hasBackgroundLocationPermission()
        ) && locationManager != null
        if (shouldRegister && !registered) {
            register()
        } else if (!shouldRegister && registered) {
            unregister()
        }
    }

    private fun register() {
        val manager = locationManager ?: return
        if (!runCatching { manager.allProviders.contains(LocationManager.PASSIVE_PROVIDER) }.getOrDefault(false)) {
            return
        }
        runCatching {
            manager.requestLocationUpdates(
                LocationManager.PASSIVE_PROVIDER,
                0L,
                0f,
                listener,
                Looper.getMainLooper()
            )
        }.onSuccess {
            registered = true
        }
    }

    private fun unregister() {
        val manager = locationManager ?: return
        runCatching { manager.removeUpdates(listener) }
        registered = false
    }

    private fun hasPreciseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        }
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

internal fun shouldRegisterQuickAddPassiveLocationUpdater(
    enabled: Boolean,
    sdkInt: Int,
    hasFinePermission: Boolean,
    hasBackgroundPermission: Boolean
): Boolean {
    return isQuickAddExecutionAllowed(
        enabled = enabled,
        sdkInt = sdkInt,
        hasPreciseLocationPermission = hasFinePermission,
        hasBackgroundLocationPermission = hasBackgroundPermission
    )
}
