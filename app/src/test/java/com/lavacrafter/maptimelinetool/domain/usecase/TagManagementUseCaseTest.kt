package com.lavacrafter.maptimelinetool.domain.usecase

import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.Tag
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagManagementUseCaseTest {
    @Test
    fun `addTag creates tag with same name`() = runBlocking {
        val fake = FakePointRepositoryGateway()
        val useCase = TagManagementUseCase(fake)

        val insertedId = useCase.addTag("work")

        assertEquals(7L, insertedId)
        assertEquals("work", fake.lastInsertedTag?.name)
    }

    @Test
    fun `renameTag updates tag name`() = runBlocking {
        val fake = FakePointRepositoryGateway()
        val useCase = TagManagementUseCase(fake)

        useCase.renameTag(Tag(id = 3, name = "old"), "new")

        assertEquals(Tag(id = 3, name = "new"), fake.lastUpdatedTag)
    }

    @Test
    fun `setTagForPoint delegates insert or delete by flag`() = runBlocking {
        val fake = FakePointRepositoryGateway()
        val useCase = TagManagementUseCase(fake)

        useCase.setTagForPoint(pointId = 10, tagId = 20, enabled = true)
        useCase.setTagForPoint(pointId = 10, tagId = 20, enabled = false)

        assertTrue(fake.insertedPointTags.contains(10L to 20L))
        assertTrue(fake.deletedPointTags.contains(10L to 20L))
    }
}

private class FakePointRepositoryGateway : PointRepositoryGateway {
    var lastInsertedTag: Tag? = null
    var lastUpdatedTag: Tag? = null
    val insertedPointTags = mutableListOf<Pair<Long, Long>>()
    val deletedPointTags = mutableListOf<Pair<Long, Long>>()

    override fun observeAll(): Flow<List<Point>> = flowOf(emptyList())
    override suspend fun insert(point: Point): Long = 1L
    override suspend fun update(point: Point) = Unit
    override suspend fun updateNoiseDb(pointId: Long, noiseDb: Float?) = Unit
    override suspend fun delete(point: Point) = Unit
    override suspend fun getAll(): List<Point> = emptyList()

    override fun observeTags(): Flow<List<Tag>> = flowOf(emptyList())
    override suspend fun insertTag(tag: Tag): Long {
        lastInsertedTag = tag
        return 7L
    }

    override suspend fun updateTag(tag: Tag) {
        lastUpdatedTag = tag
    }

    override suspend fun deleteTag(tagId: Long) = Unit
    override suspend fun insertPointTag(pointId: Long, tagId: Long) {
        insertedPointTags += pointId to tagId
    }

    override suspend fun deletePointTag(pointId: Long, tagId: Long) {
        deletedPointTags += pointId to tagId
    }

    override suspend fun getTagIdsForPoint(pointId: Long): List<Long> = emptyList()
    override fun observePointsForTag(tagId: Long): Flow<List<Point>> = flowOf(emptyList())
}
