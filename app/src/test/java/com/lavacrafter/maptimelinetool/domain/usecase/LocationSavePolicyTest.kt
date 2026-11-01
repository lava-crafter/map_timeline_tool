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
    fun recentLastKnown_requiresManualConfirmation() {
        val decision = policy.evaluate(
            preciseLocation = null,
            fallbackLocation = GeoPoint(1.0, 2.0, accuracyMeters = 90f, fixTimeMs = nowMs - 120_000L),
            flow = LocationSaveFlow.MANUAL_ADD,
            nowMs = nowMs
        )

        assertEquals(LocationSaveQuality.LAST_KNOWN_RECENT, decision.quality)
        assertTrue(decision.canSave)
        assertTrue(decision.requiresManualConfirmation)
    }

    @Test
    fun staleLastKnown_isRejectedForAutoSaveAndManualAdd() {
        val now = 10_000_000L
        val decision = policy.evaluate(
            preciseLocation = null,
            fallbackLocation = GeoPoint(1.0, 2.0, accuracyMeters = 200f, fixTimeMs = now - 8_000_000L),
            flow = LocationSaveFlow.AUTO_SAVE,
            nowMs = now
        )

        assertEquals(LocationSaveQuality.LAST_KNOWN_STALE, decision.quality)
        assertFalse(decision.canSave)
        assertFalse(decision.requiresManualConfirmation)
        assertEquals(null, decision.location)
        assertFalse(policy.evaluate(null, GeoPoint(1.0, 2.0, fixTimeMs = now - 8_000_000L), LocationSaveFlow.MANUAL_ADD, now).canSave)
    }

    @Test
    fun autoSave_acceptsOnlyPreciseFresh() {
        val precise = GeoPoint(1.0, 2.0, accuracyMeters = 4f, fixTimeMs = nowMs - 100L)
        assertTrue(policy.evaluate(precise, null, LocationSaveFlow.AUTO_SAVE, nowMs).canSave)
        assertFalse(policy.evaluate(null, precise, LocationSaveFlow.AUTO_SAVE, nowMs).canSave)
        assertFalse(policy.evaluate(null, GeoPoint(1.0, 2.0, fixTimeMs = nowMs - 120_000L), LocationSaveFlow.AUTO_SAVE, nowMs).canSave)
    }

    @Test
    fun futureAndInvalidCoordinatesAreUnavailableEvenFromPreciseProvider() {
        listOf(
            GeoPoint(1.0, 2.0, accuracyMeters = 3f, fixTimeMs = nowMs + 2_001L),
            GeoPoint(Double.NaN, 2.0, accuracyMeters = 3f, fixTimeMs = nowMs),
            GeoPoint(1.0, Double.POSITIVE_INFINITY, accuracyMeters = 3f, fixTimeMs = nowMs),
            GeoPoint(91.0, 2.0, accuracyMeters = 3f, fixTimeMs = nowMs),
            GeoPoint(1.0, -181.0, accuracyMeters = 3f, fixTimeMs = nowMs)
        ).forEach { invalid ->
            assertFalse(policy.evaluate(invalid, null, LocationSaveFlow.MANUAL_ADD, nowMs).canSave)
            assertFalse(policy.evaluate(null, invalid, LocationSaveFlow.MANUAL_ADD, nowMs).canSave)
        }
        assertTrue(policy.evaluate(GeoPoint(1.0, 2.0, accuracyMeters = 3f, fixTimeMs = nowMs + 1_000L), null, LocationSaveFlow.AUTO_SAVE, nowMs).canSave)
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
