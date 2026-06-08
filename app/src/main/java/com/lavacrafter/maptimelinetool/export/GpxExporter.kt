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

import com.lavacrafter.maptimelinetool.data.PointEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {
    fun exportToFile(points: List<PointEntity>, file: File) {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val content = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="map-timeline-tool" xmlns="http://www.topografix.com/GPX/1/1">""")
            points.forEach { p ->
                appendLine("""  <wpt lat="${'$'}{p.latitude}" lon="${'$'}{p.longitude}">""")
                appendLine("""    <time>${'$'}{sdf.format(Date(p.timestamp))}</time>""")
                appendLine("""    <name>${'$'}{escape(p.title)}</name>""")
                if (p.note.isNotBlank()) {
                    appendLine("""    <desc>${'$'}{escape(p.note)}</desc>""")
                }
                appendLine("""  </wpt>""")
            }
            appendLine("</gpx>")
        }
        file.writeText(content)
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}