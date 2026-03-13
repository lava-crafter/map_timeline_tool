@file:OptIn(ExperimentalMaterial3Api::class)

package com.lavacrafter.maptimelinetool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.NetworkStatus
import com.lavacrafter.maptimelinetool.data.TagEntity
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    followSystemTheme: Boolean,
    onFollowSystemThemeChange: (Boolean) -> Unit,
    timeoutSeconds: Int,
    onTimeoutSecondsChange: (Int) -> Unit,
    cachePolicy: MapCachePolicy,
    onCachePolicyChange: (MapCachePolicy) -> Unit,
    networkStatus: NetworkStatus,
    selectedDownloadTileSourceId: String,
    onDownloadTileSourceChange: (String) -> Unit,
    downloadMultiThreadEnabled: Boolean,
    onDownloadMultiThreadEnabledChange: (Boolean) -> Unit,
    downloadThreadCount: Int,
    onDownloadThreadCountChange: (Int) -> Unit,
    photoLosslessEnabled: Boolean,
    onPhotoLosslessEnabledChange: (Boolean) -> Unit,
    photoCompressFormat: PhotoCompressFormat,
    onPhotoCompressFormatChange: (PhotoCompressFormat) -> Unit,
    photoCompressQuality: Int,
    onPhotoCompressQualityChange: (Int) -> Unit,
    pressureEnabled: Boolean,
    onPressureEnabledChange: (Boolean) -> Unit,
    ambientLightEnabled: Boolean,
    onAmbientLightEnabledChange: (Boolean) -> Unit,
    accelerometerEnabled: Boolean,
    onAccelerometerEnabledChange: (Boolean) -> Unit,
    gyroscopeEnabled: Boolean,
    onGyroscopeEnabledChange: (Boolean) -> Unit,
    magnetometerEnabled: Boolean,
    onMagnetometerEnabledChange: (Boolean) -> Unit,
    noiseEnabled: Boolean,
    onNoiseEnabledChange: (Boolean) -> Unit,
    mapTileSourceId: String,
    onMapTileSourceChange: (String) -> Unit,
    zoomBehavior: ZoomButtonBehavior,
    onZoomBehaviorChange: (ZoomButtonBehavior) -> Unit,
    markerScale: Float,
    onMarkerScaleChange: (Float) -> Unit,
    downloadedAreas: List<DownloadedArea>,
    onRemoveDownloadedArea: (DownloadedArea) -> Unit,
    onDeduplicateDownloadedAreas: () -> Unit,
    onOpenMapDownload: () -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onClearCache: () -> Unit,
    onOpenAbout: () -> Unit,
    defaultTags: List<TagEntity>,
    selectedDefaultTagIds: Set<Long>,
    onToggleDefaultTag: (Long) -> Unit,
    route: SettingsRoute,
    onNavigateTo: (SettingsRoute) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler(enabled = route != SettingsRoute.Main) {
        onNavigateBack()
    }
    when (route) {
        SettingsRoute.Main -> SettingsOverviewScreen(
            isDarkTheme = isDarkTheme,
            onDarkThemeChange = onDarkThemeChange,
            followSystemTheme = followSystemTheme,
            onFollowSystemThemeChange = onFollowSystemThemeChange,
            onNavigateTo = onNavigateTo,
            onExportCsv = onExportCsv,
            onImportCsv = onImportCsv,
            onClearCache = onClearCache,
            onOpenAbout = onOpenAbout
        )
        SettingsRoute.Sensors -> SensorSettings(
            pressureEnabled = pressureEnabled,
            onPressureEnabledChange = onPressureEnabledChange,
            ambientLightEnabled = ambientLightEnabled,
            onAmbientLightEnabledChange = onAmbientLightEnabledChange,
            accelerometerEnabled = accelerometerEnabled,
            onAccelerometerEnabledChange = onAccelerometerEnabledChange,
            gyroscopeEnabled = gyroscopeEnabled,
            onGyroscopeEnabledChange = onGyroscopeEnabledChange,
            magnetometerEnabled = magnetometerEnabled,
            onMagnetometerEnabledChange = onMagnetometerEnabledChange,
            noiseEnabled = noiseEnabled,
            onNoiseEnabledChange = onNoiseEnabledChange,
            onBack = onNavigateBack
        )
        SettingsRoute.MapOperations -> MapOperationsSettings(
            timeoutSeconds = timeoutSeconds,
            onTimeoutSecondsChange = onTimeoutSecondsChange,
            zoomBehavior = zoomBehavior,
            onZoomBehaviorChange = onZoomBehaviorChange,
            markerScale = markerScale,
            onMarkerScaleChange = onMarkerScaleChange,
            onBack = onNavigateBack
        )
        SettingsRoute.PhotoCompression -> PhotoCompressionSettings(
            photoLosslessEnabled = photoLosslessEnabled,
            onPhotoLosslessEnabledChange = onPhotoLosslessEnabledChange,
            photoCompressFormat = photoCompressFormat,
            onPhotoCompressFormatChange = onPhotoCompressFormatChange,
            photoCompressQuality = photoCompressQuality,
            onPhotoCompressQualityChange = onPhotoCompressQualityChange,
            onBack = onNavigateBack
        )
        SettingsRoute.Cache -> CacheSettings(
            cachePolicy = cachePolicy,
            onCachePolicyChange = onCachePolicyChange,
            networkStatus = networkStatus,
            mapTileSourceId = mapTileSourceId,
            onMapTileSourceChange = onMapTileSourceChange,
            onClearCache = onClearCache,
            onBack = onNavigateBack
        )
        SettingsRoute.Download -> DownloadSettings(
            selectedDownloadTileSourceId = selectedDownloadTileSourceId,
            onDownloadTileSourceChange = onDownloadTileSourceChange,
            downloadMultiThreadEnabled = downloadMultiThreadEnabled,
            onDownloadMultiThreadEnabledChange = onDownloadMultiThreadEnabledChange,
            downloadThreadCount = downloadThreadCount,
            onDownloadThreadCountChange = onDownloadThreadCountChange,
            downloadedAreas = downloadedAreas,
            onRemoveDownloadedArea = onRemoveDownloadedArea,
            onDeduplicateDownloadedAreas = onDeduplicateDownloadedAreas,
            onOpenMapDownload = onOpenMapDownload,
            onBack = onNavigateBack
        )
        SettingsRoute.DefaultTags -> DefaultTagsScreen(
            tags = defaultTags,
            selectedTagIds = selectedDefaultTagIds,
            onToggleTag = onToggleDefaultTag,
            onBack = onNavigateBack
        )
    }
}

