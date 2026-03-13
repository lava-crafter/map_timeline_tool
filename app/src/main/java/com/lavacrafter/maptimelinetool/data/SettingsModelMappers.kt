package com.lavacrafter.maptimelinetool.data

import com.lavacrafter.maptimelinetool.domain.model.SettingsDownloadedArea
import com.lavacrafter.maptimelinetool.domain.model.SettingsLanguagePreference
import com.lavacrafter.maptimelinetool.domain.model.SettingsMapCachePolicy
import com.lavacrafter.maptimelinetool.domain.model.SettingsPhotoCompressFormat
import com.lavacrafter.maptimelinetool.domain.model.SettingsZoomButtonBehavior
import com.lavacrafter.maptimelinetool.ui.DownloadedArea
import com.lavacrafter.maptimelinetool.ui.LanguagePreference
import com.lavacrafter.maptimelinetool.ui.MapCachePolicy
import com.lavacrafter.maptimelinetool.ui.PhotoCompressFormat
import com.lavacrafter.maptimelinetool.ui.ZoomButtonBehavior

fun MapCachePolicy.toDomain(): SettingsMapCachePolicy = SettingsMapCachePolicy.fromValue(value)
fun SettingsMapCachePolicy.toUi(): MapCachePolicy = MapCachePolicy.fromValue(value)

fun ZoomButtonBehavior.toDomain(): SettingsZoomButtonBehavior = SettingsZoomButtonBehavior.fromValue(value)
fun SettingsZoomButtonBehavior.toUi(): ZoomButtonBehavior = ZoomButtonBehavior.fromValue(value)

fun LanguagePreference.toDomain(): SettingsLanguagePreference = SettingsLanguagePreference.fromValue(value)
fun SettingsLanguagePreference.toUi(): LanguagePreference = LanguagePreference.fromValue(value)

fun PhotoCompressFormat.toDomain(): SettingsPhotoCompressFormat = SettingsPhotoCompressFormat.fromValue(value)
fun SettingsPhotoCompressFormat.toUi(): PhotoCompressFormat = PhotoCompressFormat.fromValue(value)

fun DownloadedArea.toDomain(): SettingsDownloadedArea = SettingsDownloadedArea(
    north = north,
    south = south,
    east = east,
    west = west,
    minZoom = minZoom,
    maxZoom = maxZoom,
    createdAt = createdAt
)

fun SettingsDownloadedArea.toUi(): DownloadedArea = DownloadedArea(
    north = north,
    south = south,
    east = east,
    west = west,
    minZoom = minZoom,
    maxZoom = maxZoom,
    createdAt = createdAt
)
