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
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.export.CsvExporter
import com.lavacrafter.maptimelinetool.export.CsvImporter
import com.lavacrafter.maptimelinetool.export.GeoJsonExporter
import com.lavacrafter.maptimelinetool.export.KmlExporter
import com.lavacrafter.maptimelinetool.export.KmzExporter
import com.lavacrafter.maptimelinetool.export.ZipImporter
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real ContentResolver streams and deletion fallback, without a document picker or user files. */
@RunWith(AndroidJUnit4::class)
class CreatedDocumentExportInstrumentedTest {
    @Test
    fun allFiveFormatsFinishOnRealContentUris() = runBlocking {
        val context = debugContext()
        val points = listOf(Point(timestamp = 1_710_000_000_000L, latitude = 10.0, longitude = 20.0,
            title = "Export", note = "Note"))
        val writers: Map<ExportFileKind, (OutputStream) -> Unit> = mapOf(
            ExportFileKind.CSV to { CsvExporter.writeCsv(points, it) },
            ExportFileKind.GEOJSON to { GeoJsonExporter.writeGeoJson(points, it) },
            ExportFileKind.KML to { KmlExporter.writeKml(points, it) },
            ExportFileKind.KMZ to { KmzExporter.export(points, it, { null }) },
            ExportFileKind.ZIP to { PendingExportPayload(points, ExportFileKind.ZIP, true).writeOrdinaryZip(it, { null }, "test") }
        )
        for ((kind, writer) in writers) {
            val file = destination(context, kind.name)
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val result = writeCreatedDocument(context, uri) { writer(it); points.size }
                assertEquals(1, result)
                assertTrue(file.length() > 0)
                when (kind) {
                    ExportFileKind.CSV -> file.reader().use {
                        assertEquals("Export", CsvImporter.parseOrdinaryCsv(it).points.single().title)
                    }
                    ExportFileKind.GEOJSON -> assertEquals("Export", GeoJsonExporter.parsePointsFromGeoJson(file.readText()).single().title)
                    ExportFileKind.KML -> assertTrue(file.readText().contains("</kml>"))
                    ExportFileKind.KMZ -> ZipInputStream(file.inputStream()).use {
                        assertEquals("doc.kml", it.nextEntry.name)
                        assertTrue(it.readBytes().toString(Charsets.UTF_8).contains("</kml>"))
                    }
                    ExportFileKind.ZIP -> file.inputStream().use {
                        val imported = ZipImporter.importZip(it) { _, _ -> null }
                        assertEquals("Export", imported.points.single().title)
                    }
                }
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun failedWriteDeletesNewContentUri() = runBlocking {
        assertFailedDocumentRemoved(IOException("Injected write failure"))
    }

    @Test
    fun cancelledWriteDeletesNewContentUriAndPropagatesCancellation() = runBlocking {
        assertFailedDocumentRemoved(CancellationException("Injected cancellation"))
    }

    private suspend fun assertFailedDocumentRemoved(failure: Exception) {
        val context = debugContext()
        val file = destination(context, "partial")
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val thrown = runCatching {
                writeCreatedDocument(context, uri) {
                    it.write(byteArrayOf(1, 2, 3))
                    throw failure
                }
            }.exceptionOrNull()
            assertEquals(failure.javaClass, thrown?.javaClass)
            assertEquals(failure.message, thrown?.message)
            assertTrue(generateSequence(thrown) { it.cause }.any { it === failure })
            assertFalse(file.exists())
        } finally {
            file.delete()
        }
    }

    private fun debugContext(): Context = ApplicationProvider.getApplicationContext<Context>().also {
        check(it.packageName.endsWith(".debug")) { "Export tests must never use the production sandbox" }
    }

    private fun destination(context: Context, suffix: String): File =
        File(context.filesDir, "shared_backups/phase9_export_${UUID.randomUUID()}.$suffix").apply {
            parentFile!!.mkdirs()
            createNewFile()
        }
}
