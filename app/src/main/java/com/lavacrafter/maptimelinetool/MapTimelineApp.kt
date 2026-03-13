package com.lavacrafter.maptimelinetool

import android.app.Application
import android.content.Context
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
                val raw = com.lavacrafter.maptimelinetool.sensor.captureSensorSnapshot(app)
                return PointSensorSnapshot(
                    pressureHpa = if (settingsManagementUseCase.getPressureEnabled()) raw.pressureHpa else null,
                    ambientLightLux = if (settingsManagementUseCase.getAmbientLightEnabled()) raw.ambientLightLux else null,
                    accelerometerX = if (settingsManagementUseCase.getAccelerometerEnabled()) raw.accelerometerX else null,
                    accelerometerY = if (settingsManagementUseCase.getAccelerometerEnabled()) raw.accelerometerY else null,
                    accelerometerZ = if (settingsManagementUseCase.getAccelerometerEnabled()) raw.accelerometerZ else null,
                    gyroscopeX = if (settingsManagementUseCase.getGyroscopeEnabled()) raw.gyroscopeX else null,
                    gyroscopeY = if (settingsManagementUseCase.getGyroscopeEnabled()) raw.gyroscopeY else null,
                    gyroscopeZ = if (settingsManagementUseCase.getGyroscopeEnabled()) raw.gyroscopeZ else null,
                    magnetometerX = if (settingsManagementUseCase.getMagnetometerEnabled()) raw.magnetometerX else null,
                    magnetometerY = if (settingsManagementUseCase.getMagnetometerEnabled()) raw.magnetometerY else null,
                    magnetometerZ = if (settingsManagementUseCase.getMagnetometerEnabled()) raw.magnetometerZ else null
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
