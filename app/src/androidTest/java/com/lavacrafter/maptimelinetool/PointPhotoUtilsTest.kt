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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PointPhotoUtilsTest {
    @Test
    fun resolvePointPhotoFile_onlyAllowsTheDedicatedPhotoDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photoDir = getPointPhotoDir(context)
        val valid = java.io.File(photoDir, "photo-test.jpg")

        assertEquals(valid.canonicalFile, requireNotNull(resolvePointPhotoFile(context, valid.name)))
        assertEquals(valid.canonicalFile, requireNotNull(resolvePointPhotoFile(context, valid.absolutePath)))
        assertNull(resolvePointPhotoFile(context, "../shared_prefs/map_timeline_settings.xml"))
        assertNull(resolvePointPhotoFile(context, "nested/photo-test.jpg"))
        assertNull(resolvePointPhotoFile(context, context.filesDir.absolutePath))
    }
}
