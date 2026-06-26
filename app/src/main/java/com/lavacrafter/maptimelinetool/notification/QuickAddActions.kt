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
import com.lavacrafter.maptimelinetool.MainActivity
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.appGraph
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveFlow
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveQuality
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val ACTION_QUICK_ADD = "com.lavacrafter.maptimelinetool.notification.action.QUICK_ADD"

private const val QUICK_ADD_LOCATION_TIMEOUT_MS = 5_000L
private const val QUICK_ADD_NOTIFICATION_ID = 1001
private const val QUICK_ADD_RESULT_NOTIFICATION_ID = 2002
private const val QUICK_ADD_NOTIFICATION_CHANNEL_ID = "quick_add_channel"
private const val QUICK_ADD_RESULT_CHANNEL_ID = "quick_add_result_channel_v2"

internal fun Context.showQuickAddNotification() {
    if (!areNotificationsEnabledCompat()) {
        return
    }

    val notification = buildQuickAddNotification()
    NotificationManagerCompat.from(this).notify(QUICK_ADD_NOTIFICATION_ID, notification)
}

internal fun Context.cancelQuickAddNotification() {
    NotificationManagerCompat.from(this).cancel(QUICK_ADD_NOTIFICATION_ID)
}

internal fun Context.syncQuickAddNotification(enabled: Boolean) {
    if (enabled && areNotificationsEnabledCompat()) {
        showQuickAddNotification()
    } else {
        cancelQuickAddNotification()
    }
}

internal suspend fun Context.performQuickAdd() {
    if (!hasLocationPermission()) {
        showToast(getString(R.string.toast_permission_denied))
        return
    }

    val graph = appGraph()
    val decision = try {
        graph.locationSaveResolver.resolve(LocationSaveFlow.QUICK_ADD, QUICK_ADD_LOCATION_TIMEOUT_MS)
    } catch (_: Exception) {
        null
    }
    val location = decision?.location

    if (decision == null || !decision.canSave || location == null) {
        showToast(getString(R.string.toast_location_unavailable_save_failed))
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
        val message = getString(messageResForQuickAdd(decision.quality))
        showToast(message)
        vibrateOnce()
        showQuickAddResultNotification(message)
    } catch (_: Exception) {
        showToast(getString(R.string.toast_location_unavailable_save_failed))
    }
}

private fun Context.showQuickAddResultNotification(message: String) {
    if (!areNotificationsEnabledCompat()) {
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

    NotificationManagerCompat.from(this).notify(QUICK_ADD_RESULT_NOTIFICATION_ID, notification)
}

private fun Context.buildQuickAddNotification(): Notification {
    val channelId = QUICK_ADD_NOTIFICATION_CHANNEL_ID
    ensureNotificationChannel(
        channelId = channelId,
        nameResId = R.string.notification_channel_name,
        descriptionResId = R.string.notification_channel_desc
    )

    val intent = Intent(this, MainActivity::class.java)
        .setAction(ACTION_QUICK_ADD)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val pendingIntent = PendingIntent.getActivity(
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

private fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun Context.areNotificationsEnabledCompat(): Boolean {
    return NotificationManagerCompat.from(this).areNotificationsEnabled()
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

private fun messageResForQuickAdd(quality: LocationSaveQuality): Int {
    return when (quality) {
        LocationSaveQuality.PRECISE_FRESH -> R.string.toast_point_added
        LocationSaveQuality.FRESH_BUT_LOW_ACCURACY -> R.string.toast_point_added_approximate
        LocationSaveQuality.LAST_KNOWN_RECENT -> R.string.toast_point_added_last_known
        LocationSaveQuality.LAST_KNOWN_STALE -> R.string.toast_point_added_stale
        LocationSaveQuality.UNAVAILABLE -> R.string.toast_location_unavailable_save_failed
    }
}
