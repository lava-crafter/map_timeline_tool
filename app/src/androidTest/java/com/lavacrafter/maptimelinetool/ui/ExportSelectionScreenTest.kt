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

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lavacrafter.maptimelinetool.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks option propagation, not the SAF writer or CSV file contents. */
@RunWith(AndroidJUnit4::class)
class ExportSelectionScreenTest {
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
    fun pointsOnlyRadioIsNotSelectedWhileIncludeTagsIsChecked() {
        compose.setContent {
            ExportScreens(points = emptyList(), tags = emptyList(), onSelectExport = {}, onBack = {})
        }

        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))[0]
            .assertIsNotSelected()
    }

    @Test
    fun choosingPointsOnlyUpdatesTheExportSelection() {
        var selection: ExportSelection? = null
        compose.setContent {
            ExportScreens(points = emptyList(), tags = emptyList(), onSelectExport = { selection = it }, onBack = {})
        }

        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))[0]
            .performClick()
        compose.onNodeWithText(label(R.string.action_next)).performClick()
        compose.onNodeWithText(label(R.string.export_all)).performClick()

        compose.runOnIdle { assertEquals(ExportSelection(false, ExportKind.All), selection) }
    }

    @Test
    fun uncheckingTagsBeforeChoosingAllForwardsTheChoice() {
        var selection: ExportSelection? = null
        compose.setContent {
            ExportScreens(points = emptyList(), tags = emptyList(), onSelectExport = { selection = it }, onBack = {})
        }

        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onNodeWithText(label(R.string.action_next)).performClick()
        compose.onNodeWithText(label(R.string.export_all)).performClick()

        compose.runOnIdle { assertEquals(ExportSelection(false, ExportKind.All), selection) }
    }

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
