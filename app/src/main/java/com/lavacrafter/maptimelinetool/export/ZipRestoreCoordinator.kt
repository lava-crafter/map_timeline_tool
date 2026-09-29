package com.lavacrafter.maptimelinetool.export

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.lavacrafter.maptimelinetool.createPointPhotoImportStagingDir
import com.lavacrafter.maptimelinetool.deletePointPhotoImportStagingDir
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.getPointPhotoDir
import com.lavacrafter.maptimelinetool.ui.AppViewModel
import com.lavacrafter.maptimelinetool.ui.SettingsStore
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

sealed interface RestoreState {
    data object Idle : RestoreState
    data object Validating : RestoreState
    data object Restoring : RestoreState
    data object Finalizing : RestoreState
    data class Success(val points: Int) : RestoreState
    data class SuccessWithWarning(val points: Int, val warning: String) : RestoreState
    data class Failure(val reason: String) : RestoreState
}

/** Keeps archive extraction outside Room and makes the short, non-cancellable file/DB commit recoverable. */
class ZipRestoreCoordinator(
    private val context: Context,
    private val repository: PointRepositoryGateway,
    private val beforeCommit: () -> Unit = {},
    private val movePhoto: (File, File) -> Boolean = { source, destination -> source.renameTo(destination) },
    private val applySettings: (String, Map<Long, Long>, Boolean) -> Boolean = { json, tags, restoreTags ->
        SettingsStore.importBackupJson(context, json, tags, restoreTags)
    }
) {
    private val lock = Mutex()
    private val stagingRoot get() = File(context.filesDir, "point_photo_imports")

    suspend fun recover() = withContext(Dispatchers.IO) {
        lock.withLock { recoverLocked() }
    }

    suspend fun restore(
        uri: Uri,
        state: MutableStateFlow<RestoreState>,
        commitData: suspend (ZipImporter.ImportStats, suspend () -> Unit) -> AppViewModel.ZipImportResult
    ) = withContext(Dispatchers.IO) {
        lock.withLock {
            recoverLocked()
            state.value = RestoreState.Validating
            var staging: File? = null
            var dataCommitted = false
            try {
                val free = context.filesDir.usableSpace
                val reserve = maxOf(512L * 1024 * 1024, free / 10)
                if (free <= reserve) throw IOException("Not enough free storage to stage a backup safely")
                val budget = minOf(32L * 1024 * 1024 * 1024, free - reserve)
                staging = createPointPhotoImportStagingDir(context)
                val workingDir = requireNotNull(staging)
                val jobContext = currentCoroutineContext()
                val imported = context.contentResolver.openInputStream(uri)?.use { source ->
                    val cancellable = object : FilterInputStream(source) {
                        override fun read(): Int {
                            jobContext.ensureActive()
                            return super.read()
                        }
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            jobContext.ensureActive()
                            return source.read(buffer, offset, length)
                        }
                    }
                    ZipImporter.importZip(
                        cancellable,
                        limits = ZipImportLimits(maxTotalBytes = budget),
                        savePhoto = { entryName, input ->
                            jobContext.ensureActive()
                            state.value = RestoreState.Restoring
                            val extension = entryName.substringAfterLast('.', "jpg").lowercase(Locale.US)
                                .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "jpg"
                            val photo = File(workingDir, "point_photo_${UUID.randomUUID()}.$extension")
                            photo.outputStream().buffered().use { output -> input.copyTo(output) }
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(photo.absolutePath, bounds)
                            require(bounds.outWidth in 1..40_000 && bounds.outHeight in 1..40_000 &&
                                bounds.outWidth.toLong() * bounds.outHeight <= 250_000_000L) {
                                "Invalid or oversized image in backup: $entryName"
                            }
                            photo.name
                        }
                    )
                } ?: throw IOException("Cannot open backup archive")
                imported.settingsJson?.let { SettingsStore.validateBackupJson(it) }
                jobContext.ensureActive()
                beforeCommit()
                jobContext.ensureActive()
                val stagedPhotos = workingDir.listFiles()?.filter { it.isFile }.orEmpty()
                require(stagedPhotos.size == imported.importedPhotoCount) { "Incomplete staged photos" }
                require(stagedPhotos.none { File(getPointPhotoDir(context), it.name).exists() }) {
                    "A staged photo filename already exists"
                }
                val journal = File(workingDir, "restore-journal.json")
                // Persist the complete intent before any final photo is moved. A crash after this
                // point is reconciled against committed DB references on the next app start.
                val intent = JSONArray(stagedPhotos.map { it.name })
                val tmp = File(workingDir, "restore-journal.tmp")
                FileOutputStream(tmp).use { output ->
                    output.write(intent.toString().toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                check(tmp.renameTo(journal)) { "Cannot persist restore journal" }
                state.value = RestoreState.Finalizing
                withContext(NonCancellable + Dispatchers.IO) {
                    val photoDirectory = getPointPhotoDir(context)
                    val result = commitData(imported) {
                        stagedPhotos.forEach { staged ->
                            val final = File(photoDirectory, staged.name)
                            check(!final.exists() && movePhoto(staged, final)) {
                                "Cannot move staged photo to its final location"
                            }
                        }
                    }
                    dataCommitted = true
                    val settingsApplied = imported.settingsJson?.let { json ->
                        runCatching { applySettings(json, result.legacyTagIdToActualId,
                            imported.manifest.sections.tags) }.getOrDefault(false)
                    } ?: true
                    state.value = if (settingsApplied) RestoreState.Success(imported.points.size)
                    else RestoreState.SuccessWithWarning(imported.points.size, "Settings could not be saved")
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    // Also removes photos moved during a failed (rolled-back) Room transaction.
                    staging?.let { dir ->
                        val journal = File(dir, "restore-journal.json")
                        if (journal.exists() && !dataCommitted) cleanJournal(journal)
                        deletePointPhotoImportStagingDir(dir)
                    }
                }
            }
        }
    }

    private suspend fun recoverLocked() {
        stagingRoot.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val journal = File(dir, "restore-journal.json")
            if (journal.exists()) cleanJournal(journal)
            deletePointPhotoImportStagingDir(dir)
        }
    }

    private suspend fun cleanJournal(journal: File) {
        val paths = JSONArray(journal.readText(Charsets.UTF_8))
        for (index in 0 until paths.length()) {
            val name = paths.getString(index)
            require(name.startsWith("point_photo_") && name.matches(Regex("[a-zA-Z0-9_.-]+"))) {
                "Invalid restore journal"
            }
            val final = File(getPointPhotoDir(context), name)
            if (final.exists() && !repository.isPhotoReferenced(name)) {
                check(final.delete()) { "Cannot clean orphaned restore photo: $name" }
            }
        }
    }
}
