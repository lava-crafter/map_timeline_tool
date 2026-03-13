package com.lavacrafter.maptimelinetool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.data.TagEntity

sealed interface QuickTagSlot

object MoreTagSlot : QuickTagSlot

object EmptyTagSlot : QuickTagSlot

data class TagSlot(val tag: TagEntity) : QuickTagSlot

@Composable
fun QuickTagGrid(
    quickTags: List<TagEntity>,
    selectedTagIds: Set<Long>,
    onTagClick: (TagEntity) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quickSlots = remember(quickTags) {
        buildList {
            quickTags.take(3).forEach { add(TagSlot(it)) }
            while (size < 3) add(EmptyTagSlot)
            add(MoreTagSlot)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(2) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickTagCell(
                    slot = quickSlots[row * 2],
                    selectedTagIds = selectedTagIds,
                    onTagClick = onTagClick,
                    onMoreClick = onMoreClick,
                    modifier = Modifier.weight(1f)
                )
                QuickTagCell(
                    slot = quickSlots[row * 2 + 1],
                    selectedTagIds = selectedTagIds,
                    onTagClick = onTagClick,
                    onMoreClick = onMoreClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickTagCell(
    slot: QuickTagSlot,
    selectedTagIds: Set<Long>,
    onTagClick: (TagEntity) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier
) {
    val borderColor = MaterialTheme.colorScheme.outline
    val content = when (slot) {
        is TagSlot -> slot.tag.name
        MoreTagSlot -> stringResource(R.string.label_more_tags)
        EmptyTagSlot -> ""
    }
    val isSelected = slot is TagSlot && selectedTagIds.contains(slot.tag.id)
    val backgroundColor = when (slot) {
        is TagSlot -> if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = modifier
            .aspectRatio(2f)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable {
                when (slot) {
                    is TagSlot -> onTagClick(slot.tag)
                    MoreTagSlot -> onMoreClick()
                    else -> Unit
                }
            }
            .padding(8.dp)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = content,
            textAlign = TextAlign.Center,
            color = when {
                slot is TagSlot && isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun TagSelectionDialog(
    tags: List<TagEntity>,
    selectedTagIds: Set<Long>,
    onToggleTag: (Long) -> Unit,
    onCreateTag: (String, (Long) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var newTagName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = { Text(stringResource(R.string.label_tags)) },
        text = {
            Column(modifier = Modifier.height(340.dp)) {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text(stringResource(R.string.label_new_tag)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val trimmed = newTagName.trim()
                    if (trimmed.isNotEmpty()) {
                        onCreateTag(trimmed) { id ->
                            onToggleTag(id)
                        }
                        newTagName = ""
                    }
                }) {
                    Text(stringResource(R.string.action_add))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(tags) { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleTag(tag.id) }
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedTagIds.contains(tag.id),
                                onCheckedChange = { onToggleTag(tag.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tag.name)
                        }
                        Divider()
                    }
                }
            }
        }
    )
}
