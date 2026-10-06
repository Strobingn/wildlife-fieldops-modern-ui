package com.strobingn.wildlifefieldops.weather

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

@Singleton
class WeatherAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Weather alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Rain, heavy rain, wind, and snow for the next two days."
        }
        manager.createNotificationChannel(channel)
    }

    /** @return true when a notification was posted. */
    fun notify(alerts: List<WeatherAlert>): Boolean {
        if (alerts.isEmpty()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return runCatching {
            ensureChannel()
            val text = alerts.joinToString("\n") { it.summary }
            val launch = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_weather)
                .setContentTitle(if (alerts.size == 1) "Weather alert" else "Weather alerts")
                .setContentText(alerts.first().summary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        }.getOrElse {
            Log.e(TAG, "Could not post weather alert", it)
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "fieldops_weather_alerts"
        private const val NOTIFICATION_ID = 5108
        private const val TAG = "WeatherAlerts"
    }
}
