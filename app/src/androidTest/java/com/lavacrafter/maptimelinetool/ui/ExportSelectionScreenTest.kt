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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.runtime.saveable.SaverScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.data.TagEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks export selection, not the SAF writer or CSV file contents. */
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
    fun exportStartsAtSubsetMenu() {
        compose.setContent {
            ExportScreens(points = emptyList(), tags = emptyList(), onSelectExport = {}, onBack = {})
        }

        compose.onNodeWithText(label(R.string.export_all)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.export_option_points_and_tags)).assertDoesNotExist()
    }

    @Test
    fun choosingAllForwardsTheExportSelection() {
        var selection: ExportSelection? = null
        compose.setContent {
            ExportScreens(points = emptyList(), tags = emptyList(), onSelectExport = { selection = it }, onBack = {})
        }

        compose.onNodeWithText(label(R.string.export_all)).performClick()

        compose.runOnIdle { assertEquals(ExportSelection(ExportKind.All), selection) }
    }

    @Test
    fun manualRouteAndSelectedIdsSurviveStateRestorationAndExportSameIds() {
        val restoration = StateRestorationTester(compose)
        var selection: ExportSelection? = null
        restoration.setContent {
            ExportScreens(
                points = listOf(PointEntity(id = 42, timestamp = 0, latitude = 0.0, longitude = 0.0, title = "Saved point", note = "")), tags = emptyList(),
                onSelectExport = { selection = it }, onBack = {}
            )
        }
        compose.onNodeWithText(label(R.string.export_manual_select)).performClick()
        compose.onNode(isToggleable()).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Saved point").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.action_export_csv)).performClick()
        compose.runOnIdle { assertEquals(ExportSelection(ExportKind.Manual(listOf(42L))), selection) }
    }

    @Test
    fun tagRouteAndSelectedIdSurviveStateRestorationAndExportSameId() {
        val restoration = StateRestorationTester(compose)
        var selection: ExportSelection? = null
        restoration.setContent {
            ExportScreens(emptyList(), listOf(TagEntity(id = 73, name = "Saved tag")), { selection = it }, {})
        }
        compose.onNodeWithText(label(R.string.export_by_tag)).performClick()
        compose.onNode(isSelectable()).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(label(R.string.action_export_csv)).performClick()
        compose.runOnIdle { assertEquals(ExportSelection(ExportKind.ByTag(73)), selection) }
    }

    @Test
    fun timeRouteAndOpenPickerSurviveStateRestorationAndExportSameRange() {
        val restoration = StateRestorationTester(compose)
        var selection: ExportSelection? = null
        val today = LocalDate.now(ZoneId.systemDefault())
        val expected = ExportSelection(ExportKind.ByTime(
            today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        ))
        restoration.setContent { ExportScreens(emptyList(), emptyList(), { selection = it }, {}) }
        compose.onNodeWithText(label(R.string.export_by_time)).performClick()
        compose.onAllNodesWithText(today.toString())[0].performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(label(R.string.action_ok)).performClick()
        compose.onNodeWithText(label(R.string.action_export_csv)).performClick()
        compose.runOnIdle { assertEquals(expected, selection) }
    }

    @Test
    fun exportSelectionSaverRoundTripsEveryKindAndNull() {
        val values = listOf(
            null,
            ExportSelection(ExportKind.All),
            ExportSelection(ExportKind.ByTag(73)),
            ExportSelection(ExportKind.ByTime(1000, 2000)),
            ExportSelection(ExportKind.Manual(listOf(4, 9, 15)))
        )
        values.forEach { value ->
            val saved = with(ExportSelectionSaver) { with(object : SaverScope {
                override fun canBeSaved(value: Any) = true
            }) { save(value) } }
            assertEquals(value, ExportSelectionSaver.restore(requireNotNull(saved)))
        }
    }

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
