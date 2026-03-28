package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.domain.model.Point
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

object ZipExporter {
    data class ExportOptions(
        val includePoints: Boolean = true,
        val includeTags: Boolean = true,
        val includeSensors: Boolean = true,
        val includePhotos: Boolean = true
    )

    data class TagRecord(
        val id: Long,
        val name: String
    )

    data class ExportStats(
        val pointCount: Int,
        val photoCount: Int
    )

    private data class PointPhotoMeta(
        val relPath: String? = null,
        val mime: String? = null,
        val sizeBytes: Long? = null,
        val sha256: String? = null
    )

    fun export(
        points: List<Point>,
        outputStream: OutputStream,
        resolvePhotoFile: (String) -> File?,
        options: ExportOptions = ExportOptions(),
        tags: List<TagRecord> = emptyList(),
        pointTagIdsByPointId: Map<Long, List<Long>> = emptyMap(),
        settingsJsonProvider: (() -> String?)? = null,
        appVersion: String? = null
    ): ExportStats {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val includeTagsInArchive = options.includePoints && options.includeTags
        val photoEntries = mutableMapOf<String, File>()
        val pointPhotoMeta = points.map { point ->
            buildPhotoMeta(
                point = point,
                resolvePhotoFile = resolvePhotoFile,
                photoEntries = photoEntries,
                includePhotos = options.includePhotos
            )
        }

        ZipOutputStream(outputStream.buffered()).use { zip ->
            if (options.includePoints) {
                zip.putNextEntry(ZipEntry("points.csv"))
                val writer = OutputStreamWriter(zip, Charsets.UTF_8)
                CsvExporter.writeRow(writer, CsvExporter.headers)
                points.forEachIndexed { index, point ->
                    val meta = pointPhotoMeta.getOrNull(index) ?: PointPhotoMeta()
                    val sensorPressure = point.pressureHpa?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    val sensorLight = point.ambientLightLux?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    val sensorAccelX = point.accelerometerX?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    val sensorAccelY = point.accelerometerY?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    val sensorAccelZ = point.accelerometerZ?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    val sensorGyroX = point.gyroscopeX?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    val sensorGyroY = point.gyroscopeY?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    val sensorGyroZ = point.gyroscopeZ?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    val sensorMagX = point.magnetometerX?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    val sensorMagY = point.magnetometerY?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    val sensorMagZ = point.magnetometerZ?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    val sensorNoise = point.noiseDb?.toString().orEmpty().takeIf { options.includeSensors }.orEmpty()
                    CsvExporter.writeRow(
                        writer,
                        listOf(
                            point.title,
                            point.note,
                            point.latitude.toString(),
                            point.longitude.toString(),
                            sdf.format(Date(point.timestamp)),
                            sensorPressure,
                            sensorLight,
                            sensorAccelX,
                            sensorAccelY,
                            sensorAccelZ,
                            sensorGyroX,
                            sensorGyroY,
                            sensorGyroZ,
                            sensorMagX,
                            sensorMagY,
                            sensorMagZ,
                            sensorNoise,
                            meta.relPath.orEmpty(),
                            meta.mime.orEmpty(),
                            meta.sizeBytes?.toString().orEmpty(),
                            meta.sha256.orEmpty()
                        )
                    )
                }
                writer.flush()
                zip.closeEntry()
            }

            if (includeTagsInArchive) {
                val pointsWithIndex = points.withIndex().associate { (index, point) -> point.id to index }
                val usedTagIds = pointTagIdsByPointId
                    .filterKeys { pointsWithIndex.containsKey(it) }
                    .values
                    .flatten()
                    .toSet()
                val filteredTags = tags.filter { usedTagIds.contains(it.id) }

                zip.putNextEntry(ZipEntry("tags.csv"))
                val tagWriter = OutputStreamWriter(zip, Charsets.UTF_8)
                CsvExporter.writeRow(tagWriter, listOf("tag_id", "name"))
                filteredTags.forEach { tag ->
                    CsvExporter.writeRow(tagWriter, listOf(tag.id.toString(), tag.name))
                }
                tagWriter.flush()
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("point_tags.csv"))
                val pointTagWriter = OutputStreamWriter(zip, Charsets.UTF_8)
                CsvExporter.writeRow(pointTagWriter, listOf("point_index", "tag_id"))
                points.forEachIndexed { pointIndex, point ->
                    val tagIds = pointTagIdsByPointId[point.id].orEmpty()
                    tagIds.filter { usedTagIds.contains(it) }.forEach { tagId ->
                        CsvExporter.writeRow(pointTagWriter, listOf(pointIndex.toString(), tagId.toString()))
                    }
                }
                pointTagWriter.flush()
                zip.closeEntry()
            }

            photoEntries.forEach { (relPath, file) ->
                zip.putNextEntry(ZipEntry(relPath))
                file.inputStream().buffered().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }

            val settingsJson = settingsJsonProvider?.invoke()?.takeIf { it.isNotBlank() }
            val manifestJson = JSONObject().apply {
                put("backup_version", 1)
                put("created_at_utc", sdf.format(Date()))
                put("app_version", appVersion.orEmpty())
                put("sections", JSONObject().apply {
                    put("points", options.includePoints)
                    put("tags", includeTagsInArchive)
                    put("photos", options.includePhotos)
                    put("settings", settingsJson != null)
                })
                put("counts", JSONObject().apply {
                    put("points", if (options.includePoints) points.size else 0)
                    put("tags", if (includeTagsInArchive) tags.size else 0)
                    put("photos", photoEntries.size)
                })
            }.toString()
            zip.putNextEntry(ZipEntry("backup_manifest.json"))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            if (settingsJson != null) {
                zip.putNextEntry(ZipEntry("settings.json"))
                zip.write(settingsJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        return ExportStats(
            pointCount = points.size,
            photoCount = photoEntries.size
        )
    }

    private fun buildPhotoMeta(
        point: Point,
        resolvePhotoFile: (String) -> File?,
        photoEntries: MutableMap<String, File>,
        includePhotos: Boolean
    ): PointPhotoMeta {
        if (!includePhotos) return PointPhotoMeta()
        val sourcePath = point.photoPath?.trim().orEmpty()
        if (sourcePath.isEmpty()) return PointPhotoMeta()
        val photoFile = resolvePhotoFile(sourcePath)
        if (photoFile == null || !photoFile.exists() || !photoFile.isFile || !photoFile.canRead()) return PointPhotoMeta()

        var baseName = photoFile.name
            .ifBlank { "photo.bin" }
            .replace(Regex("""[/\\]|\.{2,}"""), "_")
            .replace(Regex("""[\u0000-\u001F\u007F]"""), "_")
        var relPath = "photos/$baseName"
        var suffix = 1
        while (photoEntries.containsKey(relPath) && photoEntries[relPath]?.absolutePath != photoFile.absolutePath) {
            val dot = baseName.lastIndexOf('.')
            val withSuffix = if (dot > 0) {
                "${baseName.substring(0, dot)}_$suffix${baseName.substring(dot)}"
            } else {
                "${baseName}_$suffix"
            }
            relPath = "photos/$withSuffix"
            suffix++
        }
        photoEntries.putIfAbsent(relPath, photoFile)

        return PointPhotoMeta(
            relPath = relPath,
            mime = guessMime(photoFile.extension),
            sizeBytes = photoFile.length(),
            sha256 = sha256(photoFile)
        )
    }

    private fun guessMime(extension: String): String? {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> null
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
