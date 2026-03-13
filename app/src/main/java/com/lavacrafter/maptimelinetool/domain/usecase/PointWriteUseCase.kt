package com.lavacrafter.maptimelinetool.domain.usecase

import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.port.SensorSnapshotPort
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PointWriteUseCase(
    private val repository: PointRepositoryGateway,
    private val sensorSnapshotPort: SensorSnapshotPort,
    private val deletePhoto: suspend (String?) -> Unit,
    private val shouldCollectNoise: () -> Boolean = { false },
    private val collectNoiseDb: suspend () -> Float? = { null },
    private val asyncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    suspend fun addPointWithTags(
        title: String,
        note: String,
        location: GeoPoint,
        timestamp: Long,
        tagIds: Set<Long>,
        photoPath: String? = null
    ) {
        val id = repository.insert(
            buildPoint(
                title = title,
                note = note,
                location = location,
                timestamp = timestamp,
                photoPath = photoPath
            )
        )
        tagIds.forEach { tagId ->
            repository.insertPointTag(id, tagId)
        }
        if (shouldCollectNoise()) {
            asyncScope.launch {
                val noiseDb = runCatching { collectNoiseDb() }.getOrNull()
                repository.updateNoiseDb(id, noiseDb)
            }
        }
    }

    suspend fun updatePoint(point: Point, title: String, note: String, photoPath: String?) {
        repository.update(point.copy(title = title, note = note, photoPath = photoPath))
        if (point.photoPath != photoPath) {
            deletePhoto(point.photoPath)
        }
    }

    suspend fun deletePoint(point: Point) {
        repository.delete(point)
        deletePhoto(point.photoPath)
    }

    suspend fun importPoints(pointsList: List<Point>) {
        pointsList.forEach { repository.insert(it) }
    }

    private suspend fun buildPoint(
        title: String,
        note: String,
        location: GeoPoint,
        timestamp: Long,
        photoPath: String? = null
    ): Point {
        val sensorSnapshot = sensorSnapshotPort.readSnapshot()
        return Point(
            timestamp = timestamp,
            latitude = location.latitude,
            longitude = location.longitude,
            title = title,
            note = note,
            pressureHpa = sensorSnapshot.pressureHpa,
            ambientLightLux = sensorSnapshot.ambientLightLux,
            accelerometerX = sensorSnapshot.accelerometerX,
            accelerometerY = sensorSnapshot.accelerometerY,
            accelerometerZ = sensorSnapshot.accelerometerZ,
            gyroscopeX = sensorSnapshot.gyroscopeX,
            gyroscopeY = sensorSnapshot.gyroscopeY,
            gyroscopeZ = sensorSnapshot.gyroscopeZ,
            magnetometerX = sensorSnapshot.magnetometerX,
            magnetometerY = sensorSnapshot.magnetometerY,
            magnetometerZ = sensorSnapshot.magnetometerZ,
            noiseDb = null,
            photoPath = photoPath
        )
    }
}
