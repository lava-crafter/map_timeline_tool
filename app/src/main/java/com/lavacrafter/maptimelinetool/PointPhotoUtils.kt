package com.lavacrafter.maptimelinetool

import android.content.Context
import java.io.File
import java.util.UUID

private const val POINT_PHOTO_DIR_NAME = "point_photos"

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
