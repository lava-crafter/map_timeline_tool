package com.lavacrafter.maptimelinetool.domain.model

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val fixTimeMs: Long? = null,
    val provider: String? = null
)
