package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.domain.model.Point
import java.io.Reader
import java.io.StringReader
import java.io.PushbackReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object CsvImporter {
    fun parseCsv(csv: String): List<Point> {
        return parseCsv(StringReader(csv))
    }

    fun parseCsv(
        reader: Reader,
        resolvePhotoPath: (String) -> String? = { it }
    ): List<Point> {
        val pushbackReader = PushbackReader(reader, 2)
        val records = sequence {
            while (true) {
                val record = readCsvRecord(pushbackReader) ?: break
                if (record.isNotEmpty()) {
                    yield(record)
                }
            }
        }.iterator()
        if (!records.hasNext()) return emptyList()

        var header: List<String>? = null
        while (records.hasNext()) {
            val candidate = records.next().map { it.trim() }
            val normalized = candidate.map { it.lowercase(Locale.US) }
            if (normalized.contains("name") && normalized.contains("latitude") && normalized.contains("longitude")) {
                header = normalized
                break
            }
        }
        val resolvedHeader = header ?: return emptyList()
        val indexMap = resolvedHeader.withIndex().associate { it.value to it.index }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val points = mutableListOf<Point>()
        while (records.hasNext()) {
            val row = records.next()
            if (row.all { it.isBlank() }) continue
            val lat = row.valueOf(indexMap, "latitude")?.toDoubleOrNull() ?: continue
            val lon = row.valueOf(indexMap, "longitude")?.toDoubleOrNull() ?: continue
            val title = row.valueOf(indexMap, "name").orEmpty()
            val note = row.valueOf(indexMap, "description").orEmpty()
            val timestamp = parseTimestamp(row.valueOf(indexMap, "time_utc"), sdf)
            val photoRelPath = row.valueOf(indexMap, "photo_rel_path").orEmpty().trim()
            val resolvedPhotoPath = photoRelPath.takeIf { it.isNotEmpty() }?.let(resolvePhotoPath)

            points.add(
                Point(
                    timestamp = timestamp,
                    latitude = lat,
                    longitude = lon,
                    title = title,
                    note = note,
                    pressureHpa = row.valueOf(indexMap, "pressure_hpa").toFiniteFloatOrNull(),
                    ambientLightLux = row.valueOf(indexMap, "ambient_light_lux").toFiniteFloatOrNull(),
                    accelerometerX = row.valueOf(indexMap, "accelerometer_x").toFiniteFloatOrNull(),
                    accelerometerY = row.valueOf(indexMap, "accelerometer_y").toFiniteFloatOrNull(),
                    accelerometerZ = row.valueOf(indexMap, "accelerometer_z").toFiniteFloatOrNull(),
                    gyroscopeX = row.valueOf(indexMap, "gyroscope_x").toFiniteFloatOrNull(),
                    gyroscopeY = row.valueOf(indexMap, "gyroscope_y").toFiniteFloatOrNull(),
                    gyroscopeZ = row.valueOf(indexMap, "gyroscope_z").toFiniteFloatOrNull(),
                    magnetometerX = row.valueOf(indexMap, "magnetometer_x").toFiniteFloatOrNull(),
                    magnetometerY = row.valueOf(indexMap, "magnetometer_y").toFiniteFloatOrNull(),
                    magnetometerZ = row.valueOf(indexMap, "magnetometer_z").toFiniteFloatOrNull(),
                    noiseDb = row.valueOf(indexMap, "noise_db").toFiniteFloatOrNull(),
                    photoPath = resolvedPhotoPath
                )
            )
        }
        return points
    }

    private fun parseTimestamp(time: String?, sdf: SimpleDateFormat): Long {
        val value = time?.trim().orEmpty()
        if (value.isEmpty()) return System.currentTimeMillis()
        val parsedDate = runCatching { sdf.parse(value) }.getOrNull()
        if (parsedDate != null) return parsedDate.time
        return value.toLongOrNull() ?: System.currentTimeMillis()
    }

    private fun List<String>.valueOf(indexMap: Map<String, Int>, key: String): String? {
        val index = indexMap[key] ?: return null
        if (index !in indices) return null
        return this[index]
    }

    private fun String?.toFiniteFloatOrNull(): Float? {
        val parsed = this?.trim()?.takeIf { it.isNotEmpty() }?.toFloatOrNull() ?: return null
        return parsed.takeIf { it.isFinite() }
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
