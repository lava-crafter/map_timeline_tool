package com.lavacrafter.maptimelinetool.ui

import com.lavacrafter.maptimelinetool.PhotoPersistOptions
import com.lavacrafter.maptimelinetool.PreparedPhoto
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveDecision
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveFlow
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSavePolicy
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveQuality
import com.lavacrafter.maptimelinetool.domain.usecase.PointWriteUseCase
import com.lavacrafter.maptimelinetool.text.formatPointTimestamp
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PointWriteRequest {
    data class Add(val title: String, val note: String, val eventTimeMs: Long,
        val tagIds: Set<Long>, val photoPath: String?, val options: PhotoPersistOptions,
        val autoSave: Boolean = false) : PointWriteRequest
    data class Edit(val pointId: Long, val title: String, val note: String,
        val tagIds: Set<Long>, val photoPath: String?, val options: PhotoPersistOptions) : PointWriteRequest
    data class Delete(val pointId: Long, val candidatePhotoPath: String?) : PointWriteRequest
}

sealed interface PointWriteState {
    data object Idle : PointWriteState
    data class Running(val id: String, val request: PointWriteRequest, val coreCommitted: Boolean = false) : PointWriteState
    data class ConfirmLocation(val id: String, val request: PointWriteRequest.Add, val decision: LocationSaveDecision) : PointWriteState
    data class Success(val id: String, val request: PointWriteRequest, val quality: LocationSaveQuality?, val pointId: Long) : PointWriteState
    data class Failure(val id: String, val request: PointWriteRequest, val error: Throwable,
        val locationUnavailable: Boolean = false) : PointWriteState
}

/** Application-context photo adapter; never retains an Activity or its callbacks. */
interface PointOperationPhotos {
    suspend fun prepare(path: String?, options: PhotoPersistOptions): PreparedPhoto
    suspend fun rollbackUnlessReferenced(photo: PreparedPhoto)
    suspend fun commitCleanup(photo: PreparedPhoto)
    suspend fun deleteUnlessReferenced(path: String?)
}

