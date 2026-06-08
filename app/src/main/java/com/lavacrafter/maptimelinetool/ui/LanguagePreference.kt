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
