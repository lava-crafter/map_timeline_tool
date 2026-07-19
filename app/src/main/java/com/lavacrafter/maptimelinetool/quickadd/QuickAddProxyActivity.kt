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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lavacrafter.maptimelinetool.MainActivity
import com.lavacrafter.maptimelinetool.appGraph

class QuickAddProxyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchQuickAdd()
        finish()
    }

    private fun dispatchQuickAdd() {
        val stateStore = appGraph().quickAddStateStore
        val blockedOutcome = when {
            !stateStore.isInitialized() -> QuickAddOutcome.SetupRequired
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> {
                QuickAddOutcome.LocationPermissionMissing
            }
            !canShowQuickAddNotifications() -> QuickAddOutcome.NotificationUnavailable
            stateStore.snapshot() is QuickAddTaskState.Running -> QuickAddOutcome.AlreadyRunning
            else -> null
        }
        if (blockedOutcome != null) {
            if (blockedOutcome != QuickAddOutcome.AlreadyRunning) stateStore.record(blockedOutcome)
            requestQuickAddTileRefresh()
            publishQuickAddFeedback(blockedOutcome)
            if (blockedOutcome == QuickAddOutcome.SetupRequired ||
                blockedOutcome == QuickAddOutcome.LocationPermissionMissing ||
                blockedOutcome == QuickAddOutcome.NotificationUnavailable
            ) {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(ACTION_OPEN_QUICK_ADD_SETUP)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
            return
        }

        try {
            ContextCompat.startForegroundService(this, Intent(this, QuickAddForegroundService::class.java))
        } catch (_: Exception) {
            val outcome = QuickAddOutcome.ForegroundServiceStartFailure
            stateStore.record(outcome)
            requestQuickAddTileRefresh()
            publishQuickAddFeedback(outcome)
        }
    }
}
