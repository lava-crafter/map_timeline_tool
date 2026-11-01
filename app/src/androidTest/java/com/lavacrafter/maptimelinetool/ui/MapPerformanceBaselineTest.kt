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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lavacrafter.maptimelinetool.NetworkStatus
import com.lavacrafter.maptimelinetool.data.PointEntity
import java.util.IdentityHashMap
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * C10 baseline: records timings for 1k/5k/10k points; only the per-update Marker allocation is asserted.
 * Opt-in: `-Pandroid.testInstrumentationRunnerArguments.perfBaseline=true`; results go to logcat tag
 * [TAG] and the instrumentation status.
 */
@RunWith(AndroidJUnit4::class)
class MapPerformanceBaselineTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var points by mutableStateOf(emptyList<PointEntity>())

    @Before fun optIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("perfBaseline") == "true")
        compose.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Test fun baseline1k() = measure(1_000)
    @Test fun baseline5k() = measure(5_000)
    @Test fun baseline10k() = measure(10_000)

    private fun measure(count: Int) {
        val now = System.currentTimeMillis()
        // Today's points, so every marker carries its order badge (the costlier icon path).
        val generated = List(count) { index ->
            PointEntity(id = index + 1L, timestamp = now - index * 1_000L,
                latitude = 31.2 + (index % 100) * 0.0002, longitude = 121.4 + (index / 100) * 0.0002,
                title = "Point $index", note = "note $index")
        }
        val start = System.nanoTime()
        points = generated
        compose.setContent {
            MaterialTheme {
                MapScreen(points = points, selectedPointId = null, onEditPoint = {}, isActive = true,
                    zoomBehavior = ZoomButtonBehavior.ALWAYS, markerScale = 1f,
                    mapAccessPolicy = resolveMapTileAccessPolicy(MapCachePolicy.DISABLED, NetworkStatus.NONE),
                    mapTileSourceId = "mapnik", onMapTileSourceChange = {}, onResolveCenterLocation = { it(null) })
            }
        }
        compose.waitUntil(120_000) { compose.runOnIdle { markers().size == count } }
        val firstScreenMs = (System.nanoTime() - start) / 1e6

        val updateMs = ArrayList<Double>(); val allocations = ArrayList<Int>()
        repeat(5) { round ->
            val before = compose.runOnIdle { IdentityHashMap<Marker, Unit>().apply { markers().forEach { put(it, Unit) } } }
            val target = (round * 997) % count
            val title = "Edited $round"
            val updateStart = System.nanoTime()
            compose.runOnIdle { points = points.toMutableList().also { it[target] = it[target].copy(title = title) } }
            compose.waitUntil(30_000) { compose.runOnIdle { markers().any { it.id == (target + 1L).toString() && it.title == title } } }
            updateMs += (System.nanoTime() - updateStart) / 1e6
            allocations += compose.runOnIdle { markers().count { it !in before } }
        }

        val (panMs, nearestMs) = compose.runOnIdle {
            val map = requireNotNull(findMapView(compose.activity.window.decorView))
            val bitmap = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val pan = List(20) { step ->
                val t = System.nanoTime()
                map.scrollBy(if (step % 2 == 0) 120 else -120, 40)
                map.draw(canvas)
                (System.nanoTime() - t) / 1e6
            }
            bitmap.recycle()
            val center = map.mapCenter
            val nearest = List(20) {
                val t = System.nanoTime()
                findNearestPoint(map, points, GeoPoint(center.latitude, center.longitude), 48f)
                (System.nanoTime() - t) / 1e6
            }
            pan to nearest
        }

        val line = "points=$count firstScreenMs=${"%.0f".format(firstScreenMs)} " +
            "updateMs(median)=${updateMs.median()} newMarkersPerUpdate=$allocations " +
            "panDrawMs(median/p90)=${panMs.median()}/${panMs.p90()} nearestMs(median/p90)=${nearestMs.median()}/${nearestMs.p90()}"
        Log.i(TAG, line)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("perf", line) })
        assertEquals("a single point update must rebuild only its own marker", List(5) { 1 }, allocations)
    }

    private fun markers(): List<Marker> =
        findMapView(compose.activity.window.decorView)?.overlays?.filterIsInstance<Marker>().orEmpty()

    private fun List<Double>.median() = "%.1f".format(sorted()[size / 2])
    private fun List<Double>.p90() = "%.1f".format(sorted()[(size * 9) / 10 - 1])

    private fun findMapView(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findMapView(view.getChildAt(index))?.let { return it }
        return null
    }

    private companion object { const val TAG = "MapPerformanceBaseline" }
}
