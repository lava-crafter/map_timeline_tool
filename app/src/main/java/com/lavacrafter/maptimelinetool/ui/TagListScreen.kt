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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.data.TagEntity
import com.lavacrafter.maptimelinetool.text.sanitizeTagName

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagListScreen(
    tags: List<TagEntity>,
    pinnedTagIds: Set<Long>,
    onAddTag: (String) -> Unit,
    onOpenTag: (TagEntity) -> Unit,
    onEditTag: (TagEntity) -> Unit,
    onTogglePin: (TagEntity, Boolean) -> Unit
) {
    var newTag by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = stringResource(R.string.tags_title))
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = sanitizeTagName(it) },
                label = { Text(stringResource(R.string.label_new_tag)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val name = newTag.trim()
                if (name.isNotEmpty()) {
                    onAddTag(name)
                    newTag = ""
                }
            }) {
                Text(stringResource(R.string.action_add))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tags) { tag ->
                ListItem(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onOpenTag(tag) },
                            onLongClick = { onEditTag(tag) }
                        )
                        .padding(vertical = 4.dp),
                    headlineContent = {
                        Text(
                            text = tag.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = {
                            val shouldPin = !pinnedTagIds.contains(tag.id)
                            onTogglePin(tag, shouldPin)
                        }) {
                            Text(
                                text = if (pinnedTagIds.contains(tag.id)) stringResource(R.string.tag_unlock) else stringResource(R.string.tag_lock)
                            )
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
