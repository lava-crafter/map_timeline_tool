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

package com.lavacrafter.maptimelinetool.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PointDao {
    @Insert
    suspend fun insert(point: PointEntity): Long

    @Update
    suspend fun update(point: PointEntity)

    @Delete
    suspend fun delete(point: PointEntity)

    @Query("SELECT * FROM points ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PointEntity>>

    @Query("SELECT * FROM points ORDER BY timestamp ASC")
    suspend fun getAll(): List<PointEntity>

    @Query("UPDATE points SET noiseDb = :noiseDb WHERE id = :pointId")
    suspend fun updateNoiseDb(pointId: Long, noiseDb: Float?)

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Insert
    suspend fun insertTag(tag: TagEntity): Long

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPointTag(crossRef: PointTagCrossRef)

    @Query("DELETE FROM point_tags WHERE pointId = :pointId AND tagId = :tagId")
    suspend fun deletePointTag(pointId: Long, tagId: Long)

    @Query("SELECT tagId FROM point_tags WHERE pointId = :pointId")
    suspend fun getTagIdsForPoint(pointId: Long): List<Long>

    @Transaction
    @Query("SELECT * FROM tags WHERE id = :tagId")
    fun observeTagWithPoints(tagId: Long): Flow<TagWithPoints?>

    @Query("SELECT p.* FROM points p INNER JOIN point_tags pt ON p.id = pt.pointId WHERE pt.tagId = :tagId ORDER BY p.timestamp DESC")
    fun observePointsForTag(tagId: Long): Flow<List<PointEntity>>
}
