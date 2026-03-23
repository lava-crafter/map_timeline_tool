package com.lavacrafter.maptimelinetool.ui

import android.content.Context

object SettingsStore {
    private const val PREFS = "map_timeline_settings"
    private const val KEY_TIMEOUT = "timeout_seconds"
    private const val KEY_CACHE_POLICY = "cache_policy"
    private const val KEY_SATELLITE_CACHE_POLICY = "satellite_cache_policy"
    private const val KEY_PINNED_TAGS = "pinned_tags"
    private const val KEY_RECENT_TAGS = "recent_tags"
    private const val KEY_ZOOM_BEHAVIOR = "zoom_behavior"
    private const val KEY_LANGUAGE_PREFERENCE = "language_preference"
    private const val KEY_FOLLOW_SYSTEM_THEME = "follow_system_theme"
    private const val KEY_DEFAULT_TAGS = "default_tags"
    private const val KEY_MARKER_SCALE = "marker_scale"
    private const val KEY_MAP_TILE_SOURCE = "map_tile_source"
    private const val KEY_DOWNLOADED_AREAS = "downloaded_areas"
    private const val KEY_DOWNLOAD_TILE_SOURCE = "download_tile_source"
    private const val KEY_DOWNLOAD_MULTI_THREAD = "download_multi_thread"
    private const val KEY_DOWNLOAD_THREAD_COUNT = "download_thread_count"
    private const val KEY_PHOTO_LOSSLESS_ENABLED = "photo_lossless_enabled"
    private const val KEY_PHOTO_COMPRESS_FORMAT = "photo_compress_format"
    private const val KEY_PHOTO_COMPRESS_QUALITY = "photo_compress_quality"
    private const val KEY_PRESSURE_ENABLED = "pressure_enabled"
    private const val KEY_AMBIENT_LIGHT_ENABLED = "ambient_light_enabled"
    private const val KEY_ACCELEROMETER_ENABLED = "accelerometer_enabled"
    private const val KEY_GYROSCOPE_ENABLED = "gyroscope_enabled"
    private const val KEY_MAGNETOMETER_ENABLED = "magnetometer_enabled"
    private const val KEY_NOISE_ENABLED = "noise_enabled"
    private const val SETTINGS_SCHEMA_VERSION = 1
    private const val MAX_RECENT_TAGS = 3

