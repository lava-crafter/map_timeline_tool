package com.lavacrafter.maptimelinetool

import android.app.Application
import com.lavacrafter.maptimelinetool.data.AppDatabase
import com.lavacrafter.maptimelinetool.data.PointRepository
import com.lavacrafter.maptimelinetool.data.SettingsRepository
import com.lavacrafter.maptimelinetool.domain.usecase.PointWriteUseCase
import com.lavacrafter.maptimelinetool.domain.usecase.SettingsManagementUseCase
import java.io.File
import org.osmdroid.config.Configuration

class MapTimelineApp : Application() {
    val settingsManagementUseCase: SettingsManagementUseCase by lazy {
        SettingsManagementUseCase(SettingsRepository(this))
    }

    val pointWriteUseCase: PointWriteUseCase by lazy {
        PointWriteUseCase(
            repository = PointRepository(AppDatabase.get(this).pointDao()),
            readSensorSnapshot = { com.lavacrafter.maptimelinetool.sensor.captureSensorSnapshot(this) },
            deletePhoto = { photoPath -> deletePointPhotoFile(this, photoPath) }
        )
    }

    override fun onCreate() {
        super.onCreate()
        val config = Configuration.getInstance()
        config.userAgentValue = packageName
        val basePath = File(cacheDir, "osmdroid")
        config.osmdroidBasePath = basePath
        config.osmdroidTileCache = File(basePath, "tiles")
    }
}
