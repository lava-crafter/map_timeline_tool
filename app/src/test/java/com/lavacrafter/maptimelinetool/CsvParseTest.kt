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

import com.lavacrafter.maptimelinetool.export.CsvImporter
import org.junit.Test
import org.junit.Assert.assertEquals

class CsvParseTest {
    @Test
    fun testParse() {
        val csv = """
            name,description,latitude,longitude,time_utc
            MyName,MyNote,1.0,2.0,2024-01-01T00:00:00Z
        """.trimIndent()
        val points = CsvImporter.parseCsv(csv)
        val p = points.first()
        assertEquals("MyName", p.title)
        assertEquals("MyNote", p.note)
    }
}
