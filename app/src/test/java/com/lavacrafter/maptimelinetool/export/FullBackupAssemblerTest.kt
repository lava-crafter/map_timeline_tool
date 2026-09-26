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

package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.ExportFileKind
import com.lavacrafter.maptimelinetool.PendingExportPayload
import com.lavacrafter.maptimelinetool.writeOrdinaryZip
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.Tag
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.repository.PointTagRelation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullBackupAssemblerTest {
    @Test
    fun `real full backup caller includes unused tags, sensors, photo and settings`() = runBlocking {
        val directory = createTempDirectory("full-backup").toFile()
        try {
            val photo = File(directory, "captured.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val repository = BackupRepository(
                points = listOf(point(1).copy(pressureHpa = 1_009f, photoPath = photo.path), point(2)),
                tags = listOf(Tag(10, "Attached"), Tag(20, "Unattached")),
                relations = listOf(PointTagRelation(1, 10))
            )
            val payload = FullBackupAssembler(repository).assemble()
            val output = ByteArrayOutputStream()
            payload.writeZip(output, { File(it) }, """{"marker_scale":1.4}""", "test")
            val restored = ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { name, input ->
                assertEquals(listOf(1, 2, 3, 4), input.readBytes().map { it.toInt() })
                "restored/$name"
            }

            assertEquals(2, restored.points.size)
            assertEquals(setOf("Attached", "Unattached"), restored.tags.map { it.name }.toSet())
            assertEquals(listOf(ZipImporter.ImportedPointTag(0, 10)), restored.pointTags)
            assertEquals(1_009f, restored.points.first().pressureHpa)
            assertEquals("restored/photos/captured.jpg", restored.points.first().photoPath)
            assertEquals(1, restored.importedPhotoCount)
            assertEquals(0, restored.missingPhotoCount)
            assertEquals("""{"marker_scale":1.4}""", restored.settingsJson)
            assertTrue(restored.manifest.sections.settings)
            assertEquals(2, restored.manifest.counts.tags)
            assertEquals(1, repository.transactionCount)
            assertEquals(1, repository.relationReadCount)
            assertEquals(0, repository.perPointRelationReadCount)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `ordinary selected point zip does not contain settings`() {
        val output = ByteArrayOutputStream()
        PendingExportPayload(
            points = listOf(point(2)),
            kind = ExportFileKind.ZIP,
            zip = true,
            zipOptions = ZipExporter.ExportOptions(includePhotos = false),
            zipTags = listOf(ZipExporter.TagRecord(10, "Attached")),
            pointTagIdsByPointId = mapOf(2L to listOf(10L))
        ).writeOrdinaryZip(output, { null }, "test")

        val imported = ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { _, _ -> null }
        assertEquals(null, imported.settingsJson)
        assertFalse(imported.manifest.sections.settings)
        assertEquals(listOf(ZipImporter.ImportedPointTag(0, 10)), imported.pointTags)
    }

    @Test
    fun `one snapshot and one relations query handle over a thousand points`() = runBlocking {
        val points = (1L..1_201L).map(::point)
        val relations = points.flatMap { listOf(PointTagRelation(it.id, 10), PointTagRelation(it.id, 20)) }
        val repository = BackupRepository(points, listOf(Tag(10, "First"), Tag(20, "Second")), relations)

        val payload = FullBackupAssembler(repository).assemble()
        val output = ByteArrayOutputStream()
        payload.writeZip(output, { null }, "{}", "test")
        val imported = ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { _, _ -> null }

        assertEquals(1_201, imported.points.size)
        assertEquals(2_402, imported.pointTags.size)
        assertEquals(1, repository.pointReadCount)
        assertEquals(1, repository.tagReadCount)
        assertEquals(1, repository.relationReadCount)
        assertEquals(0, repository.perPointRelationReadCount)
    }

    @Test
    fun `missing photo and orphan relation fail instead of silently losing full backup data`() = runBlocking {
        val missingPhoto = BackupRepository(listOf(point(1).copy(photoPath = "missing.jpg")), emptyList(), emptyList())
        val payload = FullBackupAssembler(missingPhoto).assemble()
        assertTrue(runCatching {
            payload.writeZip(ByteArrayOutputStream(), { null }, "{}", "test")
        }.exceptionOrNull() is IOException)

        val orphan = BackupRepository(listOf(point(1)), emptyList(), listOf(PointTagRelation(1, 99)))
        assertTrue(runCatching { FullBackupAssembler(orphan).assemble() }.exceptionOrNull() is IllegalStateException)
    }

    private fun point(id: Long) = Point(
        id = id,
        timestamp = 1_710_000_000_000L + id,
        latitude = 10.0,
        longitude = 20.0,
        title = "Point $id",
        note = "Note $id"
    )
}

private class BackupRepository(
    private val points: List<Point>,
    private val tags: List<Tag>,
    private val relations: List<PointTagRelation>
) : PointRepositoryGateway {
    var transactionCount = 0
    var pointReadCount = 0
    var tagReadCount = 0
    var relationReadCount = 0
    var perPointRelationReadCount = 0

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        transactionCount++
        return block()
    }

    override suspend fun getAll(): List<Point> { pointReadCount++; return points }
    override suspend fun getAllTags(): List<Tag> { tagReadCount++; return tags }
    override suspend fun getAllPointTagRelations(): List<PointTagRelation> { relationReadCount++; return relations }
    override suspend fun getTagIdsForPoint(pointId: Long): List<Long> {
        perPointRelationReadCount++
        error("Full backup must not perform an N+1 query")
    }

    override fun observeAll(): Flow<List<Point>> = flowOf(points)
    override suspend fun insert(point: Point): Long = error("Not used")
    override suspend fun update(point: Point): Unit = error("Not used")
    override suspend fun updateNoiseDb(pointId: Long, noiseDb: Float?): Unit = error("Not used")
    override suspend fun delete(point: Point): Unit = error("Not used")
    override suspend fun findByImportKey(timestamp: Long, latitude: Double, longitude: Double): Point? = error("Not used")
    override suspend fun getPageAfterId(afterId: Long, limit: Int): List<Point> = error("Not used")
    override fun observeTags(): Flow<List<Tag>> = flowOf(tags)
    override suspend fun insertTag(tag: Tag): Long = error("Not used")
    override suspend fun updateTag(tag: Tag): Unit = error("Not used")
    override suspend fun deleteTag(tagId: Long): Unit = error("Not used")
    override suspend fun insertPointTag(pointId: Long, tagId: Long): Unit = error("Not used")
    override suspend fun deletePointTag(pointId: Long, tagId: Long): Unit = error("Not used")
    override fun observePointsForTag(tagId: Long): Flow<List<Point>> = error("Not used")
}
