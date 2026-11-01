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

package com.lavacrafter.maptimelinetool

import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedBackupFilesTest {
    @Test
    fun `next share removes old generated backups but retains current and unrelated files`() {
        val directory = createTempDirectory("shared-backups").toFile()
        try {
            val legacy = File(directory, "map_timeline_backup_20260101_123000.zip").apply { writeText("old") }
            val unrelated = File(directory, "someone_elses_backup.zip").apply { writeText("keep") }
            val first = createSharedBackupFile(directory) { it.write(byteArrayOf(1)) }
            assertTrue(first.exists())
            val second = createSharedBackupFile(directory) { it.write(byteArrayOf(2)) }

            assertFalse(legacy.exists())
            assertFalse(first.exists())
            assertTrue(second.exists())
            assertEquals(listOf(2), second.readBytes().map { it.toInt() })
            assertTrue(unrelated.exists())
            assertEquals(1, directory.listFiles()!!.count { it.extension == "zip" && it.name.startsWith("map_timeline_backup_") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `failed share removes incomplete output and preserves last finished backup`() {
        val directory = createTempDirectory("shared-backups-failed").toFile()
        try {
            val previous = createSharedBackupFile(directory) { it.write(byteArrayOf(5)) }
            val failure = runCatching {
                createSharedBackupFile(directory) {
                    it.write(byteArrayOf(9))
                    throw IOException("Disk full")
                }
            }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertEquals(listOf(previous), directory.listFiles()!!.toList())
            assertEquals(listOf(5), previous.readBytes().map { it.toInt() })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `cancelled share propagates cancellation and preserves the last finished backup`() {
        val directory = createTempDirectory("shared-backups-cancelled").toFile()
        try {
            val previous = createSharedBackupFile(directory) { it.write(byteArrayOf(5)) }
            assertThrows(CancellationException::class.java) {
                createSharedBackupFile(directory) {
                    it.write(byteArrayOf(9))
                    throw CancellationException("Cancelled export")
                }
            }
            assertEquals(listOf(previous), directory.listFiles()!!.toList())
            assertEquals(listOf(5), previous.readBytes().map { it.toInt() })
        } finally {
            directory.deleteRecursively()
        }
    }
}
