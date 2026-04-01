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
            onResult(id)
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

    suspend fun importZipData(importStats: ZipImporter.ImportStats) {
        val existingPoints = repo.getAll()
        val existingMap = existingPoints.associateBy {
            Triple(it.timestamp, it.latitude, it.longitude)
        }.toMutableMap()

        val pointIdByIndex = mutableMapOf<Int, Long>()
        importStats.points.forEachIndexed { index, point ->
            val key = Triple(point.timestamp, point.latitude, point.longitude)
            val existing = existingMap[key]
            val actualId = if (existing != null) {
                val merged = point.copy(id = existing.id)
                repo.update(merged)
                existingMap[key] = merged
                existing.id
            } else {
                val newId = repo.insert(point.copy(id = 0))
                existingMap[key] = point.copy(id = newId)
                newId
            }
            pointIdByIndex[index] = actualId
        }

        if (importStats.tags.isEmpty() || importStats.pointTags.isEmpty()) return

        val existingTags = repo.observeTags().first()
        val existingTagByName = existingTags.associateBy { it.name.trim().lowercase(Locale.US) }.toMutableMap()
        val legacyTagIdToActualId = mutableMapOf<Long, Long>()
        importStats.tags.forEach { importedTag ->
            val normalizedName = importedTag.name.trim().lowercase(Locale.US)
            val actualId = existingTagByName[normalizedName]?.id ?: repo.insertTag(Tag(name = importedTag.name.trim()))
            existingTagByName.putIfAbsent(normalizedName, Tag(id = actualId, name = importedTag.name.trim()))
            legacyTagIdToActualId[importedTag.legacyId] = actualId
        }

        val insertedPairs = mutableSetOf<Pair<Long, Long>>()
        importStats.pointTags.forEach { importedPointTag ->
            val pointId = pointIdByIndex[importedPointTag.pointIndex] ?: return@forEach
            val tagId = legacyTagIdToActualId[importedPointTag.legacyTagId] ?: return@forEach
            val key = pointId to tagId
            if (insertedPairs.add(key)) {
                repo.insertPointTag(pointId, tagId)
            }
        }
    }

    fun setTagForPoint(pointId: Long, tagId: Long, enabled: Boolean) {
        viewModelScope.launch {
            tagManagementUseCase.setTagForPoint(pointId, tagId, enabled)
        }
    }

    suspend fun getTagIdsForPoint(pointId: Long): List<Long> = tagManagementUseCase.getTagIdsForPoint(pointId)

    fun observePointsForTag(tagId: Long) = tagManagementUseCase.observePointsForTag(tagId).map { list -> list.map { it.toEntity() } }

    fun getLastKnownLocation(): GeoPoint? = locationProvider.getLastKnownLocation()

    suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? = locationProvider.getFreshLocation(timeoutMs)

    suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? = locationProvider.getBestEffortLocation(timeoutMs)

    fun scheduleAutoAdd(createdAt: Long, timeoutSeconds: Int) {
        autoAddJob?.cancel()
        autoAddJob = viewModelScope.launch {
            kotlinx.coroutines.delay(timeoutSeconds * 1000L)
            val timestamp = createdAt
            val location = getBestEffortLocation(12000L)
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
