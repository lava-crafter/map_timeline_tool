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

import com.lavacrafter.maptimelinetool.domain.model.SettingsDownloadedArea
import com.lavacrafter.maptimelinetool.domain.model.SettingsLanguagePreference
import com.lavacrafter.maptimelinetool.domain.model.SettingsMapCachePolicy
import com.lavacrafter.maptimelinetool.domain.model.SettingsPhotoCompressFormat
import com.lavacrafter.maptimelinetool.domain.model.SettingsZoomButtonBehavior
import com.lavacrafter.maptimelinetool.domain.repository.SettingsManagementGateway
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsManagementUseCaseTest {
    @Test
    fun `cache policy delegates read and write`() {
        val fake = FakeSettingsGateway()
        val useCase = SettingsManagementUseCase(fake)

        useCase.setCachePolicy(SettingsMapCachePolicy.DISABLED)

        assertEquals(SettingsMapCachePolicy.DISABLED, useCase.getCachePolicy())
    }

    @Test
    fun `downloaded area operations keep behavior`() {
        val fake = FakeSettingsGateway()
        val useCase = SettingsManagementUseCase(fake)
        val area = SettingsDownloadedArea(1.0, 0.0, 1.0, 0.0, 10, 12, 123L)

        val afterAdd = useCase.addDownloadedArea(area)
        val afterRemove = useCase.removeDownloadedArea(area)

        assertEquals(listOf(area), afterAdd)
        assertEquals(emptyList<SettingsDownloadedArea>(), afterRemove)
    }

    @Test
    fun `noise switch delegates read and write`() {
        val fake = FakeSettingsGateway()
        val useCase = SettingsManagementUseCase(fake)

        useCase.setNoiseEnabled(true)

        assertEquals(true, useCase.getNoiseEnabled())
    }

    @Test
    fun `sensor switches delegate read and write`() {
        val fake = FakeSettingsGateway()
        val useCase = SettingsManagementUseCase(fake)

        useCase.setPressureEnabled(false)
        useCase.setAmbientLightEnabled(false)
        useCase.setAccelerometerEnabled(false)
        useCase.setGyroscopeEnabled(false)
        useCase.setMagnetometerEnabled(false)

        assertEquals(false, useCase.getPressureEnabled())
        assertEquals(false, useCase.getAmbientLightEnabled())
        assertEquals(false, useCase.getAccelerometerEnabled())
        assertEquals(false, useCase.getGyroscopeEnabled())
        assertEquals(false, useCase.getMagnetometerEnabled())
    }

    @Test
    fun `photo compression settings delegate read and write`() {
        val fake = FakeSettingsGateway()
        val useCase = SettingsManagementUseCase(fake)

        useCase.setPhotoLosslessEnabled(false)
        useCase.setPhotoCompressFormat(SettingsPhotoCompressFormat.WEBP)
        useCase.setPhotoCompressQuality(65)

        assertEquals(false, useCase.getPhotoLosslessEnabled())
        assertEquals(SettingsPhotoCompressFormat.WEBP, useCase.getPhotoCompressFormat())
        assertEquals(65, useCase.getPhotoCompressQuality())
    }
}

private class FakeSettingsGateway : SettingsManagementGateway {
    private var timeoutSeconds: Int = 20
    private var cachePolicy: SettingsMapCachePolicy = SettingsMapCachePolicy.WIFI_ONLY
    private var satelliteCachePolicy: SettingsMapCachePolicy = SettingsMapCachePolicy.WIFI_ONLY
    private var pinnedTagIds: List<Long> = emptyList()
    private var recentTagIds: List<Long> = emptyList()
    private var zoomBehavior: SettingsZoomButtonBehavior = SettingsZoomButtonBehavior.HIDE
    private var languagePreference: SettingsLanguagePreference = SettingsLanguagePreference.FOLLOW_SYSTEM
    private var followSystemTheme: Boolean = true
    private var defaultTagIds: List<Long> = emptyList()
    private var markerScale: Float = 1f
    private var mapTileSourceId: String = "osm"
    private var downloadTileSourceId: String = "osm"
    private var downloadMultiThreadEnabled: Boolean = false
    private var downloadThreadCount: Int = 4
    private var photoLosslessEnabled: Boolean = true
    private var photoCompressFormat: SettingsPhotoCompressFormat = SettingsPhotoCompressFormat.JPEG
    private var photoCompressQuality: Int = 80
    private var pressureEnabled: Boolean = true
    private var ambientLightEnabled: Boolean = true
    private var accelerometerEnabled: Boolean = true
    private var gyroscopeEnabled: Boolean = true
    private var magnetometerEnabled: Boolean = true
    private var noiseEnabled: Boolean = false
    private var downloadedAreas: List<SettingsDownloadedArea> = emptyList()

    override fun getTimeoutSeconds(): Int = timeoutSeconds
    override fun setTimeoutSeconds(seconds: Int) {
        timeoutSeconds = seconds
    }

