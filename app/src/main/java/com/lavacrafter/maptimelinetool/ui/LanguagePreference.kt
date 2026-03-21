package com.lavacrafter.maptimelinetool.ui

enum class LanguagePreference(val value: Int, val localeTag: String?) {
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
        fun fromValue(value: Int): LanguagePreference {
            return values().firstOrNull { it.value == value } ?: FOLLOW_SYSTEM
        }
    }
}
