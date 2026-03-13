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
}

private fun android.location.Location.toGeoPoint(): GeoPoint = GeoPoint(
    latitude = latitude,
    longitude = longitude
)
