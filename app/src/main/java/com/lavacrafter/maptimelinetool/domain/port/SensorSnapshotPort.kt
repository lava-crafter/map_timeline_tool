package com.lavacrafter.maptimelinetool.domain.port

import com.lavacrafter.maptimelinetool.domain.model.PointSensorSnapshot

interface SensorSnapshotPort {
    suspend fun readSnapshot(): PointSensorSnapshot
}
