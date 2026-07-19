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
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lavacrafter.maptimelinetool.MainActivity
import com.lavacrafter.maptimelinetool.R

const val QUICK_ADD_FOREGROUND_NOTIFICATION_ID = 1002
private const val QUICK_ADD_RESULT_NOTIFICATION_ID = 2002
private const val QUICK_ADD_FOREGROUND_CHANNEL_ID = "quick_add_location_service_v1"
private const val QUICK_ADD_RESULT_CHANNEL_ID = "quick_add_result_channel_v2"
private const val LEGACY_NOTIFICATION_ID = 1001
private const val LEGACY_CHANNEL_ID = "quick_add_channel"

fun Context.ensureQuickAddNotificationChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            QUICK_ADD_FOREGROUND_CHANNEL_ID,
            getString(R.string.quick_add_location_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.quick_add_location_channel_desc)
            setSound(null, null)
            enableVibration(false)
        }
    )
    manager.createNotificationChannel(
        NotificationChannel(
            QUICK_ADD_RESULT_CHANNEL_ID,
            getString(R.string.quick_add_result_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.quick_add_result_channel_desc)
            setSound(null, null)
            enableVibration(false)
        }
    )
}

fun Context.canShowQuickAddNotifications(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
    ensureQuickAddNotificationChannels()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = getSystemService(NotificationManager::class.java)
            .getNotificationChannel(QUICK_ADD_FOREGROUND_CHANNEL_ID)
        if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) return false
    }
    return true
}

fun Context.buildQuickAddForegroundNotification(): Notification {
    ensureQuickAddNotificationChannels()
    val contentIntent = PendingIntent.getActivity(
        this,
        1002,
        Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_QUICK_ADD_SETUP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(this, QUICK_ADD_FOREGROUND_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_quick_add_tile)
        .setContentTitle(getString(R.string.quick_add_notification_title))
        .setContentText(getString(R.string.quick_add_notification_locating))
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(contentIntent)
        .build()
}

fun Context.publishQuickAddFeedback(outcome: QuickAddOutcome) {
    if (outcome is QuickAddOutcome.Success) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50L)
        }
        return
    }
    if (outcome == QuickAddOutcome.AlreadyRunning || !canShowQuickAddNotifications()) return
    val notification = NotificationCompat.Builder(this, QUICK_ADD_RESULT_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_quick_add_tile)
        .setContentTitle(getString(R.string.quick_add_notification_title))
        .setContentText(getString(outcome.messageResId()))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setTimeoutAfter(5_000L)
        .build()
    try {
        NotificationManagerCompat.from(this).notify(QUICK_ADD_RESULT_NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
        // Permission or notification policy may change after the preflight check.
    }
}

fun Context.cleanupLegacyQuickAddNotification() {
    NotificationManagerCompat.from(this).cancel(LEGACY_NOTIFICATION_ID)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        getSystemService(NotificationManager::class.java).deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }
}

fun QuickAddOutcome.messageResId(): Int = when (this) {
    is QuickAddOutcome.Success -> R.string.quick_add_result_success
    QuickAddOutcome.SetupRequired -> R.string.quick_add_result_setup_required
    QuickAddOutcome.LocationPermissionMissing -> R.string.quick_add_result_permission_required
    QuickAddOutcome.NotificationUnavailable -> R.string.quick_add_result_notification_required
    QuickAddOutcome.LocationServicesDisabled -> R.string.quick_add_result_location_disabled
    QuickAddOutcome.NoLocationProvider -> R.string.quick_add_result_no_provider
    QuickAddOutcome.LocationTimeout -> R.string.quick_add_result_timeout
    QuickAddOutcome.LocationTooInaccurate -> R.string.quick_add_result_inaccurate
    QuickAddOutcome.AlreadyRunning -> R.string.quick_add_result_already_running
    QuickAddOutcome.StorageFailure -> R.string.quick_add_result_storage_failure
    QuickAddOutcome.ForegroundServiceStartFailure -> R.string.quick_add_result_start_failure
    QuickAddOutcome.UnexpectedFailure -> R.string.quick_add_result_unexpected_failure
}
