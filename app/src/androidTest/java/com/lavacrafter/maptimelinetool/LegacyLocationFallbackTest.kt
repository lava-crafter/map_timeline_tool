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

import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveFlow
import com.lavacrafter.maptimelinetool.quickadd.QuickAddLocationCache
import com.lavacrafter.maptimelinetool.quickadd.QuickAddPassiveLocationUpdater
import com.lavacrafter.maptimelinetool.quickadd.QuickAddResolver
import com.lavacrafter.maptimelinetool.quickadd.QuickAddResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B1 on a device without Play services (API 29 `default` emulator image): the LocationManager fallback must
 * deliver a fresh fix to both "add point" and Quick Add, and a provider being disabled mid-request must end
 * the request instead of crashing (pre-API 30 calls the LocationListener default methods).
 * Opt-in (`legacyLocation=true`): it installs mock location providers. Quick Add rejects mock fixes by
 * design, so its check runs separately against the emulator's real GNSS HAL while the host repeats
 * `adb emu geo fix 121.4737 31.2304` (`emulatorGeoFix=true`).
 */
@RunWith(AndroidJUnit4::class)
class LegacyLocationFallbackTest {
    private val context = ApplicationProvider.getApplicationContext<MapTimelineApp>()
    private val manager = context.getSystemService(LocationManager::class.java)
    private val providers = mutableListOf<String>()

    @Before fun grantLocation() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("legacyLocation") == "true" || arguments.getString("emulatorGeoFix") == "true")
        val pkg = context.packageName
        listOf("appops set $pkg android:mock_location allow",
            "pm grant $pkg android.permission.ACCESS_FINE_LOCATION",
            "pm grant $pkg android.permission.ACCESS_COARSE_LOCATION",
            "pm grant $pkg android.permission.ACCESS_BACKGROUND_LOCATION",
            "settings put secure location_mode 3").forEach(::shell)
    }

    private fun installMockProviders() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("legacyLocation") == "true")
        for (name in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            @Suppress("DEPRECATION")
            runCatching {
                manager.addTestProvider(name, false, false, false, false, true, true, true,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
                manager.setTestProviderEnabled(name, true)
                providers += name
            }
        }
        assertTrue("no mock provider could be installed", providers.isNotEmpty())
    }

    @After fun removeMockProviders() {
        providers.forEach { runCatching { manager.removeTestProvider(it) } }
    }

    @Test fun addPointGetsFreshFixWithoutPlayServices() = runBlocking {
        installMockProviders()
        val graph = context.graph
        val pusher = launch(Dispatchers.Default) { while (isActive) { pushFix(); delay(200) } }
        try {
            val fresh = withContext(Dispatchers.IO) { graph.locationProvider.getFreshLocation(10_000) }
            assertNotNull("fresh fix", fresh)
            assertEquals(LAT, fresh!!.latitude, 1e-6)

            val decision = withContext(Dispatchers.IO) { graph.locationSaveResolver.resolve(LocationSaveFlow.MANUAL_ADD, 10_000) }
            assertTrue("add-point decision $decision", decision.canSave && decision.location != null)
        } finally { pusher.cancel() }
    }

    @Test fun quickAddGetsFreshRealFixWithoutPlayServices() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("emulatorGeoFix") == "true")
        val graph = context.graph
        run {
            val saved = mutableListOf<Double>()
            val quickAdd = QuickAddResolver(graph.locationProvider, QuickAddLocationCache(elapsedRealtimeNanos = SystemClock::elapsedRealtimeNanos),
                addPoint = { _, location, _, onCoreSaved -> saved += location.latitude; onCoreSaved() },
                elapsedRealtimeNanos = SystemClock::elapsedRealtimeNanos)
            val result = withContext(Dispatchers.IO) { quickAdd.savePoint(10_000) }
            assertEquals(QuickAddResult.SAVED_FROM_FRESH_REQUEST, result)
            assertEquals(1, saved.size)
            assertEquals(LAT, saved.single(), 1e-4)
        }
    }

    @Test fun providerDisabledDuringRequestEndsItWithoutCrashing() = runBlocking {
        installMockProviders()
        // A passive registration also receives onProviderDisabled; it must not hit AbstractMethodError.
        val passive = QuickAddPassiveLocationUpdater(context, QuickAddLocationCache(elapsedRealtimeNanos = SystemClock::elapsedRealtimeNanos))
        withContext(Dispatchers.Main) { passive.refreshRegistration(true) }
        try {
            val started = SystemClock.elapsedRealtime()
            val request = async(Dispatchers.IO) { LocationUtils.getFreshLocation(context, 20_000) }
            delay(1_500)
            providers.forEach { manager.setTestProviderEnabled(it, false) }
            assertNull(request.await())
            assertTrue("request should end on disable, not at the 20 s timeout",
                SystemClock.elapsedRealtime() - started < 15_000)
        } finally {
            withContext(Dispatchers.Main) { passive.refreshRegistration(false) }
        }
    }

    private fun pushFix() {
        providers.forEach { name ->
            val location = Location(name).apply {
                latitude = LAT; longitude = LON; accuracy = 5f; altitude = 10.0
                time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            runCatching { manager.setTestProviderLocation(name, location) }
        }
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    private companion object {
        const val LAT = 31.2304
        const val LON = 121.4737
    }
}
