package app.pocketdictate

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Finds the focused text field in whatever app you're using and inserts the
 * transcript at the cursor, keeping the existing text.
 *
 * Method 1 (plain text fields): read the field's text and cursor, splice the
 * transcript in, write it back with ACTION_SET_TEXT, then put the cursor just
 * after the new words.
 * Method 2 (fallback, or when "Always paste" is on): put the transcript on the
 * clipboard and send ACTION_PASTE, which lets the app insert it itself. This
 * works better in rich editors such as Google Docs.
 */
class DictateAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DictateA11y"

        @Volatile
        var instance: DictateAccessibilityService? = null
            private set

        fun isEnabled(c: Context): Boolean {
            val enabled = Settings.Secure.getString(
                c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(c, DictateAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        DictationService.onKeyboardChanged(keyboardBounds())
        // Android binds accessibility services at boot, so this is a second
        // chance to bring the button back if "Launch at boot" is on.
        if (Prefs.launchAtBoot(this) && Prefs.buttonEnabled(this) && !DictationService.isRunning) {
            if (!DictationService.start(this)) Notifications.postTapToStart(this)
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        DictationService.onKeyboardChanged(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ------------------------------------------------- keyboard tracking
    // The floating button can be shown only while the on-screen keyboard is up
    // (i.e. you've tapped into a text field). We watch window changes and
    // report the keyboard's on-screen area to DictationService.

    private val checkKeyboard = Runnable { DictationService.onKeyboardChanged(keyboardBounds()) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                main.removeCallbacks(checkKeyboard)
                main.postDelayed(checkKeyboard, 120)
            }
        }
    }

    /** Screen area of the visible keyboard, or null when no keyboard is showing. */
    fun keyboardBounds(): android.graphics.Rect? = try {
        windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?.let { w -> android.graphics.Rect().also { w.getBoundsInScreen(it) } }
            ?.takeIf { !it.isEmpty && it.height() > 0 }
    } catch (_: Exception) {
        null
    }

    override fun onInterrupt() {}

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Returns true if the text was handed to a field. Must be called on the main thread.
     * [onLateFailure] runs if a SET_TEXT that the app accepted turns out, a moment
     * later, not to have changed the field (the caller then copies to the clipboard).
     */
    fun insertText(text: String, alwaysPaste: Boolean, onLateFailure: () -> Unit): Boolean {
        val node = findFocusedEditable() ?: run {
            Log.i(TAG, "No focused editable field"); return false
        }
        return try {
            (!alwaysPaste && trySetText(node, text, onLateFailure)) || tryPaste(node, text)
        } catch (e: Exception) {
            Log.w(TAG, "Insert failed", e); false
        }
    }

    private fun findFocusedEditable(): AccessibilityNodeInfo? {
        // Touching our floating button can make *it* the "active" window, so
        // look through every window for the one holding input focus.
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?.takeIf { it.isEditable }?.let { return it }
        val wins = windows.sortedByDescending { it.isFocused }
        for (w in wins) {
            if (w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            val focused = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: continue
            if (focused.isEditable) return focused
        }
        return null
    }

    private fun currentText(node: AccessibilityNodeInfo): String? {
        if (node.isPassword) return null
        if (node.isShowingHintText) return ""
        return node.text?.toString() ?: ""
    }

    private fun trySetText(node: AccessibilityNodeInfo, text: String, onLateFailure: () -> Unit): Boolean {
        if (!node.actionList.contains(AccessibilityAction.ACTION_SET_TEXT)) return false
        val current = currentText(node) ?: return false
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (start < 0 || end < 0) {
            // Cursor position unknown: only safe if the field is empty.
            if (current.isNotEmpty()) return false
            start = 0; end = 0
        }
        if (start > end) start = end.also { end = start }
        if (end > current.length) return false

        val insertion = withSpacing(current, start, end, text)
        val updated = current.substring(0, start) + insertion + current.substring(end)
        if (node.maxTextLength in 1 until updated.length) return false

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false

        val cursor = start + insertion.length
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            }
        )
        // Some apps (e.g. Compose/React-based ones like the Claude app) update the
        // field a moment later, so an immediate check gives a false "failed" and a
        // paste fallback would insert the words twice. Trust the "yes" now, and
        // only check after a short delay; if the field truly never changed, hand
        // back to the caller to copy the text instead (never paste a second copy).
        main.postDelayed({
            val after = try {
                node.refresh(); node.text?.toString()
            } catch (_: Exception) { null } ?: return@postDelayed
            if (!after.contains(insertion.trim()) && after == current) {
                Log.i(TAG, "SET_TEXT accepted but the field never changed")
                onLateFailure()
            }
        }, 600)
        return true
    }

    private fun tryPaste(node: AccessibilityNodeInfo, text: String): Boolean {
        val current = currentText(node)
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        val insertion = if (current != null && start >= 0 && end >= start && end <= current.length) {
            withSpacing(current, start, end, text)
        } else text
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Pocket Dictate", insertion))
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    /** Adds a space before/after the transcript so it doesn't run into neighbouring words. */
    private fun withSpacing(current: String, start: Int, end: Int, text: String): String {
        val before = if (start > 0) current[start - 1] else null
        val after = if (end < current.length) current[end] else null
        val first = text.first()
        val needLeading = before != null && !before.isWhitespace() &&
            first !in ".,;:!?)]}'\"" && before !in "([{"
        val needTrailing = after != null && !after.isWhitespace() && after !in ".,;:!?)]}'\""
        return (if (needLeading) " " else "") + text + (if (needTrailing) " " else "")
    }
}
