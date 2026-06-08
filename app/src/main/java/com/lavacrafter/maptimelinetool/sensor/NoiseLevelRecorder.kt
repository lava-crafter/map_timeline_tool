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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.sqrt

private const val DEFAULT_SAMPLE_RATE = 16_000
private const val NOISE_CAPTURE_DURATION_SECONDS = 3

suspend fun captureNoiseDb(context: Context): Float? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        return null
    }

    return runCatching {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(DEFAULT_SAMPLE_RATE, channelConfig, audioFormat)
        if (minBufferSize <= 0) return@runCatching null

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            DEFAULT_SAMPLE_RATE,
            channelConfig,
            audioFormat,
            minBufferSize.coerceAtLeast(DEFAULT_SAMPLE_RATE)
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return@runCatching null
        }

        val targetSamples = DEFAULT_SAMPLE_RATE * NOISE_CAPTURE_DURATION_SECONDS
        val readBuffer = ShortArray(minBufferSize / 2)
        var samplesRead = 0
        var sumSquares = 0.0

        try {
            audioRecord.startRecording()
            while (samplesRead < targetSamples) {
                val remaining = targetSamples - samplesRead
                val maxRead = minOf(readBuffer.size, remaining)
                val readCount = audioRecord.read(readBuffer, 0, maxRead)
                if (readCount <= 0) {
                    return@runCatching null
                }
                for (i in 0 until readCount) {
                    val sample = readBuffer[i].toDouble()
                    sumSquares += sample * sample
                }
                samplesRead += readCount
            }
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
        }

        if (samplesRead <= 0) {
            return@runCatching null
        }
        val rms = sqrt(sumSquares / samplesRead)
        if (rms <= 0.0) {
            return@runCatching null
        }
        val noiseDbfs = 20.0 * log10(rms / Short.MAX_VALUE.toDouble())
        if (noiseDbfs.isFinite()) noiseDbfs.toFloat() else null
    }.getOrNull()
}
