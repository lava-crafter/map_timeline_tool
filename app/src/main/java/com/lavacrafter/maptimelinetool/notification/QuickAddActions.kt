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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.appGraph
import com.lavacrafter.maptimelinetool.quickadd.hasRequiredLocationPermissionsForQuickAdd
import com.lavacrafter.maptimelinetool.quickadd.isQuickAddExecutionAllowed
import com.lavacrafter.maptimelinetool.quickadd.QuickAddResult
import com.lavacrafter.maptimelinetool.quickadd.requiresBackgroundLocationForQuickAdd

internal const val ACTION_QUICK_ADD = "com.lavacrafter.maptimelinetool.notification.action.QUICK_ADD"

private const val QUICK_ADD_LOCATION_TIMEOUT_MS = 5_000L
private const val QUICK_ADD_NOTIFICATION_ID = 1001
private const val QUICK_ADD_RESULT_NOTIFICATION_ID = 2002
private const val QUICK_ADD_NOTIFICATION_CHANNEL_ID = "quick_add_channel"
private const val QUICK_ADD_RESULT_CHANNEL_ID = "quick_add_result_channel_v2"

internal fun Context.showQuickAddNotification() {
    if (!canPostNotifications()) {
        return
    }

    val notification = buildQuickAddNotification()
    try {
        NotificationManagerCompat.from(this).notify(QUICK_ADD_NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
        // Notification permission can be revoked after the availability check.
    }
}

internal fun Context.cancelQuickAddNotification() {
    NotificationManagerCompat.from(this).cancel(QUICK_ADD_NOTIFICATION_ID)
}

internal fun Context.syncQuickAddNotification(enabled: Boolean) {
    val isQuickAddAvailable = isQuickAddNotificationAvailable(enabled)
    appGraph().quickAddPassiveLocationUpdater.refreshRegistration(isQuickAddAvailable)
    if (isQuickAddAvailable) {
        showQuickAddNotification()
    } else {
        cancelQuickAddNotification()
    }
}

internal fun Context.isQuickAddNotificationAvailable(enabled: Boolean): Boolean {
    return enabled &&
        areNotificationsEnabledCompat() &&
        hasRequiredLocationPermissionsForQuickAdd(
            sdkInt = Build.VERSION.SDK_INT,
            hasPreciseLocationPermission = hasPreciseLocationPermission(),
            hasBackgroundLocationPermission = hasBackgroundLocationPermissionForQuickAdd()
        )
}

internal suspend fun Context.performQuickAdd() {
    val graph = appGraph()
    val quickAddEnabled = graph.settingsManagementUseCase.getQuickAddNotificationEnabled()
    if (!isQuickAddExecutionAllowed(
            enabled = quickAddEnabled,
            sdkInt = Build.VERSION.SDK_INT,
            hasPreciseLocationPermission = hasPreciseLocationPermission(),
            hasBackgroundLocationPermission = hasBackgroundLocationPermissionForQuickAdd()
        )) {
        syncQuickAddNotification(quickAddEnabled)
        showQuickAddResult(quickAddBlockedReasonResId(quickAddEnabled))
        return
    }

    val clickTimeMs = System.currentTimeMillis()
    val result = try {
        graph.quickAddResolver.savePoint(timeoutMs = QUICK_ADD_LOCATION_TIMEOUT_MS, clickTimeMs = clickTimeMs)
    } catch (_: Exception) {
        null
    }

    when (result) {
        QuickAddResult.SAVED_FROM_RECENT_CACHE,
        QuickAddResult.SAVED_FROM_FRESH_REQUEST -> {
            val messageResId = messageResForQuickAdd(result)
            showToast(getString(messageResId))
            vibrateOnce()
            showQuickAddResultNotification(getString(messageResId))
        }
        QuickAddResult.FAILED_NO_FRESH_ACCURATE_LOCATION -> {
            showQuickAddResult(R.string.toast_quick_add_failed_no_fresh_accurate_location)
        }
        null -> {
            showQuickAddResult(R.string.toast_location_unavailable_save_failed)
        }
    }
}

private fun Context.showQuickAddResult(messageResId: Int) {
    val message = getString(messageResId)
    showToast(message)
    showQuickAddResultNotification(message)
}

private fun Context.showQuickAddResultNotification(message: String) {
    if (!canPostNotifications()) {
        return
    }

    val channelId = QUICK_ADD_RESULT_CHANNEL_ID
    ensureNotificationChannel(
        channelId = channelId,
        nameResId = R.string.notification_channel_name,
        descriptionResId = R.string.notification_channel_desc
    )

    val notification = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setTimeoutAfter(2000L)
        .build()

    try {
        NotificationManagerCompat.from(this).notify(QUICK_ADD_RESULT_NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
        // Notification permission can be revoked after the availability check.
    }
}

private fun Context.buildQuickAddNotification(): Notification {
    val channelId = QUICK_ADD_NOTIFICATION_CHANNEL_ID
    ensureNotificationChannel(
        channelId = channelId,
        nameResId = R.string.notification_channel_name,
        descriptionResId = R.string.notification_channel_desc
    )

    val intent = Intent(this, QuickAddReceiver::class.java)
        .setAction(ACTION_QUICK_ADD)
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

private fun Context.showToast(message: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}

private fun Context.vibrateOnce() {
    val vibrator = getSystemService(Vibrator::class.java) ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(50)
    }
}

private fun Context.hasPreciseLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun Context.hasBackgroundLocationPermissionForQuickAdd(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return true
    }
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun Context.areNotificationsEnabledCompat(): Boolean {
    return NotificationManagerCompat.from(this).areNotificationsEnabled()
}

private fun Context.canPostNotifications(): Boolean {
    if (!areNotificationsEnabledCompat()) return false
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun Context.ensureNotificationChannel(channelId: String, nameResId: Int, descriptionResId: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return
    }

    val channel = NotificationChannel(
        channelId,
        getString(nameResId),
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = getString(descriptionResId)
        setSound(null, null)
        enableVibration(false)
    }
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}

private fun messageResForQuickAdd(result: QuickAddResult): Int {
    return when (result) {
        QuickAddResult.SAVED_FROM_RECENT_CACHE -> R.string.toast_quick_add_saved_from_cache
        QuickAddResult.SAVED_FROM_FRESH_REQUEST -> R.string.toast_quick_add_saved_from_fresh
        QuickAddResult.FAILED_NO_FRESH_ACCURATE_LOCATION -> R.string.toast_quick_add_failed_no_fresh_accurate_location
    }
}

private fun Context.quickAddBlockedReasonResId(enabled: Boolean): Int {
    return when {
        !enabled -> R.string.toast_location_unavailable_save_failed
        !hasPreciseLocationPermission() -> R.string.toast_quick_add_precise_permission_required
        requiresBackgroundLocationForQuickAdd(Build.VERSION.SDK_INT) && !hasBackgroundLocationPermissionForQuickAdd() -> {
            R.string.toast_quick_add_background_permission_required
        }
        else -> R.string.toast_location_unavailable_save_failed
    }
}
