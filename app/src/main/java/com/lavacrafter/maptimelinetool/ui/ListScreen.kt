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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListScreen(
    points: List<PointEntity>,
    onSelect: (PointEntity) -> Unit,
    onLongPress: (PointEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayOrderById = remember(points) { buildTodayOrder(points) }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val calendar = remember { Calendar.getInstance() }
    LazyColumn(modifier = modifier.fillMaxSize().padding(8.dp)) {
        items(points) { p ->
            val latLonText = stringResource(R.string.label_lat_lon, p.latitude, p.longitude)
            val noteText = p.note
            val orderText = todayOrderById[p.id]?.toString()
            val localTimeText = timeFormat.format(Date(p.timestamp))
            val isToday = isSameDay(calendar, p.timestamp)
            ListItem(
                modifier = Modifier
                    .background(if (isToday) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                    .combinedClickable(
                        onClick = { onSelect(p) },
                        onLongClick = { onLongPress(p) }
                    )
                    .padding(vertical = 4.dp),
                headlineContent = {
                    Text(
                        text = p.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    val detail = if (noteText.isBlank()) {
                        "${localTimeText}\n${latLonText}"
                    } else {
                        "${noteText}\n${localTimeText}\n${latLonText}"
                    }
                    Text(
                        text = detail,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingContent = {
                    if (!orderText.isNullOrBlank()) {
                        Text(
                            text = orderText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

private fun isSameDay(calendar: Calendar, timestamp: Long): Boolean {
    val now = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    return calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        && calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
}

