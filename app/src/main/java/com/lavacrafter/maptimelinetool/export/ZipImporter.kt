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
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import com.lavacrafter.maptimelinetool.text.sanitizeTagName

object ZipImporter {
    private data class PhotoMetadata(val sizeBytes: Long?, val sha256: String?)

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
        val settingsJson: String? = null,
        val manifest: BackupManifest = BackupManifest.LEGACY
    )

    fun importZip(
        inputStream: InputStream,
        limits: ZipImportLimits = ZipImportLimits(),
        savePhoto: (entryName: String, photoInput: InputStream) -> String?
    ): ImportStats {
        val photoMapping = mutableMapOf<String, String>()
        val photoMetadata = mutableMapOf<String, PhotoMetadata>()
        val importedPhotoMetadata = mutableMapOf<String, PhotoMetadata>()
        val seenEntries = mutableSetOf<String>()
        var points = emptyList<Point>()
        var hasPointsEntry = false
        var hasTagsEntry = false
        var hasPointTagsEntry = false
        var hasSettingsEntry = false
        var pointsGeoJsonText: String? = null
        var tags = emptyList<ImportedTag>()
        var pointTags = emptyList<ImportedPointTag>()
        var settingsJson: String? = null
        var manifest: BackupManifest? = null
        var unrecognizedEntryCount = 0
        var totalBytes = 0L
        ZipInputStream(inputStream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    unrecognizedEntryCount += 1
                    if (unrecognizedEntryCount > limits.maxUnrecognizedEntries) {
                        throw ZipImportLimitExceededException("Archive has too many unrecognized entries")
                    }
                    zip.closeEntry()
                    continue
                }
                val normalizedName = normalizeEntryName(entry.name)
                if (normalizedName == null || normalizedName.length > limits.maxEntryNameLength) {
                    unrecognizedEntryCount += 1
                    if (unrecognizedEntryCount > limits.maxUnrecognizedEntries) {
                        throw ZipImportLimitExceededException("Archive has too many unrecognized entries")
                    }
                    zip.closeEntry()
                    continue
                }
                val logicalName = normalizedName.lowercase(Locale.ROOT)
                val recognized = logicalName in setOf(
                    "points.csv", "points.geojson", "data.geojson", "tags.csv", "point_tags.csv",
                    "settings.json", "backup_manifest.json"
                ) || logicalName.startsWith("photos/")
                if (recognized) require(seenEntries.add(logicalName)) { "Duplicate backup entry: $normalizedName" }
                val maxEntryBytes = when {
                    normalizedName.equals("backup_manifest.json", ignoreCase = true) -> limits.maxManifestBytes
                    normalizedName.equals("settings.json", ignoreCase = true) -> limits.maxSettingsBytes
                    normalizedName.startsWith("photos/", ignoreCase = true) -> limits.maxPhotoBytes
                    else -> limits.maxDataEntryBytes
                }
                val entryInput = BoundedZipEntryInputStream(
                    source = zip,
                    maxEntryBytes = maxEntryBytes,
                    totalBytes = { totalBytes },
                    addTotalBytes = { count -> totalBytes += count },
                    maxTotalBytes = limits.maxTotalBytes
                )
                if (normalizedName.equals("points.csv", ignoreCase = true)) {
                    hasPointsEntry = true
                    points = CsvImporter.parseCsv(
                        InputStreamReader(entryInput, Charsets.UTF_8),
                        strictRows = true,
                        onPhotoMetadata = { relPath, size, sha ->
                            val key = normalizePhotoRelPath(relPath)
                            val metadata = PhotoMetadata(size, sha)
                            val previous = photoMetadata.putIfAbsent(key, metadata)
                            require(previous == null || previous == metadata) { "Conflicting photo metadata: $relPath" }
                        }
                    )
                } else if (normalizedName.equals("points.geojson", ignoreCase = true) || normalizedName.equals("data.geojson", ignoreCase = true)) {
                    hasPointsEntry = true
                    pointsGeoJsonText = entryInput.readTextOrNull()
                } else if (normalizedName.equals("tags.csv", ignoreCase = true)) {
                    hasTagsEntry = true
                    tags = parseTagsCsv(InputStreamReader(entryInput, Charsets.UTF_8))
                } else if (normalizedName.equals("point_tags.csv", ignoreCase = true)) {
                    hasPointTagsEntry = true
                    pointTags = parsePointTagsCsv(InputStreamReader(entryInput, Charsets.UTF_8))
                } else if (normalizedName.startsWith("photos/", ignoreCase = true)) {
                    val photoName = "photos/" + normalizedName.substringAfter('/')
                    val digest = MessageDigest.getInstance("SHA-256")
                    val digestInput = DigestInputStream(entryInput, digest)
                    val storedPath = savePhoto(photoName, digestInput)
                    digestInput.drain()
                    importedPhotoMetadata[normalizePhotoRelPath(photoName)] = PhotoMetadata(
                        entryInput.entryBytes,
                        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                    )
                    if (!storedPath.isNullOrBlank()) {
                        photoMapping[normalizePhotoRelPath(photoName)] = storedPath
                    }
                } else if (normalizedName.equals("settings.json", ignoreCase = true)) {
                    hasSettingsEntry = true
                    settingsJson = entryInput.readTextOrNull()
                } else if (normalizedName.equals("backup_manifest.json", ignoreCase = true)) {
                    manifest = BackupManifest.parse(entryInput.readText())
                } else {
                    unrecognizedEntryCount += 1
                    if (unrecognizedEntryCount > limits.maxUnrecognizedEntries) {
                        throw ZipImportLimitExceededException("Archive has too many unrecognized entries")
                    }
                }
                entryInput.drain()
                zip.closeEntry()
            }
        }

        if (points.isEmpty() && !pointsGeoJsonText.isNullOrBlank()) {
            points = GeoJsonExporter.parsePointsFromGeoJson(requireNotNull(pointsGeoJsonText)) { relPath ->
                photoMapping[normalizePhotoRelPath(relPath)]
            }
        }

        manifest?.takeIf { it.version >= 2 }?.let { declared ->
            val sections = declared.sections
            require(sections.points == hasPointsEntry) { "Points section does not match backup manifest" }
            require(sections.tags == (hasTagsEntry && hasPointTagsEntry) && hasTagsEntry == hasPointTagsEntry) {
                "Tags section does not match backup manifest"
            }
            require(sections.photos || importedPhotoMetadata.isEmpty()) { "Unexpected photos in backup" }
            require(sections.settings == hasSettingsEntry) { "Settings section does not match backup manifest" }
            declared.counts.points?.let { require(it == points.size) { "Incorrect backup point count" } }
            declared.counts.tags?.let { require(it == tags.size) { "Incorrect backup tag count" } }
            declared.counts.photos?.let { require(it == importedPhotoMetadata.size) { "Incorrect backup photo count" } }
        }
        require(pointTags.all { it.pointIndex in points.indices && tags.any { tag -> tag.legacyId == it.legacyTagId } }) {
            "Invalid point-tag reference in backup"
        }
        photoMetadata.forEach { (path, declared) ->
            importedPhotoMetadata[path]?.let { actual ->
                require(declared.sha256 == null || declared.sha256.equals(actual.sha256, ignoreCase = true)) {
                    "Photo hash mismatch: $path"
                }
                require(declared.sizeBytes == null || declared.sizeBytes == actual.sizeBytes) {
                    "Photo size mismatch: $path"
                }
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
            settingsJson = settingsJson,
            manifest = manifest ?: BackupManifest.LEGACY
        )
    }

    private fun normalizeEntryName(name: String): String? {
        val normalized = name.replace('\\', '/').trim()
        if (normalized.isEmpty()) return null
        if (normalized.startsWith("/")) return null
        val segments = normalized.split('/').filterNot { it == "." || it.isEmpty() }
        if (segments.any { it == ".." }) return null
        return segments.joinToString("/")
    }

    private fun normalizePhotoRelPath(path: String): String {
        val normalized = path.trim().replace('\\', '/')
        return normalized.removePrefix("./")
    }

    private class BoundedZipEntryInputStream(
        private val source: InputStream,
        private val maxEntryBytes: Long,
        private val totalBytes: () -> Long,
        private val addTotalBytes: (Long) -> Unit,
        private val maxTotalBytes: Long
    ) : InputStream() {
        var entryBytes = 0L
            private set

        override fun read(): Int {
            val value = source.read()
            if (value >= 0) account(1L)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = source.read(buffer, offset, length)
            if (read > 0) account(read.toLong())
            return read
        }

        override fun close() = Unit

        fun readText(): String = readBytes().toString(Charsets.UTF_8)

        fun readTextOrNull(): String? = try {
            readText()
        } catch (error: ZipImportLimitExceededException) {
            throw error
        } catch (_: Exception) {
            null
        }

        fun drain() {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (read(buffer) >= 0) {
                // Account for every decompressed byte, including ignored entries.
            }
        }

        private fun account(count: Long) {
            entryBytes += count
            if (entryBytes > maxEntryBytes) {
                throw ZipImportLimitExceededException("Archive entry exceeds the configured size budget")
            }
            val nextTotal = totalBytes() + count
            if (nextTotal > maxTotalBytes) {
                throw ZipImportLimitExceededException("Archive exceeds the configured total size budget")
            }
            addTotalBytes(count)
        }
    }

    private fun InputStream.drain() {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (read(buffer) >= 0) { /* Finish hashing bytes not consumed by savePhoto. */ }
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