@Composable
private fun SettingsOverviewScreen(
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    followSystemTheme: Boolean,
    onFollowSystemThemeChange: (Boolean) -> Unit,
    onNavigateTo: (SettingsRoute) -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onClearCache: () -> Unit,
    onOpenAbout: () -> Unit
) {
    var showHelp by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.Help, contentDescription = stringResource(R.string.settings_help_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ThemeControls(
                isDarkTheme = isDarkTheme,
                onDarkThemeChange = onDarkThemeChange,
                followSystemTheme = followSystemTheme,
                onFollowSystemThemeChange = onFollowSystemThemeChange
            )

            SettingsOverviewItem(
                title = stringResource(R.string.settings_sensors_title),
                description = stringResource(R.string.settings_sensors_desc),
                onClick = { onNavigateTo(SettingsRoute.Sensors) }
            )
            SettingsOverviewItem(
                title = stringResource(R.string.settings_map_operations_title),
                description = stringResource(R.string.settings_map_operations_desc),
                onClick = { onNavigateTo(SettingsRoute.MapOperations) }
            )
            SettingsOverviewItem(
                title = stringResource(R.string.settings_photo_compression_title),
                description = stringResource(R.string.settings_photo_compression_desc),
                onClick = { onNavigateTo(SettingsRoute.PhotoCompression) }
            )
            SettingsOverviewItem(
                title = stringResource(R.string.settings_cache_title),
                description = stringResource(R.string.settings_cache_desc),
                onClick = { onNavigateTo(SettingsRoute.Cache) }
            )
            SettingsOverviewItem(
                title = stringResource(R.string.settings_download_title) + " " + stringResource(R.string.settings_experimental_label),
                description = stringResource(R.string.settings_download_desc),
                onClick = { onNavigateTo(SettingsRoute.Download) }
            )
            SettingsOverviewItem(
                title = stringResource(R.string.settings_default_tags_title),
                description = stringResource(R.string.settings_default_tags_desc),
                onClick = { onNavigateTo(SettingsRoute.DefaultTags) }
            )

            Button(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_export_csv))
            }
            Button(onClick = onImportCsv, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_import_csv))
            }
            OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_clear_cache))
            }
            OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_about))
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.settings_help_title)) },
            text = { Text(stringResource(R.string.settings_help_overview)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }
}

