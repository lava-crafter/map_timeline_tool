package com.lavacrafter.maptimelinetool.domain.usecase

import android.location.Location
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.sensor.SensorSnapshot

class PointWriteUseCase(
    private val repository: PointRepositoryGateway,
    private val readSensorSnapshot: suspend () -> SensorSnapshot,
    private val deletePhoto: suspend (String?) -> Unit
) {
    suspend fun addPointWithTags(
        title: String,
        note: String,
        location: Location,
        timestamp: Long,
        tagIds: Set<Long>,
        photoPath: String? = null
    ) {
        val id = repository.insert(
            buildPointEntity(
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

    private suspend fun buildPointEntity(
        title: String,
        note: String,
        location: Location,
        timestamp: Long,
        photoPath: String? = null
    ): Point {
        val sensorSnapshot = readSensorSnapshot()
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
            photoPath = photoPath
        )
    }
}
