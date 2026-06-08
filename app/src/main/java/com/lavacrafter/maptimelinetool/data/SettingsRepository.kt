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

package com.lavacrafter.maptimelinetool.data

import android.content.Context
import com.lavacrafter.maptimelinetool.domain.model.SettingsDownloadedArea
import com.lavacrafter.maptimelinetool.domain.model.SettingsLanguagePreference
import com.lavacrafter.maptimelinetool.domain.model.SettingsMapCachePolicy
import com.lavacrafter.maptimelinetool.domain.model.SettingsPhotoCompressFormat
import com.lavacrafter.maptimelinetool.domain.model.SettingsZoomButtonBehavior
import com.lavacrafter.maptimelinetool.domain.repository.SettingsManagementGateway
import com.lavacrafter.maptimelinetool.ui.SettingsStore

class SettingsRepository(context: Context) : SettingsManagementGateway {
    private val appContext = context.applicationContext

    override fun getTimeoutSeconds(): Int = SettingsStore.getTimeoutSeconds(appContext)
    override fun setTimeoutSeconds(seconds: Int) = SettingsStore.setTimeoutSeconds(appContext, seconds)

    override fun getCachePolicy(): SettingsMapCachePolicy = SettingsStore.getCachePolicy(appContext).toDomain()
    override fun setCachePolicy(policy: SettingsMapCachePolicy) = SettingsStore.setCachePolicy(appContext, policy.toUi())

    override fun getSatelliteCachePolicy(): SettingsMapCachePolicy = SettingsStore.getSatelliteCachePolicy(appContext).toDomain()
    override fun setSatelliteCachePolicy(policy: SettingsMapCachePolicy) = SettingsStore.setSatelliteCachePolicy(appContext, policy.toUi())

    override fun getPinnedTagIds(): List<Long> = SettingsStore.getPinnedTagIds(appContext)
    override fun setPinnedTagIds(tagIds: List<Long>) = SettingsStore.setPinnedTagIds(appContext, tagIds)

    override fun getRecentTagIds(): List<Long> = SettingsStore.getRecentTagIds(appContext)
    override fun addRecentTagId(tagId: Long): List<Long> = SettingsStore.addRecentTagId(appContext, tagId)

    override fun getZoomButtonBehavior(): SettingsZoomButtonBehavior = SettingsStore.getZoomButtonBehavior(appContext).toDomain()
    override fun setZoomButtonBehavior(behavior: SettingsZoomButtonBehavior) =
        SettingsStore.setZoomButtonBehavior(appContext, behavior.toUi())

    override fun getLanguagePreference(): SettingsLanguagePreference = SettingsStore.getLanguagePreference(appContext).toDomain()
    override fun setLanguagePreference(preference: SettingsLanguagePreference) = SettingsStore.setLanguagePreference(appContext, preference.toUi())

    override fun getFollowSystemTheme(): Boolean = SettingsStore.getFollowSystemTheme(appContext)
    override fun setFollowSystemTheme(enabled: Boolean) = SettingsStore.setFollowSystemTheme(appContext, enabled)

    override fun getDefaultTagIds(): List<Long> = SettingsStore.getDefaultTagIds(appContext)
    override fun setDefaultTagIds(tagIds: List<Long>) = SettingsStore.setDefaultTagIds(appContext, tagIds)

    override fun getMarkerScale(): Float = SettingsStore.getMarkerScale(appContext)
    override fun setMarkerScale(scale: Float) = SettingsStore.setMarkerScale(appContext, scale)

    override fun getMapTileSourceId(): String = SettingsStore.getMapTileSourceId(appContext)
    override fun setMapTileSourceId(sourceId: String) = SettingsStore.setMapTileSourceId(appContext, sourceId)

    override fun getDownloadTileSourceId(): String = SettingsStore.getDownloadTileSourceId(appContext)
    override fun setDownloadTileSourceId(sourceId: String) = SettingsStore.setDownloadTileSourceId(appContext, sourceId)

    override fun getDownloadMultiThreadEnabled(): Boolean = SettingsStore.getDownloadMultiThreadEnabled(appContext)
    override fun setDownloadMultiThreadEnabled(enabled: Boolean) =
        SettingsStore.setDownloadMultiThreadEnabled(appContext, enabled)

    override fun getDownloadThreadCount(): Int = SettingsStore.getDownloadThreadCount(appContext)
    override fun setDownloadThreadCount(count: Int) = SettingsStore.setDownloadThreadCount(appContext, count)

    override fun getPhotoLosslessEnabled(): Boolean = SettingsStore.getPhotoLosslessEnabled(appContext)
    override fun setPhotoLosslessEnabled(enabled: Boolean) = SettingsStore.setPhotoLosslessEnabled(appContext, enabled)
    override fun getPhotoCompressFormat(): SettingsPhotoCompressFormat = SettingsStore.getPhotoCompressFormat(appContext).toDomain()
    override fun setPhotoCompressFormat(format: SettingsPhotoCompressFormat) =
        SettingsStore.setPhotoCompressFormat(appContext, format.toUi())
    override fun getPhotoCompressQuality(): Int = SettingsStore.getPhotoCompressQuality(appContext)
    override fun setPhotoCompressQuality(quality: Int) = SettingsStore.setPhotoCompressQuality(appContext, quality)

    override fun getPressureEnabled(): Boolean = SettingsStore.getPressureEnabled(appContext)
    override fun setPressureEnabled(enabled: Boolean) = SettingsStore.setPressureEnabled(appContext, enabled)
    override fun getAmbientLightEnabled(): Boolean = SettingsStore.getAmbientLightEnabled(appContext)
    override fun setAmbientLightEnabled(enabled: Boolean) = SettingsStore.setAmbientLightEnabled(appContext, enabled)
    override fun getAccelerometerEnabled(): Boolean = SettingsStore.getAccelerometerEnabled(appContext)
    override fun setAccelerometerEnabled(enabled: Boolean) = SettingsStore.setAccelerometerEnabled(appContext, enabled)
    override fun getGyroscopeEnabled(): Boolean = SettingsStore.getGyroscopeEnabled(appContext)
    override fun setGyroscopeEnabled(enabled: Boolean) = SettingsStore.setGyroscopeEnabled(appContext, enabled)
    override fun getMagnetometerEnabled(): Boolean = SettingsStore.getMagnetometerEnabled(appContext)
    override fun setMagnetometerEnabled(enabled: Boolean) = SettingsStore.setMagnetometerEnabled(appContext, enabled)
    override fun getNoiseEnabled(): Boolean = SettingsStore.getNoiseEnabled(appContext)
    override fun setNoiseEnabled(enabled: Boolean) = SettingsStore.setNoiseEnabled(appContext, enabled)

    override fun getDownloadedAreas(): List<SettingsDownloadedArea> = SettingsStore.getDownloadedAreas(appContext).map { it.toDomain() }
    override fun addDownloadedArea(area: SettingsDownloadedArea): List<SettingsDownloadedArea> =
        SettingsStore.addDownloadedArea(appContext, area.toUi()).map { it.toDomain() }

    override fun removeDownloadedArea(area: SettingsDownloadedArea): List<SettingsDownloadedArea> =
        SettingsStore.removeDownloadedArea(appContext, area.toUi()).map { it.toDomain() }

    override fun dedupeDownloadedAreas(): List<SettingsDownloadedArea> =
        SettingsStore.dedupeDownloadedAreas(appContext).map { it.toDomain() }
}
