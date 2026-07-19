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
import java.io.Reader
import java.io.StringReader
import java.io.PushbackReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.lavacrafter.maptimelinetool.text.formatPointTimestamp
import com.lavacrafter.maptimelinetool.text.sanitizePointNote
import com.lavacrafter.maptimelinetool.text.sanitizePointTitle

object CsvImporter {
    data class Limits(
        val maxRecordChars: Int = 1_000_000,
        val maxFieldChars: Int = 256_000
    )

    fun parseCsv(csv: String): List<Point> {
        return parseCsv(StringReader(csv))
    }

    fun parseCsv(
        reader: Reader,
        resolvePhotoPath: (String) -> String? = { it }
    ): List<Point> {
        val points = mutableListOf<Point>()
        forEachPoint(reader, resolvePhotoPath) { points += it }
        return points
    }

    fun forEachPoint(
        reader: Reader,
        resolvePhotoPath: (String) -> String? = { it },
        limits: Limits = Limits(),
        consume: (Point) -> Unit
    ) {
        val pushbackReader = PushbackReader(reader, 2)
        var header: List<String>? = null

        while (true) {
            val record = readCsvRecord(pushbackReader, limits) ?: break
            if (record.isEmpty()) continue
            val candidate = record.map { it.trim() }
            val normalized = candidate.map { it.lowercase(Locale.US) }
            if (normalized.contains("name") && normalized.contains("latitude") && normalized.contains("longitude")) {
                header = normalized
                break
            }
        }
        val resolvedHeader = header ?: return
        val indexMap = resolvedHeader.withIndex().associate { it.value to it.index }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        while (true) {
            val row = readCsvRecord(pushbackReader, limits) ?: break
            if (row.all { it.isBlank() }) continue
            val lat = row.valueOf(indexMap, "latitude")?.toDoubleOrNull() ?: continue
            val lon = row.valueOf(indexMap, "longitude")?.toDoubleOrNull() ?: continue
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val timestamp = parseTimestamp(row.valueOf(indexMap, "time_utc"), sdf)
            val title = sanitizePointTitle(row.valueOf(indexMap, "name").orEmpty())
                .ifBlank { formatPointTimestamp(timestamp) }
            val note = sanitizePointNote(row.valueOf(indexMap, "description").orEmpty())
            val photoRelPath = row.valueOf(indexMap, "photo_rel_path").orEmpty().trim()
            val resolvedPhotoPath = photoRelPath.takeIf { it.isNotEmpty() }?.let(resolvePhotoPath)

            consume(
                Point(
                    timestamp = timestamp,
                    latitude = lat,
                    longitude = lon,
                    locationAccuracyMeters = row.valueOf(indexMap, "location_accuracy_meters").toFiniteFloatOrNull(),
                    locationFixTimeMs = row.valueOf(indexMap, "location_fix_time_ms").toFiniteLongOrNull(),
                    locationProvider = row.valueOf(indexMap, "location_provider").toTrimmedOrNull(),
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

    private fun String?.toFiniteLongOrNull(): Long? {
        return this?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()
    }

    private fun String?.toTrimmedOrNull(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun readCsvRecord(reader: PushbackReader, limits: Limits): List<String>? {
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
                validateRecord(record, limits)
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
                            validateField(field, limits)
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
                    validateRecord(record, limits)
                    field.clear()
                }
                currentChar == '\n' && !inQuotes -> {
                    record.add(field.toString())
                    validateRecord(record, limits)
                    return record
                }
                currentChar == '\r' && !inQuotes -> {
                    val next = reader.read()
                    if (next != '\n'.code && next != -1) {
                        reader.unread(next)
                    }
                    record.add(field.toString())
                    validateRecord(record, limits)
                    return record
                }
                else -> {
                    field.append(currentChar)
                    validateField(field, limits)
                }
            }
        }
    }

    private fun validateField(field: StringBuilder, limits: Limits) {
        if (field.length > limits.maxFieldChars) {
            throw IllegalArgumentException("CSV field exceeds the configured size budget")
        }
    }

    private fun validateRecord(record: List<String>, limits: Limits) {
        if (record.sumOf(String::length) > limits.maxRecordChars) {
            throw IllegalArgumentException("CSV record exceeds the configured size budget")
        }
    }
}
