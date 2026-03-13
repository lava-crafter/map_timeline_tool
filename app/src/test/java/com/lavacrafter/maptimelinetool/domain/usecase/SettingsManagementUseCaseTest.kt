package com.lavacrafter.maptimelinetool.domain.usecase

import com.lavacrafter.maptimelinetool.domain.model.SettingsDownloadedArea
import com.lavacrafter.maptimelinetool.domain.model.SettingsLanguagePreference
import com.lavacrafter.maptimelinetool.domain.model.SettingsMapCachePolicy
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
}

private class FakeSettingsGateway : SettingsManagementGateway {
    private var timeoutSeconds: Int = 20
    private var cachePolicy: SettingsMapCachePolicy = SettingsMapCachePolicy.WIFI_ONLY
    private var pinnedTagIds: List<Long> = emptyList()
    private var recentTagIds: List<Long> = emptyList()
    private var zoomBehavior: SettingsZoomButtonBehavior = SettingsZoomButtonBehavior.HIDE
    private var languagePreference: SettingsLanguagePreference = SettingsLanguagePreference.FOLLOW_SYSTEM
    private var followSystemTheme: Boolean = true
    private var defaultTagIds: List<Long> = emptyList()
    private var markerScale: Float = 1f
    private var downloadTileSourceId: String = "osm"
    private var downloadMultiThreadEnabled: Boolean = false
    private var downloadThreadCount: Int = 4
    private var downloadedAreas: List<SettingsDownloadedArea> = emptyList()

    override fun getTimeoutSeconds(): Int = timeoutSeconds
    override fun setTimeoutSeconds(seconds: Int) {
        timeoutSeconds = seconds
    }

    override fun getCachePolicy(): SettingsMapCachePolicy = cachePolicy
    override fun setCachePolicy(policy: SettingsMapCachePolicy) {
        cachePolicy = policy
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
