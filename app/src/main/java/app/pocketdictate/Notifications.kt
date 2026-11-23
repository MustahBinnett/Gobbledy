package app.pocketdictate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifications {
    const val CHANNEL_BUTTON = "button"
    const val CHANNEL_DOWNLOAD = "download"
    const val CHANNEL_ALERTS = "alerts"

    const val ID_BUTTON = 1
    const val ID_DOWNLOAD = 2
    const val ID_TAP_TO_START = 3
    const val ID_DOWNLOAD_RESULT = 4

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_BUTTON, context.getString(R.string.channel_button),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) },
                NotificationChannel(
                    CHANNEL_DOWNLOAD, context.getString(R.string.channel_download),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) },
                NotificationChannel(
                    CHANNEL_ALERTS, context.getString(R.string.channel_alerts),
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
            )
        )
    }

    fun openAppIntent(context: Context, startButton: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_START_BUTTON, startButton)
        return PendingIntent.getActivity(
            context, if (startButton) 1 else 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Android doesn't always let a microphone service start in the background
     * (for example straight after boot). When that happens we show this, and
     * tapping it starts the button from the foreground.
     */
    fun postTapToStart(context: Context) {
        val n = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(context.getString(R.string.tap_to_start_title))
            .setContentText(context.getString(R.string.tap_to_start_text))
            .setContentIntent(openAppIntent(context, startButton = true))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_TAP_TO_START, n)
    }

    fun cancel(context: Context, id: Int) =
        context.getSystemService(NotificationManager::class.java).cancel(id)
}
