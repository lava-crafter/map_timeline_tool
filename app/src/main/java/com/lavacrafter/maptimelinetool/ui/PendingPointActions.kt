package com.lavacrafter.maptimelinetool.ui

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import com.lavacrafter.maptimelinetool.PhotoPersistOptions
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveDecision
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveQuality
import java.util.UUID

enum class LocationAction { NEW_POINT, MAP_CENTER, DOWNLOAD_CENTER, QUICK_ADD_ENABLE, QUICK_ADD_ENTRY, BROWSE }
data class LocationPermissionRequest(val action: LocationAction, val id: String = UUID.randomUUID().toString(),
    val eventTimeMs: Long = System.currentTimeMillis())

fun matchLocationPermissionResult(request: LocationPermissionRequest?, resultId: String?, granted: Boolean): LocationPermissionRequest? =
    request?.takeIf { granted && resultId != null && it.id.isNotBlank() && it.id == resultId }

val LocationPermissionRequestSaver = Saver<LocationPermissionRequest?, Bundle>(
    save = { request -> request.toActionBundle() },
    restore = ::actionFromBundle
)
internal fun LocationPermissionRequest?.toActionBundle() = Bundle().apply {
    this@toActionBundle?.let { putString("action", it.action.name); putString("id", it.id); putLong("event", it.eventTimeMs) }
}
internal fun actionFromBundle(bundle: Bundle?): LocationPermissionRequest? = runCatching {
        requireNotNull(bundle)
        val id = requireNotNull(bundle.getString("id")).also { require(it.isNotBlank()) }
        require(bundle.containsKey("event"))
        LocationPermissionRequest(LocationAction.valueOf(requireNotNull(bundle.getString("action"))), id, bundle.getLong("event"))
    }.getOrNull()

internal fun PointWriteState.ConfirmLocation.toBundle() = Bundle().apply {
    putString("id", id); putString("title", request.title); putString("note", request.note)
    putLong("event", request.eventTimeMs); putLongArray("tags", request.tagIds.toLongArray())
    putString("photo", request.photoPath); putBoolean("lossless", request.options.losslessEnabled)
    putString("format", request.options.compressFormat.name); putInt("quality", request.options.compressQuality)
    putString("locationQuality", decision.quality.name)
    decision.location?.let { location ->
        putDouble("lat", location.latitude); putDouble("lon", location.longitude)
        location.accuracyMeters?.let { putFloat("accuracy", it) }
        location.fixTimeMs?.let { putLong("fix", it) }
        putString("provider", location.provider); putBoolean("mock", location.isMock)
    }
}

internal fun confirmationFromBundle(bundle: Bundle?): PointWriteState.ConfirmLocation? = runCatching {
    val b = requireNotNull(bundle)
    require(listOf("event", "lat", "lon", "tags", "lossless", "format", "quality", "locationQuality").all(b::containsKey))
    val id = requireNotNull(b.getString("id")).also { require(it.isNotBlank()) }
    val location = GeoPoint(b.getDouble("lat"), b.getDouble("lon"),
        if (b.containsKey("accuracy")) b.getFloat("accuracy") else null,
        if (b.containsKey("fix")) b.getLong("fix") else null, b.getString("provider"), b.getBoolean("mock"))
    require(location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
        location.longitude.isFinite() && location.longitude in -180.0..180.0)
    val request = PointWriteRequest.Add(requireNotNull(b.getString("title")), requireNotNull(b.getString("note")),
        b.getLong("event"), requireNotNull(b.getLongArray("tags")).toSet(), b.getString("photo"),
        PhotoPersistOptions(b.getBoolean("lossless"), PhotoCompressFormat.valueOf(requireNotNull(b.getString("format"))), b.getInt("quality")))
    PointWriteState.ConfirmLocation(id, request, LocationSaveDecision(
        quality = LocationSaveQuality.valueOf(requireNotNull(b.getString("locationQuality"))),
        location = location, canSave = true, requiresManualConfirmation = true))
}.getOrNull()
