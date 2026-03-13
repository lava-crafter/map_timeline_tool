package com.lavacrafter.maptimelinetool.domain.repository

import com.lavacrafter.maptimelinetool.domain.model.SettingsDownloadedArea
import com.lavacrafter.maptimelinetool.domain.model.SettingsLanguagePreference
import com.lavacrafter.maptimelinetool.domain.model.SettingsMapCachePolicy
import com.lavacrafter.maptimelinetool.domain.model.SettingsPhotoCompressFormat
import com.lavacrafter.maptimelinetool.domain.model.SettingsZoomButtonBehavior

interface SettingsManagementGateway {
    fun getTimeoutSeconds(): Int
    fun setTimeoutSeconds(seconds: Int)

    fun getCachePolicy(): SettingsMapCachePolicy
    fun setCachePolicy(policy: SettingsMapCachePolicy)

    fun getPinnedTagIds(): List<Long>
    fun setPinnedTagIds(tagIds: List<Long>)

    fun getRecentTagIds(): List<Long>
    fun addRecentTagId(tagId: Long): List<Long>

    fun getZoomButtonBehavior(): SettingsZoomButtonBehavior
    fun setZoomButtonBehavior(behavior: SettingsZoomButtonBehavior)

    fun getLanguagePreference(): SettingsLanguagePreference

    fun getFollowSystemTheme(): Boolean
    fun setFollowSystemTheme(enabled: Boolean)

    fun getDefaultTagIds(): List<Long>
    fun setDefaultTagIds(tagIds: List<Long>)

    fun getMarkerScale(): Float
    fun setMarkerScale(scale: Float)

    fun getDownloadTileSourceId(): String
    fun setDownloadTileSourceId(sourceId: String)

    fun getDownloadMultiThreadEnabled(): Boolean
    fun setDownloadMultiThreadEnabled(enabled: Boolean)

    fun getDownloadThreadCount(): Int
    fun setDownloadThreadCount(count: Int)

    fun getPhotoLosslessEnabled(): Boolean
    fun setPhotoLosslessEnabled(enabled: Boolean)
    fun getPhotoCompressFormat(): SettingsPhotoCompressFormat
    fun setPhotoCompressFormat(format: SettingsPhotoCompressFormat)
    fun getPhotoCompressQuality(): Int
    fun setPhotoCompressQuality(quality: Int)

    fun getPressureEnabled(): Boolean
    fun setPressureEnabled(enabled: Boolean)
    fun getAmbientLightEnabled(): Boolean
    fun setAmbientLightEnabled(enabled: Boolean)
    fun getAccelerometerEnabled(): Boolean
    fun setAccelerometerEnabled(enabled: Boolean)
    fun getGyroscopeEnabled(): Boolean
    fun setGyroscopeEnabled(enabled: Boolean)
    fun getMagnetometerEnabled(): Boolean
    fun setMagnetometerEnabled(enabled: Boolean)
    fun getNoiseEnabled(): Boolean
    fun setNoiseEnabled(enabled: Boolean)

    fun getDownloadedAreas(): List<SettingsDownloadedArea>
    fun addDownloadedArea(area: SettingsDownloadedArea): List<SettingsDownloadedArea>
    fun removeDownloadedArea(area: SettingsDownloadedArea): List<SettingsDownloadedArea>
    fun dedupeDownloadedAreas(): List<SettingsDownloadedArea>
}
