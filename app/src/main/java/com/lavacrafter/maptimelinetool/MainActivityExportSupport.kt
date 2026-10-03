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

import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.repository.PointTagRelation
import com.lavacrafter.maptimelinetool.export.ZipExporter
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal suspend fun buildPointTagNameMap(
    repository: PointRepositoryGateway,
    points: List<Point>
): Map<Long, List<String>> = repository.inTransaction {
    val tags = repository.getAllTags()
    val tagNamesById = tags.associate { it.id to it.name }
    selectedPointTagIds(points, repository.getAllPointTagRelations())
        .mapValues { (_, tagIds) -> tagIds.mapNotNull { tagNamesById[it]?.takeIf(String::isNotBlank) } }
        .filterValues { it.isNotEmpty() }
}

private fun selectedPointTagIds(
    points: List<Point>,
    relations: List<PointTagRelation>
): Map<Long, List<Long>> {
    val selectedIds = points.mapTo(HashSet(points.size)) { it.id }
    return relations.filter { it.pointId in selectedIds }.groupBy({ it.pointId }, { it.tagId })
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

/** A selected-point ZIP never carries device settings; Full Backup has its own writer. */
internal fun PendingExportPayload.writeOrdinaryZip(
    outputStream: OutputStream,
    resolvePhotoFile: (String) -> File?,
    appVersion: String?
): ZipExporter.ExportStats {
    check(zip && kind == ExportFileKind.ZIP)
    return ZipExporter.export(
        points = points,
        outputStream = outputStream,
        resolvePhotoFile = resolvePhotoFile,
        options = zipOptions,
        tags = zipTags,
        pointTagIdsByPointId = pointTagIdsByPointId,
        appVersion = appVersion
    )
}

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
    repository: PointRepositoryGateway
): PendingExportPayload {
    val (pointTagMap, zipTags) = if (includeTags) {
        repository.inTransaction {
            val tags = repository.getAllTags()
            val pointTagMap = selectedPointTagIds(points, repository.getAllPointTagRelations())
            val usedTagIds = pointTagMap.values.flatten().toSet()
            pointTagMap to tags.filter { it.id in usedTagIds }.map { ZipExporter.TagRecord(it.id, it.name) }
        }
    } else {
        emptyMap<Long, List<Long>>() to emptyList<ZipExporter.TagRecord>()
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
