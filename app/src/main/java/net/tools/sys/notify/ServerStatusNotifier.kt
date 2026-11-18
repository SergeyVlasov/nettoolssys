package net.tools.sys.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import net.tools.sys.MainActivity
import net.tools.sys.R
import net.tools.sys.monitoring.AvailabilityStatus

class ServerStatusNotifier(private val context: Context) {

    fun showStatusChange(serverId: String, serverName: String, status: AvailabilityStatus) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(manager)
        val statusLabel = context.getString(
            when (status) {
                AvailabilityStatus.AVAILABLE -> R.string.status_available
                AvailabilityStatus.UNAVAILABLE -> R.string.status_unavailable
            },
        )
        val body = context.getString(
            when (status) {
                AvailabilityStatus.AVAILABLE -> R.string.notify_recovered_body
                AvailabilityStatus.UNAVAILABLE -> R.string.notify_unavailable_body
            },
            serverName,
            statusLabel,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hourglass)
            .setContentTitle(context.getString(R.string.notify_status_title, serverName, statusLabel))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        manager.notify(notificationId(serverId), notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notify_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(serverId: String): Int = serverId.hashCode()

    private companion object {
        const val CHANNEL_ID = "server_status"
    }
}
