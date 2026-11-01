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

package com.lavacrafter.maptimelinetool.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lavacrafter.maptimelinetool.data.PointEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/** The local calendar day and the zone it was computed in; both invalidate "today" ordering. */
data class LocalDay(val date: LocalDate, val zone: ZoneId) {
    fun contains(timestampMs: Long): Boolean = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate() == date

    companion object {
        fun now(): LocalDay = ZoneId.systemDefault().let { zone -> LocalDay(LocalDate.now(zone), zone) }
    }
}

fun buildTodayOrder(points: List<PointEntity>, today: LocalDay = LocalDay.now()): Map<Long, Int> {
    val todayPoints = points.filter { today.contains(it.timestamp) }.sortedBy { it.timestamp }
    return buildMap {
        todayPoints.forEachIndexed { index, point ->
            put(point.id, index + 1)
        }
    }
}

/**
 * Current local day, refreshed at midnight, on clock/time-zone changes and when the UI resumes, so map and list
 * "today" styling never stays on yesterday.
 */
@Composable
fun rememberCurrentLocalDay(): State<LocalDay> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val day = remember { mutableStateOf(LocalDay.now()) }
    fun refresh() {
        val now = LocalDay.now()
        if (now != day.value) day.value = now
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                refresh()
                val now = ZonedDateTime.now(day.value.zone)
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L))
            }
        }
    }
    return day
}
