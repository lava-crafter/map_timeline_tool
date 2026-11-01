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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic samples cover mapping only; they cannot validate hardware timestamps or accuracy. */
class SensorSnapshotMappingTest {
    @Test
    fun incompleteVectorDoesNotCountAsACompleteSensorReading() {
        val partial = SensorSnapshot().updateFrom(Sensor.TYPE_ACCELEROMETER, floatArrayOf(1f, 2f))

        assertEquals(1f, partial.accelerometerX)
        assertEquals(2f, partial.accelerometerY)
        assertEquals(null, partial.accelerometerZ)
        assertFalse(partial.hasReadingFor(Sensor.TYPE_ACCELEROMETER))
        assertTrue(partial.updateFrom(Sensor.TYPE_ACCELEROMETER, floatArrayOf(1f, 2f, 3f))
            .hasReadingFor(Sensor.TYPE_ACCELEROMETER))
    }

    @Test
    fun eachSensorTypeUpdatesOnlyItsOwnFields() {
        val snapshot = SensorSnapshot()
            .updateFrom(Sensor.TYPE_PRESSURE, floatArrayOf(1002f))
            .updateFrom(Sensor.TYPE_LIGHT, floatArrayOf(25f))
            .updateFrom(Sensor.TYPE_GYROSCOPE, floatArrayOf(4f, 5f, 6f))
            .updateFrom(Sensor.TYPE_MAGNETIC_FIELD, floatArrayOf(7f, 8f, 9f))

        assertEquals(1002f, snapshot.pressureHpa)
        assertEquals(25f, snapshot.ambientLightLux)
        assertEquals(4f, snapshot.gyroscopeX)
        assertEquals(9f, snapshot.magnetometerZ)
        assertEquals(null, snapshot.accelerometerX)
        assertEquals(snapshot, snapshot.updateFrom(-1, floatArrayOf(100f)))
    }
}
