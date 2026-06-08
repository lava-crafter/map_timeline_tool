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

import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.lavacrafter.maptimelinetool.data.PointEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class ExportKind {
    object All : ExportKind()
    data class ByTag(val tagId: Long) : ExportKind()
    data class ByTime(val fromMs: Long, val toMs: Long) : ExportKind()
    data class Manual(val ids: List<Long>) : ExportKind()
}

data class ExportSelection(val includeTags: Boolean, val kind: ExportKind)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreens(
    points: List<PointEntity>,
    tags: List<com.lavacrafter.maptimelinetool.data.TagEntity>,
    onSelectExport: (ExportSelection) -> Unit,
    onBack: () -> Unit
) {
    var route by remember { mutableStateOf(0) }
    var includeTags by remember { mutableStateOf(true) }
        @OptIn(ExperimentalMaterial3Api::class)
        when (route) {
        0 -> ExportOptionsScreen(includeTags = includeTags, onIncludeTagsChange = { includeTags = it }, onNext = { route = 1 }, onBack = onBack)
        1 -> SubsetMenuScreen(
            onChooseByTag = { route = 2 },
            onChooseByTime = { route = 3 },
            onChooseManual = { route = 4 },
            onChooseAll = { onSelectExport(ExportSelection(includeTags, ExportKind.All)) },
            onBack = { route = 0 }
        )
        2 -> TagPickerScreen(tags = tags, onSelectTag = { tagId -> onSelectExport(ExportSelection(includeTags, ExportKind.ByTag(tagId))) }, onBack = { route = 1 })
        3 -> TimeRangePickerScreen(onExport = { fromMs, toMs -> onSelectExport(ExportSelection(includeTags, ExportKind.ByTime(fromMs, toMs))) }, onBack = { route = 1 })
        4 -> ManualSelectScreen(points = points, onExport = { ids -> onSelectExport(ExportSelection(includeTags, ExportKind.Manual(ids))) }, onBack = { route = 1 })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportOptionsScreen(includeTags: Boolean, onIncludeTagsChange: (Boolean) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.export_chooser_title)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeTags, onCheckedChange = onIncludeTagsChange)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = stringResource(R.string.export_option_points_and_tags))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = true, onClick = {})
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = stringResource(R.string.export_option_points_only))
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_next)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubsetMenuScreen(onChooseByTag: () -> Unit, onChooseByTime: () -> Unit, onChooseManual: () -> Unit, onChooseAll: () -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.export_subset_title)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onChooseByTag, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_by_tag)) }
            Button(onClick = onChooseByTime, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_by_time)) }
            Button(onClick = onChooseManual, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_manual_select)) }
            Button(onClick = onChooseAll, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_all)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPickerScreen(tags: List<com.lavacrafter.maptimelinetool.data.TagEntity>, onSelectTag: (Long) -> Unit, onBack: () -> Unit) {
    var selectedTag by remember { mutableStateOf<com.lavacrafter.maptimelinetool.data.TagEntity?>(null) }
    BackHandler { onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.export_by_tag)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tags.forEach { tag ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedTag == tag, onClick = { selectedTag = tag })
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = tag.name)
                }
            }
            Button(onClick = { if (selectedTag != null) onSelectTag(selectedTag!!.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_export_csv)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangePickerScreen(onExport: (Long, Long) -> Unit, onBack: () -> Unit) {
    val zoneId = remember { ZoneId.systemDefault() }
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()) }
    var startDate by remember { mutableStateOf(LocalDate.now(zoneId)) }
    var endDate by remember { mutableStateOf(LocalDate.now(zoneId)) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val toLocalDate: (Long) -> LocalDate = { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    }
    val startOfDayMillis: (LocalDate) -> Long = { date ->
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
    val endOfDayMillis: (LocalDate) -> Long = { date ->
        date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
    }
    BackHandler { onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.export_by_time)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.export_start_date))
                TextButton(onClick = { showStartPicker = true }) {
                    Text(text = startDate.format(formatter))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.export_end_date))
                TextButton(onClick = { showEndPicker = true }) {
                    Text(text = endDate.format(formatter))
                }
            }
            Button(
                onClick = { onExport(startOfDayMillis(startDate), endOfDayMillis(endDate)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !endDate.isBefore(startDate)
            ) {
                Text(stringResource(R.string.action_export_csv))
            }
        }
    }

    if (showStartPicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        startDate = toLocalDate(millis)
                        if (endDate.isBefore(startDate)) endDate = startDate
                    }
                    showStartPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }

    if (showEndPicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        endDate = toLocalDate(millis)
                        if (endDate.isBefore(startDate)) startDate = endDate
                    }
                    showEndPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualSelectScreen(points: List<PointEntity>, onExport: (List<Long>) -> Unit, onBack: () -> Unit) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    BackHandler { onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.export_manual_select)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            points.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selectedIds.contains(p.id), onCheckedChange = { checked ->
                        selectedIds = if (checked) selectedIds + p.id else selectedIds - p.id
                    })
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = p.title)
                }
            }
            Button(onClick = { if (selectedIds.isNotEmpty()) onExport(selectedIds.toList()) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_export_csv)) }
        }
    }
}
