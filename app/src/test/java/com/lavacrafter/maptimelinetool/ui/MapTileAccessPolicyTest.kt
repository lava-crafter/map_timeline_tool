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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MapTileAccessPolicyTest(
    private val cachePolicy: MapCachePolicy,
    private val networkStatus: NetworkStatus,
    private val expectedCacheWrites: Boolean
) {
    @Test
    fun cachePreferencesOnlyControlPersistentWrites() {
        assertEquals(
            MapTileAccessPolicy(allowNetwork = true, allowCacheWrites = expectedCacheWrites),
            resolveMapTileAccessPolicy(cachePolicy, networkStatus)
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} on {1}: cacheWrites={2}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf(MapCachePolicy.DISABLED, NetworkStatus.WIFI, false),
            arrayOf(MapCachePolicy.DISABLED, NetworkStatus.CELLULAR, false),
            arrayOf(MapCachePolicy.DISABLED, NetworkStatus.NONE, false),
            arrayOf(MapCachePolicy.WIFI_ONLY, NetworkStatus.WIFI, true),
            arrayOf(MapCachePolicy.WIFI_ONLY, NetworkStatus.CELLULAR, false),
            arrayOf(MapCachePolicy.WIFI_ONLY, NetworkStatus.NONE, false),
            arrayOf(MapCachePolicy.ALWAYS, NetworkStatus.WIFI, true),
            arrayOf(MapCachePolicy.ALWAYS, NetworkStatus.CELLULAR, true),
            arrayOf(MapCachePolicy.ALWAYS, NetworkStatus.NONE, true)
        )
    }
}
