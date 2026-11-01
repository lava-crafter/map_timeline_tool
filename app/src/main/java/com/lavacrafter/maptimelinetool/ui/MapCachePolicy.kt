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

import com.lavacrafter.maptimelinetool.NetworkStatus

enum class MapCachePolicy(val value: Int) {
    DISABLED(0),
    WIFI_ONLY(1),
    ALWAYS(2);

    companion object {
        fun fromValue(value: Int): MapCachePolicy = values().firstOrNull { it.value == value } ?: ALWAYS
    }
}

data class MapTileAccessPolicy(
    val allowNetwork: Boolean,
    val allowCacheWrites: Boolean
)

/** Cache preferences never turn the normal map into an offline-only map. */
fun resolveMapTileAccessPolicy(
    cachePolicy: MapCachePolicy,
    networkStatus: NetworkStatus
): MapTileAccessPolicy = MapTileAccessPolicy(
    allowNetwork = true,
    allowCacheWrites = when (cachePolicy) {
        MapCachePolicy.DISABLED -> false
        MapCachePolicy.WIFI_ONLY -> networkStatus == NetworkStatus.WIFI
        MapCachePolicy.ALWAYS -> true
    }
)
