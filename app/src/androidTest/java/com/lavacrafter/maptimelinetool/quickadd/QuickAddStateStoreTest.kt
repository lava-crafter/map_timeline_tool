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

package com.lavacrafter.maptimelinetool.quickadd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAddStateStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun clearPreferences() {
        context.getSharedPreferences(QuickAddStateStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun staleRunningStateCanStartAgain() {
        var now = 1_000L
        val store = QuickAddStateStore(
            context = context,
            wallClockMs = { now },
            runningStaleAfterMs = 100L
        )

        assertTrue(store.tryStart())
        assertFalse(store.tryStart())
        now += 101L
        assertTrue(store.tryStart())
    }

    @Test
    fun initializationIsPersistedAcrossInstances() {
        val first = QuickAddStateStore(context)
        first.setInitialized(true)

        assertTrue(QuickAddStateStore(context).isInitialized())
    }
}
