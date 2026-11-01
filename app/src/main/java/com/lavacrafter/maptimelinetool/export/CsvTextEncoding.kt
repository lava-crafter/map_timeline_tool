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

/**
 * Only ordinary CSV exports use this protocol; ZIP backup CSV remains unchanged. Each row names its encoding in
 * [column]. Formula-like text gets the leading apostrophe spreadsheets treat as "literal text", so the file stays
 * readable in Excel, Sheets and GIS tools while this app can still restore the exact original.
 */
internal object CsvTextEncoding {
    const val column = "mtt_text_encoding"
    const val version = "quote-v1"
    /** Older exports hex-encoded such cells; still decoded on import. */
    private const val legacyHexVersion = "hex-v1"
    private const val legacyHexPrefix = "MTTCSV1:"
    private const val quote = '\''

    fun encode(value: String): String {
        val first = value.trimStart { it.isWhitespace() || it == '\uFEFF' }.firstOrNull()
        // Values that already start with the quote are escaped too, so decoding can always drop exactly one.
        return if (first in listOf('=', '+', '-', '@') || value.startsWith(quote)) "$quote$value" else value
    }

    /** A malformed encoded cell is an invalid row, not an invitation to silently rewrite text. */
    fun decode(value: String, encoding: String?): String? = when (encoding) {
        version -> value.removePrefix(quote.toString())
        legacyHexVersion -> decodeLegacyHex(value)
        else -> value
    }

    private fun decodeLegacyHex(value: String): String? {
        if (!value.startsWith(legacyHexPrefix)) return value
        val hex = value.substring(legacyHexPrefix.length)
        if (hex.length % 2 != 0) return null
        val bytes = ByteArray(hex.length / 2)
        for (index in bytes.indices) {
            val high = hex[index * 2].digitToIntOrNull(16) ?: return null
            val low = hex[index * 2 + 1].digitToIntOrNull(16) ?: return null
            bytes[index] = ((high shl 4) or low).toByte()
        }
        return runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrNull()
    }
}
