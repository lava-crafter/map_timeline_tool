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

package com.lavacrafter.maptimelinetool.sensor

import android.hardware.Sensor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensorSnapshotReaderTest {
    @Test
    fun captureSensorSnapshot_supportedAndMissingRequestsMeetDeadline() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext

            withTimeout(2_000L) {
                captureSensorSnapshot(
                    context = context,
                    timeoutMs = 250L,
                    requestedSensorTypes = setOf(Sensor.TYPE_ACCELEROMETER)
                )
            }

            withTimeout(2_000L) {
                captureSensorSnapshot(
                    context = context,
                    timeoutMs = 250L,
                    requestedSensorTypes = setOf(Int.MAX_VALUE)
                )
            }
        }
    }
}
