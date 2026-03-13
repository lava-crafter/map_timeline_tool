package com.lavacrafter.maptimelinetool.ui

data class SettingsUiState(
    val isDarkTheme: Boolean = false,
    val followSystemTheme: Boolean = true,
    val timeoutSeconds: Int = 20,
    val cachePolicy: MapCachePolicy = MapCachePolicy.WIFI_ONLY,
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