@Composable
private fun ThemeControls(
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    followSystemTheme: Boolean,
    onFollowSystemThemeChange: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = followSystemTheme, onCheckedChange = onFollowSystemThemeChange)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (followSystemTheme) stringResource(R.string.settings_theme_follow_system_hint) else stringResource(R.string.settings_theme_follow_system)
            )
        }
        if (!followSystemTheme) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = isDarkTheme, onCheckedChange = onDarkThemeChange)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_theme_dark_mode))
            }
        }
    }
}

@Composable
private fun SettingsOverviewItem(title: String, description: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, style = MaterialTheme.typography.bodyMedium)
        Divider(modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun MapOperationsSettings(
    timeoutSeconds: Int,
    onTimeoutSecondsChange: (Int) -> Unit,
    zoomBehavior: ZoomButtonBehavior,
    onZoomBehaviorChange: (ZoomButtonBehavior) -> Unit,
    markerScale: Float,
    onMarkerScaleChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    SettingsSubpageScaffold(
        title = stringResource(R.string.settings_map_operations_title),
        tutorialText = stringResource(R.string.settings_help_map_operations),
        onBack = onBack
    ) { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.settings_timeout_label))
                var textValue by remember(timeoutSeconds) { mutableStateOf(timeoutSeconds.toString()) }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { value ->
                        textValue = value.filter { it.isDigit() }
                        textValue.toIntOrNull()?.let(onTimeoutSecondsChange)
                    },
                    label = { Text(stringResource(R.string.settings_timeout_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SelectionGroup(
                title = stringResource(R.string.settings_zoom_behavior_label),
                options = listOf(
                    SelectionItem(
                        label = stringResource(R.string.settings_zoom_behavior_hide),
                        value = ZoomButtonBehavior.HIDE
                    ),
                    SelectionItem(
                        label = stringResource(R.string.settings_zoom_behavior_interaction),
                        value = ZoomButtonBehavior.WHEN_ACTIVE
                    ),
                    SelectionItem(
                        label = stringResource(R.string.settings_zoom_behavior_always),
                        value = ZoomButtonBehavior.ALWAYS
                    )
                ),
                selectedValue = zoomBehavior,
                onSelect = onZoomBehaviorChange
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.settings_marker_size_label))
                Text(text = stringResource(R.string.settings_marker_size_value, (markerScale * 100).toInt()))
                Slider(
                    value = markerScale,
                    onValueChange = onMarkerScaleChange,
                    valueRange = 0.3f..1.75f
                )
            }
        }
    }
}

