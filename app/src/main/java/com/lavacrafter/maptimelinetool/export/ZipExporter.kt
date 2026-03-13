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

object ZipExporter {
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
        resolvePhotoFile: (String) -> File?
    ): ExportStats {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val photoEntries = mutableMapOf<String, File>()
        val pointPhotoMeta = points.map { point ->
            buildPhotoMeta(point, resolvePhotoFile, photoEntries)
        }

        ZipOutputStream(outputStream.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("points.csv"))
            val writer = OutputStreamWriter(zip, Charsets.UTF_8)
            CsvExporter.writeRow(writer, CsvExporter.headers)
            points.forEachIndexed { index, point ->
                val meta = pointPhotoMeta.getOrNull(index) ?: PointPhotoMeta()
                CsvExporter.writeRow(
                    writer,
                    listOf(
                        point.title,
                        point.note,
                        point.latitude.toString(),
                        point.longitude.toString(),
                        sdf.format(Date(point.timestamp)),
                        point.pressureHpa?.toString().orEmpty(),
                        point.ambientLightLux?.toString().orEmpty(),
                        point.accelerometerX?.toString().orEmpty(),
                        point.accelerometerY?.toString().orEmpty(),
                        point.accelerometerZ?.toString().orEmpty(),
                        point.gyroscopeX?.toString().orEmpty(),
                        point.gyroscopeY?.toString().orEmpty(),
                        point.gyroscopeZ?.toString().orEmpty(),
                        point.magnetometerX?.toString().orEmpty(),
                        point.magnetometerY?.toString().orEmpty(),
                        point.magnetometerZ?.toString().orEmpty(),
                        point.noiseDb?.toString().orEmpty(),
                        meta.relPath.orEmpty(),
                        meta.mime.orEmpty(),
                        meta.sizeBytes?.toString().orEmpty(),
                        meta.sha256.orEmpty()
                    )
                )
            }
            writer.flush()
            zip.closeEntry()

            photoEntries.forEach { (relPath, file) ->
                zip.putNextEntry(ZipEntry(relPath))
                file.inputStream().buffered().use { input -> input.copyTo(zip) }
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
        photoEntries: MutableMap<String, File>
    ): PointPhotoMeta {
        val sourcePath = point.photoPath?.trim().orEmpty()
        if (sourcePath.isEmpty()) return PointPhotoMeta()
        val photoFile = resolvePhotoFile(sourcePath)
        if (photoFile == null || !photoFile.exists() || !photoFile.isFile || !photoFile.canRead()) return PointPhotoMeta()

        var baseName = photoFile.name.ifBlank { "photo.bin" }.replace("/", "_").replace("\\", "_")
        if (baseName.contains("..")) baseName = baseName.replace("..", "_")
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
