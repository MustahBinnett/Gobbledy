package app.pocketdictate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the floating button back after a restart (or app update) if you asked for that. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!Prefs.launchAtBoot(context) || !Prefs.buttonEnabled(context)) return
        if (DictationService.isRunning) return
        // Newer Android versions often refuse to start a microphone service
        // from the background; if so, a notification lets you start it with one tap.
        if (!DictationService.start(context)) Notifications.postTapToStart(context)
    }
}
