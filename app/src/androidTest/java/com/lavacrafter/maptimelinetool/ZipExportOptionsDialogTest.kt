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

import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Component test only: the SAF callback and exported bytes need separate verification. */
@RunWith(AndroidJUnit4::class)
class ZipExportOptionsDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun keepTestHostVisible() {
        compose.runOnUiThread {
            compose.activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    @Test
    fun exportDisabledWhenNeitherPointsNorPhotosAreSelected() {
        var confirmations = 0
        compose.setContent {
            ZipExportOptionsDialog(
                includePoints = false,
                includeTags = true,
                includeSensors = false,
                includePhotos = false,
                onIncludePointsChange = {},
                onIncludeTagsChange = {},
                onIncludeSensorsChange = {},
                onIncludePhotosChange = {},
                onConfirm = { confirmations++ },
                onDismiss = {}
            )
        }

        compose.onNodeWithText(label(R.string.action_export_zip)).assertIsNotEnabled()
        assertEquals(0, confirmations)
    }

    @Test
    fun photosWithoutPointsCannotBeConfirmed() {
        compose.setContent {
            ZipExportOptionsDialog(
                includePoints = false,
                includeTags = false,
                includeSensors = false,
                includePhotos = true,
                onIncludePointsChange = {},
                onIncludeTagsChange = {},
                onIncludeSensorsChange = {},
                onIncludePhotosChange = {},
                onConfirm = {},
                onDismiss = {}
            )
        }

        compose.onAllNodes(isToggleable())[3].assertIsNotEnabled() // Photos checkbox
        compose.onNodeWithText(label(R.string.action_export_zip)).assertIsNotEnabled()
    }

    @Test
    fun selectingPointsEnablesPhotosAndForwardsValidOptions() {
        var includePoints by mutableStateOf(false)
        var includePhotos by mutableStateOf(false)
        var exportedOptions: Pair<Boolean, Boolean>? = null
        compose.setContent {
            ZipExportOptionsDialog(
                includePoints = includePoints,
                includeTags = false,
                includeSensors = false,
                includePhotos = includePhotos,
                onIncludePointsChange = { includePoints = it },
                onIncludeTagsChange = {},
                onIncludeSensorsChange = {},
                onIncludePhotosChange = { includePhotos = it },
                onConfirm = { exportedOptions = includePoints to includePhotos },
                onDismiss = {}
            )
        }
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onAllNodes(isToggleable())[3].assertIsEnabled().performClick()
        compose.onNodeWithText(label(R.string.action_export_zip)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(true to true, exportedOptions) }
    }

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
