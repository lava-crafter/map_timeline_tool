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

package com.lavacrafter.maptimelinetool.quickadd

import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.PointSensorSnapshot
import com.lavacrafter.maptimelinetool.domain.model.Tag
import com.lavacrafter.maptimelinetool.domain.port.LocationProvider
import com.lavacrafter.maptimelinetool.domain.port.SensorSnapshotPort
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.usecase.PointWriteUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAddCoordinatorTest {
    @Test
    fun `success saves fresh precise point with only existing default tags`() = runBlocking {
        val repository = FakeRepository()
        val state = FakeExecutionState()
        val coordinator = coordinator(
            repository = repository,
            state = state,
            location = GeoPoint(1.0, 2.0, 12f, 99_000L, "fused"),
            defaultTags = setOf(2L, 999L),
            existingTags = setOf(2L),
            nowMs = 100_000L
        )

        val result = coordinator.execute()

        assertTrue(result is QuickAddOutcome.Success)
        assertEquals(setOf(2L), repository.insertedTags.map { it.second }.toSet())
        assertEquals(12f, repository.inserted.single().locationAccuracyMeters)
        assertEquals(QuickAddStoredResult.SUCCESS, state.recorded)
    }

    @Test
    fun `empty default tags are allowed`() = runBlocking {
        val repository = FakeRepository()
        val result = coordinator(
            repository = repository,
            location = GeoPoint(1.0, 2.0, 12f, 99_000L, "gps")
        ).execute()

        assertTrue(result is QuickAddOutcome.Success)
        assertTrue(repository.insertedTags.isEmpty())
    }

    @Test
    fun `inaccurate location does not write`() = runBlocking {
        val repository = FakeRepository()
        val result = coordinator(
            repository = repository,
            location = GeoPoint(1.0, 2.0, 75f, 99_000L, "gps")
        ).execute()

        assertEquals(QuickAddOutcome.LocationTooInaccurate, result)
        assertTrue(repository.inserted.isEmpty())
    }

    @Test
    fun `duplicate execution is rejected`() = runBlocking {
        val repository = FakeRepository()
        val result = coordinator(
            repository = repository,
            state = FakeExecutionState(canStart = false),
            location = GeoPoint(1.0, 2.0, 10f, 99_000L, "gps")
        ).execute()

        assertEquals(QuickAddOutcome.AlreadyRunning, result)
        assertTrue(repository.inserted.isEmpty())
    }

    private fun coordinator(
        repository: FakeRepository,
        state: FakeExecutionState = FakeExecutionState(),
        location: GeoPoint?,
        defaultTags: Set<Long> = emptySet(),
        existingTags: Set<Long> = emptySet(),
        nowMs: Long = 100_000L
    ): QuickAddCoordinator {
        val provider = object : LocationProvider {
            override fun getLastKnownLocation(): GeoPoint? = null
            override suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint? = location
            override suspend fun getFreshLocation(timeoutMs: Long): GeoPoint? = null
            override suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint? = null
        }
        return QuickAddCoordinator(
            locationProvider = provider,
            pointWriteUseCase = PointWriteUseCase(
                repository = repository,
                sensorSnapshotPort = object : SensorSnapshotPort {
                    override suspend fun readSnapshot(): PointSensorSnapshot = PointSensorSnapshot()
                },
                deletePhoto = {}
            ),
            stateStore = state,
            getDefaultTagIds = { defaultTags },
            getExistingTagIds = { existingTags },
            locationAvailability = { QuickAddLocationAvailability.AVAILABLE },
            wallClockMs = { nowMs }
        )
    }
}

private class FakeExecutionState(
    private val canStart: Boolean = true
) : QuickAddExecutionState {
    var recorded: QuickAddStoredResult? = null

    override fun tryStart(): Boolean = canStart

    override fun record(outcome: QuickAddOutcome) {
        recorded = outcome.toStoredResult()
    }
}

private class FakeRepository : PointRepositoryGateway {
    val inserted = mutableListOf<Point>()
    val insertedTags = mutableListOf<Pair<Long, Long>>()

    override fun observeAll(): Flow<List<Point>> = flowOf(emptyList())
    override suspend fun insert(point: Point): Long {
        inserted += point
        return 42L
    }
    override suspend fun update(point: Point) = Unit
    override suspend fun updateNoiseDb(pointId: Long, noiseDb: Float?) = Unit
    override suspend fun delete(point: Point) = Unit
    override suspend fun getAll(): List<Point> = emptyList()
    override fun observeTags(): Flow<List<Tag>> = flowOf(emptyList())
    override suspend fun insertTag(tag: Tag): Long = 0L
    override suspend fun updateTag(tag: Tag) = Unit
    override suspend fun deleteTag(tagId: Long) = Unit
    override suspend fun insertPointTag(pointId: Long, tagId: Long) {
        insertedTags += pointId to tagId
    }
    override suspend fun deletePointTag(pointId: Long, tagId: Long) = Unit
    override suspend fun getTagIdsForPoint(pointId: Long): List<Long> = emptyList()
    override fun observePointsForTag(tagId: Long): Flow<List<Point>> = flowOf(emptyList())
}
