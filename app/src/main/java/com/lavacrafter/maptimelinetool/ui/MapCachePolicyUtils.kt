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

package com.lavacrafter.maptimelinetool.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.osmdroid.config.Configuration

private const val DEFAULT_CACHE_BYTES = 50L * 1024L * 1024L
private const val SATELLITE_CACHE_BYTES = 1024L * 1024L * 1024L

fun applyMapCachePolicy(context: Context, mapTileSourceId: String) {
    val isSatellite = mapTileSourceId == "eox_sentinel2_cloudless_2024"
    val config = Configuration.getInstance()
    // Do not set maxBytes to 0L here, because 0L will cause the SqlTileWriter to continuously delete ALL previously cached tiles!
    // The network traffic restriction is handled by map.setUseDataConnection(!downloadedOnly) instead.
    val maxBytes = if (isSatellite) SATELLITE_CACHE_BYTES else DEFAULT_CACHE_BYTES
    config.tileFileSystemCacheMaxBytes = maxBytes
}

private fun isOnWifi(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
        @Suppress("DEPRECATION")
        val underlying = cm.allNetworks.find {
            val c = cm.getNetworkCapabilities(it)
            c != null && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        if (underlying != null) {
            val c = cm.getNetworkCapabilities(underlying)
            if (c?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) return true
            if (c?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) return false
        }
        return false
    }
    return false
}
