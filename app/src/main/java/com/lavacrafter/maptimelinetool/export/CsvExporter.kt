package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.domain.model.Point
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CsvExporter {
    fun buildCsv(points: List<Point>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return buildString {
            appendLine("name,description,latitude,longitude,time_utc")
            points.forEach { p ->
                val name = escape(p.title)
                val desc = escape(p.note)
                val time = sdf.format(Date(p.timestamp))
                appendLine("${name},${desc},${p.latitude},${p.longitude},${time}")
            }
        }
    }

    private fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"${escaped}\""
    }
}
