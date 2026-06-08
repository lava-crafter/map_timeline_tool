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
