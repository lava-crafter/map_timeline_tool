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
import java.text.ParsePosition
import java.util.Locale
import java.util.TimeZone
import com.lavacrafter.maptimelinetool.text.formatPointTimestamp
import com.lavacrafter.maptimelinetool.text.sanitizePointNote
import com.lavacrafter.maptimelinetool.text.sanitizePointTitle

object CsvImporter {
    const val MAX_ORDINARY_RECORDS = 100_000
    private const val MAX_WARNINGS = 5

    enum class SkipReason { INVALID_COORDINATES, INVALID_TIMESTAMP, INVALID_TEXT_ENCODING }
    data class SkippedRow(val row: Int, val reason: SkipReason)
    data class OrdinaryCsvResult(val points: List<Point>, val skipped: Int, val warnings: List<SkippedRow>)

    /** Stream the input and bound all rows (including rejected ones) before committing anything to Room. */
    fun parseOrdinaryCsv(reader: Reader, maxRecords: Int = MAX_ORDINARY_RECORDS): OrdinaryCsvResult {
        val points = mutableListOf<Point>()
        val warnings = mutableListOf<SkippedRow>()
        var skipped = 0
        forEachPoint(
            reader, resolvePhotoPath = { null }, requireHeader = true, maxRecords = maxRecords,
            onSkippedRow = { row, reason ->
                skipped++
                if (warnings.size < MAX_WARNINGS) warnings += SkippedRow(row, reason)
            }
        ) { points += it }
        return OrdinaryCsvResult(points, skipped, warnings)
    }

    data class Limits(
        val maxRecordChars: Int = 1_000_000,
        val maxFieldChars: Int = 256_000,
        val maxFields: Int = 128
    )

    fun parseCsv(csv: String): List<Point> {
        return parseCsv(StringReader(csv))
    }

    fun parseCsv(
        reader: Reader,
        strictRows: Boolean = false,
        onPhotoMetadata: (relPath: String, sizeBytes: Long?, sha256: String?) -> Unit = { _, _, _ -> },
        resolvePhotoPath: (String) -> String? = { it },
        maxPoints: Int = Int.MAX_VALUE
    ): List<Point> {
        val points = mutableListOf<Point>()
        forEachPoint(reader, resolvePhotoPath, strictRows = strictRows, onPhotoMetadata = onPhotoMetadata) {
            if (points.size >= maxPoints) throw ZipImportLimitExceededException("Too many points in backup")
            points += it
        }
        return points
    }

