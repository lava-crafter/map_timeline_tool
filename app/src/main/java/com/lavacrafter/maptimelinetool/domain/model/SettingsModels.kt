package com.lavacrafter.maptimelinetool.domain.model

enum class SettingsMapCachePolicy(val value: Int) {
    DISABLED(0),
    WIFI_ONLY(1),
    ALWAYS(2);

    companion object {
        fun fromValue(value: Int): SettingsMapCachePolicy = values().firstOrNull { it.value == value } ?: ALWAYS
    }
}

enum class SettingsZoomButtonBehavior(val value: Int) {
    HIDE(0),
    WHEN_ACTIVE(1),
    ALWAYS(2);

    companion object {
        fun fromValue(value: Int): SettingsZoomButtonBehavior = values().firstOrNull { it.value == value } ?: HIDE
    }
}

enum class SettingsLanguagePreference(val value: Int, val localeTag: String?) {
    FOLLOW_SYSTEM(0, null),
    ENGLISH(1, "en"),
    CHINESE(2, "zh");

    companion object {
        fun fromValue(value: Int): SettingsLanguagePreference =
            values().firstOrNull { it.value == value } ?: FOLLOW_SYSTEM
    }
}

enum class SettingsPhotoCompressFormat(val value: Int) {
    JPEG(0),
    PNG(1),
    WEBP(2);

    companion object {
        fun fromValue(value: Int): SettingsPhotoCompressFormat =
            values().firstOrNull { it.value == value } ?: JPEG
    }
}

data class SettingsDownloadedArea(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val createdAt: Long = System.currentTimeMillis()
)
