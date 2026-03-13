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
                    pressureHpa = raw.pressureHpa,
                    ambientLightLux = raw.ambientLightLux,
                    accelerometerX = raw.accelerometerX,
                    accelerometerY = raw.accelerometerY,
                    accelerometerZ = raw.accelerometerZ,
                    gyroscopeX = raw.gyroscopeX,
                    gyroscopeY = raw.gyroscopeY,
                    gyroscopeZ = raw.gyroscopeZ,
                    magnetometerX = raw.magnetometerX,
                    magnetometerY = raw.magnetometerY,
                    magnetometerZ = raw.magnetometerZ
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
            deletePhoto = { photoPath -> deletePointPhotoFile(app, photoPath) }
        )
    }
}

fun Context.appGraph(): AppGraph = (applicationContext as MapTimelineApp).graph
