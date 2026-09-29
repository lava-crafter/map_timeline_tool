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
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

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
        var entryCount = 0
        var photoCount = 0
        ZipInputStream(inputStream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entryCount > limits.maxEntries) throw ZipImportLimitExceededException("Too many archive entries")
                val normalizedName = normalizeEntryName(entry.name)
                require(normalizedName != null) { "Unsafe archive path: ${entry.name}" }
                if (normalizedName.length > limits.maxEntryNameLength) {
                    throw ZipImportLimitExceededException("Archive entry name too long")
                }
                if (entry.isDirectory) {
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
                if (logicalName.startsWith("photos/")) {
                    require(normalizedName.substringAfter('/').isNotBlank() && normalizedName.count { it == '/' } == 1) {
                        "Invalid photo entry name"
                    }
                    if (++photoCount > limits.maxPhotos) throw ZipImportLimitExceededException("Too many photos in backup")
                }
                val maxEntryBytes = when {
                    normalizedName.equals("backup_manifest.json", ignoreCase = true) -> limits.maxManifestBytes
                    normalizedName.equals("settings.json", ignoreCase = true) -> limits.maxSettingsBytes
                    normalizedName.equals("points.geojson", ignoreCase = true) || normalizedName.equals("data.geojson", ignoreCase = true) -> limits.maxGeoJsonBytes
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
                        maxPoints = limits.maxPoints,
                        onPhotoMetadata = { relPath, size, sha ->
                            val key = normalizePhotoRelPath(relPath)
                            val metadata = PhotoMetadata(size, sha)
                            val previous = photoMetadata.putIfAbsent(key, metadata)
                            require(previous == null || previous == metadata) { "Conflicting photo metadata: $relPath" }
                        }
                    )
                } else if (normalizedName.equals("points.geojson", ignoreCase = true) || normalizedName.equals("data.geojson", ignoreCase = true)) {
                    hasPointsEntry = true
                    pointsGeoJsonText = entryInput.readText()
                } else if (normalizedName.equals("tags.csv", ignoreCase = true)) {
                    hasTagsEntry = true
                    tags = MetadataCsvParser.parseTags(InputStreamReader(entryInput, Charsets.UTF_8),
                        MetadataCsvParser.Limits(maxRecords = limits.maxTags))
                } else if (normalizedName.equals("point_tags.csv", ignoreCase = true)) {
                    hasPointTagsEntry = true
                    pointTags = MetadataCsvParser.parsePointTags(InputStreamReader(entryInput, Charsets.UTF_8),
                        MetadataCsvParser.Limits(maxRecords = limits.maxRelations))
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
                    settingsJson = entryInput.readText()
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
        if (points.size > limits.maxPoints) throw ZipImportLimitExceededException("Too many points in backup")

        manifest?.takeIf { it.version >= 2 }?.let { declared ->
            val sections = declared.sections
            require(sections.points == hasPointsEntry) { "Points section does not match backup manifest" }
            require(!sections.points || "points.csv" in seenEntries) { "Canonical backup must contain points.csv" }
            require(pointsGeoJsonText == null) { "Canonical backup must not contain GeoJSON" }
            require(sections.tags == (hasTagsEntry && hasPointTagsEntry) && hasTagsEntry == hasPointTagsEntry) {
                "Tags section does not match backup manifest"
            }
            require(sections.photos || importedPhotoMetadata.isEmpty()) { "Unexpected photos in backup" }
            require(sections.photos || photoMetadata.isEmpty()) { "Unexpected photo metadata in backup" }
            require(sections.settings == hasSettingsEntry) { "Settings section does not match backup manifest" }
            declared.counts.points?.let { require(it == points.size) { "Incorrect backup point count" } }
            declared.counts.tags?.let { require(it == tags.size) { "Incorrect backup tag count" } }
            declared.counts.photos?.let { require(it == importedPhotoMetadata.size) { "Incorrect backup photo count" } }
            require(tags.map { it.legacyId }.toSet().size == tags.size) { "Duplicate tag IDs in backup" }
            require(pointTags.map { it.pointIndex to it.legacyTagId }.toSet().size == pointTags.size) { "Duplicate relations in backup" }
            require(points.all { it.photoPath.isNullOrBlank() || photoMetadata.containsKey(normalizePhotoRelPath(it.photoPath)) }) {
                "Point photo is missing metadata"
            }
            require(photoMetadata.values.all { it.sizeBytes != null && it.sha256 != null }) {
                "Canonical photo is missing its size or checksum"
            }
            if (sections.points) require(photoMetadata.keys == importedPhotoMetadata.keys) {
                "Missing or unreferenced backup photos"
            }
        }
        val tagIds = tags.mapTo(HashSet(tags.size)) { it.legacyId }
        require(pointTags.all { it.pointIndex in points.indices && it.legacyTagId in tagIds }) {
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
        if ('\\' in name || name != name.trim()) return null
        val normalized = name
        if (normalized.isEmpty()) return null
        if (normalized.startsWith("/")) return null
        val segments = normalized.split('/')
        if (segments.any { it == ".." || it == "." || it.isEmpty() }) return null
        return normalized
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
            if (nextTotal < 0 || nextTotal > maxTotalBytes) {
                throw ZipImportLimitExceededException("Archive exceeds the configured total size budget")
            }
            addTotalBytes(count)
        }
    }

    private fun InputStream.drain() {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (read(buffer) >= 0) { /* Finish hashing bytes not consumed by savePhoto. */ }
    }

}
