package com.lavacrafter.maptimelinetool.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lavacrafter.maptimelinetool.data.toDomain
import com.lavacrafter.maptimelinetool.data.toUi
import com.lavacrafter.maptimelinetool.domain.usecase.SettingsManagementUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel(
    app: Application,
    private val settingsUseCase: SettingsManagementUseCase
) : AndroidViewModel(app) {
    private val initialMapTileSourceId: String = settingsUseCase.getMapTileSourceId()
        .takeIf { sourceId -> mapTileSources.any { it.id == sourceId } }
        ?: mapTileSources.first().id.also { fallbackId ->
            settingsUseCase.setMapTileSourceId(fallbackId)
        }

    private val _uiState = MutableStateFlow(buildSettingsUiState(initialMapTileSourceId))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun reloadFromStore() {
        val validMapTileSourceId = settingsUseCase.getMapTileSourceId()
            .takeIf { sourceId -> mapTileSources.any { it.id == sourceId } }
            ?: mapTileSources.first().id.also { fallbackId ->
                settingsUseCase.setMapTileSourceId(fallbackId)
            }
        _uiState.value = buildSettingsUiState(validMapTileSourceId)
    }

    private fun buildSettingsUiState(validMapTileSourceId: String): SettingsUiState {
        return SettingsUiState(
            followSystemTheme = settingsUseCase.getFollowSystemTheme(),
            languagePreference = settingsUseCase.getLanguagePreference().toUi(),
            timeoutSeconds = settingsUseCase.getTimeoutSeconds(),
            cachePolicy = settingsUseCase.getCachePolicy().toUi(),
            satelliteCachePolicy = settingsUseCase.getSatelliteCachePolicy().toUi(),
            pinnedTagIds = settingsUseCase.getPinnedTagIds().toSet(),
            recentTagIds = settingsUseCase.getRecentTagIds(),
            zoomBehavior = settingsUseCase.getZoomButtonBehavior().toUi(),
            markerScale = settingsUseCase.getMarkerScale(),
            defaultTagIds = settingsUseCase.getDefaultTagIds().toSet(),
            downloadedAreas = settingsUseCase.getDownloadedAreas().map { it.toUi() },
            downloadTileSourceId = settingsUseCase.getDownloadTileSourceId(),
            downloadMultiThreadEnabled = settingsUseCase.getDownloadMultiThreadEnabled(),
            downloadThreadCount = settingsUseCase.getDownloadThreadCount(),
            photoLosslessEnabled = settingsUseCase.getPhotoLosslessEnabled(),
            photoCompressFormat = settingsUseCase.getPhotoCompressFormat().toUi(),
            photoCompressQuality = settingsUseCase.getPhotoCompressQuality(),
            pressureEnabled = settingsUseCase.getPressureEnabled(),
            ambientLightEnabled = settingsUseCase.getAmbientLightEnabled(),
            accelerometerEnabled = settingsUseCase.getAccelerometerEnabled(),
            gyroscopeEnabled = settingsUseCase.getGyroscopeEnabled(),
            magnetometerEnabled = settingsUseCase.getMagnetometerEnabled(),
            noiseEnabled = settingsUseCase.getNoiseEnabled(),
            mapTileSourceId = validMapTileSourceId
        )
    }

    fun setDarkTheme(enabled: Boolean) {
        _uiState.update { it.copy(isDarkTheme = enabled) }
    }

    fun setFollowSystemTheme(enabled: Boolean) {
        settingsUseCase.setFollowSystemTheme(enabled)
        _uiState.update { it.copy(followSystemTheme = enabled) }
    }

    fun setLanguagePreference(preference: LanguagePreference) {
        settingsUseCase.setLanguagePreference(preference.toDomain())
        _uiState.update { it.copy(languagePreference = preference) }
        applyLanguagePreference(preference)
    }

    fun setTimeoutSeconds(seconds: Int) {
        settingsUseCase.setTimeoutSeconds(seconds)
        _uiState.update { it.copy(timeoutSeconds = seconds) }
    }

    fun setCachePolicy(policy: MapCachePolicy) {
        settingsUseCase.setCachePolicy(policy.toDomain())
        _uiState.update { it.copy(cachePolicy = policy) }
    }

    fun setSatelliteCachePolicy(policy: MapCachePolicy) {
        settingsUseCase.setSatelliteCachePolicy(policy.toDomain())
        _uiState.update { it.copy(satelliteCachePolicy = policy) }
    }

    fun setDownloadTileSourceId(sourceId: String) {
        settingsUseCase.setDownloadTileSourceId(sourceId)
        _uiState.update { it.copy(downloadTileSourceId = sourceId) }
    }

    fun setDownloadMultiThreadEnabled(enabled: Boolean) {
        settingsUseCase.setDownloadMultiThreadEnabled(enabled)
        _uiState.update { it.copy(downloadMultiThreadEnabled = enabled) }
    }

    fun setDownloadThreadCount(count: Int) {
        settingsUseCase.setDownloadThreadCount(count)
        _uiState.update { it.copy(downloadThreadCount = count) }
    }

    fun setPhotoLosslessEnabled(enabled: Boolean) {
        settingsUseCase.setPhotoLosslessEnabled(enabled)
        _uiState.update { it.copy(photoLosslessEnabled = enabled) }
    }

    fun setPhotoCompressFormat(format: PhotoCompressFormat) {
        settingsUseCase.setPhotoCompressFormat(format.toDomain())
        _uiState.update { it.copy(photoCompressFormat = format) }
    }

    fun setPhotoCompressQuality(quality: Int) {
        settingsUseCase.setPhotoCompressQuality(quality)
        _uiState.update { it.copy(photoCompressQuality = quality) }
    }

    fun setNoiseEnabled(enabled: Boolean) {
        settingsUseCase.setNoiseEnabled(enabled)
        _uiState.update { it.copy(noiseEnabled = enabled) }
    }

    fun setPressureEnabled(enabled: Boolean) {
        settingsUseCase.setPressureEnabled(enabled)
        _uiState.update { it.copy(pressureEnabled = enabled) }
    }

    fun setAmbientLightEnabled(enabled: Boolean) {
        settingsUseCase.setAmbientLightEnabled(enabled)
        _uiState.update { it.copy(ambientLightEnabled = enabled) }
    }

    fun setAccelerometerEnabled(enabled: Boolean) {
        settingsUseCase.setAccelerometerEnabled(enabled)
        _uiState.update { it.copy(accelerometerEnabled = enabled) }
    }

    fun setGyroscopeEnabled(enabled: Boolean) {
        settingsUseCase.setGyroscopeEnabled(enabled)
        _uiState.update { it.copy(gyroscopeEnabled = enabled) }
    }

    fun setMagnetometerEnabled(enabled: Boolean) {
        settingsUseCase.setMagnetometerEnabled(enabled)
        _uiState.update { it.copy(magnetometerEnabled = enabled) }
    }

    fun setMapTileSourceId(sourceId: String) {
        val validSourceId = sourceId.takeIf { id -> mapTileSources.any { it.id == id } }
            ?: mapTileSources.first().id
        settingsUseCase.setMapTileSourceId(validSourceId)
        _uiState.update { it.copy(mapTileSourceId = validSourceId) }
    }

    fun setZoomBehavior(behavior: ZoomButtonBehavior) {
        settingsUseCase.setZoomButtonBehavior(behavior.toDomain())
        _uiState.update { it.copy(zoomBehavior = behavior) }
    }

    fun setMarkerScale(scale: Float) {
        settingsUseCase.setMarkerScale(scale)
        _uiState.update { it.copy(markerScale = scale) }
    }

    fun setPinnedTagIds(tagIds: Set<Long>) {
        settingsUseCase.setPinnedTagIds(tagIds.toList())
        _uiState.update { it.copy(pinnedTagIds = tagIds) }
    }

    fun addRecentTagId(tagId: Long) {
        _uiState.update { it.copy(recentTagIds = settingsUseCase.addRecentTagId(tagId)) }
    }

    fun toggleDefaultTag(tagId: Long) {
        val next = if (_uiState.value.defaultTagIds.contains(tagId)) {
            _uiState.value.defaultTagIds - tagId
        } else {
            _uiState.value.defaultTagIds + tagId
        }
        settingsUseCase.setDefaultTagIds(next.toList())
        _uiState.update { it.copy(defaultTagIds = next) }
    }

    fun addDownloadedArea(area: DownloadedArea) {
        _uiState.update { it.copy(downloadedAreas = settingsUseCase.addDownloadedArea(area.toDomain()).map { a -> a.toUi() }) }
    }

    fun removeDownloadedArea(area: DownloadedArea) {
        _uiState.update { it.copy(downloadedAreas = settingsUseCase.removeDownloadedArea(area.toDomain()).map { a -> a.toUi() }) }
    }

    fun dedupeDownloadedAreas() {
        _uiState.update { it.copy(downloadedAreas = settingsUseCase.dedupeDownloadedAreas().map { a -> a.toUi() }) }
    }

    companion object {
        fun factory(app: Application, settingsUseCase: SettingsManagementUseCase): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    app = app,
                    settingsUseCase = settingsUseCase
                )
            }
        }
    }
}
