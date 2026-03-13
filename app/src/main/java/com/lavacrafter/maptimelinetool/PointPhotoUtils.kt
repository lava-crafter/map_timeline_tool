package com.lavacrafter.maptimelinetool

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.lavacrafter.maptimelinetool.ui.PhotoCompressFormat
import java.io.File
import java.util.UUID

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
): String? {
    if (photoPath.isNullOrBlank() || options.losslessEnabled) return photoPath
    val sourceFile = resolvePointPhotoFile(context, photoPath) ?: return photoPath
    if (!sourceFile.exists() || !sourceFile.canRead()) return photoPath
    val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return photoPath
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
    if (corrected != bitmap) {
        corrected.recycle()
    }
    bitmap.recycle()
    if (!compressed) {
        if (targetFile.exists()) targetFile.delete()
        return photoPath
    }
    if (sourceFile != targetFile) {
        sourceFile.delete()
    }
    return toStoredPhotoPath(targetFile)
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
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
