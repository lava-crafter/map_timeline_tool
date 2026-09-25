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

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lavacrafter.maptimelinetool.data.SettingsRepository
import com.lavacrafter.maptimelinetool.domain.usecase.SettingsManagementUseCase
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAndOfflineVerificationTest {
    @Test
    fun manualDarkThemeSurvivesCreatingAnotherViewModel() {
        // The instrumentation APK owns these preferences; never touch the user's installed app data.
        val settings = SettingsManagementUseCase(SettingsRepository(isolatedSettingsContext()))
        val app = ApplicationProvider.getApplicationContext<Application>()

        settings.setFollowSystemTheme(false)
        SettingsViewModel(app, settings).setDarkTheme(true)

        assertTrue(SettingsViewModel(app, settings).uiState.value.isDarkTheme)
    }

    @Test
    fun overlappingOfflineAreasDoNotDeclareTheBoundingUnionAsDownloaded() {
        val isolatedContext = isolatedSettingsContext()
        val first = DownloadedArea(north = 10.0, south = 0.0, east = 10.0, west = 0.0, minZoom = 8, maxZoom = 9)
        val second = DownloadedArea(north = 15.0, south = 5.0, east = 15.0, west = 5.0, minZoom = 9, maxZoom = 10)

        SettingsStore.addDownloadedArea(isolatedContext, first)
        val saved = SettingsStore.addDownloadedArea(isolatedContext, second)

        assertEquals(listOf(first, second).map { it.boundsKey() }.toSet(), saved.map { it.boundsKey() }.toSet())
    }

    private fun isolatedSettingsContext(): Context {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val suffix = UUID.randomUUID().toString()
        return object : ContextWrapper(testContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                testContext.getSharedPreferences("verification_${suffix}_$name", mode)
        }
    }
}
