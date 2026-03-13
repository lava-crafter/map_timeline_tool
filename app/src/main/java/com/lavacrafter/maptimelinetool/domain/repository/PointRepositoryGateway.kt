package com.lavacrafter.maptimelinetool.domain.repository

import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.data.TagEntity
import kotlinx.coroutines.flow.Flow

interface PointRepositoryGateway {
    fun observeAll(): Flow<List<PointEntity>>
    suspend fun insert(point: PointEntity): Long
    suspend fun update(point: PointEntity)
    suspend fun delete(point: PointEntity)
    suspend fun getAll(): List<PointEntity>

    fun observeTags(): Flow<List<TagEntity>>
    suspend fun insertTag(tag: TagEntity): Long
    suspend fun updateTag(tag: TagEntity)
    suspend fun deleteTag(tagId: Long)
    suspend fun insertPointTag(pointId: Long, tagId: Long)
    suspend fun deletePointTag(pointId: Long, tagId: Long)
    suspend fun getTagIdsForPoint(pointId: Long): List<Long>
    fun observePointsForTag(tagId: Long): Flow<List<PointEntity>>
}
