package com.lavacrafter.maptimelinetool.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTasksTest {
    @Test
    fun `one task at a time and the result waits for acknowledgement`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val tasks = FileTasks(scope)
        val release = CompletableDeferred<Unit>()

        assertTrue(tasks.start(FileTaskKind.EXPORT) { release.await(); FileTaskResult.Exported(3) })
        assertFalse(tasks.start(FileTaskKind.SHARE_BACKUP) { FileTaskResult.Exported(0) })
        assertEquals(FileTaskState.Running(FileTaskKind.EXPORT), tasks.state.value)

        release.complete(Unit)
        yield()
        val done = tasks.state.value as FileTaskState.Done
        assertEquals(FileTaskResult.Exported(3), done.result)
        assertFalse(tasks.start(FileTaskKind.EXPORT) { FileTaskResult.Exported(0) })

        tasks.acknowledge(done)
        assertEquals(FileTaskState.Idle, tasks.state.value)
        scope.cancel()
    }

    @Test
    fun `failures become a typed result and cancellation returns to idle`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val failures = mutableListOf<FileTaskKind>()
        val tasks = FileTasks(scope) { kind, _ -> failures += kind }

        tasks.start(FileTaskKind.CSV_IMPORT) { error("bad file") }
        assertEquals(FileTaskState.Done(FileTaskResult.Failed(FileTaskKind.CSV_IMPORT)), tasks.state.value)
        assertEquals(listOf(FileTaskKind.CSV_IMPORT), failures)
        tasks.acknowledge(tasks.state.value as FileTaskState.Done)

        tasks.start(FileTaskKind.EXPORT) { CompletableDeferred<Unit>().await(); FileTaskResult.Exported(1) }
        scope.cancel()
        yield()
        assertEquals(FileTaskState.Idle, tasks.state.value)
    }
}
