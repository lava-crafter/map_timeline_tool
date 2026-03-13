package com.lavacrafter.maptimelinetool.domain.usecase

import com.lavacrafter.maptimelinetool.domain.model.SettingsDownloadedArea
import com.lavacrafter.maptimelinetool.domain.model.SettingsLanguagePreference
import com.lavacrafter.maptimelinetool.domain.model.SettingsMapCachePolicy
import com.lavacrafter.maptimelinetool.domain.model.SettingsPhotoCompressFormat
import com.lavacrafter.maptimelinetool.domain.model.SettingsZoomButtonBehavior
import com.lavacrafter.maptimelinetool.domain.repository.SettingsManagementGateway

class SettingsManagementUseCase(
    private val settingsGateway: SettingsManagementGateway
) {
    fun getTimeoutSeconds(): Int = settingsGateway.getTimeoutSeconds()
    fun setTimeoutSeconds(seconds: Int) = settingsGateway.setTimeoutSeconds(seconds)

    fun getCachePolicy(): SettingsMapCachePolicy = settingsGateway.getCachePolicy()
    fun setCachePolicy(policy: SettingsMapCachePolicy) = settingsGateway.setCachePolicy(policy)

    fun getPinnedTagIds(): List<Long> = settingsGateway.getPinnedTagIds()
    fun setPinnedTagIds(tagIds: List<Long>) = settingsGateway.setPinnedTagIds(tagIds)

    fun getRecentTagIds(): List<Long> = settingsGateway.getRecentTagIds()
    fun addRecentTagId(tagId: Long): List<Long> = settingsGateway.addRecentTagId(tagId)

    fun getZoomButtonBehavior(): SettingsZoomButtonBehavior = settingsGateway.getZoomButtonBehavior()
    fun setZoomButtonBehavior(behavior: SettingsZoomButtonBehavior) = settingsGateway.setZoomButtonBehavior(behavior)

    fun getLanguagePreference(): SettingsLanguagePreference = settingsGateway.getLanguagePreference()

    fun getFollowSystemTheme(): Boolean = settingsGateway.getFollowSystemTheme()
    fun setFollowSystemTheme(enabled: Boolean) = settingsGateway.setFollowSystemTheme(enabled)

    fun getDefaultTagIds(): List<Long> = settingsGateway.getDefaultTagIds()
    fun setDefaultTagIds(tagIds: List<Long>) = settingsGateway.setDefaultTagIds(tagIds)

    fun getMarkerScale(): Float = settingsGateway.getMarkerScale()
    fun setMarkerScale(scale: Float) = settingsGateway.setMarkerScale(scale)

    fun getDownloadTileSourceId(): String = settingsGateway.getDownloadTileSourceId()
    fun setDownloadTileSourceId(sourceId: String) = settingsGateway.setDownloadTileSourceId(sourceId)

    fun getDownloadMultiThreadEnabled(): Boolean = settingsGateway.getDownloadMultiThreadEnabled()
    fun setDownloadMultiThreadEnabled(enabled: Boolean) = settingsGateway.setDownloadMultiThreadEnabled(enabled)

    fun getDownloadThreadCount(): Int = settingsGateway.getDownloadThreadCount()
    fun setDownloadThreadCount(count: Int) = settingsGateway.setDownloadThreadCount(count)

    fun getPhotoLosslessEnabled(): Boolean = settingsGateway.getPhotoLosslessEnabled()
    fun setPhotoLosslessEnabled(enabled: Boolean) = settingsGateway.setPhotoLosslessEnabled(enabled)
    fun getPhotoCompressFormat(): SettingsPhotoCompressFormat = settingsGateway.getPhotoCompressFormat()
    fun setPhotoCompressFormat(format: SettingsPhotoCompressFormat) = settingsGateway.setPhotoCompressFormat(format)
    fun getPhotoCompressQuality(): Int = settingsGateway.getPhotoCompressQuality()
    fun setPhotoCompressQuality(quality: Int) = settingsGateway.setPhotoCompressQuality(quality)

    fun getPressureEnabled(): Boolean = settingsGateway.getPressureEnabled()
    fun setPressureEnabled(enabled: Boolean) = settingsGateway.setPressureEnabled(enabled)
    fun getAmbientLightEnabled(): Boolean = settingsGateway.getAmbientLightEnabled()
    fun setAmbientLightEnabled(enabled: Boolean) = settingsGateway.setAmbientLightEnabled(enabled)
    fun getAccelerometerEnabled(): Boolean = settingsGateway.getAccelerometerEnabled()
    fun setAccelerometerEnabled(enabled: Boolean) = settingsGateway.setAccelerometerEnabled(enabled)
    fun getGyroscopeEnabled(): Boolean = settingsGateway.getGyroscopeEnabled()
    fun setGyroscopeEnabled(enabled: Boolean) = settingsGateway.setGyroscopeEnabled(enabled)
    fun getMagnetometerEnabled(): Boolean = settingsGateway.getMagnetometerEnabled()
    fun setMagnetometerEnabled(enabled: Boolean) = settingsGateway.setMagnetometerEnabled(enabled)
    fun getNoiseEnabled(): Boolean = settingsGateway.getNoiseEnabled()
    fun setNoiseEnabled(enabled: Boolean) = settingsGateway.setNoiseEnabled(enabled)

    fun getDownloadedAreas(): List<SettingsDownloadedArea> = settingsGateway.getDownloadedAreas()
    fun addDownloadedArea(area: SettingsDownloadedArea): List<SettingsDownloadedArea> = settingsGateway.addDownloadedArea(area)
    fun removeDownloadedArea(area: SettingsDownloadedArea): List<SettingsDownloadedArea> = settingsGateway.removeDownloadedArea(area)
    fun dedupeDownloadedAreas(): List<SettingsDownloadedArea> = settingsGateway.dedupeDownloadedAreas()
}
