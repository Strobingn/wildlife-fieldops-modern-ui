package com.strobingn.wildlifefieldops.trapreminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.strobingn.wildlifefieldops.MainActivity
import com.strobingn.wildlifefieldops.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Trap check reminders on their own channel, separate from weather alerts. */
@Singleton
class TrapReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Trap check reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "A reminder before a trap check is due, and again when it is due."
        }
        manager.createNotificationChannel(channel)
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** @return true when posted. One notification per trap, replaced by the next stage. */
    fun post(trapRowId: String, title: String, body: String): Boolean {
        if (!canPost()) return false
        return runCatching {
            ensureChannel()
            val launch = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_weather)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(TAG, notificationId(trapRowId), notification)
            true
        }.getOrElse {
            Log.e(TAG, "Could not post trap check reminder", it)
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "fieldops_trap_check_reminders"
        private const val TAG = "TrapReminders"
        private const val REQUEST_CODE = 5208
        fun notificationId(trapRowId: String): Int = 52_000 + (trapRowId.hashCode() and 0x0FFF)
    }
}
