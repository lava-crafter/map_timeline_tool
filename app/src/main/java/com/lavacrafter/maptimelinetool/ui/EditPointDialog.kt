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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.resolvePointPhotoFile
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.data.TagEntity
import com.lavacrafter.maptimelinetool.text.sanitizePointNote
import com.lavacrafter.maptimelinetool.text.sanitizePointTitle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditPointDialog(
    point: PointEntity,
    quickTags: List<TagEntity>,
    tags: List<TagEntity>,
    selectedTagIds: Set<Long>,
    onToggleTag: (Long) -> Unit,
    onOpenTagPicker: () -> Unit,
    currentPhotoPath: String?,
    onTakePhoto: () -> Unit,
    onRetakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onViewPhoto: () -> Unit,
    onSave: (String, String, String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(point) { mutableStateOf(point.title) }
    var note by remember(point) { mutableStateOf(point.note) }

    val selectedTags = remember(tags, selectedTagIds) {
        tags.filter { selectedTagIds.contains(it.id) }
    }
    val context = LocalContext.current
    val photoFileSizeText = remember(currentPhotoPath, context) {
        val file = resolvePointPhotoFile(context, currentPhotoPath)
        file?.takeIf { it.exists() && it.isFile }?.length()?.let(::formatPhotoFileSize)
    }
    val readableSummary = remember(point) { point.toReadableSensorSummary() }
    val lookDirection = remember(point) { point.toLookDirection() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(sanitizePointTitle(title).ifBlank { point.title }, sanitizePointNote(note), currentPhotoPath) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = { Text(stringResource(R.string.dialog_title_edit_point)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = sanitizePointTitle(it) },
                    label = { Text(stringResource(R.string.dialog_title_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = sanitizePointNote(it) },
                    label = { Text(stringResource(R.string.dialog_note_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (currentPhotoPath.isNullOrBlank()) stringResource(R.string.label_photo_not_added) else stringResource(R.string.label_photo_added),
                    fontWeight = FontWeight.Medium
                )
                if (photoFileSizeText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(R.string.label_photo_size, photoFileSizeText))
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentPhotoPath.isNullOrBlank()) {
                        OutlinedButton(onClick = onTakePhoto) {
                            Text(stringResource(R.string.action_take_photo))
                        }
                    } else {
                        OutlinedButton(onClick = onRetakePhoto) {
                            Text(stringResource(R.string.action_retake_photo))
                        }
                        OutlinedButton(onClick = onRemovePhoto) {
                            Text(stringResource(R.string.action_remove_photo))
                        }
                        OutlinedButton(onClick = onViewPhoto) {
                            Text(stringResource(R.string.action_view_photo))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(R.string.label_quick_tags), fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                QuickTagGrid(
                    quickTags = quickTags,
                    selectedTagIds = selectedTagIds,
                    onTagClick = { onToggleTag(it.id) },
                    onMoreClick = onOpenTagPicker,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedTags.forEach { tag ->
                            OutlinedButton(
                                onClick = { onToggleTag(tag.id) },
                                modifier = Modifier.widthIn(max = 180.dp)
                            ) {
                                Text(
                                    text = tag.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                val hasReadableSummary = true // We always have coordinates now

                if (hasReadableSummary) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.label_sensor_readable),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val latDir = if (point.latitude >= 0) R.string.label_lat_n else R.string.label_lat_s
                    val lonDir = if (point.longitude >= 0) R.string.label_lon_e else R.string.label_lon_w
                    Text(
                        stringResource(
                            R.string.label_quick_insights_coordinates,
                            kotlin.math.abs(point.latitude),
                            stringResource(latDir),
                            kotlin.math.abs(point.longitude),
                            stringResource(lonDir)
                        )
                    )

                    readableSummary.altitudeMeters?.let { altitude ->
                        Text(stringResource(R.string.label_sensor_altitude, altitude))
                    }
                    readableSummary.ambientLightLevel?.let { level ->
                        val levelText = when (level) {
                            AmbientLightLevel.HIGH -> stringResource(R.string.label_sensor_light_level_high)
                            AmbientLightLevel.MEDIUM -> stringResource(R.string.label_sensor_light_level_medium)
                            AmbientLightLevel.LOW -> stringResource(R.string.label_sensor_light_level_low)
                        }
                        Text(stringResource(R.string.label_sensor_light_level, levelText))
                    }
                    readableSummary.noiseLevel?.let { level ->
                        val levelText = when (level) {
                            NoiseLevel.QUIET -> stringResource(R.string.label_sensor_noise_quiet)
                            NoiseLevel.RELATIVELY_QUIET -> stringResource(R.string.label_sensor_noise_relatively_quiet)
                            NoiseLevel.NOISY -> stringResource(R.string.label_sensor_noise_noisy)
                            NoiseLevel.EXTREMELY_NOISY -> stringResource(R.string.label_sensor_noise_extremely_noisy)
                        }
                        Text(stringResource(R.string.label_sensor_noise_level, levelText))
                    }
                    readableSummary.azimuthDegrees?.let { azimuth ->
                        val dirRes = when {
                            azimuth < 22.5f || azimuth >= 337.5f -> R.string.label_direction_n
                            azimuth < 67.5f -> R.string.label_direction_ne
                            azimuth < 112.5f -> R.string.label_direction_e
                            azimuth < 157.5f -> R.string.label_direction_se
                            azimuth < 202.5f -> R.string.label_direction_s
                            azimuth < 247.5f -> R.string.label_direction_sw
                            azimuth < 292.5f -> R.string.label_direction_w
                            else -> R.string.label_direction_nw
                        }
                        Text(stringResource(R.string.label_sensor_azimuth, stringResource(dirRes), azimuth))
                    }
                    readableSummary.pitchDegrees?.let { pitch ->
                        val pitchType = if (pitch >= 0) R.string.label_sensor_pitch_elevation else R.string.label_sensor_pitch_depression
                        Text(stringResource(R.string.label_sensor_pitch, stringResource(pitchType), kotlin.math.abs(pitch)))
                    }
                    if (lookDirection != null) {
                        Text(
                            stringResource(
                                R.string.label_sensor_view_direction,
                                lookDirection.azimuthDegrees,
                                lookDirection.pitchDegrees
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.label_sensor_data),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.label_lat_lon, point.latitude, point.longitude))
                LocationQualityText(point)
                SensorReadingText(
                    value = point.pressureHpa,
                    formatRes = R.string.label_sensor_pressure
                )
                SensorReadingText(
                    value = point.ambientLightLux,
                    formatRes = R.string.label_sensor_light
                )
                SensorVectorText(
                    x = point.accelerometerX,
                    y = point.accelerometerY,
                    z = point.accelerometerZ,
                    formatRes = R.string.label_sensor_accelerometer
                )
                SensorVectorText(
                    x = point.gyroscopeX,
                    y = point.gyroscopeY,
                    z = point.gyroscopeZ,
                    formatRes = R.string.label_sensor_gyroscope
                )
                SensorVectorText(
                    x = point.magnetometerX,
                    y = point.magnetometerY,
                    z = point.magnetometerZ,
                    formatRes = R.string.label_sensor_magnetometer
                )
                Text(
                    if (point.noiseDb != null) {
                        stringResource(R.string.label_sensor_noise, point.noiseDb)
                    } else {
                        stringResource(R.string.label_sensor_noise_unavailable)
                    }
                )
                if (!point.hasSensorData()) {
                    Text(stringResource(R.string.label_sensor_none))
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    )
}

@Composable
private fun SensorReadingText(value: Float?, formatRes: Int) {
    if (value == null) return
    Text(stringResource(formatRes, value))
}

@Composable
private fun LocationQualityText(point: PointEntity) {
    val summary = point.buildLocationQualitySummary() ?: return
    Text(stringResource(R.string.label_location_quality))
    Text(summary)
}

@Composable
private fun SensorVectorText(x: Float?, y: Float?, z: Float?, formatRes: Int) {
    if (x == null || y == null || z == null) return
    Text(stringResource(formatRes, x, y, z))
}

private fun PointEntity.hasSensorData(): Boolean =
    pressureHpa != null ||
        ambientLightLux != null ||
        (accelerometerX != null && accelerometerY != null && accelerometerZ != null) ||
        (gyroscopeX != null && gyroscopeY != null && gyroscopeZ != null) ||
        (magnetometerX != null && magnetometerY != null && magnetometerZ != null) ||
        noiseDb != null

@Composable
private fun PointEntity.buildLocationQualitySummary(): String? {
    val providerText = locationProvider
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::formatLocationProvider)
        ?: stringResource(R.string.label_location_provider_unknown)
    val accuracyText = locationAccuracyMeters
        ?.let { stringResource(R.string.label_location_accuracy, it) }
        ?: stringResource(R.string.label_location_accuracy_unknown)
    val ageText = formatLocationFixAge(timestamp, locationFixTimeMs)

    if (locationProvider.isNullOrBlank() && locationAccuracyMeters == null && ageText == null) {
        return null
    }

    return stringResource(
        R.string.label_location_quality_summary,
        providerText,
        accuracyText,
        ageText ?: stringResource(R.string.label_location_fix_age_unknown)
    )
}

private fun formatLocationProvider(provider: String): String {
    return when (provider.trim().lowercase(Locale.US)) {
        "gps" -> "GPS"
        "network" -> "Network"
        "fused" -> "Fused"
        "cached_overlay" -> "Cached overlay"
        else -> provider
        .split('_', '-', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.lowercase(Locale.US).replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
            }
        }
    }
}

private fun formatLocationFixAge(savedAtMs: Long, fixTimeMs: Long?): String? {
    val fixTime = fixTimeMs?.takeIf { it > 0L } ?: return null
    val deltaMs = (savedAtMs - fixTime).coerceAtLeast(0L)
    val seconds = deltaMs / 1000L
    return when {
        seconds < 60L -> "${seconds}s"
        seconds < 3600L -> "${seconds / 60L}m"
        seconds < 86400L -> "${seconds / 3600L}h"
        else -> "${seconds / 86400L}d"
    }
}

private fun formatPhotoFileSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1fKB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.2fMB", mb)
}
