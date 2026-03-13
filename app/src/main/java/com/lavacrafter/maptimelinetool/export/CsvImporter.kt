package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.domain.model.Point
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CsvImporter {
    fun parseCsv(csv: String): List<Point> {
        val lines = csv.lines()
        if (lines.isEmpty()) return emptyList()
        
        // Find header index
        val headerLine = lines.firstOrNull { it.contains("name") && it.contains("latitude") } ?: return emptyList()
        val headerIndex = lines.indexOf(headerLine)
        
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        
        val points = mutableListOf<Point>()
        for (i in (headerIndex + 1) until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            
            val parts = parseCsvLine(line)
            if (parts.size >= 5) {
                try {
                    val title = parts[0]
                    val note = parts[1]
                    val lat = parts[2].toDouble()
                    val lon = parts[3].toDouble()
                    val timeStr = parts[4]
                    val timestamp = try {
                        sdf.parse(timeStr)?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                    
                    points.add(Point(
                        timestamp = timestamp,
                        latitude = lat,
                        longitude = lon,
                        title = title,
                        note = note
                    ))
                } catch (_: Exception) {}
            }
        }
        return points
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    current.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }
}
