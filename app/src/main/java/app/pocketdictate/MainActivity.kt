package app.pocketdictate

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import app.pocketdictate.engine.NativeEngine

class MainActivity : Activity() {

    companion object {
        const val EXTRA_START_BUTTON = "start_button"
        private const val REQ_MIC = 1
        private const val REQ_NOTIF = 2
    }

    private lateinit var micStatus: TextView
    private lateinit var micButton: Button
    private lateinit var notifStatus: TextView
    private lateinit var notifButton: Button
    private lateinit var overlayStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var a11yStatus: TextView
    private lateinit var modelStatus: TextView
    private lateinit var modelProgress: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var cancelButton: Button
    private lateinit var showSwitch: Switch
    private lateinit var sizeLabel: TextView

    private val downloadListener: (DownloadState.State) -> Unit = { renderModel(it) }
    private var updatingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyInsets(findViewById(R.id.root))

        micStatus = findViewById(R.id.micStatus)
        micButton = findViewById(R.id.micButton)
        notifStatus = findViewById(R.id.notifStatus)
        notifButton = findViewById(R.id.notifButton)
        overlayStatus = findViewById(R.id.overlayStatus)
        overlayButton = findViewById(R.id.overlayButton)
        a11yStatus = findViewById(R.id.a11yStatus)
        modelStatus = findViewById(R.id.modelStatus)
        modelProgress = findViewById(R.id.modelProgress)
        downloadButton = findViewById(R.id.downloadButton)
        cancelButton = findViewById(R.id.cancelButton)
        showSwitch = findViewById(R.id.showButtonSwitch)
        sizeLabel = findViewById(R.id.sizeLabel)

