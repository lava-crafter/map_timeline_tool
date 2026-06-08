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
    if (normalized.isEmpty()) return null
    val file = File(normalized)
    return if (file.isAbsolute) file else File(getPointPhotoDir(context), normalized)
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
): String? = withContext(Dispatchers.IO) {
    if (photoPath.isNullOrBlank() || options.losslessEnabled) return@withContext photoPath
    val sourceFile = resolvePointPhotoFile(context, photoPath) ?: return@withContext photoPath
    if (!sourceFile.exists() || !sourceFile.canRead()) return@withContext photoPath
    val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return@withContext photoPath
    val orientation = runCatching {
        ExifInterface(sourceFile.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val corrected = applyExifOrientation(bitmap, orientation)
    val (format, extension) = when (options.compressFormat) {
        PhotoCompressFormat.JPEG -> Bitmap.CompressFormat.JPEG to "jpg"
        PhotoCompressFormat.PNG -> Bitmap.CompressFormat.PNG to "png"
        PhotoCompressFormat.WEBP -> Bitmap.CompressFormat.WEBP to "webp"
    }
    val targetFile = createPointPhotoFile(context, extension)
    val compressed = runCatching {
        targetFile.outputStream().use { output ->
            corrected.compress(format, options.compressQuality.coerceIn(1, 100), output)
        }
    }.getOrDefault(false)
    corrected.recycle()
    if (!compressed) {
        if (targetFile.exists()) targetFile.delete()
        return@withContext photoPath
    }
    if (sourceFile != targetFile) {
        sourceFile.delete()
    }
    return@withContext toStoredPhotoPath(targetFile)
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
