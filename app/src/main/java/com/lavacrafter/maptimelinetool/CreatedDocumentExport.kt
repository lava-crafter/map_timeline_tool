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
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal suspend fun <T> writeCreatedDocument(
    context: Context,
    uri: Uri,
    write: suspend (OutputStream) -> T
): T {
    val resolver = context.contentResolver
    return writeCreatedDocument(
        openOutput = { resolver.openOutputStream(uri, "wt") },
        deleteOutput = {
            // A non-document provider can ignore deleteDocument's call yet appear to succeed.
            val deleted = if (DocumentsContract.isDocumentUri(context, uri)) {
                try {
                    DocumentsContract.deleteDocument(resolver, uri)
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
            if (!deleted) {
                check(resolver.delete(uri, null, null) > 0) { "Could not delete incomplete export" }
            }
        },
        write = write
    )
}

/** Returns only after flush/close; failed or cancelled exports remove the new destination best-effort. */
internal suspend fun <T> writeCreatedDocument(
    openOutput: () -> OutputStream?,
    deleteOutput: () -> Unit,
    write: suspend (OutputStream) -> T
): T {
    try {
        return withContext(Dispatchers.IO) {
            val output = openOutput() ?: throw IOException("Failed to open output stream")
            output.use {
                // Exporters may close their Writer/ZipOutputStream. Keep the SAF stream owned here so
                // close failures are observed once, and do not buffer a second copy of a large ZIP.
                val exporterOutput = object : OutputStream() {
                    override fun write(value: Int) = output.write(value)
                    override fun write(bytes: ByteArray, offset: Int, length: Int) = output.write(bytes, offset, length)
                    override fun flush() = output.flush()
                    override fun close() = flush()
                }
                val result = write(exporterOutput)
                currentCoroutineContext().ensureActive()
                output.flush()
                result
            }
        }
    } catch (failure: Throwable) {
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                deleteOutput()
            } catch (cleanupFailure: Exception) {
                failure.addSuppressed(cleanupFailure)
            }
        }
        throw failure
    }
}
