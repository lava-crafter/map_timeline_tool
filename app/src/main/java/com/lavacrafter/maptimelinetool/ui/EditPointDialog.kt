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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.data.TagEntity

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

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(title, note, currentPhotoPath) }) {
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
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.dialog_title_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.dialog_note_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (currentPhotoPath.isNullOrBlank()) stringResource(R.string.label_photo_not_added) else stringResource(R.string.label_photo_added),
                    fontWeight = FontWeight.Medium
                )
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
                            OutlinedButton(onClick = { onToggleTag(tag.id) }) {
                                Text(tag.name)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.label_lat_lon, point.latitude, point.longitude))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.label_sensor_data),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
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
