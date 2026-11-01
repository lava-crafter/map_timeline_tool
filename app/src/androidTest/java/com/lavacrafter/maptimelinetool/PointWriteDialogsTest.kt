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
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.ui.AddPointDialog
import com.lavacrafter.maptimelinetool.ui.EditPointDialog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Component-level success paths; Room completion/error propagation is verified by DataIntegrityVerificationTest. */
@RunWith(AndroidJUnit4::class)
class PointWriteDialogsTest {
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
    fun addSaveRemainsOpenUntilWriteCompletes() {
        val write = CompletableDeferred<Unit>()
        var visible by mutableStateOf(true)
        var saving by mutableStateOf(false)
        var savedTitle: String? = null
        compose.setContent {
            val scope = rememberCoroutineScope()
            if (visible) AddPointDialog(
                createdAt = 1_000L, quickTags = emptyList(), tags = emptyList(), selectedTagIds = emptySet(),
                title = "Test point", note = "note", remainingSeconds = 10, isCountdownPaused = true,
                isSaving = saving,
                onTitleChange = {}, onNoteChange = {}, onUserTyping = {}, onToggleTag = {}, onOpenTagPicker = {},
                hasPhoto = false, onTakePhoto = {}, onRetakePhoto = {}, onRemovePhoto = {},
                onViewPhoto = {}, onSharePhoto = {}, onDismiss = { visible = false },
                onConfirm = { title, _, _, _ ->
                    saving = true
                    scope.launch {
                        write.await()
                        savedTitle = title
                        visible = false
                    }
                }
            )
        }

        compose.onNodeWithText(label(R.string.action_save)).performClick()
        compose.onNodeWithText(label(R.string.dialog_title_new_point)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.action_save)).assertIsNotEnabled()
        write.complete(Unit)
        compose.waitUntil(5_000) { !visible }
        compose.onNodeWithText(label(R.string.dialog_title_new_point)).assertDoesNotExist()
        assertEquals("Test point", savedTitle)
    }

    @Test
    fun editSaveRemainsOpenUntilWriteCompletes() {
        val write = CompletableDeferred<Unit>()
        var visible by mutableStateOf(true)
        var saving by mutableStateOf(false)
        var savedTitle: String? = null
        compose.setContent {
            val scope = rememberCoroutineScope()
            if (visible) EditPointDialog(
                point = point(), quickTags = emptyList(), tags = emptyList(), selectedTagIds = emptySet(),
                isSaving = saving, onToggleTag = {}, onOpenTagPicker = {}, currentPhotoPath = null,
                onTakePhoto = {}, onRetakePhoto = {}, onRemovePhoto = {}, onViewPhoto = {}, onSharePhoto = {},
                onSave = { title, _, _ ->
                    saving = true
                    scope.launch {
                        write.await()
                        savedTitle = title
                        visible = false
                    }
                },
                onDelete = {}, onDismiss = { visible = false }
            )
        }

        compose.onNodeWithText(label(R.string.action_save)).performClick()
        compose.onNodeWithText(label(R.string.dialog_title_edit_point)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.action_save)).assertIsNotEnabled()
        write.complete(Unit)
        compose.waitUntil(5_000) { !visible }
        assertEquals("Original", savedTitle)
    }

    @Test
    fun deleteRemainsOpenUntilWriteCompletes() {
        val write = CompletableDeferred<Unit>()
        var visible by mutableStateOf(true)
        var saving by mutableStateOf(false)
        var deleted = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            if (visible) EditPointDialog(
                point = point(), quickTags = emptyList(), tags = emptyList(), selectedTagIds = emptySet(),
                isSaving = saving, onToggleTag = {}, onOpenTagPicker = {}, currentPhotoPath = null,
                onTakePhoto = {}, onRetakePhoto = {}, onRemovePhoto = {}, onViewPhoto = {}, onSharePhoto = {},
                onSave = { _, _, _ -> },
                onDelete = {
                    saving = true
                    scope.launch {
                        write.await()
                        deleted = true
                        visible = false
                    }
                },
                onDismiss = { visible = false }
            )
        }

        compose.onNodeWithText(label(R.string.action_delete)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, saving) }
        compose.onNodeWithText(label(R.string.dialog_title_edit_point)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.action_delete)).assertIsNotEnabled()
        write.complete(Unit)
        compose.waitUntil(5_000) { !visible }
        assertEquals(true, deleted)
    }

    private fun point() = PointEntity(
        id = 5L, timestamp = 1_000L, latitude = 10.0, longitude = 20.0,
        title = "Original", note = ""
    )

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
