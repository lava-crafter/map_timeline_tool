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

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavacrafter.maptimelinetool.domain.model.Point
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Focused consistency checks for repository deletion transactions. */
@RunWith(AndroidJUnit4::class)
class PointRepositoryIntegrityTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: PointRepository

    @Before
    fun createDatabase() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        check(app.packageName.endsWith(".debug")) { "Integrity tests must use the debug sandbox" }
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        repository = PointRepository(database, database.pointDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun deletePointRemovesOnlyItsRelationsAndPreservesUnrelatedData() = runBlocking {
        val targetPoint = insertPoint("Delete")
        val keptPoint = insertPoint("Keep")
        val targetTag = database.pointDao().insertTag(TagEntity(name = "Target"))
        val sharedTag = database.pointDao().insertTag(TagEntity(name = "Shared"))
        val keptTag = database.pointDao().insertTag(TagEntity(name = "Kept"))
        database.pointDao().insertPointTag(PointTagCrossRef(targetPoint, targetTag))
        database.pointDao().insertPointTag(PointTagCrossRef(targetPoint, sharedTag))
        database.pointDao().insertPointTag(PointTagCrossRef(keptPoint, sharedTag))
        database.pointDao().insertPointTag(PointTagCrossRef(keptPoint, keptTag))

        repository.delete(point(targetPoint, "Delete"))

        assertEquals(setOf(keptPoint), database.pointDao().getAll().map { it.id }.toSet())
        assertEquals(setOf(targetTag, sharedTag, keptTag), database.pointDao().getAllTags().map { it.id }.toSet())
        assertEquals(
            setOf(PointTagCrossRef(keptPoint, sharedTag), PointTagCrossRef(keptPoint, keptTag)),
            database.pointDao().getAllPointTagRelations().toSet()
        )
    }

    @Test
    fun deleteTagRemovesOnlyItsRelationsAndPreservesUnrelatedData() = runBlocking {
        val targetPoint = insertPoint("Target point")
        val keptPoint = insertPoint("Kept point")
        val deletedTag = database.pointDao().insertTag(TagEntity(name = "Delete"))
        val keptTag = database.pointDao().insertTag(TagEntity(name = "Keep"))
        val otherTag = database.pointDao().insertTag(TagEntity(name = "Other"))
        database.pointDao().insertPointTag(PointTagCrossRef(targetPoint, deletedTag))
        database.pointDao().insertPointTag(PointTagCrossRef(keptPoint, deletedTag))
        database.pointDao().insertPointTag(PointTagCrossRef(targetPoint, otherTag))
        database.pointDao().insertPointTag(PointTagCrossRef(keptPoint, keptTag))

        repository.deleteTag(deletedTag)

        assertEquals(setOf(targetPoint, keptPoint), database.pointDao().getAll().map { it.id }.toSet())
        assertEquals(setOf(keptTag, otherTag), database.pointDao().getAllTags().map { it.id }.toSet())
        assertEquals(
            setOf(PointTagCrossRef(targetPoint, otherTag), PointTagCrossRef(keptPoint, keptTag)),
            database.pointDao().getAllPointTagRelations().toSet()
        )
    }

    @Test
    fun failedPointDeletionRollsBackRelationCleanup() = runBlocking {
        val missingPointId = 404L
        val tagId = database.pointDao().insertTag(TagEntity(name = "Orphan candidate"))
        database.pointDao().insertPointTag(PointTagCrossRef(missingPointId, tagId))

        val failure = runCatching { repository.delete(point(missingPointId, "Missing")) }.exceptionOrNull()
        assertEquals(IllegalStateException::class.java, failure?.javaClass)

        assertEquals(
            listOf(PointTagCrossRef(missingPointId, tagId)),
            database.pointDao().getAllPointTagRelations()
        )
    }

    private suspend fun insertPoint(title: String): Long =
        database.pointDao().insert(
            PointEntity(timestamp = title.hashCode().toLong(), latitude = 10.0, longitude = 20.0, title = title, note = "")
        )

    private fun point(id: Long, title: String) = Point(
        id = id,
        timestamp = title.hashCode().toLong(),
        latitude = 10.0,
        longitude = 20.0,
        title = title,
        note = ""
    )
}
