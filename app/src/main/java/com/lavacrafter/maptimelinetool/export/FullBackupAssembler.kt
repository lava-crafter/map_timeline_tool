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

package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Read a consistent database snapshot, including tags that are not attached to any point. */
class FullBackupAssembler(private val repository: PointRepositoryGateway) {
    suspend fun assemble(): FullBackupPayload = repository.inTransaction {
        val points = repository.getAll()
        val tags = repository.getAllTags()
        val relations = repository.getAllPointTagRelations()
        val pointIds = points.mapTo(HashSet(points.size)) { it.id }
        val tagIds = tags.mapTo(HashSet(tags.size)) { it.id }
        check(relations.all { it.pointId in pointIds && it.tagId in tagIds }) {
            "Cannot export a backup with orphan point-tag relations"
        }
        FullBackupPayload(
            points = points,
            tags = tags.map { ZipExporter.TagRecord(it.id, it.name) },
            pointTagIdsByPointId = relations.groupBy({ it.pointId }, { it.tagId })
        )
    }
}

data class FullBackupPayload(
    val points: List<Point>,
    val tags: List<ZipExporter.TagRecord>,
    val pointTagIdsByPointId: Map<Long, List<Long>>
) {
    fun writeZip(
        outputStream: OutputStream,
        resolvePhotoFile: (String) -> File?,
        settingsJson: String,
        appVersion: String?
    ): ZipExporter.ExportStats {
        require(settingsJson.isNotBlank()) { "Full backup requires portable settings" }
        return ZipExporter.export(
            points = points,
            outputStream = outputStream,
            resolvePhotoFile = { path ->
                resolvePhotoFile(path)?.takeIf { it.isFile && it.canRead() }
                    ?: throw IOException("Backup photo is missing or unreadable: $path")
            },
            options = ZipExporter.ExportOptions(
                includePoints = true,
                includeTags = true,
                includeSensors = true,
                includePhotos = true
            ),
            tags = tags,
            pointTagIdsByPointId = pointTagIdsByPointId,
            settingsJsonProvider = { settingsJson },
            appVersion = appVersion
        )
    }
}