    fun getTimeoutSeconds(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_TIMEOUT, 20)
            .coerceIn(5, 300)
    }

    fun setTimeoutSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TIMEOUT, seconds.coerceIn(5, 300))
            .apply()
    }

    fun getCachePolicy(context: Context): MapCachePolicy {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CACHE_POLICY, MapCachePolicy.WIFI_ONLY.value)
        return MapCachePolicy.fromValue(value)
    }

    fun setCachePolicy(context: Context, policy: MapCachePolicy) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CACHE_POLICY, policy.value)
            .apply()
    }

    fun getSatelliteCachePolicy(context: Context): MapCachePolicy {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SATELLITE_CACHE_POLICY, MapCachePolicy.WIFI_ONLY.value)
        return MapCachePolicy.fromValue(value)
    }

    fun setSatelliteCachePolicy(context: Context, policy: MapCachePolicy) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SATELLITE_CACHE_POLICY, policy.value)
            .apply()
    }

    fun getPinnedTagIds(context: Context): List<Long> {
        return parseLongList(context, KEY_PINNED_TAGS)
    }

    fun setPinnedTagIds(context: Context, tagIds: List<Long>) {
        saveLongList(context, KEY_PINNED_TAGS, tagIds)
    }

    fun getRecentTagIds(context: Context): List<Long> {
        return parseLongList(context, KEY_RECENT_TAGS)
    }

    fun addRecentTagId(context: Context, tagId: Long): List<Long> {
        val updated = getRecentTagIds(context).toMutableList().apply {
            remove(tagId)
            add(0, tagId)
            while (size > MAX_RECENT_TAGS) removeLast()
        }
        saveLongList(context, KEY_RECENT_TAGS, updated)
        return updated
    }

    fun getZoomButtonBehavior(context: Context): ZoomButtonBehavior {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ZOOM_BEHAVIOR, ZoomButtonBehavior.HIDE.value)
        return ZoomButtonBehavior.fromValue(value)
    }

    fun setZoomButtonBehavior(context: Context, behavior: ZoomButtonBehavior) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ZOOM_BEHAVIOR, behavior.value)
            .apply()
    }

    fun getLanguagePreference(context: Context): LanguagePreference {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LANGUAGE_PREFERENCE, LanguagePreference.FOLLOW_SYSTEM.value)
        return LanguagePreference.fromValue(value)
    }

    fun setLanguagePreference(context: Context, preference: LanguagePreference) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LANGUAGE_PREFERENCE, preference.value)
            .apply()
    }

    fun getFollowSystemTheme(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FOLLOW_SYSTEM_THEME, true)
    }

    fun setFollowSystemTheme(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FOLLOW_SYSTEM_THEME, enabled)
            .apply()
    }

    fun getDefaultTagIds(context: Context): List<Long> {
        return parseLongList(context, KEY_DEFAULT_TAGS)
    }

    fun setDefaultTagIds(context: Context, tagIds: List<Long>) {
        saveLongList(context, KEY_DEFAULT_TAGS, tagIds)
    }

    fun getMarkerScale(context: Context): Float {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_MARKER_SCALE, 1.0f)
            .coerceIn(0.3f, 1.75f)
    }

    fun setMarkerScale(context: Context, scale: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_MARKER_SCALE, scale.coerceIn(0.3f, 1.75f))
            .apply()
    }

    fun getMapTileSourceId(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MAP_TILE_SOURCE, mapTileSources.first().id)
            ?: mapTileSources.first().id
    }

    fun setMapTileSourceId(context: Context, sourceId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAP_TILE_SOURCE, sourceId)
            .apply()
    }

    fun getDownloadTileSourceId(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOAD_TILE_SOURCE, downloadTileSources.first().id)
            ?: downloadTileSources.first().id
    }

    fun setDownloadTileSourceId(context: Context, sourceId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOWNLOAD_TILE_SOURCE, sourceId)
            .apply()
    }

    fun getDownloadMultiThreadEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DOWNLOAD_MULTI_THREAD, false)
    }

    fun setDownloadMultiThreadEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DOWNLOAD_MULTI_THREAD, enabled)
            .apply()
    }

    fun getDownloadThreadCount(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DOWNLOAD_THREAD_COUNT, 4)
            .coerceIn(2, 32)
    }

    fun setDownloadThreadCount(context: Context, count: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DOWNLOAD_THREAD_COUNT, count.coerceIn(2, 32))
            .apply()
    }

    fun getPhotoLosslessEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PHOTO_LOSSLESS_ENABLED, true)
    }

    fun setPhotoLosslessEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PHOTO_LOSSLESS_ENABLED, enabled)
            .apply()
    }

    fun getPhotoCompressFormat(context: Context): PhotoCompressFormat {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PHOTO_COMPRESS_FORMAT, PhotoCompressFormat.JPEG.value)
        return PhotoCompressFormat.fromValue(value)
    }

    fun setPhotoCompressFormat(context: Context, format: PhotoCompressFormat) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PHOTO_COMPRESS_FORMAT, format.value)
            .apply()
    }

    fun getPhotoCompressQuality(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PHOTO_COMPRESS_QUALITY, 80)
            .coerceIn(1, 100)
    }

    fun setPhotoCompressQuality(context: Context, quality: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PHOTO_COMPRESS_QUALITY, quality.coerceIn(1, 100))
            .apply()
    }

    fun getPressureEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRESSURE_ENABLED, true)

    fun setPressureEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRESSURE_ENABLED, enabled)
            .apply()
    }

    fun getAmbientLightEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AMBIENT_LIGHT_ENABLED, true)

    fun setAmbientLightEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AMBIENT_LIGHT_ENABLED, enabled)
            .apply()
    }

    fun getAccelerometerEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCELEROMETER_ENABLED, true)

    fun setAccelerometerEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCELEROMETER_ENABLED, enabled)
            .apply()
    }

    fun getGyroscopeEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GYROSCOPE_ENABLED, true)

    fun setGyroscopeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GYROSCOPE_ENABLED, enabled)
            .apply()
    }

    fun getMagnetometerEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MAGNETOMETER_ENABLED, true)

    fun setMagnetometerEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MAGNETOMETER_ENABLED, enabled)
            .apply()
    }

    fun getNoiseEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOISE_ENABLED, false)
    }

    fun setNoiseEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOISE_ENABLED, enabled)
            .apply()
    }

    fun getDownloadedAreas(context: Context): List<DownloadedArea> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOADED_AREAS, null)
            ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        DownloadedArea(
                            north = obj.getDouble("north"),
                            south = obj.getDouble("south"),
                            east = obj.getDouble("east"),
                            west = obj.getDouble("west"),
                            minZoom = obj.getInt("minZoom"),
                            maxZoom = obj.getInt("maxZoom"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addDownloadedArea(context: Context, area: DownloadedArea): List<DownloadedArea> {
        val updated = getDownloadedAreas(context).toMutableList().apply { add(area) }
        val deduped = dedupeAreas(updated)
        saveDownloadedAreas(context, deduped)
        return deduped
    }

    fun removeDownloadedArea(context: Context, area: DownloadedArea): List<DownloadedArea> {
        val updated = getDownloadedAreas(context).filterNot { it.boundsKey() == area.boundsKey() }
        saveDownloadedAreas(context, updated)
        return updated
    }

    fun dedupeDownloadedAreas(context: Context): List<DownloadedArea> {
        val deduped = dedupeAreas(getDownloadedAreas(context))
        saveDownloadedAreas(context, deduped)
        return deduped
    }

    fun exportBackupJson(context: Context): String {
        val root = org.json.JSONObject()
        root.put("schema_version", SETTINGS_SCHEMA_VERSION)
        root.put(KEY_TIMEOUT, getTimeoutSeconds(context))
        root.put(KEY_CACHE_POLICY, getCachePolicy(context).value)
        root.put(KEY_SATELLITE_CACHE_POLICY, getSatelliteCachePolicy(context).value)
        root.put(KEY_PINNED_TAGS, org.json.JSONArray(getPinnedTagIds(context)))
        root.put(KEY_RECENT_TAGS, org.json.JSONArray(getRecentTagIds(context)))
        root.put(KEY_ZOOM_BEHAVIOR, getZoomButtonBehavior(context).value)
        root.put(KEY_LANGUAGE_PREFERENCE, getLanguagePreference(context).value)
        root.put(KEY_FOLLOW_SYSTEM_THEME, getFollowSystemTheme(context))
        root.put(KEY_DEFAULT_TAGS, org.json.JSONArray(getDefaultTagIds(context)))
        root.put(KEY_MARKER_SCALE, getMarkerScale(context).toDouble())
        root.put(KEY_MAP_TILE_SOURCE, getMapTileSourceId(context))
        root.put(KEY_DOWNLOAD_TILE_SOURCE, getDownloadTileSourceId(context))
        root.put(KEY_DOWNLOAD_MULTI_THREAD, getDownloadMultiThreadEnabled(context))
        root.put(KEY_DOWNLOAD_THREAD_COUNT, getDownloadThreadCount(context))
        root.put(KEY_PHOTO_LOSSLESS_ENABLED, getPhotoLosslessEnabled(context))
        root.put(KEY_PHOTO_COMPRESS_FORMAT, getPhotoCompressFormat(context).value)
        root.put(KEY_PHOTO_COMPRESS_QUALITY, getPhotoCompressQuality(context))
        root.put(KEY_PRESSURE_ENABLED, getPressureEnabled(context))
        root.put(KEY_AMBIENT_LIGHT_ENABLED, getAmbientLightEnabled(context))
        root.put(KEY_ACCELEROMETER_ENABLED, getAccelerometerEnabled(context))
        root.put(KEY_GYROSCOPE_ENABLED, getGyroscopeEnabled(context))
        root.put(KEY_MAGNETOMETER_ENABLED, getMagnetometerEnabled(context))
        root.put(KEY_NOISE_ENABLED, getNoiseEnabled(context))
        val downloadedAreas = org.json.JSONArray()
        getDownloadedAreas(context).forEach { area ->
            val areaObj = org.json.JSONObject()
            areaObj.put("north", area.north)
            areaObj.put("south", area.south)
            areaObj.put("east", area.east)
            areaObj.put("west", area.west)
            areaObj.put("minZoom", area.minZoom)
            areaObj.put("maxZoom", area.maxZoom)
            areaObj.put("createdAt", area.createdAt)
            downloadedAreas.put(areaObj)
        }
        root.put(KEY_DOWNLOADED_AREAS, downloadedAreas)
        return root.toString()
    }

    fun importBackupJson(context: Context, json: String): Boolean {
        return runCatching {
            val root = org.json.JSONObject(json)
            if (root.has(KEY_TIMEOUT)) setTimeoutSeconds(context, root.optInt(KEY_TIMEOUT, getTimeoutSeconds(context)))
            if (root.has(KEY_CACHE_POLICY)) setCachePolicy(context, MapCachePolicy.fromValue(root.optInt(KEY_CACHE_POLICY, getCachePolicy(context).value)))
            if (root.has(KEY_SATELLITE_CACHE_POLICY)) {
                setSatelliteCachePolicy(context, MapCachePolicy.fromValue(root.optInt(KEY_SATELLITE_CACHE_POLICY, getSatelliteCachePolicy(context).value)))
            }
            if (root.has(KEY_PINNED_TAGS)) setPinnedTagIds(context, parseLongArray(root.optJSONArray(KEY_PINNED_TAGS)))
            if (root.has(KEY_RECENT_TAGS)) {
                // Validate external backup payload and keep in-app recent tags limit.
                saveLongList(context, KEY_RECENT_TAGS, parseLongArray(root.optJSONArray(KEY_RECENT_TAGS)).take(MAX_RECENT_TAGS))
            }
            if (root.has(KEY_ZOOM_BEHAVIOR)) {
                setZoomButtonBehavior(context, ZoomButtonBehavior.fromValue(root.optInt(KEY_ZOOM_BEHAVIOR, getZoomButtonBehavior(context).value)))
            }
            if (root.has(KEY_LANGUAGE_PREFERENCE)) {
                setLanguagePreference(context, LanguagePreference.fromValue(root.optInt(KEY_LANGUAGE_PREFERENCE, getLanguagePreference(context).value)))
            }
            if (root.has(KEY_FOLLOW_SYSTEM_THEME)) setFollowSystemTheme(context, root.optBoolean(KEY_FOLLOW_SYSTEM_THEME, getFollowSystemTheme(context)))
            if (root.has(KEY_DEFAULT_TAGS)) setDefaultTagIds(context, parseLongArray(root.optJSONArray(KEY_DEFAULT_TAGS)))
            if (root.has(KEY_MARKER_SCALE)) setMarkerScale(context, root.optDouble(KEY_MARKER_SCALE, getMarkerScale(context).toDouble()).toFloat())
            if (root.has(KEY_MAP_TILE_SOURCE)) setMapTileSourceId(context, root.optString(KEY_MAP_TILE_SOURCE, getMapTileSourceId(context)))
            if (root.has(KEY_DOWNLOAD_TILE_SOURCE)) setDownloadTileSourceId(context, root.optString(KEY_DOWNLOAD_TILE_SOURCE, getDownloadTileSourceId(context)))
            if (root.has(KEY_DOWNLOAD_MULTI_THREAD)) {
                setDownloadMultiThreadEnabled(context, root.optBoolean(KEY_DOWNLOAD_MULTI_THREAD, getDownloadMultiThreadEnabled(context)))
            }
            if (root.has(KEY_DOWNLOAD_THREAD_COUNT)) {
                setDownloadThreadCount(context, root.optInt(KEY_DOWNLOAD_THREAD_COUNT, getDownloadThreadCount(context)))
            }
            if (root.has(KEY_PHOTO_LOSSLESS_ENABLED)) {
                setPhotoLosslessEnabled(context, root.optBoolean(KEY_PHOTO_LOSSLESS_ENABLED, getPhotoLosslessEnabled(context)))
            }
            if (root.has(KEY_PHOTO_COMPRESS_FORMAT)) {
                setPhotoCompressFormat(context, PhotoCompressFormat.fromValue(root.optInt(KEY_PHOTO_COMPRESS_FORMAT, getPhotoCompressFormat(context).value)))
            }
            if (root.has(KEY_PHOTO_COMPRESS_QUALITY)) setPhotoCompressQuality(context, root.optInt(KEY_PHOTO_COMPRESS_QUALITY, getPhotoCompressQuality(context)))
            if (root.has(KEY_PRESSURE_ENABLED)) setPressureEnabled(context, root.optBoolean(KEY_PRESSURE_ENABLED, getPressureEnabled(context)))
            if (root.has(KEY_AMBIENT_LIGHT_ENABLED)) setAmbientLightEnabled(context, root.optBoolean(KEY_AMBIENT_LIGHT_ENABLED, getAmbientLightEnabled(context)))
            if (root.has(KEY_ACCELEROMETER_ENABLED)) setAccelerometerEnabled(context, root.optBoolean(KEY_ACCELEROMETER_ENABLED, getAccelerometerEnabled(context)))
            if (root.has(KEY_GYROSCOPE_ENABLED)) setGyroscopeEnabled(context, root.optBoolean(KEY_GYROSCOPE_ENABLED, getGyroscopeEnabled(context)))
            if (root.has(KEY_MAGNETOMETER_ENABLED)) setMagnetometerEnabled(context, root.optBoolean(KEY_MAGNETOMETER_ENABLED, getMagnetometerEnabled(context)))
            if (root.has(KEY_NOISE_ENABLED)) setNoiseEnabled(context, root.optBoolean(KEY_NOISE_ENABLED, getNoiseEnabled(context)))
            if (root.has(KEY_DOWNLOADED_AREAS)) {
                val areasArray = root.optJSONArray(KEY_DOWNLOADED_AREAS) ?: org.json.JSONArray()
                val areas = buildList {
                    for (i in 0 until areasArray.length()) {
                        val obj = areasArray.optJSONObject(i) ?: continue
                        add(
                            DownloadedArea(
                                north = obj.optDouble("north"),
                                south = obj.optDouble("south"),
                                east = obj.optDouble("east"),
                                west = obj.optDouble("west"),
                                minZoom = obj.optInt("minZoom", 0),
                                maxZoom = obj.optInt("maxZoom", 0),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
                saveDownloadedAreas(context, dedupeAreas(areas))
            }
            true
        }.getOrDefault(false)
    }

    private fun saveDownloadedAreas(context: Context, areas: List<DownloadedArea>) {
        val array = org.json.JSONArray()
        areas.forEach { area ->
            val obj = org.json.JSONObject()
            obj.put("north", area.north)
            obj.put("south", area.south)
            obj.put("east", area.east)
            obj.put("west", area.west)
            obj.put("minZoom", area.minZoom)
            obj.put("maxZoom", area.maxZoom)
            obj.put("createdAt", area.createdAt)
            array.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOWNLOADED_AREAS, array.toString())
            .apply()
    }

    private fun dedupeAreas(areas: List<DownloadedArea>): List<DownloadedArea> {
        if (areas.isEmpty()) return emptyList()
        val sorted = areas.sortedBy { it.createdAt }
        val merged = mutableListOf<DownloadedArea>()
        for (area in sorted) {
            val existingIndex = merged.indexOfFirst { candidate ->
                zoomOverlaps(candidate, area) && bboxOverlaps(candidate, area)
            }
            if (existingIndex >= 0) {
                val candidate = merged[existingIndex]
                merged[existingIndex] = mergeAreas(candidate, area)
            } else {
                merged.add(area)
            }
        }
        return merged
    }

    private fun zoomOverlaps(a: DownloadedArea, b: DownloadedArea): Boolean {
        return a.minZoom <= b.maxZoom && b.minZoom <= a.maxZoom
    }

    private fun bboxOverlaps(a: DownloadedArea, b: DownloadedArea): Boolean {
        val north = minOf(a.north, b.north)
        val south = maxOf(a.south, b.south)
        val west = maxOf(a.west, b.west)
        val east = minOf(a.east, b.east)
        return north >= south && east >= west
    }

    private fun mergeAreas(a: DownloadedArea, b: DownloadedArea): DownloadedArea {
        return DownloadedArea(
            north = maxOf(a.north, b.north),
            south = minOf(a.south, b.south),
            east = maxOf(a.east, b.east),
            west = minOf(a.west, b.west),
            minZoom = minOf(a.minZoom, b.minZoom),
            maxZoom = maxOf(a.maxZoom, b.maxZoom),
            createdAt = minOf(a.createdAt, b.createdAt)
        )
    }

    private fun parseLongArray(array: org.json.JSONArray?): List<Long> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = when (val raw = array.opt(i)) {
                    is Number -> raw.toLong()
                    is String -> raw.toLongOrNull()
                    else -> null
                }
                if (value != null) add(value)
            }
        }
    }

    private fun parseLongList(context: Context, key: String): List<Long> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, null)
            ?: return emptyList()
        return raw.split(',').mapNotNull { it.trim().toLongOrNull() }
    }

    private fun saveLongList(context: Context, key: String, values: List<Long>) {
        val data = values.joinToString(",")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, data)
            .apply()
    }
}
