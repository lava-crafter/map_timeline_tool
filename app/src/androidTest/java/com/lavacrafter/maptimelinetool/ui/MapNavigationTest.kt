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
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.lavacrafter.maptimelinetool.MainActivity
import com.lavacrafter.maptimelinetool.R
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapNavigationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        ))
        .around(compose)

    @Test
    fun normalSettingsHaveNoAreaDownloadEntryAndTheMapIsStillAvailable() {
        compose.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.onNodeWithTag("map_screen").assertIsDisplayed()
        compose.onNodeWithTag("map_attribution").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.tab_settings)).performClick()
        assertNoDownloads()

        for (title in listOf(R.string.settings_map_operations_title, R.string.settings_cache_title)) {
            compose.onNodeWithText(label(title)).performScrollTo().performClick()
            assertNoDownloads()
            compose.onNodeWithContentDescription(label(R.string.action_back)).performClick()
        }

        compose.onNodeWithText(label(R.string.tab_map)).performClick()
        compose.onNodeWithTag("map_screen").assertIsDisplayed()
        compose.onNodeWithTag("map_attribution").assertIsDisplayed()
    }

    private fun assertNoDownloads() {
        compose.onNodeWithText(label(R.string.settings_download_title)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.settings_download_desc)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.settings_map_download_title)).assertDoesNotExist()
    }

    private fun label(id: Int): String = compose.activity.getString(id)
}
