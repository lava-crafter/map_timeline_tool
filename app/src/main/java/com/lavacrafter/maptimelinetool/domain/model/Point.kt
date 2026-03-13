package com.lavacrafter.maptimelinetool.domain.model

data class Point(
    val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val note: String,
    val pressureHpa: Float? = null,
    val ambientLightLux: Float? = null,
    val accelerometerX: Float? = null,
    val accelerometerY: Float? = null,
    val accelerometerZ: Float? = null,
    val gyroscopeX: Float? = null,
    val gyroscopeY: Float? = null,
    val gyroscopeZ: Float? = null,
    val magnetometerX: Float? = null,
    val magnetometerY: Float? = null,
    val magnetometerZ: Float? = null,
    val noiseDb: Float? = null,
    val photoPath: String? = null
)
