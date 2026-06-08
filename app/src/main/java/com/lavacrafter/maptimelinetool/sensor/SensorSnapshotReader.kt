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

        val sensors = listOfNotNull(
            if (Sensor.TYPE_PRESSURE in activeSensorTypes) sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { Sensor.TYPE_PRESSURE to it } else null,
            if (Sensor.TYPE_LIGHT in activeSensorTypes) sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { Sensor.TYPE_LIGHT to it } else null,
            if (Sensor.TYPE_ACCELEROMETER in activeSensorTypes) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { Sensor.TYPE_ACCELEROMETER to it } else null,
            if (Sensor.TYPE_GYROSCOPE in activeSensorTypes) sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { Sensor.TYPE_GYROSCOPE to it } else null,
            if (Sensor.TYPE_MAGNETIC_FIELD in activeSensorTypes) sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { Sensor.TYPE_MAGNETIC_FIELD to it } else null
        )

        if (sensors.isEmpty()) {
            continuation.resume(SensorSnapshot())
            return@suspendCancellableCoroutine
        }

        val callbackHandler = Handler(Looper.getMainLooper())
        val registeredTypes = mutableSetOf<Int>()
        var snapshot = SensorSnapshot()
        lateinit var timeoutRunnable: Runnable

        fun finish(listener: SensorEventListener) {
            if (!continuation.isActive) return
            sensorManager.unregisterListener(listener)
            callbackHandler.removeCallbacks(timeoutRunnable)
            continuation.resume(snapshot)
        }

        fun SensorSnapshot.hasReadingFor(sensorType: Int): Boolean =
            when (sensorType) {
                Sensor.TYPE_PRESSURE -> pressureHpa != null
                Sensor.TYPE_LIGHT -> ambientLightLux != null
                Sensor.TYPE_ACCELEROMETER -> accelerometerX != null && accelerometerY != null && accelerometerZ != null
                Sensor.TYPE_GYROSCOPE -> gyroscopeX != null && gyroscopeY != null && gyroscopeZ != null
                Sensor.TYPE_MAGNETIC_FIELD -> magnetometerX != null && magnetometerY != null && magnetometerZ != null
                else -> false
            }

        fun SensorSnapshot.updateFrom(event: SensorEvent): SensorSnapshot =
            when (event.sensor.type) {
                Sensor.TYPE_PRESSURE -> copy(pressureHpa = event.values.firstOrNull())
                Sensor.TYPE_LIGHT -> copy(ambientLightLux = event.values.firstOrNull())
                Sensor.TYPE_ACCELEROMETER -> copy(
                    accelerometerX = event.values.getOrNull(0),
                    accelerometerY = event.values.getOrNull(1),
                    accelerometerZ = event.values.getOrNull(2)
                )
                Sensor.TYPE_GYROSCOPE -> copy(
                    gyroscopeX = event.values.getOrNull(0),
                    gyroscopeY = event.values.getOrNull(1),
                    gyroscopeZ = event.values.getOrNull(2)
                )
                Sensor.TYPE_MAGNETIC_FIELD -> copy(
                    magnetometerX = event.values.getOrNull(0),
                    magnetometerY = event.values.getOrNull(1),
                    magnetometerZ = event.values.getOrNull(2)
                )
                else -> this
            }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                snapshot = snapshot.updateFrom(event)
                if (registeredTypes.isNotEmpty() && registeredTypes.all(snapshot::hasReadingFor)) {
                    finish(this)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        timeoutRunnable = Runnable {
            finish(listener)
        }

        sensors.forEach { (sensorType, sensor) ->
            val registered = sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
                callbackHandler
            )
            if (registered) {
                registeredTypes += sensorType
            }
        }

        if (registeredTypes.isEmpty()) {
            continuation.resume(SensorSnapshot())
            return@suspendCancellableCoroutine
        }

        callbackHandler.postDelayed(timeoutRunnable, timeoutMs)
        continuation.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
            callbackHandler.removeCallbacks(timeoutRunnable)
        }
    }
