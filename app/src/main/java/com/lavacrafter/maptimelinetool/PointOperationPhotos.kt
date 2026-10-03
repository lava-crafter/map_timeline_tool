package com.lavacrafter.maptimelinetool

import android.content.Context
import android.util.Log
import com.lavacrafter.maptimelinetool.domain.repository.PointRepositoryGateway
import com.lavacrafter.maptimelinetool.domain.usecase.PhotoCommitGuard
import com.lavacrafter.maptimelinetool.ui.PointOperationPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPointOperationPhotos(
    context: Context,
    private val repository: PointRepositoryGateway,
    private val guard: PhotoCommitGuard
) : PointOperationPhotos {
    private val appContext = context.applicationContext
    override suspend fun prepare(path: String?, options: PhotoPersistOptions) =
        preparePhotoForPersist(appContext, path, options)

    override suspend fun rollbackUnlessReferenced(photo: PreparedPhoto) = withContext(Dispatchers.IO) {
        guard.withLock {
            val referenced = photo.generatedPath?.let { path ->
                try { repository.isPhotoReferenced(path) }
                catch (error: Exception) {
                    Log.w("PointOperationPhotos", "Could not verify committed photo; preserving it", error)
                    true
                }
            } ?: false
            if (!referenced) photo.rollback(appContext)
        }
    }

    override suspend fun commitCleanup(photo: PreparedPhoto) = withContext(Dispatchers.IO) {
        guard.withLock {
            try { photo.commitCleanup(appContext, repository::isPhotoReferenced) }
            catch (error: Exception) { Log.w("PointOperationPhotos", "Photo source cleanup failed", error) }
            Unit
        }
    }

    override suspend fun deleteUnlessReferenced(path: String?) = withContext(Dispatchers.IO) {
        guard.withLock {
            if (path != null && !repository.isPhotoReferenced(path)) deletePointPhotoFile(appContext, path)
        }
    }
}
