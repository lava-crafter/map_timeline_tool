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

import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatedDocumentExportTest {
    @Test
    fun resultIsReturnedOnlyAfterFlushAndClose() = runBlocking {
        val output = RecordingOutput()
        val result = writeCreatedDocument({ output }, { error("Successful export must not be deleted") }) {
            it.write(byteArrayOf(1, 2, 3))
            3
        }
        assertEquals(3, result)
        assertEquals(listOf("write", "flush", "close"), output.events)
        assertTrue(output.toByteArray().contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun exporterCloseOnlyFlushesAndOwnerClosesDestinationOnce() = runBlocking {
        val output = RecordingOutput()
        writeCreatedDocument({ output }, { error("Unexpected cleanup") }) {
            it.use { stream -> stream.write(byteArrayOf(1, 2)) }
            assertEquals(listOf("write", "flush"), output.events)
        }
        assertEquals(listOf("write", "flush", "flush", "close"), output.events)
    }

    @Test
    fun failedWriteClosesThenDeletesDestination() = runBlocking {
        val output = RecordingOutput()
        val failure = IOException("Write failed")
        val thrown = runCatching {
            writeCreatedDocument({ output }, { output.events += "delete" }) {
                it.write(byteArrayOf(1))
                throw failure
            }
        }.exceptionOrNull()
        assertFailurePreserved(failure, thrown)
        assertEquals(listOf("write", "close", "delete"), output.events)
    }

    @Test
    fun failedFlushOrCloseNeverReturnsSuccessAndDeletesDestination() = runBlocking {
        for (step in listOf("flush", "close")) {
            val output = RecordingOutput(failAt = step)
            var deleted = false
            val failure = runCatching {
                writeCreatedDocument({ output }, { deleted = true }) { it.write(byteArrayOf(1)); "success" }
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(deleted)
            assertEquals("close", output.events.last())
        }
    }

    @Test
    fun nullOrFailedOpenAlsoDeletesNewDocument() = runBlocking {
        for (failOnOpen in listOf(false, true)) {
            var deleted = false
            val failure = runCatching {
                writeCreatedDocument(
                    openOutput = { if (failOnOpen) throw IOException("Open failed") else null },
                    deleteOutput = { deleted = true }
                ) { error("Must not call writer") }
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(deleted)
        }
    }

    @Test
    fun missingRequestMetadataCleansUpDocument() = runBlocking {
        val output = RecordingOutput()
        var deleted = false
        val failure = runCatching {
            writeCreatedDocument({ output }, { deleted = true }) { error("Missing export request") }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(deleted)
        assertEquals(listOf("close"), output.events)
    }

    @Test
    fun cleanupFailureDoesNotReplaceOriginalFailure() = runBlocking {
        val failure = IOException("Write failed")
        val cleanupFailure = IOException("Provider refused deletion")
        val thrown = runCatching {
            writeCreatedDocument({ RecordingOutput() }, { throw cleanupFailure }) { throw failure }
        }.exceptionOrNull()
        assertFailurePreserved(failure, thrown)
        assertTrue(generateSequence(thrown) { it.cause }.flatMap { it.suppressed.asSequence() }
            .any { it === cleanupFailure })
    }

    @Test
    fun cancellationIsPropagatedAndStillClosesAndDeletes() = runBlocking {
        val output = RecordingOutput()
        var cancelled = false
        val job = launch {
            try {
                writeCreatedDocument({ output }, { output.events += "delete" }) {
                    it.write(byteArrayOf(1))
                    currentCoroutineContext().cancel()
                    currentCoroutineContext().ensureActive()
                }
                error("Cancelled export must not return success")
            } catch (failure: CancellationException) {
                cancelled = true
                throw failure
            }
        }
        job.join()
        assertTrue(cancelled)
        assertTrue(job.isCancelled)
        assertEquals(listOf("write", "close", "delete"), output.events)
    }

    private fun assertFailurePreserved(expected: Exception, actual: Throwable?) {
        // Coroutine stack-trace recovery may copy an exception, retaining the original as a cause.
        assertEquals(expected.javaClass, actual?.javaClass)
        assertEquals(expected.message, actual?.message)
        assertTrue(generateSequence(actual) { it.cause }.any { it === expected })
    }

    private class RecordingOutput(private val failAt: String? = null) : ByteArrayOutputStream() {
        val events = mutableListOf<String>()
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            events += "write"
            super.write(bytes, offset, length)
        }
        override fun flush() {
            events += "flush"
            if (failAt == "flush") throw IOException("Flush failed")
        }
        override fun close() {
            events += "close"
            if (failAt == "close") throw IOException("Close failed")
        }
    }
}
