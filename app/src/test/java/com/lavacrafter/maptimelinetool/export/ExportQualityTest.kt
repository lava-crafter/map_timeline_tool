package com.lavacrafter.maptimelinetool.export

import com.lavacrafter.maptimelinetool.domain.model.Point
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.charset.MalformedInputException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportQualityTest {
    private fun point(title: String = "P", lon: Double = 20.0, photoPath: String? = null) = Point(
        timestamp = 1_710_000_000_000L, latitude = 10.0, longitude = lon, title = title, note = "", photoPath = photoPath,
        locationProvider = "gps\u0001"
    )

    @Test
    fun `kml drops xml-illegal characters and writes plain decimal coordinates`() {
        val kml = KmlExporter.buildKml(listOf(point(title = "a\u0001b￾\uD800c", lon = -0.0005)))
        assertTrue(kml.contains("<name>abc</name>"))
        assertTrue(kml.contains("<coordinates>-0.0005,10,0</coordinates>"))
        assertFalse(kml.contains("\u0001"))
        val streamed = ByteArrayOutputStream().also { KmlExporter.writeKml(listOf(point()), it) }.toString(Charsets.UTF_8.name())
        assertEquals(KmlExporter.buildKml(listOf(point())), streamed)
    }

    @Test
    fun `standalone csv starts with a bom and ordinary import tolerates spreadsheet preamble`() {
        val bytes = ByteArrayOutputStream().also { CsvExporter.writeCsv(listOf(point()), it) }.toByteArray()
        assertEquals("﻿", String(bytes, Charsets.UTF_8).take(1))
        val reimported = CsvImporter.parseOrdinaryCsv(CsvImporter.strictUtf8Reader(ByteArrayInputStream(bytes)))
        assertEquals(1, reimported.points.size)
        assertEquals("gps", reimported.points.single().locationProvider)

        val withPreamble = "\nsep=,\n" + String(bytes, Charsets.UTF_8).drop(1)
        assertEquals(1, CsvImporter.parseOrdinaryCsv(StringReader(withPreamble)).points.size)
    }

    @Test(expected = MalformedInputException::class)
    fun `strict reader rejects non utf8 csv instead of importing replacement characters`() {
        val gbk = "name,latitude,longitude\n中文,1,2\n".toByteArray(charset("GBK"))
        CsvImporter.parseOrdinaryCsv(CsvImporter.strictUtf8Reader(ByteArrayInputStream(gbk)))
    }

    @Test
    fun `geojson null title is treated as missing`() {
        val json = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point",
            "coordinates":[20.0,10.0]},"properties":{"title":null,"note":null,"timestamp_ms":1710000000000}}]}"""
        val parsed = GeoJsonExporter.parsePointsFromGeoJson(json).single()
        assertFalse(parsed.title == "null")
        assertEquals("", parsed.note)
    }

    @Test
    fun `photo names differing only in case export as distinct entries`() {
        val dir = createTempDirectory("case-photos").toFile()
        try {
            val upper = java.io.File(dir, "a").apply { mkdirs() }.resolve("IMG.jpg").apply { writeBytes(byteArrayOf(1)) }
            val lower = java.io.File(dir, "b").apply { mkdirs() }.resolve("img.jpg").apply { writeBytes(byteArrayOf(2)) }
            val output = ByteArrayOutputStream()
            ZipExporter.export(listOf(point(photoPath = upper.path), point(title = "Q", photoPath = lower.path)), output, { java.io.File(it) })
            val imported = ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { name, input -> input.readBytes(); name }
            assertEquals(2, imported.importedPhotoCount)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `archive with nothing recognizable is rejected`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("folder/points.csv"))
            zip.write("name,latitude,longitude\n".toByteArray())
            zip.closeEntry()
        }
        ZipImporter.importZip(ByteArrayInputStream(output.toByteArray())) { _, _ -> null }
    }
}
