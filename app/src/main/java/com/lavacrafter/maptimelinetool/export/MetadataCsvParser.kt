/*
Copyright 2026 Muchen Jiang (lava-crafter)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
*/

package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.text.sanitizeTagName
import java.io.PushbackReader
import java.io.Reader
import java.util.Locale

/** Strict, bounded parser for the canonical metadata CSV files in a backup. */
object MetadataCsvParser {
    data class Limits(
        val maxRecords: Int = 100_000,
        val maxFields: Int = 16,
        val maxFieldChars: Int = 256_000,
        val maxRecordChars: Int = 1_000_000
    )

    fun parseTags(reader: Reader, limits: Limits = Limits()): List<ZipImporter.ImportedTag> {
        val rows = readRows(reader, limits)
        require(rows.isNotEmpty()) { "tags.csv is missing a header" }
        val header = header(rows.first(), "tags.csv")
        val idIndex = header.indexOf("tag_id").takeIf { it >= 0 }
            ?: header.indexOf("id").takeIf { it >= 0 }
            ?: malformed("tags.csv is missing tag_id")
        val nameIndex = header.indexOf("name").takeIf { it >= 0 }
            ?: malformed("tags.csv is missing name")
        rows.drop(1).forEach { row -> require(row.size == header.size) { "Malformed row in tags.csv" } }
        return rows.drop(1).mapIndexed { rowNumber, row ->
            val id = row[idIndex].trim().toLongOrNull()
                ?: malformed("Invalid tag_id in tags.csv row ${rowNumber + 2}")
            require(id >= 0) { "Negative tag_id in tags.csv row ${rowNumber + 2}" }
            val name = sanitizeTagName(row[nameIndex].trim())
            require(name.isNotEmpty()) { "Empty tag name in tags.csv row ${rowNumber + 2}" }
            ZipImporter.ImportedTag(id, name)
        }
    }

    fun parsePointTags(reader: Reader, limits: Limits = Limits()): List<ZipImporter.ImportedPointTag> {
        val rows = readRows(reader, limits)
        require(rows.isNotEmpty()) { "point_tags.csv is missing a header" }
        val header = header(rows.first(), "point_tags.csv")
        val pointIndex = header.indexOf("point_index")
        require(pointIndex >= 0) { "point_tags.csv is missing point_index" }
        val tagIdIndex = header.indexOf("tag_id").takeIf { it >= 0 }
            ?: header.indexOf("id").takeIf { it >= 0 }
            ?: malformed("point_tags.csv is missing tag_id")
        rows.drop(1).forEach { row -> require(row.size == header.size) { "Malformed row in point_tags.csv" } }
        return rows.drop(1).mapIndexed { rowNumber, row ->
            val point = row[pointIndex].trim().toIntOrNull()
                ?: malformed("Invalid point_index in point_tags.csv row ${rowNumber + 2}")
            val tag = row[tagIdIndex].trim().toLongOrNull()
                ?: malformed("Invalid tag_id in point_tags.csv row ${rowNumber + 2}")
            require(point >= 0) { "Negative point_index in point_tags.csv row ${rowNumber + 2}" }
            require(tag >= 0) { "Negative tag_id in point_tags.csv row ${rowNumber + 2}" }
            ZipImporter.ImportedPointTag(point, tag)
        }
    }

    private fun header(row: List<String>, fileName: String): List<String> {
        return row.map { it.trim().lowercase(Locale.US) }.also { values ->
            require(values.isNotEmpty() && values.none { it.isEmpty() }) { "Invalid $fileName header" }
            require(values.toSet().size == values.size) { "Duplicate column in $fileName header" }
        }
    }

    private fun readRows(reader: Reader, limits: Limits): List<List<String>> {
        require(limits.maxRecords >= 0 && limits.maxFields > 0) { "Invalid metadata CSV limits" }
        val input = PushbackReader(reader, 1)
        val rows = mutableListOf<List<String>>()
        while (true) {
            val row = readRow(input, limits) ?: break
            require(row.size <= limits.maxFields) { "Metadata CSV row has too many fields" }
            require(row.any { it.isNotEmpty() }) { "Blank metadata CSV row" }
            // maxRecords counts metadata rows, not the header row.
            if (rows.size >= limits.maxRecords + 1) {
                throw IllegalArgumentException("Metadata CSV exceeds the record limit")
            }
            rows += row
        }
        return rows
    }

    private fun readRow(reader: PushbackReader, limits: Limits): List<String>? {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var started = false
        var total = 0
        fun add(ch: Char) {
            field.append(ch)
            if (field.length > limits.maxFieldChars || ++total > limits.maxRecordChars) {
                throw IllegalArgumentException("Metadata CSV exceeds the configured size budget")
            }
        }
        while (true) {
            val value = reader.read()
            if (value < 0) {
                if (!started && fields.isEmpty() && field.isEmpty()) return null
                require(!quoted) { "Unterminated quoted field in metadata CSV" }
                fields += field.toString()
                return fields
            }
            val ch = value.toChar()
            if (!started && fields.isEmpty() && field.isEmpty() && ch == '\uFEFF') continue
            started = true
            when {
                quoted && ch == '"' -> {
                    val next = reader.read()
                    if (next == '"'.code) add('"')
                    else {
                        quoted = false
                        closedQuote = true
                        if (next >= 0) reader.unread(next)
                    }
                }
                quoted -> add(ch)
                closedQuote && ch != ',' && ch != '\n' && ch != '\r' ->
                    throw IllegalArgumentException("Malformed quoted field in metadata CSV")
                ch == '"' && field.isEmpty() -> quoted = true
                ch == '"' -> throw IllegalArgumentException("Malformed quoted field in metadata CSV")
                ch == ',' -> {
                    fields += field.toString()
                    require(fields.size < limits.maxFields) { "Metadata CSV row has too many fields" }
                    field.clear()
                    closedQuote = false
                }
                ch == '\n' || ch == '\r' -> {
                    if (ch == '\r') {
                        val next = reader.read()
                        if (next >= 0 && next != '\n'.code) reader.unread(next)
                    }
                    fields += field.toString()
                    return fields
                }
                else -> add(ch)
            }
        }
    }

    private fun malformed(message: String): Nothing = throw IllegalArgumentException(message)
}
