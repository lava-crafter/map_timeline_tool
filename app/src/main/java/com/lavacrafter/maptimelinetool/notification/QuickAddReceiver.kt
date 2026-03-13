package com.lavacrafter.maptimelinetool.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.appGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuickAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val graph = context.appGraph()
                val location = graph.locationProvider.getFreshLocation(5000L)
                    ?: run {
                        showToast(context, context.getString(R.string.toast_location_failed))
                        return@launch
                    }

                val timestamp = System.currentTimeMillis()
                val title = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                graph.pointWriteUseCase.addPointWithTags(
                    title = title,
                    note = "",
                    location = location,
                    timestamp = timestamp,
                    tagIds = graph.settingsManagementUseCase.getDefaultTagIds().toSet()
                )
                showToast(context, context.getString(R.string.toast_point_added))
                vibrateOnce(context)
                showAddNotification(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private fun showToast(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

private fun showAddNotification(context: Context) {
    val channelId = "quick_add_result_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            channelId,
            context.getString(R.string.notification_channel_name),
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
        }
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.toast_point_added))
        .setContentText(context.getString(R.string.toast_point_added))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setTimeoutAfter(2000L)
        .build()

    NotificationManagerCompat.from(context).notify(2002, notification)
}

private fun vibrateOnce(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(50)
    }
}