        micButton.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
        notifButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            }
        }
        overlayButton.setOnClickListener {
            open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.a11yButton).setOnClickListener {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.appInfoButton).setOnClickListener { openAppInfo() }
        findViewById<Button>(R.id.batteryButton).setOnClickListener {
            if (!open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), quiet = true)) openAppInfo()
        }

        findViewById<TextView>(R.id.modelName).text =
            getString(R.string.model_name, ModelSpec.DISPLAY_NAME, ModelSpec.SIZE_BYTES / 1_000_000)
        downloadButton.setOnClickListener { onDownloadClicked() }
        cancelButton.setOnClickListener { ModelDownloadService.cancel(this) }

        showSwitch.setOnCheckedChangeListener { _, on -> if (!updatingUi) setButtonEnabled(on) }

        val seek = findViewById<SeekBar>(R.id.sizeSeek)
        seek.max = Prefs.MAX_SIZE_DP - Prefs.MIN_SIZE_DP
        seek.progress = Prefs.buttonSizeDp(this) - Prefs.MIN_SIZE_DP
        renderSize(Prefs.buttonSizeDp(this))
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                val dp = p + Prefs.MIN_SIZE_DP
                renderSize(dp)
                if (fromUser) Prefs.set(this@MainActivity, Prefs.BUTTON_SIZE_DP, dp)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })

        bindSwitch(R.id.bootSwitch, Prefs.LAUNCH_AT_BOOT, Prefs.launchAtBoot(this))
        bindSwitch(R.id.auSwitch, Prefs.AU_SPELLING, Prefs.auSpelling(this))
        bindSwitch(R.id.pasteSwitch, Prefs.ALWAYS_PASTE, Prefs.alwaysPaste(this))
        findViewById<Button>(R.id.wordListButton).setOnClickListener {
            startActivity(Intent(this, WordListActivity::class.java))
        }

        val engineVersion = runCatching { NativeEngine.nativeVersion() }.getOrDefault("?")
        findViewById<TextView>(R.id.about).text =
            getString(R.string.about_text, BuildConfig.VERSION_NAME, engineVersion)

        findViewById<Button>(R.id.licencesButton).setOnClickListener {
            startActivity(Intent(this, LicencesActivity::class.java))
        }

        handleStartExtra(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStartExtra(intent)
    }

    /** Tapping the "tap to start" notification lands here; start from the foreground. */
    private fun handleStartExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_BUTTON, false) == true) {
            intent.removeExtra(EXTRA_START_BUTTON)
            if (Prefs.buttonEnabled(this) && !DictationService.isRunning) {
                if (DictationService.start(this)) moveTaskToBack(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSetup()
        DownloadState.observe(downloadListener)
    }

    override fun onPause() {
        DownloadState.remove(downloadListener)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED &&
            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        ) {
            // Permanently denied: the only way back is the app's settings page.
            Toast.makeText(this, R.string.toast_mic_settings, Toast.LENGTH_LONG).show()
            openAppInfo()
        }
        refreshSetup()
    }

    // ------------------------------------------------------------ setup ui

    private fun hasMic() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasNotif() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun refreshSetup() {
        val mic = hasMic()
        micStatus.text = status(mic, R.string.status_mic)
        micButton.visibility = if (mic) View.GONE else View.VISIBLE

        val notif = hasNotif()
        notifStatus.text = status(notif, R.string.status_notif)
        notifButton.visibility = if (notif) View.GONE else View.VISIBLE

        val overlay = Settings.canDrawOverlays(this)
        overlayStatus.text = status(overlay, R.string.status_overlay)
        overlayButton.visibility = if (overlay) View.GONE else View.VISIBLE

        a11yStatus.text = status(DictateAccessibilityService.isEnabled(this), R.string.status_a11y)

        updatingUi = true
        showSwitch.isChecked = DictationService.isRunning
        updatingUi = false
    }

    private fun status(ok: Boolean, labelRes: Int) =
        (if (ok) "✅  " else "⚠️  ") + getString(labelRes) + " — " +
            getString(if (ok) R.string.status_done else R.string.status_needed)

    private fun setButtonEnabled(on: Boolean) {
        if (!on) {
            Prefs.set(this, Prefs.BUTTON_ENABLED, false)
            DictationService.stop(this)
            return
        }
        val missing = when {
            !hasMic() -> R.string.toast_need_mic
            !Settings.canDrawOverlays(this) -> R.string.toast_need_overlay
            else -> 0
        }
        if (missing != 0) {
            Toast.makeText(this, missing, Toast.LENGTH_LONG).show()
            updatingUi = true; showSwitch.isChecked = false; updatingUi = false
            return
        }
        Prefs.set(this, Prefs.BUTTON_ENABLED, true)
        if (!DictationService.start(this)) {
            Toast.makeText(this, R.string.toast_start_failed, Toast.LENGTH_LONG).show()
        }
        if (!ModelFiles.isReady(this)) {
            Toast.makeText(this, R.string.toast_no_model, Toast.LENGTH_LONG).show()
        }
    }

    private fun renderSize(dp: Int) {
        sizeLabel.text = getString(R.string.button_size, dp)
    }

    private fun bindSwitch(id: Int, key: String, value: Boolean) {
        val s = findViewById<Switch>(id)
        s.isChecked = value
        s.setOnCheckedChangeListener { _, on -> Prefs.set(this, key, on) }
    }

    // ------------------------------------------------------------ model ui

    private fun renderModel(s: DownloadState.State) {
        val ready = ModelFiles.isReady(this)
        modelProgress.visibility = View.GONE
        cancelButton.visibility = View.GONE
        downloadButton.isEnabled = true
        downloadButton.setText(if (ready) R.string.redownload_model else R.string.download_model)
        when (s) {
            is DownloadState.Running -> {
                modelProgress.visibility = View.VISIBLE
                modelProgress.isIndeterminate = false
                modelProgress.progress = (s.downloaded * 1000 / s.total).toInt()
                modelStatus.text = getString(
                    R.string.model_downloading, s.downloaded / 1_048_576, s.total / 1_048_576,
                    s.downloaded * 100 / s.total
                )
                cancelButton.visibility = View.VISIBLE
                downloadButton.isEnabled = false
            }
            DownloadState.Verifying -> {
                modelProgress.visibility = View.VISIBLE
                modelProgress.isIndeterminate = true
                modelStatus.setText(R.string.model_verifying)
                downloadButton.isEnabled = false
            }
            is DownloadState.Failed -> modelStatus.text = s.message
            else -> {
                val partial = ModelFiles.partial(this).length()
                modelStatus.text = when {
                    ready -> getString(R.string.model_ready)
                    partial > 0 -> getString(R.string.model_partial, partial * 100 / ModelSpec.SIZE_BYTES)
                    else -> getString(R.string.model_missing)
                }
                if (!ready && partial > 0) downloadButton.setText(R.string.resume_download)
            }
        }
    }

    private fun onDownloadClicked() {
        if (ModelFiles.isReady(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.redownload_title)
                .setMessage(R.string.redownload_message)
                .setPositiveButton(R.string.redownload_model) { _, _ ->
                    ModelFiles.deleteAll(this)
                    DictationService.onModelChanged()
                    confirmNetworkThenDownload()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            confirmNetworkThenDownload()
        }
    }

    private fun confirmNetworkThenDownload() {
        if (!hasNotif() && Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
        val cm = getSystemService(ConnectivityManager::class.java)
        if (cm.isActiveNetworkMetered) {
            AlertDialog.Builder(this)
                .setTitle(R.string.metered_title)
                .setMessage(R.string.metered_message)
                .setPositiveButton(R.string.download_anyway) { _, _ -> ModelDownloadService.start(this) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            ModelDownloadService.start(this)
        }
    }

    // ------------------------------------------------------------ helpers

    private fun openAppInfo() {
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun open(intent: Intent, quiet: Boolean = false): Boolean = try {
        startActivity(intent); true
    } catch (e: ActivityNotFoundException) {
        if (!quiet) Toast.makeText(this, R.string.toast_cant_open, Toast.LENGTH_SHORT).show()
        false
    }
}

/** Android 15+ draws apps edge-to-edge; pad content so it clears the status and nav bars. */
fun applyInsets(root: ViewGroup) {
    if (Build.VERSION.SDK_INT < 30) return
    val l = root.paddingLeft; val t = root.paddingTop; val r = root.paddingRight; val b = root.paddingBottom
    root.setOnApplyWindowInsetsListener { v, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
        v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
        insets
    }
}
