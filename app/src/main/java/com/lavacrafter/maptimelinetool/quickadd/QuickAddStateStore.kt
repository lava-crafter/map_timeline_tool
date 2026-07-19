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

import android.annotation.SuppressLint
import android.content.Context

sealed interface QuickAddTaskState {
    data object Idle : QuickAddTaskState
    data class Running(val startedAtMs: Long) : QuickAddTaskState
    data class Result(val result: QuickAddStoredResult, val completedAtMs: Long) : QuickAddTaskState
}

enum class QuickAddStoredResult {
    SUCCESS,
    SETUP_REQUIRED,
    PERMISSION_REQUIRED,
    NOTIFICATION_REQUIRED,
    LOCATION_DISABLED,
    NO_PROVIDER,
    LOCATION_TIMEOUT,
    LOCATION_INACCURATE,
    ALREADY_RUNNING,
    STORAGE_FAILURE,
    START_FAILURE,
    UNEXPECTED_FAILURE
}

interface QuickAddExecutionState {
    fun tryStart(): Boolean
    fun record(outcome: QuickAddOutcome)
}

@SuppressLint("ApplySharedPref")
class QuickAddStateStore(
    context: Context,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val runningStaleAfterMs: Long = RUNNING_STALE_AFTER_MS,
    private val resultVisibleForMs: Long = RESULT_VISIBLE_FOR_MS
) : QuickAddExecutionState {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun isInitialized(): Boolean = preferences.getBoolean(KEY_INITIALIZED, false)

    fun setInitialized(initialized: Boolean) {
        preferences.edit().putBoolean(KEY_INITIALIZED, initialized).apply()
    }

    fun snapshot(nowMs: Long = wallClockMs()): QuickAddTaskState = synchronized(lock) {
        readEffectiveState(nowMs)
    }

    override fun tryStart(): Boolean = tryStartAt(wallClockMs())

    internal fun tryStartAt(nowMs: Long): Boolean = synchronized(lock) {
        if (readEffectiveState(nowMs) is QuickAddTaskState.Running) {
            return false
        }
        preferences.edit()
            .putString(KEY_STATE, STATE_RUNNING)
            .putLong(KEY_TIMESTAMP, nowMs)
            .remove(KEY_RESULT)
            .commit()
    }

    override fun record(outcome: QuickAddOutcome) {
        recordOutcomeAt(outcome, wallClockMs())
    }

    internal fun recordOutcomeAt(outcome: QuickAddOutcome, nowMs: Long) {
        if (outcome == QuickAddOutcome.AlreadyRunning) return
        record(outcome.toStoredResult(), nowMs)
    }

    fun record(result: QuickAddStoredResult, nowMs: Long = wallClockMs()) = synchronized(lock) {
        preferences.edit()
            .putString(KEY_STATE, STATE_RESULT)
            .putString(KEY_RESULT, result.name)
            .putLong(KEY_TIMESTAMP, nowMs)
            .commit()
        Unit
    }

    fun clear() = synchronized(lock) {
        clearTaskState()
    }

    private fun readEffectiveState(nowMs: Long): QuickAddTaskState {
        val timestamp = preferences.getLong(KEY_TIMESTAMP, 0L)
        return when (preferences.getString(KEY_STATE, STATE_IDLE)) {
            STATE_RUNNING -> {
                if (timestamp <= 0L || nowMs < timestamp || nowMs - timestamp > runningStaleAfterMs) {
                    clearTaskState()
                    QuickAddTaskState.Idle
                } else {
                    QuickAddTaskState.Running(timestamp)
                }
            }
            STATE_RESULT -> {
                val result = preferences.getString(KEY_RESULT, null)
                    ?.let { runCatching { QuickAddStoredResult.valueOf(it) }.getOrNull() }
                if (result == null || timestamp <= 0L || nowMs < timestamp || nowMs - timestamp > resultVisibleForMs) {
                    clearTaskState()
                    QuickAddTaskState.Idle
                } else {
                    QuickAddTaskState.Result(result, timestamp)
                }
            }
            else -> QuickAddTaskState.Idle
        }
    }

    private fun clearTaskState() {
        preferences.edit()
            .remove(KEY_STATE)
            .remove(KEY_RESULT)
            .remove(KEY_TIMESTAMP)
            .commit()
    }

    companion object {
        const val PREFS_NAME = "quick_add_device_state"
        const val RUNNING_STALE_AFTER_MS = 60_000L
        const val RESULT_VISIBLE_FOR_MS = 4_000L

        private const val KEY_INITIALIZED = "initialized"
        private const val KEY_STATE = "state"
        private const val KEY_RESULT = "result"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val STATE_IDLE = "idle"
        private const val STATE_RUNNING = "running"
        private const val STATE_RESULT = "result"
    }
}
