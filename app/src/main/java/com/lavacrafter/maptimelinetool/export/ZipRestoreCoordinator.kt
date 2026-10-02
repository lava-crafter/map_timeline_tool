package com.lavacrafter.maptimelinetool.export

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.lavacrafter.maptimelinetool.createPointPhotoImportStagingDir
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.usecase.PhotoCommitGuard
import com.lavacrafter.maptimelinetool.getPointPhotoDir
import com.lavacrafter.maptimelinetool.resolvePointPhotoFile
import com.lavacrafter.maptimelinetool.ui.AppViewModel
import com.lavacrafter.maptimelinetool.ui.SettingsStore
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

sealed interface RestoreState {
    data object Idle : RestoreState
    data object Validating : RestoreState
    data object Restoring : RestoreState
    data object Finalizing : RestoreState
    data class Success(val points: Int) : RestoreState
    data class SuccessWithWarning(
        val points: Int,
        val warning: String,
        val settingsNotApplied: Boolean = true,
        val photoCleanupPending: Boolean = false
    ) : RestoreState
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
    },
    private val photoCommitGuard: PhotoCommitGuard = PhotoCommitGuard(),
    private val deletePhoto: (File) -> Boolean = { it.delete() }
) {
    private val lock = Mutex()
    private val stagingRoot get() = File(context.filesDir, "point_photo_imports")

    suspend fun recover() = withContext(Dispatchers.IO) {
        lock.withLock { photoCommitGuard.withLock { recoverLocked() } }
    }

    suspend fun restore(
        uri: Uri,
        state: MutableStateFlow<RestoreState>,
        commitData: suspend (ZipImporter.ImportStats, suspend (Set<String>) -> Unit) -> AppViewModel.ZipImportResult
    ) = withContext(Dispatchers.IO) {
        lock.withLock {
            photoCommitGuard.withLock { recoverLocked() }
            state.value = RestoreState.Validating
            var staging: File? = null
            var dataCommitted = false
            var failure: Throwable? = null
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
                require(!imported.manifest.sections.photos || imported.manifest.sections.points) {
                    "Photo backups must include points"
                }
                imported.settingsJson?.let { SettingsStore.validateBackupJson(it) }
                jobContext.ensureActive()
                beforeCommit()
                jobContext.ensureActive()
                val stagedPhotos = workingDir.listFiles()?.filter { it.isFile }.orEmpty()
                require(stagedPhotos.size == imported.importedPhotoCount) { "Incomplete staged photos" }
                require(stagedPhotos.map { it.name }.toSet() == imported.points.mapNotNull { it.photoPath }.toSet()) {
                    "Missing or unreferenced staged photos"
                }
                val journal = File(workingDir, "restore-journal.json")
                state.value = RestoreState.Finalizing
                withContext(NonCancellable + Dispatchers.IO) {
                    photoCommitGuard.withLock {
                        val photoDirectory = getPointPhotoDir(context)
                        require(stagedPhotos.none { File(photoDirectory, it.name).exists() }) {
                            "A staged photo filename already exists"
                        }
                        val result = commitData(imported) { retiredPhotos ->
                            // Called inside Room, after matching, but BEFORE any file move or DB commit.
                            // On rollback the old files remain referenced; on commit only retired,
                            // unshared files become eligible for cleanup.
                            writeJournal(journal, stagedPhotos.map { it.name }, retiredPhotos)
                            stagedPhotos.forEach { staged ->
                                val final = File(photoDirectory, staged.name)
                                check(!final.exists() && movePhoto(staged, final)) {
                                    "Cannot move staged photo to its final location"
                                }
                            }
                        }
                        dataCommitted = true
                        var cleanupPending = false
                        try {
                            cleanJournal(journal)
                            check(workingDir.deleteRecursively()) { "Cannot remove restore staging directory" }
                        } catch (error: Exception) {
                            cleanupPending = true
                            Log.w("ZipRestoreCoordinator", "Photo cleanup will retry from the restore journal", error)
                        }
                        val settingsApplied = imported.settingsJson?.let { json ->
                            runCatching { applySettings(json, result.legacyTagIdToActualId,
                                imported.manifest.sections.tags) }.getOrDefault(false)
                        } ?: true
                        val warnings = listOfNotNull(
                            if (!settingsApplied) "Settings could not be saved" else null,
                            if (cleanupPending) "Photo cleanup pending; it will be retried" else null
                        )
                        state.value = if (warnings.isEmpty()) RestoreState.Success(imported.points.size)
                        else RestoreState.SuccessWithWarning(imported.points.size, warnings.joinToString("; "),
                            settingsNotApplied = !settingsApplied, photoCleanupPending = cleanupPending)
                    }
                }
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (!dataCommitted) photoCommitGuard.withLock {
                        // Cleanup failures must preserve both the original error and the journal.
                        try {
                            staging?.let { dir ->
                                val journal = File(dir, "restore-journal.json")
                                if (journal.exists()) cleanJournal(journal)
                                check(!dir.exists() || dir.deleteRecursively()) { "Cannot remove restore staging directory" }
                            }
                        } catch (cleanupError: Exception) {
                            failure?.addSuppressed(cleanupError) ?: throw cleanupError
                        }
                    }
                }
            }
        }
    }

    private suspend fun recoverLocked() {
        stagingRoot.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val journal = File(dir, "restore-journal.json")
            if (journal.exists()) cleanJournal(journal)
            check(dir.deleteRecursively()) { "Cannot remove restore staging directory" }
        }
    }

    private fun writeJournal(journal: File, newPhotos: List<String>, retiredPhotos: Set<String>) {
        (newPhotos + retiredPhotos).forEach { requireSafePhotoName(it) }
        val intent = JSONObject().put("version", 2)
            .put("newPhotos", JSONArray(newPhotos)).put("retiredPhotos", JSONArray(retiredPhotos.toList()))
        val tmp = File(journal.parentFile, "restore-journal.tmp")
        FileOutputStream(tmp).use { output ->
            output.write(intent.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        check(tmp.renameTo(journal)) { "Cannot persist restore journal" }
    }

    private fun requireSafePhotoName(name: String) {
        val resolved = resolvePointPhotoFile(context, name)
        require(name == name.trim() && resolved != null && resolved.name == name) {
            "Invalid restore journal photo path"
        }
    }

    private suspend fun cleanJournal(journal: File) {
        val text = journal.readText(Charsets.UTF_8)
        val paths = if (text.trimStart().startsWith("[")) {
            // v1 journals only owned newly generated point_photo_* files.
            val legacy = JSONArray(text)
            List(legacy.length()) { legacy.getString(it) }.also { names ->
                require(names.all { it.startsWith("point_photo_") && it.matches(Regex("[a-zA-Z0-9_.-]+")) }) {
                    "Invalid legacy restore journal"
                }
            }
        } else {
            val root = JSONObject(text)
            require(root.getInt("version") == 2) { "Unsupported restore journal" }
            listOf("newPhotos", "retiredPhotos").flatMap { key ->
                val array = root.getJSONArray(key)
                List(array.length()) { array.getString(it) }
            }
        }
        // Validate the whole journal before deleting anything; keep it until ALL cleanup succeeds.
        paths.forEach(::requireSafePhotoName)
        for (name in paths.distinct()) {
            val final = requireNotNull(resolvePointPhotoFile(context, name))
            if (final.exists() && !repository.isPhotoReferenced(name)) {
                check(deletePhoto(final)) { "Cannot clean orphaned restore photo: $name" }
            }
        }
    }
}
