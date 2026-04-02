package com.lavacrafter.maptimelinetool.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextInputSanitizerTest {
    @Test
    fun `sanitize single line text collapses controls and whitespace`() {
        assertEquals("Hello World", sanitizePointTitle("  Hello\tWorld\u0007  "))
    }

    @Test
    fun `sanitize helpers cap length`() {
        assertEquals(MAX_POINT_TITLE_LENGTH, sanitizePointTitle("x".repeat(MAX_POINT_TITLE_LENGTH + 20)).length)
        assertEquals(MAX_POINT_NOTE_LENGTH, sanitizePointNote("y".repeat(MAX_POINT_NOTE_LENGTH + 20)).length)
        assertEquals(MAX_TAG_NAME_LENGTH, sanitizeTagName("z".repeat(MAX_TAG_NAME_LENGTH + 20)).length)
    }
}