@Composable
private fun PhotoCompressionSettings(
    photoLosslessEnabled: Boolean,
    onPhotoLosslessEnabledChange: (Boolean) -> Unit,
    photoCompressFormat: PhotoCompressFormat,
    onPhotoCompressFormatChange: (PhotoCompressFormat) -> Unit,
    photoCompressQuality: Int,
    onPhotoCompressQualityChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    SettingsSubpageScaffold(
        title = stringResource(R.string.settings_photo_compression_title),
        tutorialText = stringResource(R.string.settings_help_photo_compression),
        onBack = onBack
    ) { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SelectionGroup(
                title = stringResource(R.string.settings_photo_compress_mode_label),
                options = listOf(
                    SelectionItem(
                        label = stringResource(R.string.settings_photo_compress_mode_lossless),
                        value = true
                    ),
                    SelectionItem(
                        label = stringResource(R.string.settings_photo_compress_mode_compressed),
                        value = false
                    )
                ),
                selectedValue = photoLosslessEnabled,
                onSelect = onPhotoLosslessEnabledChange
            )

            SelectionGroup(
                title = stringResource(R.string.settings_photo_compress_format_label),
                options = listOf(
                    SelectionItem(
                        label = stringResource(R.string.settings_photo_compress_format_jpeg),
                        value = PhotoCompressFormat.JPEG
                    ),
                    SelectionItem(
                        label = stringResource(R.string.settings_photo_compress_format_png),
                        value = PhotoCompressFormat.PNG
                    ),
                    SelectionItem(
                        label = stringResource(R.string.settings_photo_compress_format_webp),
                        value = PhotoCompressFormat.WEBP
                    )
                ),
                selectedValue = photoCompressFormat,
                onSelect = onPhotoCompressFormatChange,
                enabled = !photoLosslessEnabled
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.settings_photo_compress_quality_label))
                Text(text = stringResource(R.string.settings_photo_compress_quality_value, photoCompressQuality))
                Slider(
                    value = photoCompressQuality.toFloat(),
                    onValueChange = { onPhotoCompressQualityChange(it.roundToInt().coerceIn(1, 100)) },
                    valueRange = 1f..100f,
                    steps = 98,
                    enabled = !photoLosslessEnabled
                )
            }
        }
    }
}

@Composable
private fun SensorSettings(
    pressureEnabled: Boolean,
    onPressureEnabledChange: (Boolean) -> Unit,
    ambientLightEnabled: Boolean,
    onAmbientLightEnabledChange: (Boolean) -> Unit,
    accelerometerEnabled: Boolean,
    onAccelerometerEnabledChange: (Boolean) -> Unit,
    gyroscopeEnabled: Boolean,
    onGyroscopeEnabledChange: (Boolean) -> Unit,
    magnetometerEnabled: Boolean,
    onMagnetometerEnabledChange: (Boolean) -> Unit,
    noiseEnabled: Boolean,
    onNoiseEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    SettingsSubpageScaffold(
        title = stringResource(R.string.settings_sensors_title),
        tutorialText = stringResource(R.string.settings_help_sensors),
        onBack = onBack
    ) { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SensorToggleRow(
                label = stringResource(R.string.settings_sensor_pressure),
                checked = pressureEnabled,
                onCheckedChange = onPressureEnabledChange
            )
            SensorToggleRow(
                label = stringResource(R.string.settings_sensor_ambient_light),
                checked = ambientLightEnabled,
                onCheckedChange = onAmbientLightEnabledChange
            )
            SensorToggleRow(
                label = stringResource(R.string.settings_sensor_accelerometer),
                checked = accelerometerEnabled,
                onCheckedChange = onAccelerometerEnabledChange
            )
            SensorToggleRow(
                label = stringResource(R.string.settings_sensor_gyroscope),
                checked = gyroscopeEnabled,
                onCheckedChange = onGyroscopeEnabledChange
            )
            SensorToggleRow(
                label = stringResource(R.string.settings_sensor_magnetometer),
                checked = magnetometerEnabled,
                onCheckedChange = onMagnetometerEnabledChange
            )
            Column {
                SensorToggleRow(
                    label = stringResource(R.string.settings_sensor_noise),
                    checked = noiseEnabled,
                    onCheckedChange = onNoiseEnabledChange
                )
                Text(
                    text = stringResource(R.string.settings_noise_collect_desc),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SensorToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}

@Composable
private fun CacheSettings(
    cachePolicy: MapCachePolicy,
    onCachePolicyChange: (MapCachePolicy) -> Unit,
    networkStatus: NetworkStatus,
    mapTileSourceId: String,
    onMapTileSourceChange: (String) -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit
) {
    SettingsSubpageScaffold(
        title = stringResource(R.string.settings_cache_title),
        tutorialText = stringResource(R.string.settings_help_cache),
        onBack = onBack
    ) { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SelectionGroup(
                title = stringResource(R.string.settings_cache_source_label),
                options = mapTileSources.map { source ->
                    SelectionItem(label = stringResource(source.labelRes), value = source.id)
                },
                selectedValue = mapTileSourceId,
                onSelect = onMapTileSourceChange
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(
                    R.string.settings_network_status_label,
                    stringResource(
                        when (networkStatus) {
                            NetworkStatus.WIFI -> R.string.settings_network_status_wifi
                            NetworkStatus.CELLULAR -> R.string.settings_network_status_cellular
                            NetworkStatus.NONE -> R.string.settings_network_status_offline
                        }
                    )
                ))
                SelectionGroup(
                    title = stringResource(R.string.settings_cache_policy_label),
                    options = listOf(
                        SelectionItem(label = stringResource(R.string.settings_cache_policy_disabled), value = MapCachePolicy.DISABLED),
                        SelectionItem(label = stringResource(R.string.settings_cache_policy_wifi), value = MapCachePolicy.WIFI_ONLY),
                        SelectionItem(label = stringResource(R.string.settings_cache_policy_always), value = MapCachePolicy.ALWAYS)
                    ),
                    selectedValue = cachePolicy,
                    onSelect = onCachePolicyChange
                )
            }

            OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_clear_cache))
            }
        }
    }
}

