package com.lavacrafter.maptimelinetool.ui

import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lavacrafter.maptimelinetool.data.AppDatabase
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.data.PointRepository
import com.lavacrafter.maptimelinetool.data.TagEntity
import com.lavacrafter.maptimelinetool.deletePointPhotoFile
import com.lavacrafter.maptimelinetool.sensor.captureSensorSnapshot
import com.lavacrafter.maptimelinetool.ui.HeadingLocationOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PointRepository(AppDatabase.get(app).pointDao())
    val points = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags = repo.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
            val id = repo.insert(
                buildPointEntity(
                    title = title,
                    note = note,
                    location = location,
                    timestamp = timestamp,
                    photoPath = photoPath
                )
            )
            tagIds.forEach { tagId ->
                repo.insertPointTag(id, tagId)
            }
        }
    }

    fun updatePoint(point: PointEntity, title: String, note: String, photoPath: String?) {
        viewModelScope.launch {
            repo.update(point.copy(title = title, note = note, photoPath = photoPath))
            if (point.photoPath != photoPath) {
                deletePointPhotoOnIo(point.photoPath)
            }
        }
    }

    fun deletePoint(point: PointEntity) {
        viewModelScope.launch {
            repo.delete(point)
            deletePointPhotoOnIo(point.photoPath)
        }
    }

    fun addTag(name: String, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.insertTag(TagEntity(name = name))
            onResult(id)
        }
    }

    fun renameTag(tag: TagEntity, name: String) {
        viewModelScope.launch {
            repo.updateTag(tag.copy(name = name))
        }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            repo.deleteTag(tagId)
        }
    }

    fun importPoints(pointsList: List<PointEntity>) {
        viewModelScope.launch {
            pointsList.forEach { repo.insert(it) }
        }
    }

    fun setTagForPoint(pointId: Long, tagId: Long, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) repo.insertPointTag(pointId, tagId) else repo.deletePointTag(pointId, tagId)
        }
    }

    suspend fun getTagIdsForPoint(pointId: Long): List<Long> = repo.getTagIdsForPoint(pointId)

    fun observePointsForTag(tagId: Long) = repo.observePointsForTag(tagId)

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
                repo.insert(
                    buildPointEntity(
                        title = title,
                        note = "",
                        location = location,
                        timestamp = timestamp
                    )
                )
                _autoAdded.tryEmit(Unit)
            }
        }
    }

    private suspend fun buildPointEntity(
        title: String,
        note: String,
        location: Location,
        timestamp: Long,
        photoPath: String? = null
    ): PointEntity {
        val sensorSnapshot = captureSensorSnapshot(getApplication())
        return PointEntity(
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

    suspend fun getAllPoints(): List<PointEntity> = repo.getAll()
}