    override fun getCachePolicy(): SettingsMapCachePolicy = cachePolicy
    override fun setCachePolicy(policy: SettingsMapCachePolicy) {
        cachePolicy = policy
    }

    override fun getSatelliteCachePolicy(): SettingsMapCachePolicy = satelliteCachePolicy
    override fun setSatelliteCachePolicy(policy: SettingsMapCachePolicy) {
        satelliteCachePolicy = policy
    }

    override fun getPinnedTagIds(): List<Long> = pinnedTagIds
    override fun setPinnedTagIds(tagIds: List<Long>) {
        pinnedTagIds = tagIds
    }

    override fun getRecentTagIds(): List<Long> = recentTagIds
    override fun addRecentTagId(tagId: Long): List<Long> {
        recentTagIds = listOf(tagId) + recentTagIds.filterNot { it == tagId }
        return recentTagIds
    }

    override fun getZoomButtonBehavior(): SettingsZoomButtonBehavior = zoomBehavior
    override fun setZoomButtonBehavior(behavior: SettingsZoomButtonBehavior) {
        zoomBehavior = behavior
    }

    override fun getLanguagePreference(): SettingsLanguagePreference = languagePreference
    override fun setLanguagePreference(preference: SettingsLanguagePreference) {
        languagePreference = preference
    }

    override fun getFollowSystemTheme(): Boolean = followSystemTheme
    override fun setFollowSystemTheme(enabled: Boolean) {
        followSystemTheme = enabled
    }

    override fun getDefaultTagIds(): List<Long> = defaultTagIds
    override fun setDefaultTagIds(tagIds: List<Long>) {
        defaultTagIds = tagIds
    }

    override fun getMarkerScale(): Float = markerScale
    override fun setMarkerScale(scale: Float) {
        markerScale = scale
    }

    override fun getMapTileSourceId(): String = mapTileSourceId
    override fun setMapTileSourceId(sourceId: String) {
        mapTileSourceId = sourceId
    }

    override fun getDownloadTileSourceId(): String = downloadTileSourceId
    override fun setDownloadTileSourceId(sourceId: String) {
        downloadTileSourceId = sourceId
    }

    override fun getDownloadMultiThreadEnabled(): Boolean = downloadMultiThreadEnabled
    override fun setDownloadMultiThreadEnabled(enabled: Boolean) {
        downloadMultiThreadEnabled = enabled
    }

    override fun getDownloadThreadCount(): Int = downloadThreadCount
    override fun setDownloadThreadCount(count: Int) {
        downloadThreadCount = count
    }

    override fun getPhotoLosslessEnabled(): Boolean = photoLosslessEnabled
    override fun setPhotoLosslessEnabled(enabled: Boolean) {
        photoLosslessEnabled = enabled
    }
    override fun getPhotoCompressFormat(): SettingsPhotoCompressFormat = photoCompressFormat
    override fun setPhotoCompressFormat(format: SettingsPhotoCompressFormat) {
        photoCompressFormat = format
    }
    override fun getPhotoCompressQuality(): Int = photoCompressQuality
    override fun setPhotoCompressQuality(quality: Int) {
        photoCompressQuality = quality
    }

    override fun getPressureEnabled(): Boolean = pressureEnabled
    override fun setPressureEnabled(enabled: Boolean) {
        pressureEnabled = enabled
    }
    override fun getAmbientLightEnabled(): Boolean = ambientLightEnabled
    override fun setAmbientLightEnabled(enabled: Boolean) {
        ambientLightEnabled = enabled
    }
    override fun getAccelerometerEnabled(): Boolean = accelerometerEnabled
    override fun setAccelerometerEnabled(enabled: Boolean) {
        accelerometerEnabled = enabled
    }
    override fun getGyroscopeEnabled(): Boolean = gyroscopeEnabled
    override fun setGyroscopeEnabled(enabled: Boolean) {
        gyroscopeEnabled = enabled
    }
    override fun getMagnetometerEnabled(): Boolean = magnetometerEnabled
    override fun setMagnetometerEnabled(enabled: Boolean) {
        magnetometerEnabled = enabled
    }
    override fun getNoiseEnabled(): Boolean = noiseEnabled
    override fun setNoiseEnabled(enabled: Boolean) {
        noiseEnabled = enabled
    }

    override fun getDownloadedAreas(): List<SettingsDownloadedArea> = downloadedAreas
    override fun addDownloadedArea(area: SettingsDownloadedArea): List<SettingsDownloadedArea> {
        downloadedAreas = downloadedAreas + area
        return downloadedAreas
    }

    override fun removeDownloadedArea(area: SettingsDownloadedArea): List<SettingsDownloadedArea> {
        downloadedAreas = downloadedAreas.filterNot { it == area }
        return downloadedAreas
    }

    override fun dedupeDownloadedAreas(): List<SettingsDownloadedArea> {
        downloadedAreas = downloadedAreas.distinct()
        return downloadedAreas
    }
}