    fun forEachPoint(
        reader: Reader,
        resolvePhotoPath: (String) -> String? = { it },
        limits: Limits = Limits(),
        strictRows: Boolean = false,
        onPhotoMetadata: (relPath: String, sizeBytes: Long?, sha256: String?) -> Unit = { _, _, _ -> },
        requireHeader: Boolean = false,
        maxRecords: Int = Int.MAX_VALUE,
        onSkippedRow: (Int, SkipReason) -> Unit = { _, _ -> },
        consume: (Point) -> Unit
    ) {
        val pushbackReader = PushbackReader(reader, 2)
        var header: List<String>? = null

        while (true) {
            val record = readCsvRecord(pushbackReader, limits, strictRows) ?: break
            if (record.isEmpty()) continue
            val candidate = record.map { it.trim() }
            val normalized = candidate.map { it.lowercase(Locale.US) }
            if (normalized.contains("name") && normalized.contains("latitude") && normalized.contains("longitude")) {
                header = normalized
                break
            }
            if (strictRows || requireHeader) throw IllegalArgumentException("Invalid points CSV header")
        }
        val resolvedHeader = header ?: run {
            if (strictRows || requireHeader) throw IllegalArgumentException("Missing points CSV header")
            return
        }
        if (strictRows) require("time_utc" in resolvedHeader) { "Missing canonical point timestamp" }
        val indexMap = resolvedHeader.withIndex().associate { it.value to it.index }
        var rowNumber = 0

        while (true) {
            val row = readCsvRecord(pushbackReader, limits, strictRows) ?: break
            if (row.all { it.isBlank() }) continue
            rowNumber++
            if (rowNumber > maxRecords) throw IllegalArgumentException("CSV exceeds the $maxRecords record limit")
            if (strictRows) {
                require(row.size == resolvedHeader.size) { "Incorrect number of point CSV fields" }
                for (key in listOf("location_accuracy_meters", "pressure_hpa", "ambient_light_lux",
                    "accelerometer_x", "accelerometer_y", "accelerometer_z", "gyroscope_x", "gyroscope_y",
                    "gyroscope_z", "magnetometer_x", "magnetometer_y", "magnetometer_z", "noise_db")) {
                    val value = row.valueOf(indexMap, key)?.trim().orEmpty()
                    require(value.isEmpty() || value.toFloatOrNull()?.isFinite() == true) { "Invalid point field: $key" }
                }
                val fixTime = row.valueOf(indexMap, "location_fix_time_ms")?.trim().orEmpty()
                require(fixTime.isEmpty() || fixTime.toLongOrNull() != null) { "Invalid location fix time" }
            }
            val lat = row.valueOf(indexMap, "latitude")?.toDoubleOrNull()
            val lon = row.valueOf(indexMap, "longitude")?.toDoubleOrNull()
            if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                if (strictRows) throw IllegalArgumentException("Invalid point coordinates in CSV")
                onSkippedRow(rowNumber, SkipReason.INVALID_COORDINATES)
                continue
            }
            val timestamp = parseTimestamp(row.valueOf(indexMap, "time_utc"))
            if (timestamp == null) {
                if (strictRows) throw IllegalArgumentException("Invalid point timestamp in CSV")
                onSkippedRow(rowNumber, SkipReason.INVALID_TIMESTAMP)
                continue
            }
            val encodedText = !strictRows && row.valueOf(indexMap, CsvTextEncoding.column) == CsvTextEncoding.version
            val rawTitle = row.valueOf(indexMap, "name").orEmpty()
            val rawNote = row.valueOf(indexMap, "description").orEmpty()
            val decodedTitle = if (encodedText) CsvTextEncoding.decode(rawTitle) else rawTitle
            val decodedNote = if (encodedText) CsvTextEncoding.decode(rawNote) else rawNote
            val rawProvider = row.valueOf(indexMap, "location_provider").orEmpty()
            val decodedProvider = if (encodedText) CsvTextEncoding.decode(rawProvider) else rawProvider
            val rawPhotoPath = row.valueOf(indexMap, "photo_rel_path").orEmpty()
            val decodedPhotoPath = if (encodedText) CsvTextEncoding.decode(rawPhotoPath) else rawPhotoPath
            if (decodedTitle == null || decodedNote == null || decodedProvider == null || decodedPhotoPath == null) {
                onSkippedRow(rowNumber, SkipReason.INVALID_TEXT_ENCODING)
                continue
            }
            val title = sanitizePointTitle(decodedTitle)
                .ifBlank { formatPointTimestamp(timestamp) }
            val note = sanitizePointNote(decodedNote)
            val photoRelPath = decodedPhotoPath.trim()
            val resolvedPhotoPath = photoRelPath.takeIf { it.isNotEmpty() }?.let(resolvePhotoPath)
            if (photoRelPath.isNotEmpty()) {
                val rawSize = row.valueOf(indexMap, "photo_size_bytes")?.trim().orEmpty()
                val size = rawSize.toLongOrNull()
                val hash = row.valueOf(indexMap, "photo_sha256")?.trim()?.takeIf { it.isNotEmpty() }
                if (strictRows && (rawSize.isNotEmpty() && (size == null || size < 0) ||
                        hash != null && !hash.matches(Regex("[a-fA-F0-9]{64}")))) {
                    throw IllegalArgumentException("Invalid photo metadata in CSV")
                }
                onPhotoMetadata(
                    photoRelPath,
                    size,
                    hash
                )
            }

            consume(
                Point(
                    timestamp = timestamp,
                    latitude = lat,
                    longitude = lon,
                    locationAccuracyMeters = row.valueOf(indexMap, "location_accuracy_meters").toFiniteFloatOrNull(),
                    locationFixTimeMs = row.valueOf(indexMap, "location_fix_time_ms").toFiniteLongOrNull(),
                    locationProvider = decodedProvider.toTrimmedOrNull(),
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

    private fun parseTimestamp(time: String?): Long? {
        if (time == null) return null // A missing time must not be replaced by a fabricated import time.
        val value = time.trim()
        if (value.isEmpty()) return null
        value.toLongOrNull()?.let { return it }
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            val position = ParsePosition(0)
            val parsedDate = sdf.parse(value, position)
            if (parsedDate != null && position.index == value.length) return parsedDate.time
        }
        return null
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

    private fun readCsvRecord(reader: PushbackReader, limits: Limits, strict: Boolean): List<String>? {
        val record = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var closedQuote = false
        var anyContent = false

        while (true) {
            val intChar = reader.read()
            if (intChar == -1) {
                if (strict) require(!inQuotes) { "Unterminated CSV quoted field" }
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
            if (strict && closedQuote && currentChar != ',' && currentChar != '\n' && currentChar != '\r') {
                throw IllegalArgumentException("Malformed CSV quoted field")
            }
            when {
                currentChar == '"' -> {
                    if (inQuotes) {
                        val next = reader.read()
                        if (next == '"'.code) {
                            field.append('"')
                            validateField(field, limits)
                        } else {
                            inQuotes = false
                            closedQuote = true
                            if (next != -1) reader.unread(next)
                        }
                    } else {
                        if (strict) require(field.isEmpty()) { "Malformed CSV quoted field" }
                        inQuotes = true
                    }
                }
                currentChar == ',' && !inQuotes -> {
                    record.add(field.toString())
                    validateRecord(record, limits)
                    field.clear()
                    closedQuote = false
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
        if (record.size > limits.maxFields) throw IllegalArgumentException("CSV has too many fields")
        if (record.sumOf(String::length) > limits.maxRecordChars) {
            throw IllegalArgumentException("CSV record exceeds the configured size budget")
        }
    }
}
