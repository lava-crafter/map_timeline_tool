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
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CsvExporter {
    val headers: List<String> = listOf(
        "name",
        "description",
        "latitude",
        "longitude",
        "time_utc",
        "location_accuracy_meters",
        "location_fix_time_ms",
        "location_provider",
        "pressure_hpa",
        "ambient_light_lux",
        "accelerometer_x",
        "accelerometer_y",
        "accelerometer_z",
        "gyroscope_x",
        "gyroscope_y",
        "gyroscope_z",
        "magnetometer_x",
        "magnetometer_y",
        "magnetometer_z",
        "noise_db",
        "photo_rel_path",
        "photo_mime",
        "photo_size_bytes",
        "photo_sha256"
    )

    fun buildCsv(points: List<Point>): String {
        return buildCsv(points) { null }
    }

    fun buildCsv(
        points: List<Point>,
        photoRelPathResolver: (Point) -> String?
    ): String {
        val writer = StringWriter()
        writeCsv(points, writer, photoRelPathResolver)
        return writer.toString()
    }

    fun writeCsv(
        points: List<Point>,
        outputStream: OutputStream,
        photoRelPathResolver: (Point) -> String? = { null }
    ) {
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            writeCsv(points, writer, photoRelPathResolver)
        }
    }

    fun writeCsv(
        points: List<Point>,
        writer: Writer,
        photoRelPathResolver: (Point) -> String? = { null }
    ) {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        writeRow(writer, headers)
        points.forEach { p ->
            val time = sdf.format(Date(p.timestamp))
            val values = listOf(
                p.title,
                p.note,
                p.latitude.toString(),
                p.longitude.toString(),
                time,
                p.locationAccuracyMeters?.toString().orEmpty(),
                p.locationFixTimeMs?.toString().orEmpty(),
                p.locationProvider.orEmpty(),
                p.pressureHpa?.toString().orEmpty(),
                p.ambientLightLux?.toString().orEmpty(),
                p.accelerometerX?.toString().orEmpty(),
                p.accelerometerY?.toString().orEmpty(),
                p.accelerometerZ?.toString().orEmpty(),
                p.gyroscopeX?.toString().orEmpty(),
                p.gyroscopeY?.toString().orEmpty(),
                p.gyroscopeZ?.toString().orEmpty(),
                p.magnetometerX?.toString().orEmpty(),
                p.magnetometerY?.toString().orEmpty(),
                p.magnetometerZ?.toString().orEmpty(),
                p.noiseDb?.toString().orEmpty(),
                photoRelPathResolver(p).orEmpty(),
                "",
                "",
                ""
            )
            writeRow(writer, values)
        }
    }

    fun writeRow(writer: Writer, values: List<String>) {
        val escaped = values.joinToString(",") { value ->
            val safe = value.replace("\"", "\"\"")
            "\"$safe\""
        }
        writer.write(escaped)
        writer.write("\n")
    }
}
