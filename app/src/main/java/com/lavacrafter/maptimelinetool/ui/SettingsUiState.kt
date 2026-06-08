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

data class SettingsUiState(
    val isDarkTheme: Boolean = false,
    val followSystemTheme: Boolean = true,
    val languagePreference: LanguagePreference = LanguagePreference.FOLLOW_SYSTEM,
    val timeoutSeconds: Int = 20,
    val cachePolicy: MapCachePolicy = MapCachePolicy.WIFI_ONLY,
    val satelliteCachePolicy: MapCachePolicy = MapCachePolicy.WIFI_ONLY,
    val pinnedTagIds: Set<Long> = emptySet(),
    val recentTagIds: List<Long> = emptyList(),
    val zoomBehavior: ZoomButtonBehavior = ZoomButtonBehavior.HIDE,
    val markerScale: Float = 1f,
    val defaultTagIds: Set<Long> = emptySet(),
    val downloadedAreas: List<DownloadedArea> = emptyList(),
    val downloadTileSourceId: String = "",
    val downloadMultiThreadEnabled: Boolean = false,
    val downloadThreadCount: Int = 4,
    val photoLosslessEnabled: Boolean = true,
    val photoCompressFormat: PhotoCompressFormat = PhotoCompressFormat.JPEG,
    val photoCompressQuality: Int = 80,
    val pressureEnabled: Boolean = true,
    val ambientLightEnabled: Boolean = true,
    val accelerometerEnabled: Boolean = true,
    val gyroscopeEnabled: Boolean = true,
    val magnetometerEnabled: Boolean = true,
    val noiseEnabled: Boolean = false,
    val mapTileSourceId: String = mapTileSources.first().id
)
