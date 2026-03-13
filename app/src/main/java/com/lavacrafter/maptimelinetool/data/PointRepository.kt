package com.lavacrafter.maptimelinetool.data

import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

class PointRepository(private val dao: PointDao) : PointRepositoryGateway {
    override fun observeAll(): Flow<List<com.lavacrafter.maptimelinetool.domain.model.Point>> =
        dao.observeAll().map { points -> points.map { it.toDomain() } }

    override suspend fun insert(point: com.lavacrafter.maptimelinetool.domain.model.Point): Long = dao.insert(point.toEntity())
    override suspend fun update(point: com.lavacrafter.maptimelinetool.domain.model.Point) = dao.update(point.toEntity())
    override suspend fun updateNoiseDb(pointId: Long, noiseDb: Float?) = dao.updateNoiseDb(pointId, noiseDb)
    override suspend fun delete(point: com.lavacrafter.maptimelinetool.domain.model.Point) = dao.delete(point.toEntity())
    override suspend fun getAll() = dao.getAll().map { it.toDomain() }

    override fun observeTags() = dao.observeTags().map { tags -> tags.map { it.toDomain() } }
    override suspend fun insertTag(tag: com.lavacrafter.maptimelinetool.domain.model.Tag) = dao.insertTag(tag.toEntity())
    override suspend fun updateTag(tag: com.lavacrafter.maptimelinetool.domain.model.Tag) = dao.updateTag(tag.toEntity())
    override suspend fun deleteTag(tagId: Long) = dao.deleteTag(tagId)
    override suspend fun insertPointTag(pointId: Long, tagId: Long) = dao.insertPointTag(PointTagCrossRef(pointId, tagId))
    override suspend fun deletePointTag(pointId: Long, tagId: Long) = dao.deletePointTag(pointId, tagId)
    override suspend fun getTagIdsForPoint(pointId: Long) = dao.getTagIdsForPoint(pointId)
    fun observeTagWithPoints(tagId: Long) = dao.observeTagWithPoints(tagId)
    override fun observePointsForTag(tagId: Long) = dao.observePointsForTag(tagId).map { points -> points.map { it.toDomain() } }
}
