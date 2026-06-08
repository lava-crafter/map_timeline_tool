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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.data.TagEntity

@Composable
fun TagDetailScreen(
    tag: TagEntity,
    points: List<PointEntity>,
    allPoints: List<PointEntity>,
    onSelectPoint: (PointEntity) -> Unit,
    onLongPressPoint: (PointEntity) -> Unit,
    onAddPointToTag: (PointEntity) -> Unit,
    onRemovePointFromTag: (PointEntity) -> Unit,
    onBack: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(stringResource(R.string.action_back))
        }
        Text(text = tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { showPicker = true }) { Text(stringResource(R.string.action_add_point_to_tag)) }
        Spacer(modifier = Modifier.height(8.dp))
        ListScreen(
            points = points,
            onSelect = onSelectPoint,
            onLongPress = onLongPressPoint,
            modifier = Modifier.weight(1f)
        )
    }

    if (showPicker) {
        TagPointPickerDialog(
            tag = tag,
            points = allPoints,
            selected = points.map { it.id }.toSet(),
            onDismiss = { showPicker = false },
            onToggle = { point, checked ->
                if (checked) onAddPointToTag(point) else onRemovePointFromTag(point)
            }
        )
    }
}

@Composable
private fun TagPointPickerDialog(
    tag: TagEntity,
    points: List<PointEntity>,
    selected: Set<Long>,
    onDismiss: () -> Unit,
    onToggle: (PointEntity, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
        title = {
            Text(
                text = stringResource(R.string.tag_manage_points_title, tag.name),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                points.forEach { point ->
                    val isChecked = selected.contains(point.id)
                    TextButton(onClick = { onToggle(point, !isChecked) }) {
                        Checkbox(checked = isChecked, onCheckedChange = { onToggle(point, it) })
                        Text(text = point.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    )
}
