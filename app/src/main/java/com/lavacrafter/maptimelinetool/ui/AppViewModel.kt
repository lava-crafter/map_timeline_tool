package com.lavacrafter.maptimelinetool.ui

import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lavacrafter.maptimelinetool.data.AppDatabase
import com.lavacrafter.maptimelinetool.data.toDomain
import com.lavacrafter.maptimelinetool.data.toEntity
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.data.PointRepository
import com.lavacrafter.maptimelinetool.data.TagEntity
import com.lavacrafter.maptimelinetool.deletePointPhotoFile
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.usecase.PointWriteUseCase
import com.lavacrafter.maptimelinetool.domain.usecase.TagManagementUseCase
import com.lavacrafter.maptimelinetool.sensor.captureSensorSnapshot
import com.lavacrafter.maptimelinetool.ui.HeadingLocationOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: PointRepositoryGateway = PointRepository(AppDatabase.get(app).pointDao())
    private val pointWriteUseCase = PointWriteUseCase(
        repository = repo,
        readSensorSnapshot = { captureSensorSnapshot(getApplication()) },
        deletePhoto = { photoPath -> deletePointPhotoOnIo(photoPath) }
    )
    private val tagManagementUseCase = TagManagementUseCase(repo)
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
        location: Location?,
        timestamp: Long,
        tagIds: Set<Long>,
        photoPath: String? = null
    ) {
        if (location == null) return
        viewModelScope.launch {
            pointWriteUseCase.addPointWithTags(title, note, location, timestamp, tagIds, photoPath)
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

    fun importPoints(pointsList: List<PointEntity>) {
        viewModelScope.launch {
            pointWriteUseCase.importPoints(pointsList.map { it.toDomain() })
        }
    }

    fun setTagForPoint(pointId: Long, tagId: Long, enabled: Boolean) {
        viewModelScope.launch {
            tagManagementUseCase.setTagForPoint(pointId, tagId, enabled)
        }
    }

    suspend fun getTagIdsForPoint(pointId: Long): List<Long> = tagManagementUseCase.getTagIdsForPoint(pointId)

    fun observePointsForTag(tagId: Long) = tagManagementUseCase.observePointsForTag(tagId).map { list -> list.map { it.toEntity() } }

    fun getLastKnownLocation(): Location? {
        return com.lavacrafter.maptimelinetool.LocationUtils.getLastKnownLocation(getApplication())
    }

    suspend fun getFreshLocation(timeoutMs: Long): Location? {
        return com.lavacrafter.maptimelinetool.LocationUtils.getFreshLocation(getApplication(), timeoutMs)
    }

    fun scheduleAutoAdd(createdAt: Long, timeoutSeconds: Int) {
        autoAddJob?.cancel()
        autoAddJob = viewModelScope.launch {
            kotlinx.coroutines.delay(timeoutSeconds * 1000L)
            val timestamp = createdAt
            val title = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
            val location = getFreshLocation(5000L)
            if (location != null) {
                pointWriteUseCase.addPointWithTags(title, "", location, timestamp, emptySet())
                _autoAdded.tryEmit(Unit)
            }
        }
    }

    private suspend fun deletePointPhotoOnIo(photoPath: String?) {
        withContext(Dispatchers.IO) {
            deletePointPhotoFile(getApplication(), photoPath)
        }
    }

    private fun readCachedLocation(): Location? {
        val prefs = getApplication<Application>().getSharedPreferences(HeadingLocationOverlay.LOCATION_PREFS, Application.MODE_PRIVATE)
        if (!prefs.contains(HeadingLocationOverlay.KEY_LAT) || !prefs.contains(HeadingLocationOverlay.KEY_LON)) {
            return null
        }
        return Location("cached").apply {
            latitude = prefs.getFloat(HeadingLocationOverlay.KEY_LAT, 0f).toDouble()
            longitude = prefs.getFloat(HeadingLocationOverlay.KEY_LON, 0f).toDouble()
            time = prefs.getLong(HeadingLocationOverlay.KEY_TIME, System.currentTimeMillis())
        }
    }

    fun cancelAutoAdd() {
        autoAddJob?.cancel()
        autoAddJob = null
    }

    suspend fun getAllPoints(): List<PointEntity> = repo.getAll().map { it.toEntity() }
}
