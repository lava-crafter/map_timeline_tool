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
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackReader
import java.util.Locale
import java.util.zip.ZipInputStream
import com.lavacrafter.maptimelinetool.text.sanitizeTagName

object ZipImporter {
    data class ImportedTag(
        val legacyId: Long,
        val name: String
    )

    data class ImportedPointTag(
        val pointIndex: Int,
        val legacyTagId: Long
    )

    data class ImportStats(
        val points: List<Point>,
        val tags: List<ImportedTag>,
        val pointTags: List<ImportedPointTag>,
        val importedPhotoCount: Int,
        val missingPhotoCount: Int,
        val settingsJson: String? = null
    )

    fun importZip(
        inputStream: InputStream,
        savePhoto: (entryName: String, photoInput: InputStream) -> String?
    ): ImportStats {
        val photoMapping = mutableMapOf<String, String>()
        var points = emptyList<Point>()
        var pointsGeoJsonText: String? = null
        var tags = emptyList<ImportedTag>()
        var pointTags = emptyList<ImportedPointTag>()
        var settingsJson: String? = null
        ZipInputStream(inputStream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val normalizedName = normalizeEntryName(entry.name)
                if (normalizedName == null) {
                    zip.closeEntry()
                    continue
                }
                if (normalizedName.equals("points.csv", ignoreCase = true)) {
                    points = CsvImporter.parseCsv(InputStreamReader(zip, Charsets.UTF_8))
                } else if (normalizedName.equals("points.geojson", ignoreCase = true) || normalizedName.equals("data.geojson", ignoreCase = true)) {
                    pointsGeoJsonText = runCatching { zip.readBytes().toString(Charsets.UTF_8) }.getOrNull()
                } else if (normalizedName.equals("tags.csv", ignoreCase = true)) {
                    tags = parseTagsCsv(InputStreamReader(zip, Charsets.UTF_8))
                } else if (normalizedName.equals("point_tags.csv", ignoreCase = true)) {
                    pointTags = parsePointTagsCsv(InputStreamReader(zip, Charsets.UTF_8))
                } else if (normalizedName.startsWith("photos/")) {
                    val storedPath = runCatching { savePhoto(normalizedName, zip) }.getOrNull()
                    if (!storedPath.isNullOrBlank()) {
                        photoMapping[normalizePhotoRelPath(normalizedName)] = storedPath
                    }
                } else if (normalizedName.equals("settings.json", ignoreCase = true)) {
                    settingsJson = runCatching { zip.readBytes().toString(Charsets.UTF_8) }.getOrNull()
                }
                zip.closeEntry()
            }
        }

        if (points.isEmpty() && !pointsGeoJsonText.isNullOrBlank()) {
            points = GeoJsonExporter.parsePointsFromGeoJson(requireNotNull(pointsGeoJsonText)) { relPath ->
                photoMapping[normalizePhotoRelPath(relPath)]
            }
        }

        var missingPhotoCount = 0
        val resolvedPoints = points.map { point ->
            val relPath = point.photoPath?.trim().orEmpty()
            if (relPath.isEmpty()) {
                point.copy(photoPath = null)
            } else {
                val storedPath = photoMapping[normalizePhotoRelPath(relPath)]
                if (storedPath == null) {
                    missingPhotoCount++
                }
                point.copy(photoPath = storedPath)
            }
        }
        return ImportStats(
            points = resolvedPoints,
            tags = tags,
            pointTags = pointTags,
            importedPhotoCount = photoMapping.size,
            missingPhotoCount = missingPhotoCount,
            settingsJson = settingsJson
        )
    }

    private fun normalizeEntryName(name: String): String? {
        val normalized = name.replace('\\', '/').trim()
        if (normalized.isEmpty()) return null
        if (normalized.startsWith("/")) return null
        val segments = normalized.split('/')
        if (segments.any { it == ".." }) return null
        return segments.joinToString("/")
    }

    private fun normalizePhotoRelPath(path: String): String {
        val normalized = path.trim().replace('\\', '/')
        return normalized.removePrefix("./")
    }

    private fun parseTagsCsv(reader: InputStreamReader): List<ImportedTag> {
        val rows = parseCsvRows(reader)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase(Locale.US) }
        val idIndex = header.indexOf("tag_id").takeIf { it >= 0 } ?: header.indexOf("id").takeIf { it >= 0 } ?: return emptyList()
        val nameIndex = header.indexOf("name").takeIf { it >= 0 } ?: return emptyList()
        return rows.drop(1).mapNotNull { row ->
            val legacyId = row.getOrNull(idIndex)?.trim()?.toLongOrNull() ?: return@mapNotNull null
            val name = sanitizeTagName(row.getOrNull(nameIndex)?.trim().orEmpty())
            if (name.isEmpty()) return@mapNotNull null
            ImportedTag(legacyId = legacyId, name = name)
        }
    }

    private fun parsePointTagsCsv(reader: InputStreamReader): List<ImportedPointTag> {
        val rows = parseCsvRows(reader)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase(Locale.US) }
        val pointIndex = header.indexOf("point_index").takeIf { it >= 0 } ?: return emptyList()
        val tagIdIndex = header.indexOf("tag_id").takeIf { it >= 0 } ?: header.indexOf("id").takeIf { it >= 0 } ?: return emptyList()
        return rows.drop(1).mapNotNull { row ->
            val pointIdx = row.getOrNull(pointIndex)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val tagId = row.getOrNull(tagIdIndex)?.trim()?.toLongOrNull() ?: return@mapNotNull null
            ImportedPointTag(pointIndex = pointIdx, legacyTagId = tagId)
        }
    }

    private fun parseCsvRows(reader: InputStreamReader): List<List<String>> {
        val pushbackReader = PushbackReader(reader, 2)
        val rows = mutableListOf<List<String>>()
        while (true) {
            val row = readCsvRecord(pushbackReader) ?: break
            if (row.isNotEmpty()) rows.add(row)
        }
        return rows
    }

    private fun readCsvRecord(reader: PushbackReader): List<String>? {
        val record = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var anyContent = false

        while (true) {
            val intChar = reader.read()
            if (intChar == -1) {
                if (!anyContent && field.isEmpty() && record.isEmpty()) {
                    return null
                }
                record.add(field.toString())
                return record
            }

            val currentChar = intChar.toChar()
            if (!anyContent && currentChar == '\uFEFF') {
                continue
            }

            anyContent = true
            when {
                currentChar == '"' -> {
                    if (inQuotes) {
                        val next = reader.read()
                        if (next == '"'.code) {
                            field.append('"')
                        } else {
                            inQuotes = false
                            if (next != -1) reader.unread(next)
                        }
                    } else {
                        inQuotes = true
                    }
                }
                currentChar == ',' && !inQuotes -> {
                    record.add(field.toString())
                    field.clear()
                }
                currentChar == '\n' && !inQuotes -> {
                    record.add(field.toString())
                    return record
                }
                currentChar == '\r' && !inQuotes -> {
                    val next = reader.read()
                    if (next != '\n'.code && next != -1) {
                        reader.unread(next)
                    }
                    record.add(field.toString())
                    return record
                }
                else -> field.append(currentChar)
            }
        }
    }
}
