package com.lavacrafter.maptimelinetool.domain.port

import com.lavacrafter.maptimelinetool.domain.model.GeoPoint

interface LocationProvider {
    fun getLastKnownLocation(): GeoPoint?
    suspend fun getFreshLocation(timeoutMs: Long): GeoPoint?
}
