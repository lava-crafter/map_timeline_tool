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

package com.lavacrafter.maptimelinetool.text

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val MAX_POINT_TITLE_LENGTH = 120
const val MAX_POINT_NOTE_LENGTH = 4000
const val MAX_TAG_NAME_LENGTH = 60

fun sanitizeSingleLineText(raw: String, maxLength: Int): String {
    if (maxLength <= 0 || raw.isEmpty()) return ""

    val builder = StringBuilder(minOf(raw.length, maxLength))
    var previousWasSpace = false

    for (ch in raw) {
        val normalizedChar = when (ch) {
            '\u0000' -> null
            '\n', '\r', '\t', '\u000B', '\u000C' -> ' '
            else -> if (ch.isISOControl()) null else ch
        } ?: continue

        if (normalizedChar.isWhitespace()) {
            if (builder.isNotEmpty() && !previousWasSpace) {
                builder.append(' ')
                previousWasSpace = true
            }
        } else {
            builder.append(normalizedChar)
            previousWasSpace = false
        }

        if (builder.length >= maxLength) {
            break
        }
    }

    return builder.toString().trim().take(maxLength)
}

fun sanitizeMultilineText(raw: String, maxLength: Int): String {
    if (maxLength <= 0 || raw.isEmpty()) return ""

    val normalizedLineEndings = raw.replace("\r\n", "\n").replace('\r', '\n')
    val builder = StringBuilder(minOf(normalizedLineEndings.length, maxLength))

    for (ch in normalizedLineEndings) {
        val normalizedChar = when (ch) {
            '\u0000' -> null
            '\n' -> '\n'
            '\t' -> ' '
            else -> if (ch.isISOControl()) null else ch
        } ?: continue

        builder.append(normalizedChar)

        if (builder.length >= maxLength) {
            break
        }
    }

    return builder.toString().trim().take(maxLength)
}

fun sanitizePointTitle(raw: String): String = sanitizeSingleLineText(raw, MAX_POINT_TITLE_LENGTH)

fun sanitizePointNote(raw: String): String = sanitizeMultilineText(raw, MAX_POINT_NOTE_LENGTH)

fun sanitizeTagName(raw: String): String = sanitizeSingleLineText(raw, MAX_TAG_NAME_LENGTH)

fun formatPointTimestamp(timestampMs: Long, locale: Locale = Locale.getDefault()): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale).format(Date(timestampMs))
}