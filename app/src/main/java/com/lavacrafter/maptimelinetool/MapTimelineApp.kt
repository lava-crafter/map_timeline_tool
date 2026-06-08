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

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import com.lavacrafter.maptimelinetool.data.AppDatabase
import com.lavacrafter.maptimelinetool.data.PointRepository
import com.lavacrafter.maptimelinetool.data.SettingsRepository
import com.lavacrafter.maptimelinetool.domain.model.PointSensorSnapshot
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider
import com.lavacrafter.maptimelinetool.domain.port.SensorSnapshotPort
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.usecase.PointWriteUseCase
import com.lavacrafter.maptimelinetool.domain.usecase.SettingsManagementUseCase
import com.lavacrafter.maptimelinetool.domain.usecase.TagManagementUseCase
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.osmdroid.config.Configuration

class MapTimelineApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
    val pointRepositoryGateway: PointRepositoryGateway get() = graph.pointRepositoryGateway
    val settingsManagementUseCase: SettingsManagementUseCase get() = graph.settingsManagementUseCase
    val tagManagementUseCase: TagManagementUseCase get() = graph.tagManagementUseCase
    val pointWriteUseCase: PointWriteUseCase get() = graph.pointWriteUseCase

    override fun onCreate() {
        super.onCreate()
        val config = Configuration.getInstance()
        config.userAgentValue = packageName
        val basePath = File(cacheDir, "osmdroid")
        config.osmdroidBasePath = basePath
        config.osmdroidTileCache = File(basePath, "tiles")
    }
}

class AppGraph(
    private val app: Application
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val pointRepositoryGateway: PointRepositoryGateway by lazy {
        PointRepository(AppDatabase.get(app).pointDao())
    }

    val settingsManagementUseCase: SettingsManagementUseCase by lazy {
        SettingsManagementUseCase(SettingsRepository(app))
    }

    val locationProvider: LocationProvider by lazy {
        AndroidLocationProvider(app)
    }

    val sensorSnapshotPort: SensorSnapshotPort by lazy {
        object : SensorSnapshotPort {
            override suspend fun readSnapshot(): PointSensorSnapshot {
                val pressureEnabled = settingsManagementUseCase.getPressureEnabled()
                val ambientLightEnabled = settingsManagementUseCase.getAmbientLightEnabled()
                val accelerometerEnabled = settingsManagementUseCase.getAccelerometerEnabled()
                val gyroscopeEnabled = settingsManagementUseCase.getGyroscopeEnabled()
                val magnetometerEnabled = settingsManagementUseCase.getMagnetometerEnabled()
                val requestedSensorTypes = buildSet {
                    if (pressureEnabled) add(Sensor.TYPE_PRESSURE)
                    if (ambientLightEnabled) add(Sensor.TYPE_LIGHT)
                    if (accelerometerEnabled) add(Sensor.TYPE_ACCELEROMETER)
                    if (gyroscopeEnabled) add(Sensor.TYPE_GYROSCOPE)
                    if (magnetometerEnabled) add(Sensor.TYPE_MAGNETIC_FIELD)
                }
                val raw = com.lavacrafter.maptimelinetool.sensor.captureSensorSnapshot(
                    app,
                    requestedSensorTypes = requestedSensorTypes
                )
                return PointSensorSnapshot(
                    pressureHpa = if (pressureEnabled) raw.pressureHpa else null,
                    ambientLightLux = if (ambientLightEnabled) raw.ambientLightLux else null,
                    accelerometerX = if (accelerometerEnabled) raw.accelerometerX else null,
                    accelerometerY = if (accelerometerEnabled) raw.accelerometerY else null,
                    accelerometerZ = if (accelerometerEnabled) raw.accelerometerZ else null,
                    gyroscopeX = if (gyroscopeEnabled) raw.gyroscopeX else null,
                    gyroscopeY = if (gyroscopeEnabled) raw.gyroscopeY else null,
                    gyroscopeZ = if (gyroscopeEnabled) raw.gyroscopeZ else null,
                    magnetometerX = if (magnetometerEnabled) raw.magnetometerX else null,
                    magnetometerY = if (magnetometerEnabled) raw.magnetometerY else null,
                    magnetometerZ = if (magnetometerEnabled) raw.magnetometerZ else null
                )
            }
        }
    }

    val tagManagementUseCase: TagManagementUseCase by lazy {
        TagManagementUseCase(pointRepositoryGateway)
    }

    val pointWriteUseCase: PointWriteUseCase by lazy {
        PointWriteUseCase(
            repository = pointRepositoryGateway,
            sensorSnapshotPort = sensorSnapshotPort,
            deletePhoto = { photoPath -> deletePointPhotoFile(app, photoPath) },
            shouldCollectNoise = { settingsManagementUseCase.getNoiseEnabled() },
            collectNoiseDb = { com.lavacrafter.maptimelinetool.sensor.captureNoiseDb(app) },
            asyncScope = appScope
        )
    }
}

fun Context.appGraph(): AppGraph = (applicationContext as MapTimelineApp).graph
