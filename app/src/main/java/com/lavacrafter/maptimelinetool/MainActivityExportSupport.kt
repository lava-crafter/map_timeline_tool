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

package com.lavacrafter.maptimelinetool

import com.lavacrafter.maptimelinetool.data.TagEntity
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveDecision
import com.lavacrafter.maptimelinetool.export.ZipExporter
import com.lavacrafter.maptimelinetool.ui.AppViewModel
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal suspend fun buildPointTagNameMap(
    viewModel: AppViewModel,
    points: List<Point>
): Map<Long, List<String>> {
    val tags = viewModel.tags.first()
    val tagNamesById = tags.associate { it.id to it.name }
    val result = mutableMapOf<Long, List<String>>()
    points.forEach { point ->
        val tagNames = viewModel.getTagIdsForPoint(point.id)
            .mapNotNull { tagId -> tagNamesById[tagId] }
            .filter { it.isNotBlank() }
        if (tagNames.isNotEmpty()) {
            result[point.id] = tagNames
        }
    }
    return result
}

internal enum class ExportFileKind {
    CSV,
    GEOJSON,
    KML,
    ZIP,
    KMZ
}

internal data class PendingExportPayload(
    val points: List<Point>,
    val kind: ExportFileKind,
    val zip: Boolean,
    val zipOptions: ZipExporter.ExportOptions = ZipExporter.ExportOptions(),
    val zipTags: List<ZipExporter.TagRecord> = emptyList(),
    val pointTagIdsByPointId: Map<Long, List<Long>> = emptyMap()
)

internal data class PendingManualSaveConfirmation(
    val title: String,
    val note: String,
    val createdAt: Long,
    val selectedTags: Set<Long>,
    val photoPath: String?,
    val decision: LocationSaveDecision
)

internal fun buildStandardExportPayload(
    points: List<Point>,
    kind: ExportFileKind
): PendingExportPayload = PendingExportPayload(points = points, kind = kind, zip = false)

internal suspend fun buildZipExportPayload(
    points: List<Point>,
    includePoints: Boolean,
    includeTags: Boolean,
    includeSensors: Boolean,
    includePhotos: Boolean,
    viewModel: AppViewModel,
    tags: List<TagEntity>
): PendingExportPayload {
    val pointTagMap = mutableMapOf<Long, List<Long>>()
    val zipTags = mutableListOf<ZipExporter.TagRecord>()

    if (includeTags) {
        points.forEach { point ->
            val tagIds = viewModel.getTagIdsForPoint(point.id)
            if (tagIds.isNotEmpty()) {
                pointTagMap[point.id] = tagIds
            }
        }

        val usedTagIds = pointTagMap.values.flatten().toSet()
        tags
            .filter { usedTagIds.contains(it.id) }
            .forEach { zipTags.add(ZipExporter.TagRecord(it.id, it.name)) }
    }

    return PendingExportPayload(
        points = points,
        kind = ExportFileKind.ZIP,
        zip = true,
        zipOptions = ZipExporter.ExportOptions(
            includePoints = includePoints,
            includeTags = includeTags,
            includeSensors = includeSensors,
            includePhotos = includePhotos
        ),
        zipTags = zipTags,
        pointTagIdsByPointId = pointTagMap
    )
}

internal fun buildExportBaseName(date: Date = Date()): String {
    val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    return "map_timeline_${format.format(date)}"
}
