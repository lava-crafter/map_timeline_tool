package com.lavacrafter.maptimelinetool.ui

import android.net.Uri
import com.lavacrafter.maptimelinetool.export.CsvImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FileTaskKind { EXPORT, CSV_IMPORT, SHARE_BACKUP }

sealed interface FileTaskResult {
    data class Exported(val count: Int) : FileTaskResult
    data class CsvImported(val imported: Int, val skipped: Int, val warnings: List<CsvImporter.SkippedRow>) : FileTaskResult
    data class BackupReady(val uri: Uri, val count: Int, val missingPhotoCount: Int) : FileTaskResult
    data class Failed(val kind: FileTaskKind) : FileTaskResult
}

sealed interface FileTaskState {
    data object Idle : FileTaskState
    data class Running(val kind: FileTaskKind) : FileTaskState
    data class Done(val result: FileTaskResult) : FileTaskState
}

/**
 * Export, CSV import and backup sharing run in a ViewModel scope so Activity recreation neither cancels them
 * (deleting a just-created document, or hiding a committed import) nor starts a second copy. One task runs at a
 * time; a finished result is kept until the UI acknowledges it.
 */
class FileTasks(
    private val scope: CoroutineScope,
    private val onFailure: (FileTaskKind, Exception) -> Unit = { _, _ -> }
) {
    private val _state = MutableStateFlow<FileTaskState>(FileTaskState.Idle)
    val state: StateFlow<FileTaskState> = _state.asStateFlow()

    /** Main-thread confined like other UI entry points; returns false while another task is pending. */
    fun start(kind: FileTaskKind, block: suspend () -> FileTaskResult): Boolean {
        if (_state.value != FileTaskState.Idle) return false
        _state.value = FileTaskState.Running(kind)
        scope.launch {
            val result = try {
                block()
            } catch (cancelled: CancellationException) {
                _state.value = FileTaskState.Idle
                throw cancelled
            } catch (error: Exception) {
                onFailure(kind, error)
                FileTaskResult.Failed(kind)
            }
            _state.value = FileTaskState.Done(result)
        }
        return true
    }

    fun acknowledge(done: FileTaskState.Done) {
        _state.compareAndSet(done, FileTaskState.Idle)
    }
}
