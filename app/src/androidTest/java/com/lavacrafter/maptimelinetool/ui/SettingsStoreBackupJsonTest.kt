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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class SettingsStoreBackupJsonTest {
    @Test
    fun sanitizeBackupJsonForImport_remapsTagPreferencesAndDropsDevicePermissionFlag() {
        val sanitized = SettingsStore.sanitizeBackupJsonForImport(
            json = """{"default_tags":[10,30,20,10],"pinned_tags":[20],"recent_tags":[30,10],"quick_add_notification_permission_requested":true,"timeout_seconds":25}""",
            legacyTagIdToActualId = mapOf(10L to 101L, 20L to 202L)
        )

        val root = JSONObject(requireNotNull(sanitized))
        assertEquals(listOf(101L, 202L), jsonLongArray(root, "default_tags"))
        assertEquals(listOf(202L), jsonLongArray(root, "pinned_tags"))
        assertEquals(listOf(101L), jsonLongArray(root, "recent_tags"))
        assertFalse(root.has("quick_add_notification_permission_requested"))
        assertEquals(25, root.getInt("timeout_seconds"))
    }

    @Test
    fun sanitizeBackupJsonForImport_clearsUnmappedTagPreferences() {
        val sanitized = SettingsStore.sanitizeBackupJsonForImport(
            json = """{"default_tags":[10],"pinned_tags":[20],"recent_tags":[30],"follow_system_theme":false}"""
        )

        val root = JSONObject(requireNotNull(sanitized))
        assertEquals(emptyList<Long>(), jsonLongArray(root, "default_tags"))
        assertEquals(emptyList<Long>(), jsonLongArray(root, "pinned_tags"))
        assertEquals(emptyList<Long>(), jsonLongArray(root, "recent_tags"))
        assertEquals(false, root.getBoolean("follow_system_theme"))
    }

    @Test
    fun sanitizeBackupJsonForImport_preservesLocalTagPreferencesWhenArchiveHasNoTags() {
        val sanitized = SettingsStore.sanitizeBackupJsonForImport(
            json = """{"default_tags":[10],"pinned_tags":[20],"recent_tags":[30],"timeout_seconds":25}""",
            restoreTagSettings = false
        )

        val root = JSONObject(requireNotNull(sanitized))
        assertFalse(root.has("default_tags"))
        assertFalse(root.has("pinned_tags"))
        assertFalse(root.has("recent_tags"))
        assertEquals(25, root.getInt("timeout_seconds"))
    }

    @Test
    fun sanitizeBackupJsonForImport_returnsNullForInvalidJson() {
        val sanitized = SettingsStore.sanitizeBackupJsonForImport("not-json")

        assertNull(sanitized)
    }

    private fun jsonLongArray(root: JSONObject, key: String): List<Long> {
        val array = requireNotNull(root.optJSONArray(key))
        return buildList {
            for (index in 0 until array.length()) {
                add(array.getLong(index))
            }
        }
    }
}
