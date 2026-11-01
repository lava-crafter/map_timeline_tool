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

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lavacrafter.maptimelinetool.ui.accelerometerToPitchDegrees
import com.lavacrafter.maptimelinetool.ui.deviceAzimuthDegrees
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2 on real hardware: compares the app's accelerometer + magnetometer heading with the platform's fused
 * TYPE_ROTATION_VECTOR, using the same pointing-axis rule. Opt-in (`liveSensors=true`) because the result
 * depends on how the device is held and on local magnetic disturbance; it does not replace the manual
 * flat / upright / rotated check.
 */
@RunWith(AndroidJUnit4::class)
class LiveHeadingCrossCheckTest {
    @Test fun appHeadingAgreesWithPlatformRotationVector() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSensors") == "true")
        val manager = ApplicationProvider.getApplicationContext<Context>().getSystemService(SensorManager::class.java)
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val rotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        assumeTrue(accelerometer != null && magnetometer != null && rotation != null)

        val gravity = FloatArray(3); val field = FloatArray(3); val rotationVector = FloatArray(5)
        var haveGravity = false; var haveField = false; var haveRotation = false
        val differences = ArrayList<Float>(); val appHeadings = ArrayList<Float>(); val pitches = ArrayList<Float>()
        val done = CountDownLatch(1)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> { event.values.copyInto(gravity, 0, 0, 3); haveGravity = true }
                    Sensor.TYPE_MAGNETIC_FIELD -> { event.values.copyInto(field, 0, 0, 3); haveField = true }
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        event.values.copyInto(rotationVector, 0, 0, minOf(5, event.values.size)); haveRotation = true
                    }
                }
                if (!(haveGravity && haveField && haveRotation) || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                val app = deviceAzimuthDegrees(gravity[0], gravity[1], gravity[2], field[0], field[1], field[2]) ?: return
                differences += angularDistance(app, referenceHeading(rotationVector))
                appHeadings += app
                pitches += accelerometerToPitchDegrees(gravity[1], gravity[2])
                if (differences.size >= 100) done.countDown()
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        val handler = Handler(Looper.getMainLooper())
        listOf(accelerometer, magnetometer, rotation).forEach {
            manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler)
        }
        try {
            // Let the fusion filter settle before sampling counts.
            Thread.sleep(1_500); differences.clear(); appHeadings.clear(); pitches.clear()
            assertTrue("no paired samples", done.await(15, TimeUnit.SECONDS))
        } finally { manager.unregisterListener(listener) }

        val median = differences.sorted()[differences.size / 2]
        val line = "appHeading(median)=${appHeadings.sorted()[appHeadings.size / 2]} " +
            "pitch(median)=${pitches.sorted()[pitches.size / 2]} |app-rotationVector| median=$median " +
            "p90=${differences.sorted()[differences.size * 9 / 10]}"
        Log.i("LiveHeadingCrossCheck", line)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("heading", line) })
        assertTrue(line, median <= 15f)
    }

    /** Heading of the same pointing axis (top edge or back camera) from the platform's rotation matrix. */
    private fun referenceHeading(rotationVector: FloatArray): Float {
        val r = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(r, rotationVector)
        // Rows are world east / north / up; column 1 is device +Y, column 2 is device +Z.
        val topEast = r[1].toDouble(); val topNorth = r[4].toDouble()
        val backEast = -r[2].toDouble(); val backNorth = -r[5].toDouble()
        val useTop = topEast * topEast + topNorth * topNorth >= backEast * backEast + backNorth * backNorth
        val deg = if (useTop) Math.toDegrees(atan2(topEast, topNorth)) else Math.toDegrees(atan2(backEast, backNorth))
        return ((deg.toFloat() % 360f) + 360f) % 360f
    }

    private fun angularDistance(a: Float, b: Float): Float = abs(((a - b + 540f) % 360f) - 180f)
}
