package com.lavacrafter.maptimelinetool

import android.content.Context
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider

class AndroidLocationProvider(
    private val context: Context
) : LocationProvider {
    override fun getLastKnownLocation(): GeoPoint? =
        LocationUtils.getLastKnownLocation(context)?.toGeoPoint()

    override suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? =
        LocationUtils.getFreshLocation(context, timeoutMs)?.toGeoPoint()

    override suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? =
        LocationUtils.getBestEffortLocation(context, timeoutMs)?.toGeoPoint()
}

private fun android.location.Location.toGeoPoint(): GeoPoint = GeoPoint(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    fixTimeMs = time.takeIf { it > 0L },
    provider = provider
)
