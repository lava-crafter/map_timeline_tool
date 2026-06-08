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