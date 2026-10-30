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
const val MAX_LOCATION_PROVIDER_LENGTH = 64

fun normalizeSingleLineTextForEditing(raw: String, maxLength: Int): String {
    if (maxLength <= 0 || raw.isEmpty()) return ""

    val normalizedLineEndings = wellFormed(raw).replace("\r\n", "\n").replace('\r', '\n')
    val builder = StringBuilder(minOf(normalizedLineEndings.length, maxLength))

    for (ch in normalizedLineEndings) {
        val normalizedChar = when (ch) {
            '\u0000' -> null
            '\n', '\t', '\u000B', '\u000C' -> ' '
            else -> if (ch.isISOControl()) null else ch
        } ?: continue

        builder.append(normalizedChar)

        if (builder.length >= maxLength) {
            break
        }
    }
    builder.dropTrailingHighSurrogate()

    return builder.toString()
}

fun sanitizeSingleLineText(raw: String, maxLength: Int): String {
    if (maxLength <= 0 || raw.isEmpty()) return ""

    val builder = StringBuilder(minOf(raw.length, maxLength))
    var previousWasSpace = false

    for (ch in wellFormed(raw)) {
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
    builder.dropTrailingHighSurrogate()

    return builder.toString().trim().takeWhole(maxLength)
}

fun normalizeMultilineTextForEditing(raw: String, maxLength: Int): String {
    if (maxLength <= 0 || raw.isEmpty()) return ""

    val normalizedLineEndings = wellFormed(raw).replace("\r\n", "\n").replace('\r', '\n')
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
    builder.dropTrailingHighSurrogate()

    return builder.toString()
}

fun sanitizeMultilineText(raw: String, maxLength: Int): String {
    if (maxLength <= 0 || raw.isEmpty()) return ""

    val normalizedLineEndings = wellFormed(raw).replace("\r\n", "\n").replace('\r', '\n')
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
    builder.dropTrailingHighSurrogate()

    return builder.toString().trim().takeWhole(maxLength)
}

fun sanitizePointTitle(raw: String): String = sanitizeSingleLineText(raw, MAX_POINT_TITLE_LENGTH)

fun sanitizePointNote(raw: String): String = sanitizeMultilineText(raw, MAX_POINT_NOTE_LENGTH)

fun sanitizeTagName(raw: String): String = sanitizeSingleLineText(raw, MAX_TAG_NAME_LENGTH)

/** Imported provider names end up in KML/CSV text; keep them short, single-line and free of controls. */
fun sanitizeLocationProvider(raw: String?): String? =
    raw?.let { sanitizeSingleLineText(it, MAX_LOCATION_PROVIDER_LENGTH) }?.takeIf { it.isNotEmpty() }

fun normalizePointTitleForEditing(raw: String): String =
    normalizeSingleLineTextForEditing(raw, MAX_POINT_TITLE_LENGTH)

fun normalizePointNoteForEditing(raw: String): String =
    normalizeMultilineTextForEditing(raw, MAX_POINT_NOTE_LENGTH)

fun normalizeTagNameForEditing(raw: String): String =
    normalizeSingleLineTextForEditing(raw, MAX_TAG_NAME_LENGTH)

/**
 * Drops lone surrogates and the U+FFFE/U+FFFF noncharacters: they are invalid in XML (KML) and become "?" in
 * UTF-8, so a stored title must never contain them. Valid surrogate pairs (emoji) are kept.
 */
private fun wellFormed(text: String): String {
    if (text.none { it.isSurrogate() || it == '\uFFFE' || it == '\uFFFF' }) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (ch.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
            out.append(ch).append(text[i + 1])
            i += 2
            continue
        }
        if (!ch.isSurrogate() && ch != '\uFFFE' && ch != '\uFFFF') out.append(ch)
        i++
    }
    return out.toString()
}

/** Length limits count UTF-16 units; never cut a surrogate pair in half at the limit. */
private fun StringBuilder.dropTrailingHighSurrogate() {
    if (isNotEmpty() && this[length - 1].isHighSurrogate()) setLength(length - 1)
}

private fun String.takeWhole(maxLength: Int): String {
    if (length <= maxLength) return this
    val end = if (this[maxLength - 1].isHighSurrogate()) maxLength - 1 else maxLength
    return substring(0, end)
}

fun formatPointTimestamp(timestampMs: Long, locale: Locale = Locale.getDefault()): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale).format(Date(timestampMs))
}
