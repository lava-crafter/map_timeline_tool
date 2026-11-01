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

import android.Manifest
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.lavacrafter.maptimelinetool.NetworkStatus
import com.lavacrafter.maptimelinetool.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.osmdroid.views.MapView

/** Tests UI and provider configuration; no assertion depends on live tile requests succeeding. */
@RunWith(AndroidJUnit4::class)
class OnlineMapScreenTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        ))
        .around(compose)

    private var sourceId by mutableStateOf("mapnik")
    private var policy by mutableStateOf(resolveMapTileAccessPolicy(MapCachePolicy.DISABLED, NetworkStatus.CELLULAR))

    @Before
    fun showMap() {
        compose.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.setContent {
            MaterialTheme {
                MapScreen(
                    points = emptyList(), selectedPointId = null, onEditPoint = {}, isActive = true,
                    zoomBehavior = ZoomButtonBehavior.ALWAYS, markerScale = 1f,
                    mapAccessPolicy = policy, mapTileSourceId = sourceId,
                    onMapTileSourceChange = { sourceId = it }, onResolveCenterLocation = { it(null) }
                )
            }
        }
    }

    @Test
    fun attributionIsVisibleAndUpdatesWithTheActualLayerControl() {
        for (source in mapTileSources) {
            compose.onNodeWithTag("map_screen").assertIsDisplayed()
            val attribution = compose.onNodeWithTag("map_attribution")
                .assertIsDisplayed().assertTextEquals(compose.activity.getString(source.attributionRes))
                .fetchSemanticsNode().boundsInRoot
            for (description in listOf(compose.activity.getString(R.string.action_cycle_map_layer), "Zoom In", "Zoom Out")) {
                val button = compose.onNodeWithContentDescription(description).assertIsDisplayed()
                    .fetchSemanticsNode().boundsInRoot
                assertFalse("Attribution overlaps $description", attribution.overlaps(button))
            }
            val oldProvider = compose.runOnIdle {
                requireNotNull(findMapView(compose.activity.window.decorView)).tileProvider
            }
            val oldCache = oldProvider.tileWriter as PolicyAwareFilesystemCache
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_cycle_map_layer)).performClick()
            compose.runOnIdle {
                val map = requireNotNull(findMapView(compose.activity.window.decorView))
                assertNotSame(oldProvider, map.tileProvider)
                assertFalse(oldCache.allowWrites)
            }
        }
    }

    @Test
    fun cacheAndNetworkTransitionsKeepOnlineAccessForBothSources() {
        for (source in mapTileSources) {
            for (cachePolicy in MapCachePolicy.entries) {
                for (networkStatus in NetworkStatus.entries) {
                    val expected = resolveMapTileAccessPolicy(cachePolicy, networkStatus)
                    compose.runOnIdle { sourceId = source.id; policy = expected }
                    compose.onNodeWithTag("map_attribution").assertIsDisplayed()
                    compose.runOnIdle {
                        val map = requireNotNull(findMapView(compose.activity.window.decorView))
                        assertTrue(map.useDataConnection())
                        assertTrue(map.tileProvider.useDataConnection())
                        assertTrue(map.tileProvider is OnlineMapTileProvider)
                        assertEquals(source.toOsmdroidSource(compose.activity).name(), map.tileProvider.tileSource.name())
                        val cache = map.tileProvider.tileWriter as PolicyAwareFilesystemCache
                        assertEquals(expected.allowCacheWrites, cache.allowWrites)
                    }
                }
            }
        }
    }

    private fun findMapView(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findMapView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
