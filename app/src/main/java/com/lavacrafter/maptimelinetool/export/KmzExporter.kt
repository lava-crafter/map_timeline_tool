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
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object KmzExporter {
    data class ExportStats(
        val pointCount: Int,
        val photoCount: Int
    )

    fun export(
        points: List<Point>,
        outputStream: OutputStream,
        resolvePhotoFile: (String) -> File?,
        includeSensors: Boolean = true,
        includePhotos: Boolean = true,
        pointTagNamesByPointId: Map<Long, List<String>> = emptyMap()
    ): ExportStats {
        val photoEntries = mutableMapOf<String, File>()
        val photoRelPathByPointId = mutableMapOf<Long, String>()

        if (includePhotos) {
            points.forEach { point ->
                val sourcePath = point.photoPath?.trim().orEmpty()
                if (sourcePath.isEmpty()) return@forEach
                val photoFile = resolvePhotoFile(sourcePath)
                if (photoFile == null || !photoFile.exists() || !photoFile.isFile || !photoFile.canRead()) return@forEach

                val relPath = buildSafePhotoRelPath(photoFile.name, photoEntries, photoFile)
                photoEntries.putIfAbsent(relPath, photoFile)
                photoRelPathByPointId[point.id] = relPath
            }
        }

        ZipOutputStream(outputStream.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("doc.kml"))
            KmlExporter.writeKml(
                points = points,
                outputStream = zip,
                documentName = "Map Timeline KMZ Export",
                includeSensors = includeSensors,
                pointTagNamesByPointId = pointTagNamesByPointId,
                photoRelPathResolver = { point -> photoRelPathByPointId[point.id] }
            )
            zip.closeEntry()

            photoEntries.forEach { (relPath, photoFile) ->
                zip.putNextEntry(ZipEntry(relPath))
                photoFile.inputStream().buffered().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }

        return ExportStats(
            pointCount = points.size,
            photoCount = photoEntries.size
        )
    }

    private fun buildSafePhotoRelPath(
        originalName: String,
        existingEntries: Map<String, File>,
        photoFile: File
    ): String {
        val baseName = originalName
            .ifBlank { "photo.bin" }
            .replace(Regex("""[/\\]|\.{2,}"""), "_")
            .replace(Regex("""[\u0000-\u001F\u007F]"""), "_")

        var relPath = "photos/$baseName"
        var suffix = 1
        while (existingEntries.containsKey(relPath) && existingEntries[relPath]?.absolutePath != photoFile.absolutePath) {
            val dotIndex = baseName.lastIndexOf('.')
            val candidateName = if (dotIndex > 0) {
                "${baseName.substring(0, dotIndex)}_$suffix${baseName.substring(dotIndex)}"
            } else {
                "${baseName}_$suffix"
            }
            relPath = "photos/$candidateName"
            suffix++
        }
        return relPath
    }
}
