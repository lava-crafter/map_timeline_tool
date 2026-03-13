package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.domain.model.Point
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object ZipImporter {
    data class ImportStats(
        val points: List<Point>,
        val importedPhotoCount: Int,
        val missingPhotoCount: Int
    )

    fun importZip(
        inputStream: InputStream,
        savePhoto: (entryName: String, photoInput: InputStream) -> String?
    ): ImportStats {
        val photoMapping = mutableMapOf<String, String>()
        var points = emptyList<Point>()
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
                } else if (normalizedName.startsWith("photos/")) {
                    val storedPath = runCatching { savePhoto(normalizedName, zip) }.getOrNull()
                    if (!storedPath.isNullOrBlank()) {
                        photoMapping[normalizePhotoRelPath(normalizedName)] = storedPath
                    }
                }
                zip.closeEntry()
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
            importedPhotoCount = photoMapping.size,
            missingPhotoCount = missingPhotoCount
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
}
