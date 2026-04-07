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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
            collectNoiseDb = { -20f },
            asyncScope = this
        )

        useCase.addPointWithTags(
            title = "title",
            note = "note",
            location = GeoPoint(12.3, 45.6),
            timestamp = 1234L,
            tagIds = setOf(2L, 3L),
            photoPath = "/tmp/p.jpg"
        )

        val inserted = fakeRepository.inserted.single()
        assertEquals(12.3, inserted.latitude, 0.0)
        assertEquals(45.6, inserted.longitude, 0.0)
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
            deletePhoto = {},
            asyncScope = this
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
}

private class FakePointRepository : PointRepositoryGateway {
    val inserted = mutableListOf<Point>()
    val insertedTags = mutableListOf<Pair<Long, Long>>()
    val updatedNoiseDb = mutableListOf<Pair<Long, Float?>>()

    override fun observeAll(): Flow<List<Point>> = flowOf(emptyList())
    override suspend fun insert(point: Point): Long {
        inserted += point
        return 10L
    }
    override suspend fun update(point: Point) = Unit
    override suspend fun updateNoiseDb(pointId: Long, noiseDb: Float?) {
        updatedNoiseDb += pointId to noiseDb
    }
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
