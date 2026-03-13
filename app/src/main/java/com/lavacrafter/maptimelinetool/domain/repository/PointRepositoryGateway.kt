package com.lavacrafter.maptimelinetool.domain.repository

import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface PointRepositoryGateway {
    fun observeAll(): Flow<List<Point>>
    suspend fun insert(point: Point): Long
    suspend fun update(point: Point)
    suspend fun updateNoiseDb(pointId: Long, noiseDb: Float?)
    suspend fun delete(point: Point)
    suspend fun getAll(): List<Point>

    fun observeTags(): Flow<List<Tag>>
    suspend fun insertTag(tag: Tag): Long
    suspend fun updateTag(tag: Tag)
    suspend fun deleteTag(tagId: Long)
    suspend fun insertPointTag(pointId: Long, tagId: Long)
    suspend fun deletePointTag(pointId: Long, tagId: Long)
    suspend fun getTagIdsForPoint(pointId: Long): List<Long>
    fun observePointsForTag(tagId: Long): Flow<List<Point>>
}
