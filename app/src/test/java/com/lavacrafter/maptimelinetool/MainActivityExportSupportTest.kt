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

package com.lavacrafter.maptimelinetool

import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.Tag
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.repository.PointTagRelation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityExportSupportTest {
    @Test
    fun tagNameMappingReadsAllRelationsOnceForOverOneThousandPoints() = runBlocking {
        val points = (1L..1_201L).map(::point)
        val repository = ExportRepository(
            tags = listOf(Tag(10, "First"), Tag(20, "Second"), Tag(30, " ")),
            relations = points.flatMap { listOf(PointTagRelation(it.id, 10), PointTagRelation(it.id, 20)) } +
                listOf(PointTagRelation(1, 30), PointTagRelation(1, 99), PointTagRelation(1_202, 10))
        )

        val names = buildPointTagNameMap(repository, points)

        assertEquals(1_201, names.size)
        assertTrue(names.values.all { it == listOf("First", "Second") })
        repository.assertSingleSnapshot()
    }

    @Test
    fun selectedZipUsesOneRelationQueryAndOnlyIncludesRelatedTags() = runBlocking {
        val points = (1L..1_201L).map(::point)
        val repository = ExportRepository(
            tags = listOf(Tag(10, "Selected"), Tag(20, "Other point"), Tag(30, "Unattached")),
            relations = points.map { PointTagRelation(it.id, 10) } + PointTagRelation(1_202, 20)
        )

        val payload = buildZipExportPayload(points, true, true, true, true, repository)

        assertEquals(listOf(10L), payload.zipTags.map { it.id })
        assertEquals(points.map { it.id }.toSet(), payload.pointTagIdsByPointId.keys)
        assertTrue(payload.pointTagIdsByPointId.values.all { it == listOf(10L) })
        repository.assertSingleSnapshot()
    }

    @Test
    fun zipWithoutTagsDoesNotReadTagsOrRelations() = runBlocking {
        val repository = ExportRepository(emptyList(), emptyList())
        val payload = buildZipExportPayload(listOf(point(1)), true, false, true, true, repository)
        assertTrue(payload.zipTags.isEmpty())
        assertTrue(payload.pointTagIdsByPointId.isEmpty())
        assertEquals(0, repository.transactions)
        assertEquals(0, repository.tagReads)
        assertEquals(0, repository.relationReads)
    }

    private fun point(id: Long) = Point(
        id = id, timestamp = 1_710_000_000_000L + id, latitude = 10.0, longitude = 20.0,
        title = "Point $id", note = ""
    )

    private class ExportRepository(
        private val tags: List<Tag>,
        private val relations: List<PointTagRelation>
    ) : PointRepositoryGateway {
        var transactions = 0
        var tagReads = 0
        var relationReads = 0
        private var inSnapshot = false

        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            transactions++
            inSnapshot = true
            return try { block() } finally { inSnapshot = false }
        }
        override suspend fun getAllTags(): List<Tag> {
            check(inSnapshot)
            tagReads++
            return tags
        }
        override suspend fun getAllPointTagRelations(): List<PointTagRelation> {
            check(inSnapshot)
            relationReads++
            return relations
        }
        override suspend fun getTagIdsForPoint(pointId: Long): List<Long> = error("N+1 query is forbidden")
        fun assertSingleSnapshot() {
            assertEquals(1, transactions)
            assertEquals(1, tagReads)
            assertEquals(1, relationReads)
        }

        override fun observeAll(): Flow<List<Point>> = error("Not used")
        override suspend fun insert(point: Point): Long = error("Not used")
        override suspend fun update(point: Point): Unit = error("Not used")
        override suspend fun updateNoiseDb(pointId: Long, noiseDb: Float?): Unit = error("Not used")
        override suspend fun delete(point: Point): Unit = error("Not used")
        override suspend fun getAll(): List<Point> = error("Not used")
        override suspend fun findByImportKey(timestamp: Long, latitude: Double, longitude: Double): Point? = error("Not used")
        override suspend fun getPageAfterId(afterId: Long, limit: Int): List<Point> = error("Not used")
        override fun observeTags(): Flow<List<Tag>> = error("Not used")
        override suspend fun insertTag(tag: Tag): Long = error("Not used")
        override suspend fun updateTag(tag: Tag): Unit = error("Not used")
        override suspend fun deleteTag(tagId: Long): Unit = error("Not used")
        override suspend fun insertPointTag(pointId: Long, tagId: Long): Unit = error("Not used")
        override suspend fun deletePointTag(pointId: Long, tagId: Long): Unit = error("Not used")
        override fun observePointsForTag(tagId: Long): Flow<List<Point>> = error("Not used")
    }
}
