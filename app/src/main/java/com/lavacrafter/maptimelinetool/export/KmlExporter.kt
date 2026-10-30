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
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object KmlExporter {
    fun writeKml(
        points: List<Point>,
        outputStream: OutputStream,
        documentName: String = "Map Timeline Export",
        includeSensors: Boolean = true,
        pointTagNamesByPointId: Map<Long, List<String>> = emptyMap(),
        photoRelPathResolver: (Point) -> String? = { point -> point.photoPath }
    ) {
        // Stream straight to the destination; building the whole document first costs several copies for
        // large exports. The caller owns (and closes) the stream.
        val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
        appendKml(writer, points, documentName, includeSensors, pointTagNamesByPointId, photoRelPathResolver)
        writer.flush()
    }

    fun buildKml(
        points: List<Point>,
        documentName: String = "Map Timeline Export",
        includeSensors: Boolean = true,
        pointTagNamesByPointId: Map<Long, List<String>> = emptyMap(),
        photoRelPathResolver: (Point) -> String? = { point -> point.photoPath }
    ): String = buildString {
        appendKml(this, points, documentName, includeSensors, pointTagNamesByPointId, photoRelPathResolver)
    }

    private fun appendKml(
        out: Appendable,
        points: List<Point>,
        documentName: String,
        includeSensors: Boolean,
        pointTagNamesByPointId: Map<Long, List<String>>,
        photoRelPathResolver: (Point) -> String?
    ) {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        with(out) {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<kml xmlns=\"http://www.opengis.net/kml/2.2\">")
            appendLine("  <Document>")
            appendLine("    <name>${escapeXml(documentName)}</name>")
            points.forEach { point ->
                val descriptionLines = mutableListOf<String>()
                if (point.note.isNotBlank()) {
                    descriptionLines.add("Note: ${point.note}")
                }
                val tags = pointTagNamesByPointId[point.id].orEmpty().filter { it.isNotBlank() }
                if (tags.isNotEmpty()) {
                    descriptionLines.add("Tags: ${tags.joinToString(", ")}")
                }

                val locationQuality = buildLocationQualityDescription(point)
                if (locationQuality != null) {
                    descriptionLines.add(locationQuality)
                }

                val photoPath = photoRelPathResolver(point)?.trim().orEmpty()
                if (photoPath.isNotEmpty()) {
                    descriptionLines.add("Photo: $photoPath")
                }

                if (includeSensors) {
                    point.pressureHpa?.let { descriptionLines.add("Pressure(hPa): $it") }
                    point.ambientLightLux?.let { descriptionLines.add("AmbientLight(lux): $it") }
                    point.accelerometerX?.let { descriptionLines.add("AccelX: $it") }
                    point.accelerometerY?.let { descriptionLines.add("AccelY: $it") }
                    point.accelerometerZ?.let { descriptionLines.add("AccelZ: $it") }
                    point.gyroscopeX?.let { descriptionLines.add("GyroX: $it") }
                    point.gyroscopeY?.let { descriptionLines.add("GyroY: $it") }
                    point.gyroscopeZ?.let { descriptionLines.add("GyroZ: $it") }
                    point.magnetometerX?.let { descriptionLines.add("MagX: $it") }
                    point.magnetometerY?.let { descriptionLines.add("MagY: $it") }
                    point.magnetometerZ?.let { descriptionLines.add("MagZ: $it") }
                    point.noiseDb?.let { descriptionLines.add("Noise(dBFS): $it") }
                }

                appendLine("    <Placemark>")
                appendLine("      <name>${escapeXml(point.title)}</name>")
                appendLine("      <TimeStamp><when>${escapeXml(sdf.format(Date(point.timestamp)))}</when></TimeStamp>")
                if (descriptionLines.isNotEmpty()) {
                    val fullDescription = descriptionLines.joinToString("\n")
                    appendLine("      <description>${escapeXml(fullDescription)}</description>")
                }
                appendLine("      <Point>")
                // Plain decimal: Double.toString gives "5.0E-4" near 0, which some KML readers reject.
                appendLine("        <coordinates>${plainDecimal(point.longitude)},${plainDecimal(point.latitude)},0</coordinates>")
                appendLine("      </Point>")
                appendLine("    </Placemark>")
            }
            appendLine("  </Document>")
            appendLine("</kml>")
        }
    }

    private fun plainDecimal(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    /** Escapes markup and drops characters XML 1.0 forbids, which would make the whole file unreadable. */
    private fun escapeXml(value: String): String {
        return xmlSafe(value)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildLocationQualityDescription(point: Point): String? {
        val parts = mutableListOf<String>()
        point.locationProvider?.takeIf { it.isNotBlank() }?.let { parts += "Location provider: $it" }
        point.locationAccuracyMeters?.let { parts += "Accuracy(m): $it" }
        point.locationFixTimeMs?.let { parts += "Fix time(ms): $it" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" | ")
    }
}

private fun xmlSafe(value: String): String {
    fun allowed(ch: Char) = ch == '\t' || ch == '\n' || ch == '\r' || (ch >= ' ' && ch != '\uFFFE' && ch != '\uFFFF')
    if (value.all { allowed(it) && !it.isSurrogate() }) return value
    val out = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val ch = value[i]
        if (ch.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate()) {
            out.append(ch).append(value[i + 1])
            i += 2
            continue
        }
        if (!ch.isSurrogate() && allowed(ch)) out.append(ch)
        i++
    }
    return out.toString()
}
