package com.lavacrafter.maptimelinetool.ui

import com.lavacrafter.maptimelinetool.PhotoPersistOptions
import com.lavacrafter.maptimelinetool.PreparedPhoto
import com.lavacrafter.maptimelinetool.domain.model.*
import com.lavacrafter.maptimelinetool.domain.port.SensorSnapshotPort
import com.lavacrafter.maptimelinetool.domain.repository.*
import com.lavacrafter.maptimelinetool.domain.usecase.*
import com.lavacrafter.maptimelinetool.text.formatPointTimestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class PointWriteOperationsTest {
    @Test fun `confirmation camera and saving always pause auto submission`() {
        assertTrue(canAdvanceAutoSave(true, event, false, PointWriteState.Idle, false))
        assertFalse(canAdvanceAutoSave(true, event, false, PointWriteState.Idle, true))
        assertFalse(canAdvanceAutoSave(true, event, true, PointWriteState.Idle, false))
        assertFalse(canAdvanceAutoSave(false, event, false, PointWriteState.Idle, false))
        assertFalse(canAdvanceAutoSave(true, null, false, PointWriteState.Idle, false))
        assertFalse(canAdvanceAutoSave(true, event, false, PointWriteState.Running("id", request()), false))
        assertFalse(canAdvanceAutoSave(true, event, false, PointWriteState.ConfirmLocation("id", request(), decision()), false))
        assertFalse(canAdvanceAutoSave(true, event, false, PointWriteState.Success("id", request(), decision().quality, 1), false))
    }
    private val event = 1_710_000_000_000L
    private val fix = GeoPoint(10.0, 20.0, 5f, event + 1000, "gps")
    private fun request() = PointWriteRequest.Add("", "note", event, setOf(2), "candidate", PhotoPersistOptions(true, PhotoCompressFormat.JPEG, 90))
    private fun decision() = LocationSaveDecision(LocationSaveQuality.PRECISE_FRESH, fix, true, false)
    private fun writer(repo: OperationRepo, noise: suspend () -> Float? = { null }, noiseEnabled: Boolean = false,
        snapshot: suspend () -> PointSensorSnapshot = { PointSensorSnapshot() }) = PointWriteUseCase(repo,
        object : SensorSnapshotPort { override suspend fun readSnapshot() = snapshot() }, {},
        shouldCollectNoise = { noiseEnabled }, collectNoiseDb = noise)
    private suspend fun PointWriteOperations.terminal() = withTimeout(5000) {
        state.first { it is PointWriteState.Success || it is PointWriteState.Failure }
    }

    @Test fun `resolving survives observer replacement and blocks double submit`() = runBlocking {
        val repo = OperationRepo(); val photos = OperationPhotos(); val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val operations = PointWriteOperations(this, repo, writer(repo), { started.complete(Unit); release.await(); decision() }, photos)
        assertTrue(operations.submit(request()))
        started.await()
        val id = (operations.state.value as PointWriteState.Running).id
        assertFalse(operations.submit(request()))
        assertEquals(id, (operations.state.first() as PointWriteState.Running).id)
        release.complete(Unit)
        val success = operations.terminal() as PointWriteState.Success
        assertEquals(id, success.id); assertEquals(1, repo.rows.size)
        assertEquals(event, repo.rows.single().timestamp)
        assertEquals(event + 1000, repo.rows.single().locationFixTimeMs)
        assertEquals(formatPointTimestamp(event), repo.rows.single().title)
        assertEquals(listOf(1L to 2L), repo.links)
        assertEquals(1, photos.commits); assertEquals(0, photos.rollbacks)
        assertFalse(operations.submit(request()))
        operations.acknowledge("wrong"); assertEquals(success, operations.state.value)
        operations.acknowledge(id); assertEquals(PointWriteState.Idle, operations.state.value)
        Unit
    }

    @Test fun `database failure rolls back generated photo and preserves retry request`() = runBlocking {
        val repo = OperationRepo().apply { failInsert = true }; val photos = OperationPhotos()
        val operations = PointWriteOperations(this, repo, writer(repo), { decision() }, photos)
        operations.submit(request())
        val failed = operations.terminal() as PointWriteState.Failure
        assertEquals(request(), failed.request); assertTrue(repo.rows.isEmpty())
        assertEquals(1, photos.rollbacks); assertEquals(0, photos.commits)
        operations.acknowledge(failed.id); repo.failInsert = false
        assertTrue(operations.submit(failed.request))
        assertTrue(operations.terminal() is PointWriteState.Success)
        assertEquals(1, repo.rows.size)
        Unit
    }

    @Test fun `confirmation prevents auto submit and stale restored fix cannot commit`() = runBlocking {
        val repo = OperationRepo(); val photos = OperationPhotos()
        val confirmation = PointWriteState.ConfirmLocation("saved-id", request(),
            LocationSaveDecision(LocationSaveQuality.LAST_KNOWN_RECENT, fix, true, true))
        var saved: PointWriteState.ConfirmLocation? = confirmation
        val operations = PointWriteOperations(this, repo, writer(repo), { error("must not resolve") }, photos,
            nowMs = { event + 3_601_001 }, saveConfirmation = { saved = it }, initialConfirmation = confirmation)
        assertFalse(operations.submit(request().copy(autoSave = true)))
        operations.confirmLocation("wrong"); assertEquals(confirmation, operations.state.value)
        operations.confirmLocation(confirmation.id)
        val failure = operations.terminal() as PointWriteState.Failure
        assertTrue(failure.locationUnavailable); assertTrue(repo.rows.isEmpty()); assertNull(saved)
        assertEquals(0, photos.prepares)
        Unit
    }

    @Test fun `confirmation snapshots request and dismiss checks operation id`() = runBlocking {
        val repo = OperationRepo(); val photos = OperationPhotos(); val tags = mutableSetOf(2L)
        var saved: PointWriteState.ConfirmLocation? = null
        val operations = PointWriteOperations(this, repo, writer(repo), {
            decision().copy(quality = LocationSaveQuality.FRESH_BUT_LOW_ACCURACY, requiresManualConfirmation = true)
        }, photos, saveConfirmation = { saved = it })
        operations.submit(request().copy(tagIds = tags)); tags.add(3)
        val pending = withTimeout(5000) { operations.state.first { it is PointWriteState.ConfirmLocation } } as PointWriteState.ConfirmLocation
        assertEquals(setOf(2L), pending.request.tagIds); assertEquals(pending, saved)
        assertTrue(repo.rows.isEmpty()); assertEquals(0, photos.prepares)
        operations.dismissConfirmation("wrong"); assertEquals(pending, operations.state.value)
        operations.dismissConfirmation(pending.id); assertNull(saved); assertEquals(PointWriteState.Idle, operations.state.value)
        Unit
    }

    @Test fun `cancellation during optional noise is committed success not retry failure`() = runBlocking {
        val repo = OperationRepo(); val photos = OperationPhotos(); val noiseStarted = CompletableDeferred<Unit>()
        val parent = SupervisorJob(); val owner = CoroutineScope(coroutineContext + parent)
        val operations = PointWriteOperations(owner, repo, writer(repo, noise = {
            noiseStarted.complete(Unit); awaitCancellation()
        }, noiseEnabled = true), { decision() }, photos)
        operations.submit(request()); noiseStarted.await()
        assertTrue((operations.state.value as PointWriteState.Running).coreCommitted)
        parent.cancel()
        assertTrue(operations.terminal() is PointWriteState.Success)
        assertEquals(1, repo.rows.size); assertEquals(1, photos.commits); assertEquals(0, photos.rollbacks)
        Unit
    }

    @Test fun `cancel before core commit rolls back without success`() = runBlocking {
        val repo = OperationRepo(); val photos = OperationPhotos(); val sampled = CompletableDeferred<Unit>()
        val parent = SupervisorJob()
        val operations = PointWriteOperations(CoroutineScope(coroutineContext + parent), repo, writer(repo, snapshot = {
            sampled.complete(Unit); awaitCancellation()
        }), { decision() }, photos)
        operations.submit(request()); sampled.await(); parent.cancel()
        assertTrue(operations.terminal() is PointWriteState.Failure)
        assertTrue(repo.rows.isEmpty()); assertEquals(1, photos.rollbacks)
        Unit
    }

    @Test fun `cancel at transaction return still publishes committed success`() = runBlocking {
        val repo = OperationRepo(); val photos = OperationPhotos()
        val committed = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        repo.afterTransaction = { committed.complete(Unit); release.await() }
        val parent = SupervisorJob()
        val operations = PointWriteOperations(CoroutineScope(coroutineContext + parent), repo, writer(repo), { decision() }, photos)
        operations.submit(request()); committed.await(); parent.cancel(); release.complete(Unit)
        assertTrue(operations.terminal() is PointWriteState.Success)
        assertEquals(1, repo.rows.size); assertEquals(1, photos.commits)
        Unit
    }

    @Test fun `edit and delete cancelled after transaction remain successful`() = runBlocking {
        for (delete in listOf(false, true)) {
            val repo = OperationRepo(); val photos = OperationPhotos()
            repo.rows += Point(id = 1, timestamp = event, latitude = 10.0, longitude = 20.0, title = "old", note = "", photoPath = "old")
            val committed = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            repo.afterTransaction = { committed.complete(Unit); release.await() }
            val parent = SupervisorJob()
            val operations = PointWriteOperations(CoroutineScope(coroutineContext + parent), repo, writer(repo), { decision() }, photos)
            operations.submit(if (delete) PointWriteRequest.Delete(1, "candidate") else
                PointWriteRequest.Edit(1, "edited", "new note", setOf(2), "candidate", request().options))
            committed.await(); parent.cancel(); release.complete(Unit)
            assertTrue(operations.terminal() is PointWriteState.Success)
            if (delete) { assertTrue(repo.rows.isEmpty()); assertEquals(1, photos.deletes) }
            else { assertEquals("edited", repo.rows.single().title); assertEquals(1, photos.commits) }
            assertEquals(0, photos.rollbacks)
        }
        Unit
    }
}

