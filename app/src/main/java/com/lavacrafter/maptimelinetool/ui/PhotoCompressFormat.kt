package com.lavacrafter.maptimelinetool.ui

enum class PhotoCompressFormat(val value: Int) {
    JPEG(0),
    PNG(1),
    WEBP(2);

    companion object {
        fun fromValue(value: Int): PhotoCompressFormat = values().firstOrNull { it.value == value } ?: JPEG
    }
}
