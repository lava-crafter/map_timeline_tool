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
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.content.ContextWrapper
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class SettingsStoreBackupJsonTest {
    private val preferencesName = "settings_backup_test_${UUID.randomUUID()}"
    private val baseContext: Context = ApplicationProvider.getApplicationContext()
    private val context: Context = object : ContextWrapper(baseContext) {
        override fun getSharedPreferences(name: String, mode: Int) =
            baseContext.getSharedPreferences(preferencesName, mode)
    }

    @After
    fun deleteTestPreferences() {
        baseContext.deleteSharedPreferences(preferencesName)
    }

    @Test
    fun exportBackupJson_containsPortablePreferencesAndExcludesDeviceState() {
        SettingsStore.setTimeoutSeconds(context, 42)
        SettingsStore.setFollowSystemTheme(context, false)
        SettingsStore.setMarkerScale(context, 1.4f)
        SettingsStore.setMapTileSourceId(context, "osm")
        SettingsStore.setQuickAddNotificationEnabled(context, true)
        SettingsStore.setQuickAddNotificationPermissionRequested(context, true)
        SettingsStore.setDownloadTileSourceId(context, "download-source")
        SettingsStore.setDownloadMultiThreadEnabled(context, true)
        SettingsStore.setDownloadThreadCount(context, 12)
        SettingsStore.addDownloadedArea(context, DownloadedArea(1.0, 0.0, 1.0, 0.0, 3, 8))

        val backup = SettingsStore.exportBackupJson(context)
        val root = JSONObject(backup)

        assertEquals(42, root.getInt("timeout_seconds"))
        assertEquals(false, root.getBoolean("follow_system_theme"))
        assertEquals(1.4, root.getDouble("marker_scale"), 0.001)
        assertEquals("osm", root.getString("map_tile_source"))
        assertFalse(root.has("quick_add_notification_enabled"))
        assertFalse(root.has("quick_add_notification_permission_requested"))
        assertFalse(root.has("download_tile_source"))
        assertFalse(root.has("download_multi_thread"))
        assertFalse(root.has("download_thread_count"))
        assertFalse(root.has("downloaded_areas"))

        context.getSharedPreferences("map_timeline_settings", Context.MODE_PRIVATE).edit().clear().commit()
        assertTrue(SettingsStore.importBackupJson(context, backup))
        assertEquals(42, SettingsStore.getTimeoutSeconds(context))
        assertEquals(false, SettingsStore.getFollowSystemTheme(context))
        assertEquals(1.4f, SettingsStore.getMarkerScale(context), 0.001f)
        assertEquals("osm", SettingsStore.getMapTileSourceId(context))
        assertTrue(SettingsStore.getDownloadedAreas(context).isEmpty())
    }

    @Test
    fun importBackupJson_ignoresLegacyExcludedPreferencesAndPreservesLocalValues() {
        SettingsStore.setQuickAddNotificationEnabled(context, true)
        SettingsStore.setQuickAddNotificationPermissionRequested(context, true)
        SettingsStore.setDownloadTileSourceId(context, "local-source")
        SettingsStore.setDownloadMultiThreadEnabled(context, true)
        SettingsStore.setDownloadThreadCount(context, 11)
        SettingsStore.addDownloadedArea(context, DownloadedArea(2.0, 1.0, 2.0, 1.0, 4, 9))

        val imported = SettingsStore.importBackupJson(
            context,
            """{"timeout_seconds":55,"quick_add_notification_enabled":false,"quick_add_notification_permission_requested":false,"download_tile_source":"legacy-source","download_multi_thread":false,"download_thread_count":2,"downloaded_areas":[]}"""
        )

        assertTrue(imported)
        assertEquals(55, SettingsStore.getTimeoutSeconds(context))
        assertTrue(SettingsStore.getQuickAddNotificationEnabled(context))
        assertTrue(SettingsStore.getQuickAddNotificationPermissionRequested(context))
        assertEquals("local-source", SettingsStore.getDownloadTileSourceId(context))
        assertTrue(SettingsStore.getDownloadMultiThreadEnabled(context))
        assertEquals(11, SettingsStore.getDownloadThreadCount(context))
        assertEquals(1, SettingsStore.getDownloadedAreas(context).size)
    }

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
