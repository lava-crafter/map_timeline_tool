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

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.data.TagEntity
import com.lavacrafter.maptimelinetool.text.sanitizePointNote
import com.lavacrafter.maptimelinetool.text.sanitizePointTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPointDialog(
    createdAt: Long,
    quickTags: List<TagEntity>,
    tags: List<TagEntity>,
    selectedTagIds: Set<Long>,
    title: String,
    note: String,
    remainingSeconds: Int,
    isCountdownPaused: Boolean,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onUserTyping: () -> Unit,
    onToggleTag: (Long) -> Unit,
    onOpenTagPicker: () -> Unit,
    hasPhoto: Boolean,
    onTakePhoto: () -> Unit,
    onRetakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onViewPhoto: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long, Set<Long>) -> Unit
) {
    val defaultTitle = remember(createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(createdAt))
    }

    val selectedTags = remember(tags, selectedTagIds) {
        tags.filter { selectedTagIds.contains(it.id) }
    }


    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(if (title.isBlank()) defaultTitle else title, note, createdAt, selectedTagIds) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = {
            Text(
                stringResource(R.string.dialog_title_new_point),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        onTitleChange(sanitizePointTitle(it))
                        onUserTyping()
                    },
                    label = { Text(stringResource(R.string.dialog_title_label)) },
                    placeholder = { Text(defaultTitle) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        onNoteChange(sanitizePointNote(it))
                        onUserTyping()
                    },
                    label = { Text(stringResource(R.string.dialog_note_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (hasPhoto) stringResource(R.string.label_photo_added) else stringResource(R.string.label_photo_not_added),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasPhoto) {
                        OutlinedButton(onClick = onRetakePhoto) {
                            Text(stringResource(R.string.action_retake_photo))
                        }
                        OutlinedButton(onClick = onRemovePhoto) {
                            Text(stringResource(R.string.action_remove_photo))
                        }
                        OutlinedButton(onClick = onViewPhoto) {
                            Text(stringResource(R.string.action_view_photo))
                        }
                    } else {
                        OutlinedButton(onClick = onTakePhoto) {
                            Text(stringResource(R.string.action_take_photo))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val safeSeconds = remainingSeconds.coerceAtLeast(0)
                val countdownLabel = if (isCountdownPaused) {
                    stringResource(R.string.label_auto_save_paused, safeSeconds)
                } else {
                    stringResource(R.string.label_auto_save_countdown, safeSeconds)
                }
                Text(
                    text = countdownLabel,
                    color = if (isCountdownPaused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.label_quick_tags),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    Text(text = stringResource(R.string.label_selected_tags))
                    Spacer(modifier = Modifier.height(4.dp))
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
            }
        }
    )
}
