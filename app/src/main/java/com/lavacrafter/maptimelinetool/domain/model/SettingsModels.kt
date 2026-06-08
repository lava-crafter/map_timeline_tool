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
    CHINESE_SIMPLIFIED(2, "zh-CN"),
    CHINESE_TRADITIONAL(3, "zh-TW"),
    JAPANESE(4, "ja"),
    KOREAN(5, "ko"),
    SPANISH(6, "es"),
    FRENCH(7, "fr"),
    PORTUGUESE(8, "pt"),
    ARABIC(9, "ar"),
    RUSSIAN(10, "ru"),
    HEBREW(11, "he");

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