@Composable
private fun DownloadSettings(
    selectedDownloadTileSourceId: String,
    onDownloadTileSourceChange: (String) -> Unit,
    downloadMultiThreadEnabled: Boolean,
    onDownloadMultiThreadEnabledChange: (Boolean) -> Unit,
    downloadThreadCount: Int,
    onDownloadThreadCountChange: (Int) -> Unit,
    downloadedAreas: List<DownloadedArea>,
    onRemoveDownloadedArea: (DownloadedArea) -> Unit,
    onDeduplicateDownloadedAreas: () -> Unit,
    onOpenMapDownload: () -> Unit,
    onBack: () -> Unit
) {
    SettingsSubpageScaffold(
        title = stringResource(R.string.settings_download_title) + " " + stringResource(R.string.settings_experimental_label),
        tutorialText = stringResource(R.string.settings_help_download),
        onBack = onBack
    ) { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SelectionGroup(
                title = stringResource(R.string.settings_download_source_label),
                options = downloadTileSources.map { source ->
                    SelectionItem(label = stringResource(source.labelRes), value = source.id)
                },
                selectedValue = selectedDownloadTileSourceId,
                onSelect = onDownloadTileSourceChange
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = downloadMultiThreadEnabled,
                        onCheckedChange = onDownloadMultiThreadEnabledChange
                    )
                    Text(text = stringResource(R.string.settings_download_multi_thread_label))
                }

                Text(
                    text = stringResource(
                        R.string.settings_download_thread_count_label,
                        downloadThreadCount
                    )
                )
                Slider(
                    value = downloadThreadCount.toFloat(),
                    onValueChange = { value ->
                        onDownloadThreadCountChange(value.roundToInt().coerceIn(2, 32))
                    },
                    valueRange = 2f..32f,
                    steps = 29,
                    enabled = downloadMultiThreadEnabled
                )
            }

            Button(onClick = onOpenMapDownload, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_map_download_title))
            }

            if (downloadedAreas.isEmpty()) {
                Text(text = stringResource(R.string.settings_downloaded_areas_empty))
            } else {
                downloadedAreas.forEach { area ->
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.settings_downloaded_area_range, area.minZoom, area.maxZoom))
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.settings_downloaded_area_bounds,
                                    area.north,
                                    area.south,
                                    area.east,
                                    area.west
                                )
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = { onRemoveDownloadedArea(area) }) {
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    )
                    Divider()
                }
                OutlinedButton(onClick = onDeduplicateDownloadedAreas, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_deduplicate))
                }
            }
        }
    }
}

@Composable
private fun SettingsSubpageScaffold(
    title: String,
    tutorialText: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    var showHelp by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.Help, contentDescription = stringResource(R.string.settings_help_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            content(Modifier)
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.settings_help_title)) },
            text = { Text(tutorialText) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }
}

@Composable
private fun <T> SelectionGroup(
    title: String,
    options: List<SelectionItem<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title)
        options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base ->
                        if (enabled) {
                            base.clickable { onSelect(option.value) }
                        } else {
                            base
                        }
                    }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = option.value == selectedValue,
                    onClick = { onSelect(option.value) },
                    enabled = enabled
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = option.label)
            }
        }
    }
}

data class SelectionItem<T>(val label: String, val value: T)
