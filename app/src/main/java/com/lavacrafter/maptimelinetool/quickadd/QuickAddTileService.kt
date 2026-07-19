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
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.appGraph

class QuickAddTileService : TileService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (appGraph().quickAddStateStore.snapshot() is QuickAddTaskState.Running) {
            updateTile()
            return
        }
        val launch = { launchProxyActivity() }
        if (isLocked) unlockAndRun(launch) else launch()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchProxyActivity() {
        val intent = Intent(this, QuickAddProxyActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val state = appGraph().quickAddStateStore.snapshot()
        val labelRes = when (state) {
            QuickAddTaskState.Idle -> R.string.quick_add_tile_label
            is QuickAddTaskState.Running -> R.string.quick_add_tile_locating
            is QuickAddTaskState.Result -> when (state.result) {
                QuickAddStoredResult.SUCCESS -> R.string.quick_add_tile_success
                QuickAddStoredResult.SETUP_REQUIRED,
                QuickAddStoredResult.PERMISSION_REQUIRED,
                QuickAddStoredResult.NOTIFICATION_REQUIRED -> R.string.quick_add_tile_setup_required
                else -> R.string.quick_add_tile_failed
            }
        }
        tile.label = getString(labelRes)
        tile.state = if (state is QuickAddTaskState.Running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
        if (state is QuickAddTaskState.Result) {
            val remaining = (QuickAddStateStore.RESULT_VISIBLE_FOR_MS -
                (System.currentTimeMillis() - state.completedAtMs)).coerceAtLeast(0L)
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ updateTile() }, remaining + 50L)
        }
    }
}
