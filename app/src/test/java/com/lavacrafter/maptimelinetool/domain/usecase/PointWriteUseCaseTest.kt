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

import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.PointSensorSnapshot
import com.lavacrafter.maptimelinetool.domain.model.Tag
import com.lavacrafter.maptimelinetool.domain.port.SensorSnapshotPort
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.text.formatPointTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointWriteUseCaseTest {
    @Test
    fun `addPointWithTags uses domain location and snapshot port`() = runBlocking {
        val fakeRepository = FakePointRepository()
        val useCase = PointWriteUseCase(
            repository = fakeRepository,
            sensorSnapshotPort = object : SensorSnapshotPort {
                override suspend fun readSnapshot(): PointSensorSnapshot = PointSensorSnapshot(
                    pressureHpa = 1000f,
                    accelerometerX = 1f
                )
            },
            deletePhoto = {},
            shouldCollectNoise = { true },
            collectNoiseDb = { -20f }
        )

        val id = useCase.addPointWithTags(
            title = "title",
            note = "note",
            location = GeoPoint(12.3, 45.6, accuracyMeters = 7.5f, fixTimeMs = 1200L, provider = "gps"),
            timestamp = 1234L,
            tagIds = setOf(2L, 3L),
            photoPath = "/tmp/p.jpg"
        )

        val inserted = fakeRepository.inserted.single()
        assertEquals(10L, id)
        assertEquals(12.3, inserted.latitude, 0.0)
        assertEquals(45.6, inserted.longitude, 0.0)
        assertEquals(7.5f, inserted.locationAccuracyMeters)
        assertEquals(1200L, inserted.locationFixTimeMs)
        assertEquals("gps", inserted.locationProvider)
        assertEquals(1000f, inserted.pressureHpa)
        assertEquals(1f, inserted.accelerometerX)
        assertEquals(setOf(2L, 3L), fakeRepository.insertedTags.map { it.second }.toSet())
        assertEquals(listOf(10L to -20f), fakeRepository.updatedNoiseDb)
    }

    @Test
    fun `addPointWithTags sanitizes title and note before storing`() = runBlocking {
        val fakeRepository = FakePointRepository()
        val useCase = PointWriteUseCase(
            repository = fakeRepository,
            sensorSnapshotPort = object : SensorSnapshotPort {
                override suspend fun readSnapshot(): PointSensorSnapshot = PointSensorSnapshot()
            },
            deletePhoto = {}
        )

        val timestamp = 1710000000000L
        useCase.addPointWithTags(
            title = "\u0000\t ",
            note = "Line1\r\nLine2\tLine3\u0007",
            location = GeoPoint(12.3, 45.6),
            timestamp = timestamp,
            tagIds = emptySet()
        )

        val inserted = fakeRepository.inserted.single()
        assertEquals(formatPointTimestamp(timestamp), inserted.title)
        assertEquals("Line1\nLine2 Line3", inserted.note)
    }

    @Test
    fun `insert failure cannot return a successful id`() = runBlocking {
        val repo = FakePointRepository().apply { insertFailure = IllegalStateException("insert failed") }
        var coreSaved = false
        val failure = runCatching {
            writer(repo).addPointWithTags("New", "", GeoPoint(1.0, 2.0), 1L, emptySet(), onCoreSaved = { coreSaved = true })
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(coreSaved)
        assertTrue(repo.inserted.isEmpty())
    }

    @Test
    fun `tag failure rolls back point and previously attached tags`() = runBlocking {
        val repo = FakePointRepository().apply { tagFailureId = 3L }
        var coreSaved = false
        val failure = runCatching {
            writer(repo).addPointWithTags("New", "", GeoPoint(1.0, 2.0), 1L, linkedSetOf(2L, 3L), onCoreSaved = { coreSaved = true })
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(coreSaved)
        assertTrue(repo.inserted.isEmpty())
        assertTrue(repo.insertedTags.isEmpty())
    }

    @Test
    fun `update failure leaves original row and tags unchanged`() = runBlocking {
        val old = point().copy(id = 10, photoPath = "old.jpg")
        val repo = FakePointRepository().apply {
            inserted += old
            insertedTags += 10L to 2L
            tagFailureId = 3L
        }
        var coreSaved = false
        val deletedPhotos = mutableListOf<String?>()
        val failure = runCatching {
            writer(repo, deletePhoto = { deletedPhotos += it }).updatePoint(
                old, "Changed", "New note", "new.jpg", setOf(3L), onCoreSaved = { coreSaved = true }
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(coreSaved)
        assertEquals(listOf(old), repo.inserted)
        assertEquals(listOf(10L to 2L), repo.insertedTags)
        assertTrue(deletedPhotos.isEmpty())
    }

    @Test
    fun `delete failure keeps point and referenced photo`() = runBlocking {
        val old = point().copy(id = 10, photoPath = "old.jpg")
        val repo = FakePointRepository().apply {
            inserted += old
            deleteFailure = IllegalStateException("delete failed")
        }
        val deletedPhotos = mutableListOf<String?>()
        val failure = runCatching { writer(repo, deletePhoto = { deletedPhotos += it }).deletePoint(old) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf(old), repo.inserted)
        assertTrue(deletedPhotos.isEmpty())
    }

    @Test
    fun `photo cleanup failure cannot turn a successful delete into a failed write`() = runBlocking {
        val old = point().copy(id = 10L, photoPath = "old.jpg")
        val repo = FakePointRepository().apply { inserted += old }
        val errors = mutableListOf<Exception>()
        val writer = PointWriteUseCase(
            repo, snapshotPort(), deletePhoto = { throw IllegalStateException("file cleanup failed") },
            onOptionalFailure = errors::add
        )

        writer.deletePoint(old)

        assertTrue(repo.inserted.isEmpty())
        assertEquals(listOf("file cleanup failed"), errors.map { it.message })
    }

    @Test
    fun `noise collection and update failures cannot invalidate a committed point`() = runBlocking {
        val errors = mutableListOf<Exception>()
        val repo = FakePointRepository().apply { noiseFailure = IllegalStateException("update noise failed") }
        val collectionFailure = PointWriteUseCase(
            repo, snapshotPort(), {}, shouldCollectNoise = { true },
            collectNoiseDb = { throw IllegalStateException("capture failed") }, onOptionalFailure = errors::add
        )
        assertEquals(10L, collectionFailure.addPointWithTags("A", "", GeoPoint(1.0, 2.0), 1L, emptySet()))
        val updateFailure = PointWriteUseCase(
            repo, snapshotPort(), {}, shouldCollectNoise = { true },
            collectNoiseDb = { -20f }, onOptionalFailure = errors::add
        )
        assertEquals(10L, updateFailure.addPointWithTags("B", "", GeoPoint(1.0, 2.0), 2L, emptySet()))
        assertEquals(2, repo.inserted.size)
        assertEquals(listOf("capture failed", "update noise failed"), errors.map { it.message })
    }

    @Test
    fun `cancellation propagates and rolls back unfinished transaction`() = runBlocking {
        val repo = FakePointRepository().apply { tagFailureId = 3L; tagFailure = CancellationException("cancelled") }
        val failure = runCatching {
            writer(repo).addPointWithTags("New", "", GeoPoint(1.0, 2.0), 1L, linkedSetOf(2L, 3L))
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(repo.inserted.isEmpty())
        assertTrue(repo.insertedTags.isEmpty())
    }

    @Test
    fun `cancellation of optional noise propagates without removing committed point`() = runBlocking {
        val repo = FakePointRepository()
        val writer = PointWriteUseCase(repo, snapshotPort(), {}, shouldCollectNoise = { true },
            collectNoiseDb = { throw CancellationException("cancelled") })
        var committed = false
        val failure = runCatching {
            writer.addPointWithTags("New", "", GeoPoint(1.0, 2.0), 1L, emptySet(), onCoreSaved = { committed = true })
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(committed)
        assertEquals(1, repo.inserted.size)
    }

    private fun snapshotPort() = object : SensorSnapshotPort {
        override suspend fun readSnapshot(): PointSensorSnapshot = PointSensorSnapshot()
    }

    private fun writer(repo: FakePointRepository, deletePhoto: suspend (String?) -> Unit = {}) =
        PointWriteUseCase(repo, snapshotPort(), deletePhoto)

    private fun point() = Point(timestamp = 1L, latitude = 1.0, longitude = 2.0, title = "Old", note = "")
}

private class FakePointRepository : PointRepositoryGateway {
    val inserted = mutableListOf<Point>()
    val insertedTags = mutableListOf<Pair<Long, Long>>()
    val updatedNoiseDb = mutableListOf<Pair<Long, Float?>>()
    var insertFailure: Exception? = null
    var tagFailureId: Long? = null
    var tagFailure: Exception = IllegalStateException("tag insert failed")
    var updateFailure: Exception? = null
    var deleteFailure: Exception? = null
    var noiseFailure: Exception? = null

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        val previousPoints = inserted.toList()
        val previousTags = insertedTags.toList()
        return try {
            block()
        } catch (error: Throwable) {
            inserted.clear()
            inserted.addAll(previousPoints)
            insertedTags.clear()
            insertedTags.addAll(previousTags)
            throw error
        }
    }

    override fun observeAll(): Flow<List<Point>> = flowOf(emptyList())
    override suspend fun insert(point: Point): Long {
        insertFailure?.let { throw it }
        inserted += point
        return 10L
    }
    override suspend fun update(point: Point) {
        updateFailure?.let { throw it }
        val oldIndex = inserted.indexOfFirst { it.id == point.id }
        if (oldIndex >= 0) inserted[oldIndex] = point
    }
    override suspend fun updateNoiseDb(pointId: Long, noiseDb: Float?) {
        noiseFailure?.let { throw it }
        updatedNoiseDb += pointId to noiseDb
    }
    override suspend fun delete(point: Point) {
        deleteFailure?.let { throw it }
        inserted.removeAll { it.id == point.id }
    }
    override suspend fun getAll(): List<Point> = inserted.toList()
    override suspend fun isPhotoReferenced(photoPath: String): Boolean = inserted.any { it.photoPath == photoPath }
    override suspend fun findByImportKey(timestamp: Long, latitude: Double, longitude: Double): Point? = null
    override suspend fun getPageAfterId(afterId: Long, limit: Int): List<Point> = emptyList()
    override fun observeTags(): Flow<List<Tag>> = flowOf(emptyList())
    override suspend fun getAllTags(): List<Tag> = emptyList()
    override suspend fun insertTag(tag: Tag): Long = 0L
    override suspend fun updateTag(tag: Tag) = Unit
    override suspend fun deleteTag(tagId: Long) = Unit
    override suspend fun insertPointTag(pointId: Long, tagId: Long) {
        if (tagId == tagFailureId) throw tagFailure
        insertedTags += pointId to tagId
    }
    override suspend fun deletePointTag(pointId: Long, tagId: Long) {
        insertedTags.remove(pointId to tagId)
    }
    override suspend fun getTagIdsForPoint(pointId: Long): List<Long> = insertedTags.filter { it.first == pointId }.map { it.second }
    override suspend fun getAllPointTagRelations(): List<com.lavacrafter.maptimelinetool.domain.repository.PointTagRelation> =
        insertedTags.map { com.lavacrafter.maptimelinetool.domain.repository.PointTagRelation(it.first, it.second) }
    override fun observePointsForTag(tagId: Long): Flow<List<Point>> = flowOf(emptyList())
}