/** One operation owns resolve -> confirmation -> prepare -> core commit -> cleanup -> terminal state. */
class PointWriteOperations(
    private val scope: CoroutineScope,
    private val repository: PointRepositoryGateway,
    private val writer: PointWriteUseCase,
    private val resolveLocation: suspend (LocationSaveFlow) -> LocationSaveDecision,
    private val photos: PointOperationPhotos,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val saveConfirmation: (PointWriteState.ConfirmLocation?) -> Unit = {},
    initialConfirmation: PointWriteState.ConfirmLocation? = null
) {
    private val mutableState = MutableStateFlow<PointWriteState>(initialConfirmation ?: PointWriteState.Idle)
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    // UI entry points are main-thread confined, setting state before launching prevents same-frame double taps.
    fun submit(request: PointWriteRequest): Boolean {
        if (mutableState.value != PointWriteState.Idle || job?.isActive == true) return false
        val snapshot = when (request) {
            is PointWriteRequest.Add -> request.copy(tagIds = request.tagIds.toSet())
            is PointWriteRequest.Edit -> request.copy(tagIds = request.tagIds.toSet())
            is PointWriteRequest.Delete -> request
        }
        val id = UUID.randomUUID().toString()
        mutableState.value = PointWriteState.Running(id, snapshot)
        job = scope.launch { execute(id, snapshot) }
        return true
    }

    fun confirmLocation(id: String) {
        val pending = mutableState.value as? PointWriteState.ConfirmLocation ?: return
        if (pending.id != id || job?.isActive == true) return
        saveConfirmation(null)
        mutableState.value = PointWriteState.Running(id, pending.request)
        job = scope.launch { execute(id, pending.request, pending.decision) }
    }

    fun dismissConfirmation(id: String) {
        val pending = mutableState.value as? PointWriteState.ConfirmLocation ?: return
        if (pending.id == id) {
            saveConfirmation(null)
            mutableState.value = PointWriteState.Idle
        }
    }

    /** Consume a terminal result only after the matching draft has been reset or left available for retry. */
    fun acknowledge(id: String) {
        val terminalId = when (val current = mutableState.value) {
            is PointWriteState.Success -> current.id
            is PointWriteState.Failure -> current.id
            else -> null
        }
        if (terminalId == id) mutableState.value = PointWriteState.Idle
    }

    private suspend fun execute(id: String, request: PointWriteRequest, confirmed: LocationSaveDecision? = null) {
        var prepared: PreparedPhoto? = null
        var committed = false
        var pointId = when (request) {
            is PointWriteRequest.Edit -> request.pointId
            is PointWriteRequest.Delete -> request.pointId
            else -> 0L
        }
        var quality: LocationSaveQuality? = null
        var failure: Throwable? = null
        var unavailable = false
        fun coreSaved(savedId: Long = pointId) {
            committed = true
            pointId = savedId
            mutableState.value = PointWriteState.Running(id, request, coreCommitted = true)
        }
        try {
            when (request) {
                is PointWriteRequest.Add -> {
                    val flow = if (request.autoSave) LocationSaveFlow.AUTO_SAVE else LocationSaveFlow.MANUAL_ADD
                    val decision = if (confirmed == null) resolveLocation(flow) else LocationSavePolicy().evaluate(
                        preciseLocation = null, fallbackLocation = confirmed.location, flow = flow, nowMs = nowMs())
                    if (!decision.canSave || decision.location == null) {
                        unavailable = true
                        throw IllegalStateException("Location is no longer suitable for saving")
                    }
                    quality = decision.quality
                    if (confirmed == null && decision.requiresManualConfirmation) {
                        val pending = PointWriteState.ConfirmLocation(id, request, decision)
                        saveConfirmation(pending)
                        mutableState.value = pending
                        return
                    }
                    prepared = photos.prepare(request.photoPath, request.options)
                    writer.addPointWithTags(request.title.trim().ifBlank { formatPointTimestamp(request.eventTimeMs) },
                        request.note.trim(), decision.location, request.eventTimeMs, request.tagIds,
                        prepared.storedPath, onCoreSaved = { coreSaved(it) })
                }
                is PointWriteRequest.Edit -> {
                    val current = checkNotNull(repository.getById(request.pointId)) { "Point no longer exists" }
                    prepared = if (current.photoPath == request.photoPath) PreparedPhoto(request.photoPath, null, null)
                        else photos.prepare(request.photoPath, request.options)
                    writer.updatePoint(current, request.title, request.note, prepared.storedPath,
                        request.tagIds, onCoreSaved = { coreSaved() })
                }
                is PointWriteRequest.Delete -> {
                    val current = checkNotNull(repository.getById(request.pointId)) { "Point no longer exists" }
                    writer.deletePoint(current, onCoreSaved = { coreSaved() })
                }
            }
        } catch (error: Throwable) {
            failure = error
        } finally {
            withContext(NonCancellable) {
                prepared?.let { photo ->
                    try {
                        if (committed) photos.commitCleanup(photo) else photos.rollbackUnlessReferenced(photo)
                    } catch (cleanupError: Exception) {
                        // A committed row remains successful; the photo adapter logs cleanup errors.
                        failure?.addSuppressed(cleanupError)
                    }
                }
                if (committed && request is PointWriteRequest.Delete) {
                    try { photos.deleteUnlessReferenced(request.candidatePhotoPath) }
                    catch (cleanupError: Exception) { failure?.addSuppressed(cleanupError) }
                }
            }
        }
        if (committed) mutableState.value = PointWriteState.Success(id, request, quality, pointId)
        else mutableState.value = PointWriteState.Failure(id, request,
            failure ?: IllegalStateException("Point write did not commit"), unavailable)
        (failure as? CancellationException)?.let { throw it }
    }
}
