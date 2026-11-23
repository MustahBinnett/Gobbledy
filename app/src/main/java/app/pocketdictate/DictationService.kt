package app.pocketdictate

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PersistableBundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import app.pocketdictate.engine.Transcriber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground service (type "microphone") that owns the floating button, keeps
 * the speech model loaded, records while the button is held and sends the
 * transcript to the accessibility service for insertion.
 */
class DictationService : Service(), MicButtonView.Listener {

    companion object {
        private const val TAG = "DictationService"
        private const val ACTION_STOP = "app.pocketdictate.STOP"
        private const val MIN_SAMPLES = AudioRecorder.SAMPLE_RATE * 3 / 10 // 0.3 s

        @Volatile private var instance: DictationService? = null
        val isRunning get() = instance != null

        /** Returns false if Android refused to start the service right now. */
        fun start(c: Context): Boolean = try {
            c.startForegroundService(Intent(c, DictationService::class.java))
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForegroundService refused", e)
            false
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, DictationService::class.java))
        }

        /** From the accessibility service: keyboard area on screen, or null when hidden. */
        fun onKeyboardChanged(bounds: android.graphics.Rect?) {
            val s = instance ?: return
            s.main.post { s.keyboard = bounds; s.updateVisibility() }
        }

        /** Called after a (re)download so a running service picks up the new file. */
        fun onModelChanged() {
            val s = instance ?: return
            s.main.post { s.loadModel() }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val engine: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "pd-engine") }
    private var transcriber: Transcriber? = null // engine thread only

    private lateinit var wm: WindowManager
    private var button: MicButtonView? = null
    private lateinit var lp: WindowManager.LayoutParams
    private var recorder: AudioRecorder? = null
    private var state = ButtonState.LOADING
    private var started = false

    /** Where you put the button; it is only lifted temporarily above the keyboard. */
    private var savedX = 0
    private var savedY = 0
    private var keyboard: android.graphics.Rect? = null
    private var dragging = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.BUTTON_SIZE_DP) applySize()
        if (key == Prefs.ONLY_WHILE_TYPING) updateVisibility()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!startAsForeground()) {
            Notifications.postTapToStart(this)
            stopSelf()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast(R.string.toast_need_overlay)
            stopSelf()
            return
        }
        started = true
        instance = this
        Notifications.cancel(this, Notifications.ID_TAP_TO_START)
        wm = getSystemService(WindowManager::class.java)
        keyboard = DictateAccessibilityService.instance?.keyboardBounds()
        addOverlay()
        Prefs.of(this).registerOnSharedPreferenceChangeListener(prefListener)
        loadModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.set(this, Prefs.BUTTON_ENABLED, false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        if (started) {
            Prefs.of(this).unregisterOnSharedPreferenceChangeListener(prefListener)
            recorder?.cancel(); recorder = null
            button?.let { runCatching { wm.removeView(it) } }
            button = null
        }
        engine.execute { transcriber?.close(); transcriber = null }
        engine.shutdown()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- setup

    private fun startAsForeground(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(
                    Notifications.ID_BUTTON, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                // Android 10 takes the type from the manifest.
                startForeground(Notifications.ID_BUTTON, buildNotification())
            }
            true
        } catch (e: Exception) {
            // Android blocks microphone services started from the background
            // (e.g. at boot on Android 14+). The caller shows "tap to start".
            Log.w(TAG, "startForeground refused", e)
            false
        }
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 0, Intent(this, DictationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, Notifications.CHANNEL_BUTTON)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(Notifications.openAppIntent(this))
            .addAction(Notification.Action.Builder(null, getString(R.string.notification_stop), stop).build())
            .build()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun addOverlay() {
        val size = dp(Prefs.buttonSizeDp(this))
        lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE: touching the button never takes focus away from
            // the text field you are typing in.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = resources.displayMetrics
            x = Prefs.of(this@DictationService).getInt(Prefs.POS_X, dm.widthPixels - size - dp(8))
            y = Prefs.of(this@DictationService).getInt(Prefs.POS_Y, dm.heightPixels / 3)
        }
        val v = MicButtonView(this, this)
        v.contentDescription = getString(R.string.button_description)
        button = v
        clampPosition()
        savedX = lp.x; savedY = lp.y
        wm.addView(v, lp)
        setState(state)
        updateVisibility()
    }

    private fun applySize() {
        val size = dp(Prefs.buttonSizeDp(this))
        lp.width = size; lp.height = size
        updateVisibility()
    }

    /**
     * Shows or hides the button. With "Only show while typing" on, it appears
     * while the keyboard is up and is lifted above the keyboard if it would be
     * hidden behind it. It never disappears mid-recording or mid-transcription.
     * Without Accessibility we can't see the keyboard, so it always shows.
     */
    fun updateVisibility() {
        val b = button ?: return
        val busy = state == ButtonState.RECORDING || state == ButtonState.TRANSCRIBING || dragging
        val show = !Prefs.onlyWhileTyping(this) || keyboard != null || busy ||
            DictateAccessibilityService.instance == null
        b.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        lp.flags = if (show) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (!dragging) {
            lp.x = savedX; lp.y = savedY
            val kb = keyboard
            if (kb != null && lp.y + lp.height > kb.top - dp(8)) {
                lp.y = kb.top - lp.height - dp(12)
            }
        }
        clampPosition()
        runCatching { wm.updateViewLayout(b, lp) }
    }

    private fun clampPosition() {
        val dm = resources.displayMetrics
        lp.x = lp.x.coerceIn(0, maxOf(0, dm.widthPixels - lp.width))
        lp.y = lp.y.coerceIn(0, maxOf(0, dm.heightPixels - lp.height))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (button == null) return
        updateVisibility()
    }

    private fun setState(s: ButtonState) {
        val wasBusy = state == ButtonState.RECORDING || state == ButtonState.TRANSCRIBING
        state = s
        button?.state = s
        // A keyboard that closed mid-dictation hides the button once we're done.
        if (wasBusy && s != ButtonState.RECORDING && s != ButtonState.TRANSCRIBING) updateVisibility()
    }

    fun loadModel() {
        if (state == ButtonState.RECORDING || state == ButtonState.TRANSCRIBING) {
            main.postDelayed({ loadModel() }, 1000); return
        }
        setState(ButtonState.LOADING)
        engine.execute {
            transcriber?.close(); transcriber = null
            if (!ModelFiles.isReady(this)) {
                main.post { setState(ButtonState.NO_MODEL) }
                return@execute
            }
            try {
                val t0 = System.currentTimeMillis()
                transcriber = Transcriber.load(ModelFiles.model(this))
                Log.i(TAG, "Model loaded in ${System.currentTimeMillis() - t0} ms")
                main.post { setState(ButtonState.READY) }
            } catch (e: Throwable) {
                Log.e(TAG, "Model load failed", e)
                main.post {
                    setState(ButtonState.ERROR)
                    toast(getString(R.string.toast_model_load_failed, e.message ?: ""))
                }
            }
        }
    }

    // ------------------------------------------------------- button events

    override fun onPressDown() {
        if (state != ButtonState.READY) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast(R.string.toast_need_mic); return
        }
        val r = AudioRecorder()
        if (r.start()) recorder = r else toast(R.string.toast_mic_busy)
    }

    override fun onHoldConfirmed() {
        when (state) {
            ButtonState.READY -> if (recorder != null) {
                setState(ButtonState.RECORDING)
                buzz()
            }
            ButtonState.NO_MODEL -> toast(R.string.toast_no_model)
            ButtonState.LOADING -> toast(R.string.toast_loading)
            ButtonState.TRANSCRIBING -> toast(R.string.toast_busy)
            ButtonState.ERROR -> {
                toast(R.string.toast_retry_load); loadModel()
            }
            ButtonState.RECORDING -> {}
        }
    }

    override fun onRelease(holdConfirmed: Boolean) {
        val r = recorder ?: run {
            if (!holdConfirmed && state == ButtonState.NO_MODEL) toast(R.string.toast_no_model)
            return
        }
        recorder = null
        if (!holdConfirmed || state != ButtonState.RECORDING) {
            r.cancel()
            toast(R.string.toast_hold_hint)
            return
        }
        val pcm = r.stop()
        buzz()
        if (pcm.size < MIN_SAMPLES) {
            setState(ButtonState.READY)
            toast(R.string.toast_hold_hint)
            return
        }
        setState(ButtonState.TRANSCRIBING)
        engine.execute {
            val result = try {
                Result.success(transcriber?.transcribe(pcm) ?: error("Model not loaded"))
            } catch (e: Throwable) {
                Log.e(TAG, "Transcription failed", e)
                Result.failure(e)
            }
            main.post {
                setState(if (transcriber != null) ButtonState.READY else ButtonState.NO_MODEL)
                result.onSuccess { deliver(it) }
                    .onFailure { toast(getString(R.string.toast_transcribe_failed, it.message ?: "")) }
            }
        }
    }

    override fun onDragStart() {
        dragging = true
        recorder?.cancel(); recorder = null
        if (state == ButtonState.RECORDING) setState(ButtonState.READY)
    }

    override fun onDragBy(dx: Int, dy: Int) {
        val b = button ?: return
        lp.x += dx; lp.y += dy
        clampPosition()
        runCatching { wm.updateViewLayout(b, lp) }
    }

    override fun onDragEnd() {
        dragging = false
        savedX = lp.x; savedY = lp.y
        Prefs.of(this).edit().putInt(Prefs.POS_X, lp.x).putInt(Prefs.POS_Y, lp.y).apply()
        updateVisibility()
    }

    // ------------------------------------------------------------- output

    private fun deliver(raw: String) {
        var text = raw.trim()
        if (text.isEmpty()) {
            toast(R.string.toast_nothing_heard); return
        }
        if (Prefs.auSpelling(this)) {
            text = runCatching { SpellingConverter.get(this).convert(text) }.getOrDefault(text)
        }
        val inserted = DictateAccessibilityService.instance
            ?.insertText(text, Prefs.alwaysPaste(this)) {
                copyToClipboard(text)
                toast(R.string.toast_copied)
            } ?: false
        if (!inserted) {
            copyToClipboard(text)
            toast(
                if (DictateAccessibilityService.instance == null) R.string.toast_copied_no_accessibility
                else R.string.toast_copied
            )
        }
    }

    private fun copyToClipboard(text: String) {
        val clip = ClipData.newPlainText("Gobbledy", text)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
            }
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    private fun buzz() {
        val vib: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        if (vib.hasVibrator()) vib.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun toast(resId: Int) = toast(getString(resId))
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
