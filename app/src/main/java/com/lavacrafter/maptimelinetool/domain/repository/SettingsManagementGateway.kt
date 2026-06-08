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

    fun getSatelliteCachePolicy(): SettingsMapCachePolicy
    fun setSatelliteCachePolicy(policy: SettingsMapCachePolicy)

    fun getPinnedTagIds(): List<Long>
    fun setPinnedTagIds(tagIds: List<Long>)

    fun getRecentTagIds(): List<Long>
    fun addRecentTagId(tagId: Long): List<Long>

    fun getZoomButtonBehavior(): SettingsZoomButtonBehavior
    fun setZoomButtonBehavior(behavior: SettingsZoomButtonBehavior)

    fun getLanguagePreference(): SettingsLanguagePreference
    fun setLanguagePreference(preference: SettingsLanguagePreference)

    fun getFollowSystemTheme(): Boolean
    fun setFollowSystemTheme(enabled: Boolean)

    fun getDefaultTagIds(): List<Long>
    fun setDefaultTagIds(tagIds: List<Long>)

    fun getMarkerScale(): Float
    fun setMarkerScale(scale: Float)

    fun getMapTileSourceId(): String
    fun setMapTileSourceId(sourceId: String)

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
