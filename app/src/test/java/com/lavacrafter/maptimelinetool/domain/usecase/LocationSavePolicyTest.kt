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

package com.lavacrafter.maptimelinetool.domain.usecase

import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSavePolicyTest {
    private val policy = LocationSavePolicy()
    private val nowMs = 1_000_000L

    @Test
    fun preciseLocation_isAcceptedWithoutConfirmation() {
        val decision = policy.evaluate(
            preciseLocation = GeoPoint(1.0, 2.0, accuracyMeters = 12f, fixTimeMs = nowMs - 1_000L),
            fallbackLocation = null,
            flow = LocationSaveFlow.MANUAL_ADD,
            nowMs = nowMs
        )

        assertEquals(LocationSaveQuality.PRECISE_FRESH, decision.quality)
        assertTrue(decision.canSave)
        assertFalse(decision.requiresManualConfirmation)
    }

    @Test
    fun freshFallback_requiresConfirmationForManualAdd() {
        val decision = policy.evaluate(
            preciseLocation = null,
            fallbackLocation = GeoPoint(1.0, 2.0, accuracyMeters = 180f, fixTimeMs = nowMs - 10_000L),
            flow = LocationSaveFlow.MANUAL_ADD,
            nowMs = nowMs
        )

        assertEquals(LocationSaveQuality.FRESH_BUT_LOW_ACCURACY, decision.quality)
        assertTrue(decision.canSave)
        assertTrue(decision.requiresManualConfirmation)
    }

    @Test
    fun recentLastKnown_isSavedWithoutManualConfirmation() {
        val decision = policy.evaluate(
            preciseLocation = null,
            fallbackLocation = GeoPoint(1.0, 2.0, accuracyMeters = 90f, fixTimeMs = nowMs - 120_000L),
            flow = LocationSaveFlow.MANUAL_ADD,
            nowMs = nowMs
        )

        assertEquals(LocationSaveQuality.LAST_KNOWN_RECENT, decision.quality)
        assertTrue(decision.canSave)
        assertFalse(decision.requiresManualConfirmation)
    }

    @Test
    fun staleLastKnown_doesNotRequireConfirmationForAutoSave() {
        val decision = policy.evaluate(
            preciseLocation = null,
            fallbackLocation = GeoPoint(1.0, 2.0, accuracyMeters = 200f, fixTimeMs = nowMs - 8_000_000L),
            flow = LocationSaveFlow.AUTO_SAVE,
            nowMs = nowMs
        )

        assertEquals(LocationSaveQuality.LAST_KNOWN_STALE, decision.quality)
        assertTrue(decision.canSave)
        assertFalse(decision.requiresManualConfirmation)
    }

    @Test
    fun missingLocation_isUnavailable() {
        val decision = policy.evaluate(
            preciseLocation = null,
            fallbackLocation = null,
            flow = LocationSaveFlow.QUICK_ADD,
            nowMs = nowMs
        )

        assertEquals(LocationSaveQuality.UNAVAILABLE, decision.quality)
        assertFalse(decision.canSave)
        assertFalse(decision.requiresManualConfirmation)
    }
}
