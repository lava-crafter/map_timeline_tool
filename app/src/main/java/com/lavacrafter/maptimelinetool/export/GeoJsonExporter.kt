package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.domain.model.Point
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

object GeoJsonExporter {
    private val utcFormatter: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun writeGeoJson(
        points: List<Point>,
        outputStream: OutputStream,
        includeSensors: Boolean = true,
        pointTagNamesByPointId: Map<Long, List<String>> = emptyMap(),
        photoRelPathResolver: (Point) -> String? = { point -> point.photoPath }
    ) {
        val geoJson = buildGeoJson(
            points = points,
            includeSensors = includeSensors,
            pointTagNamesByPointId = pointTagNamesByPointId,
            photoRelPathResolver = photoRelPathResolver
        )
        outputStream.write(geoJson.toByteArray(Charsets.UTF_8))
    }

    fun buildGeoJson(
        points: List<Point>,
        includeSensors: Boolean = true,
        pointTagNamesByPointId: Map<Long, List<String>> = emptyMap(),
        photoRelPathResolver: (Point) -> String? = { point -> point.photoPath }
    ): String {
        val sdf = utcFormatter
        val features = JSONArray()
        points.forEach { point ->
            val properties = JSONObject().apply {
                put("id", point.id)
                put("name", point.title)
                put("title", point.title)
                put("description", point.note)
                put("note", point.note)
                put("latitude", point.latitude)
                put("longitude", point.longitude)
                put("timestamp_ms", point.timestamp)
                put("time_utc", sdf.format(Date(point.timestamp)))

                val photoRelPath = photoRelPathResolver(point)?.trim().orEmpty()
                if (photoRelPath.isNotEmpty()) {
                    put("photo_rel_path", photoRelPath)
                }

                val tagNames = pointTagNamesByPointId[point.id].orEmpty().filter { it.isNotBlank() }
                if (tagNames.isNotEmpty()) {
                    put("tags", JSONArray(tagNames))
                }

                if (includeSensors) {
                    point.pressureHpa?.let { put("pressure_hpa", it) }
                    point.ambientLightLux?.let { put("ambient_light_lux", it) }
                    point.accelerometerX?.let { put("accelerometer_x", it) }
                    point.accelerometerY?.let { put("accelerometer_y", it) }
                    point.accelerometerZ?.let { put("accelerometer_z", it) }
                    point.gyroscopeX?.let { put("gyroscope_x", it) }
                    point.gyroscopeY?.let { put("gyroscope_y", it) }
                    point.gyroscopeZ?.let { put("gyroscope_z", it) }
                    point.magnetometerX?.let { put("magnetometer_x", it) }
                    point.magnetometerY?.let { put("magnetometer_y", it) }
                    point.magnetometerZ?.let { put("magnetometer_z", it) }
                    point.noiseDb?.let { put("noise_db", it) }
                }
            }

            val geometry = JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().apply {
                    put(point.longitude)
                    put(point.latitude)
                })
            }

            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", geometry)
                    put("properties", properties)
                }
            )
        }

        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString(2)
    }

    fun parsePointsFromGeoJson(
        geoJsonText: String,
        resolvePhotoPath: (String) -> String? = { it }
    ): List<Point> {
        val root = runCatching { JSONObject(geoJsonText) }.getOrNull() ?: return emptyList()
        val features = root.optJSONArray("features") ?: return emptyList()
        val sdf = utcFormatter
        val points = mutableListOf<Point>()

        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index) ?: continue
            val properties = feature.optJSONObject("properties")
            val geometry = feature.optJSONObject("geometry")

            val coordinates = geometry?.optJSONArray("coordinates")
            val lonFromGeometry = coordinates?.optDoubleOrNull(0)
            val latFromGeometry = coordinates?.optDoubleOrNull(1)

            val lat = latFromGeometry
                ?: properties?.optDoubleOrNull("latitude")
                ?: continue
            val lon = lonFromGeometry
                ?: properties?.optDoubleOrNull("longitude")
                ?: continue

            val title = properties?.optString("title")
                ?.takeIf { it.isNotBlank() }
                ?: properties?.optString("name")
                ?: ""
            val note = properties?.optString("note")
                ?.takeIf { it.isNotBlank() }
                ?: properties?.optString("description")
                ?: ""

            val timestampMs = properties?.optLong("timestamp_ms")
                ?.takeIf { it > 0L }
                ?: parseTimestamp(properties?.optString("time_utc"), sdf)

            val relPhotoPath = properties?.optString("photo_rel_path")?.trim().orEmpty()
            val resolvedPhotoPath = relPhotoPath.takeIf { it.isNotEmpty() }?.let(resolvePhotoPath)

            points.add(
                Point(
                    timestamp = timestampMs,
                    latitude = lat,
                    longitude = lon,
                    title = title,
                    note = note,
                    pressureHpa = properties?.optFloatOrNull("pressure_hpa"),
                    ambientLightLux = properties?.optFloatOrNull("ambient_light_lux"),
                    accelerometerX = properties?.optFloatOrNull("accelerometer_x"),
                    accelerometerY = properties?.optFloatOrNull("accelerometer_y"),
                    accelerometerZ = properties?.optFloatOrNull("accelerometer_z"),
                    gyroscopeX = properties?.optFloatOrNull("gyroscope_x"),
                    gyroscopeY = properties?.optFloatOrNull("gyroscope_y"),
                    gyroscopeZ = properties?.optFloatOrNull("gyroscope_z"),
                    magnetometerX = properties?.optFloatOrNull("magnetometer_x"),
                    magnetometerY = properties?.optFloatOrNull("magnetometer_y"),
                    magnetometerZ = properties?.optFloatOrNull("magnetometer_z"),
                    noiseDb = properties?.optFloatOrNull("noise_db"),
                    photoPath = resolvedPhotoPath
                )
            )
        }
        return points
    }

    private fun parseTimestamp(raw: String?, sdf: SimpleDateFormat): Long {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return System.currentTimeMillis()
        val parsedDate = runCatching { sdf.parse(value) }.getOrNull()
        if (parsedDate != null) return parsedDate.time
        return value.toLongOrNull() ?: System.currentTimeMillis()
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val parsed = optDouble(key, Double.NaN)
        return parsed.takeIf { it.isFinite() }
    }

    private fun JSONObject.optFloatOrNull(key: String): Float? {
        val doubleValue = optDoubleOrNull(key) ?: return null
        return doubleValue.toFloat().takeIf { it.isFinite() }
    }

    private fun JSONArray.optDoubleOrNull(index: Int): Double? {
        if (index !in 0 until length()) return null
        val parsed = optDouble(index, Double.NaN)
        return parsed.takeIf { it.isFinite() }
    }
}