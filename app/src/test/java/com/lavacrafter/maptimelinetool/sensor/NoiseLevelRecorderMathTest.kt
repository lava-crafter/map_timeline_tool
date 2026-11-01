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

package com.lavacrafter.maptimelinetool.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoiseLevelRecorderMathTest {
    @Test
    fun constantHalfScalePcmIsAboutMinusSixDbfs() {
        val samples = ShortArray(8) { 16_384 }
        val sumSquares = samples.sumOf { it.toDouble() * it.toDouble() }

        assertEquals(-6.02f, pcm16SumSquaresToDbfs(sumSquares, samples.size)!!, 0.02f)
    }

    @Test
    fun fullScaleAndSilenceUseDigitalFullScaleNotSoundPressureUnits() {
        assertEquals(0f, pcm16SumSquaresToDbfs(Short.MAX_VALUE.toDouble() * Short.MAX_VALUE, 1)!!, 0.001f)
        assertNull(pcm16SumSquaresToDbfs(0.0, 8))
        assertNull(pcm16SumSquaresToDbfs(0.0, 0))
    }
}
