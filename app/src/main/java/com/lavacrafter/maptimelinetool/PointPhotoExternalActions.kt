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

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

internal enum class PointPhotoExternalActionResult {
    LAUNCHED,
    PHOTO_UNAVAILABLE,
    NO_HANDLER
}

internal fun launchPointPhotoViewer(
    context: Context,
    photoPath: String?
): PointPhotoExternalActionResult {
    return launchPointPhotoAction(context, photoPath) { uri, mimeType ->
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal fun launchPointPhotoShare(
    context: Context,
    photoPath: String?,
    chooserTitle: String
): PointPhotoExternalActionResult {
    return launchPointPhotoAction(context, photoPath) { uri, mimeType ->
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("point_photo", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(shareIntent, chooserTitle)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun launchPointPhotoAction(
    context: Context,
    photoPath: String?,
    createIntent: (android.net.Uri, String) -> Intent
): PointPhotoExternalActionResult {
    val photoFile = resolveReadablePointPhotoFile(context, photoPath)
        ?: return PointPhotoExternalActionResult.PHOTO_UNAVAILABLE
    val photoUri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    } catch (_: IllegalArgumentException) {
        return PointPhotoExternalActionResult.PHOTO_UNAVAILABLE
    }

    return try {
        context.startActivity(createIntent(photoUri, photoMimeType(photoFile)))
        PointPhotoExternalActionResult.LAUNCHED
    } catch (_: ActivityNotFoundException) {
        PointPhotoExternalActionResult.NO_HANDLER
    } catch (_: SecurityException) {
        PointPhotoExternalActionResult.NO_HANDLER
    }
}

private fun resolveReadablePointPhotoFile(context: Context, photoPath: String?): File? {
    return resolvePointPhotoFile(context, photoPath)
        ?.takeIf { it.exists() && it.isFile && it.canRead() }
}

private fun photoMimeType(file: File): String {
    val extension = file.extension.lowercase(Locale.ROOT)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/*"
}
