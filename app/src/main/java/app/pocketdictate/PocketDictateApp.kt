package app.pocketdictate

import android.app.Application

class PocketDictateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
