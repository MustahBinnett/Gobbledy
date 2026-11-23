package app.pocketdictate

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView

/** Shows assets/third_party_notices.txt: the copyright notices and licences of bundled open-source code. */
class LicencesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
        val text = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextIsSelectable(true)
            text = runCatching {
                assets.open("third_party_notices.txt").bufferedReader().use { it.readText() }
            }.getOrDefault("Notices file missing.")
        }
        val scroll = ScrollView(this).apply {
            setPadding(pad, pad, pad, pad)
            clipToPadding = false
            addView(text)
        }
        setContentView(scroll)
        applyInsets(scroll)
    }
}
