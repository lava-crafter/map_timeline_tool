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

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.lavacrafter.maptimelinetool.appGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class QuickAddForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var executionJob: Job? = null
    private var foregroundStarted = false
    private var stoppingNormally = false

    override fun onCreate() {
        super.onCreate()
        foregroundStarted = try {
            ServiceCompat.startForeground(
                this,
                QUICK_ADD_FOREGROUND_NOTIFICATION_ID,
                buildQuickAddForegroundNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
            true
        } catch (_: Exception) {
            val outcome = QuickAddOutcome.ForegroundServiceStartFailure
            appGraph().quickAddStateStore.record(outcome)
            requestQuickAddTileRefresh()
            false
        }
        if (!foregroundStarted) stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundStarted) return START_NOT_STICKY
        if (executionJob?.isActive == true) return START_NOT_STICKY
        executionJob = serviceScope.launch {
            val outcome = try {
                appGraph().quickAddCoordinator.execute()
            } catch (_: Exception) {
                QuickAddOutcome.UnexpectedFailure.also { appGraph().quickAddStateStore.record(it) }
            }
            try {
                runCatching { publishQuickAddFeedback(outcome) }
                runCatching { requestQuickAddTileRefresh() }
            } finally {
                stoppingNormally = true
                ServiceCompat.stopForeground(this@QuickAddForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (!stoppingNormally && executionJob?.isActive == true) {
            appGraph().quickAddStateStore.record(QuickAddOutcome.UnexpectedFailure)
            requestQuickAddTileRefresh()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