private class OperationPhotos : PointOperationPhotos {
    var prepares = 0; var commits = 0; var rollbacks = 0; var deletes = 0
    override suspend fun prepare(path: String?, options: PhotoPersistOptions): PreparedPhoto {
        prepares++; return PreparedPhoto("generated", "generated", path)
    }
    override suspend fun rollbackUnlessReferenced(photo: PreparedPhoto) { rollbacks++ }
    override suspend fun commitCleanup(photo: PreparedPhoto) { commits++ }
    override suspend fun deleteUnlessReferenced(path: String?) { deletes++ }
}

private class OperationRepo : PointRepositoryGateway {
    val rows = mutableListOf<Point>(); val links = mutableListOf<Pair<Long, Long>>()
    var failInsert = false; var afterTransaction: suspend () -> Unit = {}
    override suspend fun <T> inTransaction(block: suspend () -> T): T = block().also { afterTransaction() }
    override fun observeAll() = flowOf(rows.toList())
    override suspend fun insert(point: Point): Long {
        check(!failInsert) { "DB unavailable" }; val id = rows.size.toLong() + 1; rows += point.copy(id = id); return id
    }
    override suspend fun update(point: Point) { rows[rows.indexOfFirst { it.id == point.id }] = point }
    override suspend fun delete(point: Point) { rows.removeAll { it.id == point.id } }
    override suspend fun updateNoiseDb(pointId: Long, noiseDb: Float?) = Unit
    override suspend fun getAll() = rows.toList()
    override suspend fun isPhotoReferenced(photoPath: String) = rows.any { it.photoPath == photoPath }
    override suspend fun findByImportKey(timestamp: Long, latitude: Double, longitude: Double): Point? = null
    override suspend fun getPageAfterId(afterId: Long, limit: Int) = emptyList<Point>()
    override fun observeTags() = flowOf(emptyList<Tag>())
    override suspend fun getAllTags() = emptyList<Tag>()
    override suspend fun insertTag(tag: Tag) = 0L
    override suspend fun updateTag(tag: Tag) = Unit
    override suspend fun deleteTag(tagId: Long) = Unit
    override suspend fun insertPointTag(pointId: Long, tagId: Long) { links += pointId to tagId }
    override suspend fun deletePointTag(pointId: Long, tagId: Long) { links.remove(pointId to tagId) }
    override suspend fun getTagIdsForPoint(pointId: Long) = links.filter { it.first == pointId }.map { it.second }
    override suspend fun getAllPointTagRelations() = links.map { PointTagRelation(it.first, it.second) }
    override fun observePointsForTag(tagId: Long) = flowOf(emptyList<Point>())
}
