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

/** Only ordinary CSV exports use this protocol; ZIP backup CSV remains unchanged. */
internal object CsvTextEncoding {
    const val column = "mtt_text_encoding"
    const val version = "hex-v1"
    private const val prefix = "MTTCSV1:"
    private const val digits = "0123456789abcdef"

    fun encode(value: String): String {
        val first = value.trimStart { it.isWhitespace() || it == '\uFEFF' }.firstOrNull()
        if (first !in listOf('=', '+', '-', '@') && !value.startsWith(prefix)) return value
        return buildString {
            append(prefix)
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                append(digits[(byte.toInt() and 0xff) ushr 4])
                append(digits[byte.toInt() and 0x0f])
            }
        }
    }

    /** A malformed encoded cell is an invalid row, not an invitation to silently rewrite text. */
    fun decode(value: String): String? {
        if (!value.startsWith(prefix)) return value
        val hex = value.substring(prefix.length)
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
