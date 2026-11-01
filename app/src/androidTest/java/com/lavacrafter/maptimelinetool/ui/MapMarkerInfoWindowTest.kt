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

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavacrafter.maptimelinetool.NetworkStatus
import com.lavacrafter.maptimelinetool.data.PointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@RunWith(AndroidJUnit4::class)
class MapMarkerInfoWindowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var points by mutableStateOf(listOf(
        PointEntity(id = 1, timestamp = 1_000L, latitude = 31.2, longitude = 121.4, title = "A", note = ""),
        PointEntity(id = 2, timestamp = 2_000L, latitude = 31.201, longitude = 121.401, title = "B", note = "")
    ))

    @Test fun markersShareOneBubbleThatEditsTheLatestOpenPoint() {
        val edited = mutableListOf<PointEntity>()
        compose.setContent {
            MaterialTheme {
                MapScreen(points = points, selectedPointId = null, onEditPoint = { edited += it }, isActive = true,
                    zoomBehavior = ZoomButtonBehavior.ALWAYS, markerScale = 1f,
                    mapAccessPolicy = resolveMapTileAccessPolicy(MapCachePolicy.DISABLED, NetworkStatus.NONE),
                    mapTileSourceId = "mapnik", onMapTileSourceChange = {}, onResolveCenterLocation = { it(null) })
            }
        }
        compose.waitUntil(10_000) { compose.runOnIdle { markers().size == 2 } }
        compose.runOnIdle {
            val (a, b) = markers().sortedBy { it.id }
            assertSame(a.infoWindow, b.infoWindow)
            a.showInfoWindow(); a.infoWindow.view.performClick()
            b.showInfoWindow(); b.infoWindow.view.performClick()
            points = listOf(points[0].copy(title = "A edited"), points[1])
        }
        compose.waitUntil(10_000) { compose.runOnIdle { markers().any { it.title == "A edited" } } }
        compose.runOnIdle {
            val a = markers().single { it.id == "1" }
            a.showInfoWindow(); a.infoWindow.view.performClick()
        }
        assertEquals(listOf("A", "B", "A edited"), edited.map { it.title })
    }

    private fun markers(): List<Marker> =
        findMapView(compose.activity.window.decorView)?.overlays?.filterIsInstance<Marker>().orEmpty()

    private fun findMapView(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findMapView(view.getChildAt(index))?.let { return it }
        return null
    }
}
