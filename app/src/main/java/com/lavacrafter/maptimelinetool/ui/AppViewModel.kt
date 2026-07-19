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

package com.lavacrafter.maptimelinetool.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lavacrafter.maptimelinetool.data.toDomain
import com.lavacrafter.maptimelinetool.data.toEntity
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.data.TagEntity
import com.lavacrafter.maptimelinetool.AppGraph
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.Tag
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.usecase.PointWriteUseCase
import com.lavacrafter.maptimelinetool.domain.usecase.TagManagementUseCase
import com.lavacrafter.maptimelinetool.export.ZipImporter
import com.lavacrafter.maptimelinetool.text.formatPointTimestamp
import com.lavacrafter.maptimelinetool.text.sanitizePointNote
import com.lavacrafter.maptimelinetool.text.sanitizePointTitle
import com.lavacrafter.maptimelinetool.text.sanitizeTagName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppViewModel(
    app: Application,
    private val repo: PointRepositoryGateway,
    private val pointWriteUseCase: PointWriteUseCase,
    private val tagManagementUseCase: TagManagementUseCase,
    private val locationProvider: LocationProvider
) : AndroidViewModel(app) {
    data class ZipImportResult(
        val legacyTagIdToActualId: Map<Long, Long>
    )

    val points = repo.observeAll().map { list -> list.map { it.toEntity() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags = tagManagementUseCase.observeTags().map { list -> list.map { it.toEntity() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var autoAddJob: kotlinx.coroutines.Job? = null
    private val _autoAdded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val autoAdded = _autoAdded

    fun addPointWithTags(
        title: String,
        note: String,
        location: GeoPoint?,
        timestamp: Long,
        tagIds: Set<Long>,
        photoPath: String? = null
    ) {
        if (location == null) return
        val normalizedTimestamp = normalizeTimestamp(timestamp, location)
        viewModelScope.launch {
            pointWriteUseCase.addPointWithTags(title, note, location, normalizedTimestamp, tagIds, photoPath)
        }
    }

    fun updatePoint(point: PointEntity, title: String, note: String, photoPath: String?) {
        viewModelScope.launch {
            pointWriteUseCase.updatePoint(point.toDomain(), title, note, photoPath)
        }
    }

    fun deletePoint(point: PointEntity) {
        viewModelScope.launch {
            pointWriteUseCase.deletePoint(point.toDomain())
        }
    }

    fun addTag(name: String, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = tagManagementUseCase.addTag(name)
            if (id > 0L) {
                onResult(id)
            }
        }
    }

    fun renameTag(tag: TagEntity, name: String) {
        viewModelScope.launch {
            tagManagementUseCase.renameTag(tag.toDomain(), name)
        }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            tagManagementUseCase.deleteTag(tagId)
        }
    }

    fun importPoints(pointsList: List<Point>) {
        viewModelScope.launch {
            pointWriteUseCase.importPoints(pointsList)
        }
    }

    suspend fun importZipData(importStats: ZipImporter.ImportStats): ZipImportResult {
        return repo.inTransaction {
            val pointIdByIndex = if (importStats.manifest.sections.tags) mutableMapOf<Int, Long>() else null
            importStats.points.forEachIndexed { index, point ->
                val normalizedPoint = point.copy(
                    title = sanitizePointTitle(point.title).ifBlank { formatPointTimestamp(point.timestamp) },
                    note = sanitizePointNote(point.note)
                )
                val existing = repo.findByImportKey(
                    timestamp = normalizedPoint.timestamp,
                    latitude = normalizedPoint.latitude,
                    longitude = normalizedPoint.longitude
                )
                val actualId = if (existing != null) {
                    val merged = mergeImportedPoint(existing, normalizedPoint, importStats.manifest)
                    repo.update(merged)
                    existing.id
                } else {
                    val newId = repo.insert(normalizedPoint.copy(id = 0))
                    newId
                }
                pointIdByIndex?.set(index, actualId)
            }

            val legacyTagIdToActualId = mutableMapOf<Long, Long>()
            if (importStats.manifest.sections.tags) {
                val existingTags = repo.getAllTags()
                val existingTagByName = existingTags.associateBy { sanitizeTagName(it.name).lowercase(Locale.US) }.toMutableMap()
                importStats.tags.forEach { importedTag ->
                    val normalizedName = sanitizeTagName(importedTag.name)
                    if (normalizedName.isBlank()) return@forEach
                    val normalizedKey = normalizedName.lowercase(Locale.US)
                    val actualId = existingTagByName[normalizedKey]?.id ?: repo.insertTag(Tag(name = normalizedName))
                    existingTagByName.putIfAbsent(normalizedKey, Tag(id = actualId, name = normalizedName))
                    legacyTagIdToActualId[importedTag.legacyId] = actualId
                }

                val insertedPairs = mutableSetOf<Pair<Long, Long>>()
                importStats.pointTags.forEach { importedPointTag ->
                    val pointId = pointIdByIndex?.get(importedPointTag.pointIndex) ?: return@forEach
                    val tagId = legacyTagIdToActualId[importedPointTag.legacyTagId] ?: return@forEach
                    val key = pointId to tagId
                    if (insertedPairs.add(key)) {
                        repo.insertPointTag(pointId, tagId)
                    }
                }
            }
            ZipImportResult(legacyTagIdToActualId = legacyTagIdToActualId)
        }
    }

    private fun mergeImportedPoint(
        existing: Point,
        imported: Point,
        manifest: com.lavacrafter.maptimelinetool.export.BackupManifest
    ): Point {
        val overwriteSensors = manifest.version >= 2 && manifest.sections.sensors
        fun <T> sensor(importedValue: T?, existingValue: T?): T? =
            if (overwriteSensors) importedValue else importedValue ?: existingValue
        return imported.copy(
            id = existing.id,
            pressureHpa = sensor(imported.pressureHpa, existing.pressureHpa),
            ambientLightLux = sensor(imported.ambientLightLux, existing.ambientLightLux),
            accelerometerX = sensor(imported.accelerometerX, existing.accelerometerX),
            accelerometerY = sensor(imported.accelerometerY, existing.accelerometerY),
            accelerometerZ = sensor(imported.accelerometerZ, existing.accelerometerZ),
            gyroscopeX = sensor(imported.gyroscopeX, existing.gyroscopeX),
            gyroscopeY = sensor(imported.gyroscopeY, existing.gyroscopeY),
            gyroscopeZ = sensor(imported.gyroscopeZ, existing.gyroscopeZ),
            magnetometerX = sensor(imported.magnetometerX, existing.magnetometerX),
            magnetometerY = sensor(imported.magnetometerY, existing.magnetometerY),
            magnetometerZ = sensor(imported.magnetometerZ, existing.magnetometerZ),
            noiseDb = sensor(imported.noiseDb, existing.noiseDb),
            photoPath = if (manifest.sections.photos && !imported.photoPath.isNullOrBlank()) {
                imported.photoPath
            } else {
                existing.photoPath
            }
        )
    }

    fun setTagForPoint(pointId: Long, tagId: Long, enabled: Boolean) {
        viewModelScope.launch {
            tagManagementUseCase.setTagForPoint(pointId, tagId, enabled)
        }
    }

    suspend fun getTagIdsForPoint(pointId: Long): List<Long> = tagManagementUseCase.getTagIdsForPoint(pointId)

    fun observePointsForTag(tagId: Long) = tagManagementUseCase.observePointsForTag(tagId).map { list -> list.map { it.toEntity() } }

    fun getLastKnownLocation(): GeoPoint? = locationProvider.getLastKnownLocation()

    suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint? = locationProvider.getPreciseLocation(timeoutMs)

    suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? = locationProvider.getFreshLocation(timeoutMs)

    suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? = locationProvider.getBestEffortLocation(timeoutMs)

    fun scheduleAutoAdd(createdAt: Long, timeoutSeconds: Int) {
        autoAddJob?.cancel()
        autoAddJob = viewModelScope.launch {
            kotlinx.coroutines.delay(timeoutSeconds * 1000L)
            val timestamp = createdAt
            val location = getBestEffortLocation(5_000L)
            if (location != null) {
                val normalizedTimestamp = normalizeTimestamp(timestamp, location)
                val title = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(normalizedTimestamp))
                pointWriteUseCase.addPointWithTags(title, "", location, normalizedTimestamp, emptySet())
                _autoAdded.tryEmit(Unit)
            }
        }
    }

    private fun normalizeTimestamp(eventTimeMs: Long, location: GeoPoint): Long {
        val fixTime = location.fixTimeMs ?: return eventTimeMs
        if (fixTime <= 0L) {
            return eventTimeMs
        }
        return maxOf(eventTimeMs, fixTime)
    }

    fun cancelAutoAdd() {
        autoAddJob?.cancel()
        autoAddJob = null
    }

    suspend fun getAllPoints(): List<PointEntity> = repo.getAll().map { it.toEntity() }

    companion object {
        fun factory(app: Application, graph: AppGraph): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppViewModel(
                    app = app,
                    repo = graph.pointRepositoryGateway,
                    pointWriteUseCase = graph.pointWriteUseCase,
                    tagManagementUseCase = graph.tagManagementUseCase,
                    locationProvider = graph.locationProvider
                )
            }
        }
    }
}
