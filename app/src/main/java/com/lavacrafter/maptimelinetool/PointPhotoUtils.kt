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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.lavacrafter.maptimelinetool.ui.PhotoCompressFormat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private const val POINT_PHOTO_DIR_NAME = "point_photos"

data class PhotoPersistOptions(
    val losslessEnabled: Boolean,
    val compressFormat: PhotoCompressFormat,
    val compressQuality: Int
)

fun getPointPhotoDir(context: Context): File {
    val dir = File(context.filesDir, POINT_PHOTO_DIR_NAME)
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

fun createPointPhotoImportStagingDir(context: Context): File {
    val directory = File(context.filesDir, "point_photo_imports/import_${UUID.randomUUID()}")
    check(directory.mkdirs() || directory.isDirectory) { "Unable to create photo import staging directory" }
    return directory
}

fun commitPointPhotoImport(context: Context, stagingFiles: List<File>): List<String> {
    val destinationDir = getPointPhotoDir(context)
    val committed = mutableListOf<File>()
    try {
        stagingFiles.forEach { stagingFile ->
            if (!stagingFile.isFile || stagingFile.parentFile?.isDirectory != true) {
                throw IllegalArgumentException("Invalid staged photo")
            }
            val destination = File(destinationDir, stagingFile.name)
            check(!destination.exists()) { "Generated photo name already exists" }
            val moved = stagingFile.renameTo(destination)
            if (!moved) {
                stagingFile.inputStream().buffered().use { input ->
                    destination.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                if (!stagingFile.delete()) {
                    destination.delete()
                    throw IllegalStateException("Unable to remove staged photo")
                }
            }
            committed += destination
        }
        return committed.map(::toStoredPhotoPath)
    } catch (error: Exception) {
        committed.forEach { it.delete() }
        throw error
    }
}

fun deletePointPhotoImportStagingDir(stagingDir: File?) {
    stagingDir?.takeIf { it.exists() }?.deleteRecursively()
}

fun createPendingPointPhotoFile(context: Context): File {
    val fileName = "point_photo_${UUID.randomUUID()}.jpg"
    return File(getPointPhotoDir(context), fileName)
}

private fun createPointPhotoFile(context: Context, extension: String): File {
    val fileName = "point_photo_${UUID.randomUUID()}.$extension"
    return File(getPointPhotoDir(context), fileName)
}

fun toStoredPhotoPath(file: File): String = file.name

fun resolvePointPhotoFile(context: Context, photoPath: String?): File? {
    val normalized = photoPath?.trim().orEmpty()
    if (normalized.isEmpty() || normalized.contains('/') || normalized.contains('\\') || normalized == "." || normalized == "..") {
        return null
    }
    val photoDirectory = getPointPhotoDir(context).canonicalFile
    val candidate = File(normalized)
    val resolved = if (candidate.isAbsolute) candidate.canonicalFile else File(photoDirectory, normalized).canonicalFile
    return resolved.takeIf { file ->
        file.parentFile?.canonicalFile == photoDirectory
    }
}

fun deletePointPhotoFile(context: Context, photoPath: String?) {
    resolvePointPhotoFile(context, photoPath)?.let { file ->
        if (file.exists()) {
            file.delete()
        }
    }
}

suspend fun preparePhotoForPersist(
    context: Context,
    photoPath: String?,
    options: PhotoPersistOptions
): PreparedPhoto {
    var generatedPhotoPath: String? = null
    try {
        return withContext(Dispatchers.IO) {
            if (photoPath.isNullOrBlank() || options.losslessEnabled) {
                return@withContext PreparedPhoto(photoPath, generatedPath = null, sourcePath = null)
            }
            val sourceFile = resolvePointPhotoFile(context, photoPath)
                ?: return@withContext PreparedPhoto(photoPath, generatedPath = null, sourcePath = null)
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                return@withContext PreparedPhoto(photoPath, generatedPath = null, sourcePath = null)
            }
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
                ?: return@withContext PreparedPhoto(photoPath, generatedPath = null, sourcePath = null)
            var corrected: Bitmap? = null
            try {
                val orientation = runCatching {
                    ExifInterface(sourceFile.absolutePath).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                val outputBitmap = applyExifOrientation(bitmap, orientation)
                corrected = outputBitmap
                val (format, extension) = when (options.compressFormat) {
                    PhotoCompressFormat.JPEG -> Bitmap.CompressFormat.JPEG to "jpg"
                    PhotoCompressFormat.PNG -> Bitmap.CompressFormat.PNG to "png"
                    PhotoCompressFormat.WEBP -> Bitmap.CompressFormat.WEBP to "webp"
                }
                val generatedFile = createPointPhotoFile(context, extension)
                generatedPhotoPath = toStoredPhotoPath(generatedFile)
                generatedFile.outputStream().use { output ->
                    check(outputBitmap.compress(format, options.compressQuality.coerceIn(1, 100), output)) {
                        "Unable to compress point photo"
                    }
                }
                PreparedPhoto(
                    storedPath = generatedPhotoPath,
                    generatedPath = generatedPhotoPath,
                    sourcePath = toStoredPhotoPath(sourceFile)
                )
            } finally {
                corrected?.takeUnless { it.isRecycled }?.recycle()
                if (corrected !== bitmap && !bitmap.isRecycled) bitmap.recycle()
            }
        }
    } catch (error: Throwable) {
        // withContext can be cancelled even after its IO block finishes but before its result is delivered.
        // Clean the generated output in both cases without deleting the original candidate.
        generatedPhotoPath?.let { path ->
            withContext(NonCancellable + Dispatchers.IO) {
                resolvePointPhotoFile(context, path)?.delete()
            }
        }
        throw error
    }
}

data class PreparedPhoto(
    val storedPath: String?,
    val generatedPath: String?,
    val sourcePath: String?
) {
    suspend fun commitCleanup(
        context: Context,
        isPhotoReferenced: suspend (String) -> Boolean
    ) {
        val generated = generatedPath ?: return
        val source = sourcePath ?: return
        if (generated == source || isPhotoReferenced(source)) return
        withContext(Dispatchers.IO) {
            resolvePointPhotoFile(context, source)?.takeIf { it.name != generated }?.delete()
        }
    }

    suspend fun rollback(context: Context) {
        withContext(Dispatchers.IO) {
            generatedPath?.let { resolvePointPhotoFile(context, it)?.delete() }
        }
    }
}

fun applyExifOrientation(
    bitmap: Bitmap,
    orientation: Int
): Bitmap {
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postScale(-1f, 1f)
                postRotate(270f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postScale(-1f, 1f)
                postRotate(90f)
            }
        }
    }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it != bitmap) {
            bitmap.recycle()
        }
    }
}
