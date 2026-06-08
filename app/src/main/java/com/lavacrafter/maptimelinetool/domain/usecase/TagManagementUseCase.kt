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

import com.lavacrafter.maptimelinetool.domain.model.Tag
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.text.sanitizeTagName
import kotlinx.coroutines.flow.Flow

class TagManagementUseCase(
    private val repository: PointRepositoryGateway
) {
    fun observeTags(): Flow<List<Tag>> = repository.observeTags()

    suspend fun addTag(name: String): Long {
        val normalizedName = sanitizeTagName(name)
        if (normalizedName.isBlank()) return 0L
        return repository.insertTag(Tag(name = normalizedName))
    }

    suspend fun renameTag(tag: Tag, name: String) {
        val normalizedName = sanitizeTagName(name)
        if (normalizedName.isBlank()) return
        repository.updateTag(tag.copy(name = normalizedName))
    }

    suspend fun deleteTag(tagId: Long) = repository.deleteTag(tagId)

    suspend fun setTagForPoint(pointId: Long, tagId: Long, enabled: Boolean) {
        if (enabled) {
            repository.insertPointTag(pointId, tagId)
        } else {
            repository.deletePointTag(pointId, tagId)
        }
    }

    suspend fun getTagIdsForPoint(pointId: Long): List<Long> = repository.getTagIdsForPoint(pointId)

    fun observePointsForTag(tagId: Long) = repository.observePointsForTag(tagId)
}
