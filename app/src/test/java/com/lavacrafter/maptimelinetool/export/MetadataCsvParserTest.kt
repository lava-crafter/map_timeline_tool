package com.lavacrafter.maptimelinetool.export

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataCsvParserTest {
    @Test
    fun parsesCanonicalMetadataWithQuotedNames() {
        val tags = MetadataCsvParser.parseTags(StringReader("tag_id,name\n7,\"Work, now\"\n"))
        val links = MetadataCsvParser.parsePointTags(StringReader("point_index,tag_id\n2,7\n"))
        assertEquals(listOf(ZipImporter.ImportedTag(7, "Work, now")), tags)
        assertEquals(listOf(ZipImporter.ImportedPointTag(2, 7)), links)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedCanonicalRowInsteadOfSkippingIt() {
        MetadataCsvParser.parseTags(StringReader("tag_id,name\n7,Work\nbad,Skipped\n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnterminatedQuotes() {
        MetadataCsvParser.parsePointTags(StringReader("point_index,tag_id\n1,\"7\n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun enforcesRecordCeiling() {
        MetadataCsvParser.parseTags(
            StringReader("tag_id,name\n1,A\n2,B\n3,C\n"),
            MetadataCsvParser.Limits(maxRecords = 2)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun enforcesFieldCeiling() {
        MetadataCsvParser.parseTags(
            StringReader("tag_id,name\n1,abcdef\n"),
            MetadataCsvParser.Limits(maxFieldChars = 3)
        )
    }
}
