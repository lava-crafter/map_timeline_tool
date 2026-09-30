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

import android.Manifest
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.rule.GrantPermissionRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lavacrafter.maptimelinetool.domain.model.Point
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Exercises the same saved Activity state used by camera and document callbacks without opening a camera. */
@RunWith(AndroidJUnit4::class)
class ActivityResultRecreationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        ))
        .around(compose)

    @Test
    fun addDraftRemainsOpenWithEditedTitleAfterRecreation() {
        keepActivityVisible()
        compose.onNodeWithText(label(R.string.action_add_point)).performClick()
        compose.onNodeWithText(label(R.string.dialog_title_new_point)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.dialog_title_label)).performTextInput("Phase 6 draft")

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText(label(R.string.dialog_title_new_point)).assertIsDisplayed()
        compose.onNodeWithText("Phase 6 draft").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.action_cancel)).performClick()
    }

    @Test
    fun editDraftReturnsToTheSamePointAfterRecreation() {
        keepActivityVisible()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = context.appGraph().pointRepositoryGateway
        val title = "Phase 6 edit ${UUID.randomUUID()}"
        val point = Point(timestamp = System.currentTimeMillis(), latitude = 10.0, longitude = 20.0,
            title = title, note = "Original note")
        val id = runBlocking { repository.insert(point) }
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
            // Expand the sheet itself: a root-coordinate swipe can pan the map instead,
            // leaving the row behind the navigation bar and sending its long-click there.
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
                .performSemanticsAction(SemanticsActions.Expand)
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(title).performTouchInput { longClick(durationMillis = 1_200) }
            compose.onNodeWithText(label(R.string.dialog_title_edit_point)).assertIsDisplayed()
            compose.onNodeWithText(label(R.string.dialog_note_label)).performTextReplacement("Phase 6 note draft")
            compose.onNodeWithText("Phase 6 note draft").assertIsDisplayed()

            compose.activityRule.scenario.recreate()

            compose.onNodeWithText(label(R.string.dialog_title_edit_point)).assertIsDisplayed()
            assertTrue(compose.onAllNodesWithText(title).fetchSemanticsNodes().size >= 2)
            compose.onNodeWithText("Phase 6 note draft").assertIsDisplayed()
            compose.onNodeWithText(label(R.string.action_cancel)).performClick()
            assertTrue(runBlocking { repository.getAll().any { it.id == id } })
        } finally {
            runBlocking { repository.delete(point.copy(id = id)) }
        }
    }

    private fun keepActivityVisible() {
        compose.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun label(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
