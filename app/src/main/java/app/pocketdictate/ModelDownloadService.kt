package app.pocketdictate

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the model in a foreground service so it keeps going if you leave
 * the app. Data goes to a ".part" file; if the download is interrupted, the
 * next attempt asks the server for the remaining bytes only (HTTP Range).
 */
class ModelDownloadService : Service() {

    companion object {
        private const val TAG = "ModelDownload"
        private const val ACTION_START = "app.pocketdictate.download.START"
        private const val ACTION_CANCEL = "app.pocketdictate.download.CANCEL"
        private const val MAX_ATTEMPTS = 6

        fun start(c: Context) {
            c.startForegroundService(Intent(c, ModelDownloadService::class.java).setAction(ACTION_START))
        }

        fun cancel(c: Context) {
            c.startService(Intent(c, ModelDownloadService::class.java).setAction(ACTION_CANCEL))
        }
    }

    @Volatile private var cancelled = false
    @Volatile private var connection: HttpURLConnection? = null
    private var worker: Thread? = null
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelled = true
                connection?.disconnect()
                if (worker?.isAlive != true) stopSelf()
            }
            else -> {
                try {
                    startForeground(
                        Notifications.ID_DOWNLOAD, progressNotification(0, 0),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Could not start foreground", e)
                    DownloadState.set(DownloadState.Failed(getString(R.string.download_failed_start)))
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (worker?.isAlive != true) {
                    cancelled = false
                    worker = Thread({ runDownload() }, "pd-download").also { it.start() }
                }
            }
        }
        return START_NOT_STICKY
    }

    // Android 15+ limits dataSync services to 6 hours a day.
    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelled = true
        connection?.disconnect()
        DownloadState.set(DownloadState.Failed(getString(R.string.download_failed_timeout)))
        stopSelf()
    }

    private fun runDownload() {
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketDictate:download")
        wakeLock.acquire(3 * 60 * 60 * 1000L)
        try {
            if (ModelFiles.isReady(this)) {
                DownloadState.set(DownloadState.Done)
                return
            }
            var attempt = 0
            while (true) {
                try {
                    downloadOnce()
                    break
                } catch (e: IOException) {
                    if (cancelled) throw e
                    attempt++
                    Log.w(TAG, "Attempt $attempt failed", e)
                    if (attempt >= MAX_ATTEMPTS) throw e
                    Thread.sleep(2000L * (1 shl (attempt - 1)).coerceAtMost(16))
                }
            }
            verifyAndInstall()
        } catch (e: Exception) {
            if (cancelled) {
                DownloadState.set(DownloadState.Idle)
            } else {
                Log.e(TAG, "Download failed", e)
                val msg = e.message ?: e.javaClass.simpleName
                DownloadState.set(DownloadState.Failed(getString(R.string.download_failed_detail, msg)))
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun downloadOnce() {
        val part = ModelFiles.partial(this)
        var have = part.length()
        if (have > ModelSpec.SIZE_BYTES) {
            part.delete(); have = 0
        }
        if (have == ModelSpec.SIZE_BYTES) return

        val free = StatFs(filesDir.absolutePath).availableBytes
        val needed = ModelSpec.SIZE_BYTES - have + 50L * 1024 * 1024
        if (free < needed) {
            throw IOException(getString(R.string.download_failed_space, (needed - free) / (1024 * 1024) + 1))
        }

        val conn = (URL(ModelSpec.URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PocketDictate/${BuildConfig.VERSION_NAME} (Android)")
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        connection = conn
        try {
            val append = when (conn.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false // server ignored Range: start again
                416 -> { part.delete(); throw IOException("Server rejected resume; restarting") }
                else -> throw IOException("HTTP ${conn.responseCode}")
            }
            if (!append) have = 0
            DownloadState.set(DownloadState.Running(have, ModelSpec.SIZE_BYTES))
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (cancelled) throw IOException("Cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        have += n
                        reportProgress(have)
                    }
                }
            }
            if (have != ModelSpec.SIZE_BYTES) throw IOException("Connection closed early ($have bytes)")
        } finally {
            conn.disconnect()
            connection = null
        }
    }

    private fun reportProgress(have: Long) {
        val now = System.currentTimeMillis()
        if (now - lastNotified < 500) return
        lastNotified = now
        DownloadState.set(DownloadState.Running(have, ModelSpec.SIZE_BYTES))
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.ID_DOWNLOAD, progressNotification(have, ModelSpec.SIZE_BYTES))
    }

    private fun verifyAndInstall() {
        DownloadState.set(DownloadState.Verifying)
        val part = ModelFiles.partial(this)
        val digest = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(part, "r").use { f ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                if (cancelled) throw IOException("Cancelled")
                val n = f.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (hex != ModelSpec.SHA256) {
            part.delete()
            throw IOException(getString(R.string.download_failed_hash))
        }
        val target: File = ModelFiles.model(this)
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not move the model into place")
        DownloadState.set(DownloadState.Done)
        DictationService.onModelChanged()
    }

    private fun progressNotification(done: Long, total: Long): Notification {
        val pct = if (total > 0) (done * 100 / total).toInt() else 0
        return Notification.Builder(this, Notifications.CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.download_notification_title))
            .setContentText(getString(R.string.download_progress, done / 1_048_576, total / 1_048_576))
            .setProgress(100, pct, total == 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(Notifications.openAppIntent(this))
            .build()
    }
}
