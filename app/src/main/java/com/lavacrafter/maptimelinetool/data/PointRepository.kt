package com.lavacrafter.maptimelinetool.data

import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import kotlinx.coroutines.flow.Flow

class PointRepository(private val dao: PointDao) : PointRepositoryGateway {
    override fun observeAll(): Flow<List<PointEntity>> = dao.observeAll()
    override suspend fun insert(point: PointEntity): Long = dao.insert(point)
    override suspend fun update(point: PointEntity) = dao.update(point)
    override suspend fun delete(point: PointEntity) = dao.delete(point)
    override suspend fun getAll() = dao.getAll()

    override fun observeTags() = dao.observeTags()
    override suspend fun insertTag(tag: TagEntity) = dao.insertTag(tag)
    override suspend fun updateTag(tag: TagEntity) = dao.updateTag(tag)
    override suspend fun deleteTag(tagId: Long) = dao.deleteTag(tagId)
    override suspend fun insertPointTag(pointId: Long, tagId: Long) = dao.insertPointTag(PointTagCrossRef(pointId, tagId))
    override suspend fun deletePointTag(pointId: Long, tagId: Long) = dao.deletePointTag(pointId, tagId)
    override suspend fun getTagIdsForPoint(pointId: Long) = dao.getTagIdsForPoint(pointId)
    fun observeTagWithPoints(tagId: Long) = dao.observeTagWithPoints(tagId)
    override fun observePointsForTag(tagId: Long) = dao.observePointsForTag(tagId)
}
