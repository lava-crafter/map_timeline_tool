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
import android.os.Handler
import android.os.Looper
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class SensorSnapshot(
    val pressureHpa: Float? = null,
    val ambientLightLux: Float? = null,
    val accelerometerX: Float? = null,
    val accelerometerY: Float? = null,
    val accelerometerZ: Float? = null,
    val gyroscopeX: Float? = null,
    val gyroscopeY: Float? = null,
    val gyroscopeZ: Float? = null,
    val magnetometerX: Float? = null,
    val magnetometerY: Float? = null,
    val magnetometerZ: Float? = null
)

suspend fun captureSensorSnapshot(
    context: Context,
    timeoutMs: Long = 1500L,
    requestedSensorTypes: Set<Int>? = null
): SensorSnapshot =
    suspendCancellableCoroutine { continuation ->
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            continuation.resume(SensorSnapshot())
            return@suspendCancellableCoroutine
        }

        val activeSensorTypes = requestedSensorTypes ?: setOf(
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD
        )

        if (activeSensorTypes.isEmpty()) {
            continuation.resume(SensorSnapshot())
            return@suspendCancellableCoroutine
        }

        val callbackHandler = Handler(Looper.getMainLooper())
        val registeredTypes = mutableSetOf<Int>()
        var snapshot = SensorSnapshot()
        var completed = false
        var registrationsComplete = false
        lateinit var timeoutRunnable: Runnable

        fun finish(listener: SensorEventListener) {
            if (completed) return
            completed = true
            sensorManager.unregisterListener(listener)
            callbackHandler.removeCallbacks(timeoutRunnable)
            if (continuation.isActive) {
                continuation.resume(snapshot)
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (completed) return
                snapshot = snapshot.updateFrom(event.sensor.type, event.values)
                if (registrationsComplete && registeredTypes.isNotEmpty() && registeredTypes.all(snapshot::hasReadingFor)) {
                    finish(this)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        timeoutRunnable = Runnable {
            finish(listener)
        }

        callbackHandler.post {
            if (!continuation.isActive || completed) return@post

            val sensors = listOfNotNull(
                if (Sensor.TYPE_PRESSURE in activeSensorTypes) sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { Sensor.TYPE_PRESSURE to it } else null,
                if (Sensor.TYPE_LIGHT in activeSensorTypes) sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { Sensor.TYPE_LIGHT to it } else null,
                if (Sensor.TYPE_ACCELEROMETER in activeSensorTypes) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { Sensor.TYPE_ACCELEROMETER to it } else null,
                if (Sensor.TYPE_GYROSCOPE in activeSensorTypes) sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { Sensor.TYPE_GYROSCOPE to it } else null,
                if (Sensor.TYPE_MAGNETIC_FIELD in activeSensorTypes) sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { Sensor.TYPE_MAGNETIC_FIELD to it } else null
            )

            if (sensors.isEmpty()) {
                finish(listener)
                return@post
            }

            sensors.forEach { (sensorType, sensor) ->
                val registered = sensorManager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                    callbackHandler
                )
                if (registered) registeredTypes += sensorType
            }
            // Callbacks must not complete a partial registration set, even if the platform
            // invokes a listener synchronously from registerListener.
            registrationsComplete = true

            if (registeredTypes.isEmpty()) {
                finish(listener)
                return@post
            }

            if (registeredTypes.all(snapshot::hasReadingFor)) {
                finish(listener)
                return@post
            }

            callbackHandler.postDelayed(timeoutRunnable, timeoutMs)
        }
        continuation.invokeOnCancellation {
            callbackHandler.post {
                if (completed) return@post
                completed = true
                sensorManager.unregisterListener(listener)
                callbackHandler.removeCallbacks(timeoutRunnable)
            }
        }
    }

internal fun SensorSnapshot.hasReadingFor(sensorType: Int): Boolean = when (sensorType) {
    Sensor.TYPE_PRESSURE -> pressureHpa != null
    Sensor.TYPE_LIGHT -> ambientLightLux != null
    Sensor.TYPE_ACCELEROMETER -> accelerometerX != null && accelerometerY != null && accelerometerZ != null
    Sensor.TYPE_GYROSCOPE -> gyroscopeX != null && gyroscopeY != null && gyroscopeZ != null
    Sensor.TYPE_MAGNETIC_FIELD -> magnetometerX != null && magnetometerY != null && magnetometerZ != null
    else -> false
}

/** Same mapping used by the listener; accepts sample values without constructing Android SensorEvent. */
internal fun SensorSnapshot.updateFrom(sensorType: Int, values: FloatArray): SensorSnapshot = when (sensorType) {
    Sensor.TYPE_PRESSURE -> copy(pressureHpa = values.firstOrNull())
    Sensor.TYPE_LIGHT -> copy(ambientLightLux = values.firstOrNull())
    Sensor.TYPE_ACCELEROMETER -> copy(
        accelerometerX = values.getOrNull(0),
        accelerometerY = values.getOrNull(1),
        accelerometerZ = values.getOrNull(2)
    )
    Sensor.TYPE_GYROSCOPE -> copy(
        gyroscopeX = values.getOrNull(0),
        gyroscopeY = values.getOrNull(1),
        gyroscopeZ = values.getOrNull(2)
    )
    Sensor.TYPE_MAGNETIC_FIELD -> copy(
        magnetometerX = values.getOrNull(0),
        magnetometerY = values.getOrNull(1),
        magnetometerZ = values.getOrNull(2)
    )
    else -> this
}
