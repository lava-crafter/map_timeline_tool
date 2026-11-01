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

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationDeadlineTest {
    @Test
    fun nestedRequestsShareOneDeadlineRatherThanResettingTheirBudget() {
        var nowMs = 1_000L
        val deadline = LocationDeadline.after(timeoutMs = 100L, monotonicNowMs = { nowMs })

        assertEquals(100L, deadline.remainingMs())
        assertEquals(60L, deadline.preferredBudgetMs())
        nowMs += 50L
        assertEquals(50L, deadline.remainingMs())
        assertEquals(30L, deadline.preferredBudgetMs())
        nowMs += 51L
        assertEquals(0L, deadline.remainingMs())
        assertEquals(0L, deadline.preferredBudgetMs())
    }

    @Test
    fun nonpositiveTimeoutStillHasOneMillisecondOfBudget() {
        val deadline = LocationDeadline.after(timeoutMs = -10L, monotonicNowMs = { 1_000L })

        assertEquals(1L, deadline.remainingMs())
        assertEquals(1L, deadline.preferredBudgetMs())
    }
}
