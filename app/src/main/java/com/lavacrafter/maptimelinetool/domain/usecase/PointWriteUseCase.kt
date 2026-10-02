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

package com.lavacrafter.maptimelinetool.domain.usecase

import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.port.SensorSnapshotPort
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.text.formatPointTimestamp
import com.lavacrafter.maptimelinetool.text.sanitizeMultilineText
import com.lavacrafter.maptimelinetool.text.sanitizeSingleLineText
import com.lavacrafter.maptimelinetool.text.MAX_POINT_NOTE_LENGTH
import com.lavacrafter.maptimelinetool.text.MAX_POINT_TITLE_LENGTH
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class PointWriteUseCase(
    private val repository: PointRepositoryGateway,
    private val sensorSnapshotPort: SensorSnapshotPort,
    private val deletePhoto: suspend (String?) -> Unit,
    private val shouldCollectNoise: () -> Boolean = { false },
    private val collectNoiseDb: suspend () -> Float? = { null },
    private val onOptionalFailure: (Exception) -> Unit = { it.printStackTrace() },
    private val photoCommitGuard: PhotoCommitGuard = PhotoCommitGuard()
) {
    suspend fun addPointWithTags(
        title: String,
        note: String,
        location: GeoPoint,
        timestamp: Long,
        tagIds: Set<Long>,
        photoPath: String? = null,
        onCoreSaved: (Long) -> Unit = {}
    ): Long {
        val normalizedTitle = sanitizeSingleLineText(title, MAX_POINT_TITLE_LENGTH)
            .ifBlank { formatPointTimestamp(timestamp) }
        val normalizedNote = sanitizeMultilineText(note, MAX_POINT_NOTE_LENGTH)
        val point = buildPoint(
            title = normalizedTitle,
            note = normalizedNote,
            location = location,
            timestamp = timestamp,
            photoPath = photoPath
        )
        val id = photoCommitGuard.withLock {
            photoCommitGuard.requirePhoto(photoPath)
            repository.inTransaction {
                val insertedId = repository.insert(point)
                tagIds.forEach { tagId -> repository.insertPointTag(insertedId, tagId) }
                insertedId
            }.also(onCoreSaved)
        }
        try {
            if (shouldCollectNoise()) {
                withContext(Dispatchers.IO) {
                    collectNoiseDb()?.let { repository.updateNoiseDb(id, it) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Noise is optional; the already-committed point must not be reported as a failed insert.
            onOptionalFailure(error)
        }
        return id
    }

    suspend fun updatePoint(
        point: Point, title: String, note: String, photoPath: String?, tagIds: Set<Long>? = null,
        onCoreSaved: () -> Unit = {}
    ) {
        val normalizedTitle = sanitizeSingleLineText(title, MAX_POINT_TITLE_LENGTH)
            .ifBlank { point.title }
        val normalizedNote = sanitizeMultilineText(note, MAX_POINT_NOTE_LENGTH)
        photoCommitGuard.withLock {
            photoCommitGuard.requirePhoto(photoPath)
            val retiredPhoto = repository.inTransaction {
                val current = checkNotNull(repository.getById(point.id)) { "Point no longer exists" }
                repository.update(current.copy(title = normalizedTitle, note = normalizedNote, photoPath = photoPath))
                if (tagIds != null) {
                    val oldTagIds = repository.getTagIdsForPoint(point.id).toSet()
                    (oldTagIds - tagIds).forEach { repository.deletePointTag(point.id, it) }
                    (tagIds - oldTagIds).forEach { repository.insertPointTag(point.id, it) }
                }
                current.photoPath?.takeIf { it != photoPath }
            }
            onCoreSaved()
            cleanupUnreferencedPhoto(retiredPhoto)
        }
    }

    suspend fun deletePoint(point: Point) {
        photoCommitGuard.withLock {
            val retiredPhoto = repository.inTransaction {
                val current = checkNotNull(repository.getById(point.id)) { "Point no longer exists" }
                repository.delete(current)
                current.photoPath
            }
            cleanupUnreferencedPhoto(retiredPhoto)
        }
    }

    data class ImportResult(val imported: Int)

    suspend fun importPoints(pointsList: List<Point>): ImportResult {
        photoCommitGuard.withLock {
            repository.inTransaction {
                pointsList.forEach { p ->
                    photoCommitGuard.requirePhoto(p.photoPath)
                    val normalizedPoint = p.copy(
                        title = sanitizeSingleLineText(p.title, MAX_POINT_TITLE_LENGTH)
                            .ifBlank { formatPointTimestamp(p.timestamp) },
                        note = sanitizeMultilineText(p.note, MAX_POINT_NOTE_LENGTH)
                    )
                    // Ordinary CSV is an exchange format: even identical coordinates/times are independent rows.
                    repository.insert(normalizedPoint.copy(id = 0))
                }
            }
        }
        return ImportResult(pointsList.size)
    }

    private suspend fun cleanupUnreferencedPhoto(path: String?) {
        if (path == null) return
        try {
            withContext(NonCancellable + Dispatchers.IO) {
                if (!repository.isPhotoReferenced(path)) deletePhoto(path)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // The row is already committed. A failed best-effort file cleanup is not a failed write.
            onOptionalFailure(error)
        }
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
            locationAccuracyMeters = location.accuracyMeters,
            locationFixTimeMs = location.fixTimeMs,
            locationProvider = location.provider,
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
