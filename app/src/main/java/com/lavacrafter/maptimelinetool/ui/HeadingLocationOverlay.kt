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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.location.Location
import com.lavacrafter.maptimelinetool.LocationUtils
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.compass.IOrientationConsumer
import org.osmdroid.views.overlay.compass.IOrientationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider

class HeadingLocationOverlay(private val context: Context) : Overlay(), IMyLocationConsumer, IOrientationConsumer {
    var location: Location? = null
    @Volatile
    var invalidateMap: (() -> Unit)? = null
    private var bearing: Float = 0f
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        style = Paint.Style.FILL
    }

    override fun onLocationChanged(location: Location?, provider: IMyLocationProvider?) {
        if (location != null) {
            this.location = location
            LocationUtils.cacheLocation(context, location)
            invalidateMap?.invoke()
        }
    }

    override fun onOrientationChanged(orientation: Float, provider: IOrientationProvider?) {
        bearing = orientation
        invalidateMap?.invoke()
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val loc = location ?: return
        val projection = mapView.projection ?: return
        val point = projection.toPixels(GeoPoint(loc.latitude, loc.longitude), Point())
        val density = context.resources.displayMetrics.density
        val circleRadius = 8f * density
        val arrowSize = 16f * density
        val arrowWidth = 12f * density

        val cx = point.x.toFloat()
        val cy = point.y.toFloat()

        canvas.drawCircle(cx, cy, circleRadius, circlePaint)

        val path = Path().apply {
            moveTo(0f, -arrowSize)
            lineTo(arrowWidth / 2f, 0f)
            lineTo(-arrowWidth / 2f, 0f)
            close()
        }
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(bearing)
        canvas.drawPath(path, arrowPaint)
        canvas.restore()
    }

    companion object {
        const val LOCATION_PREFS = "location_cache"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_TIME = "time"
        const val KEY_ACCURACY = "accuracy"
        const val KEY_PROVIDER = "provider"
    }
}
