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

package com.lavacrafter.maptimelinetool.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.appGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val ACTION_QUICK_ADD = "com.lavacrafter.maptimelinetool.notification.action.QUICK_ADD"

private const val QUICK_ADD_LOCATION_TIMEOUT_MS = 5_000L
private const val QUICK_ADD_RESULT_CHANNEL_ID = "quick_add_result_channel_v2"

class QuickAddService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_QUICK_ADD) {
            serviceScope.launch {
                handleQuickAdd()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handleQuickAdd() {
        if (!hasLocationPermission()) {
            showToast(getString(R.string.toast_location_failed))
            return
        }

        startAsForeground(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

        val graph = appGraph()
        val location = try {
            graph.locationProvider.getBestEffortLocation(QUICK_ADD_LOCATION_TIMEOUT_MS)
        } catch (_: Exception) {
            null
        }

        if (location == null) {
            showToast(getString(R.string.toast_location_failed))
            return
        }

        val eventTime = System.currentTimeMillis()
        val timestamp = location.fixTimeMs
            ?.takeIf { it > 0L }
            ?.let { maxOf(eventTime, it) }
            ?: eventTime
        val title = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

        try {
            graph.pointWriteUseCase.addPointWithTags(
                title = title,
                note = "",
                location = location,
                timestamp = timestamp,
                tagIds = graph.settingsManagementUseCase.getDefaultTagIds().toSet()
            )
            showToast(getString(R.string.toast_point_added))
            vibrateOnce()
            showAddNotification()
        } catch (_: Exception) {
            showToast(getString(R.string.toast_location_failed))
        }
    }

    private fun startAsForeground(serviceType: Int) {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddNotification() {
        val channelId = QUICK_ADD_RESULT_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.toast_point_added))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setTimeoutAfter(2000L)
            .build()

        androidx.core.app.NotificationManagerCompat.from(this).notify(2002, notification)
    }

    private fun vibrateOnce() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun buildNotification(): Notification {
        val channelId = NOTIFICATION_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, QuickAddReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "quick_add_channel"
    }
}