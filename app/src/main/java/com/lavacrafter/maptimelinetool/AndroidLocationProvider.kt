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

package com.lavacrafter.maptimelinetool

import android.content.Context
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider

class AndroidLocationProvider(
    private val context: Context
) : LocationProvider {
    override fun getLastKnownLocation(): GeoPoint? =
        LocationUtils.getLastKnownLocation(context)?.toGeoPoint()

    override suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint? =
        LocationUtils.getPreciseLocation(context, timeoutMs)?.toGeoPoint()

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
