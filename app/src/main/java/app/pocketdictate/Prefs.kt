package app.pocketdictate

import android.content.Context
import android.content.SharedPreferences

/** All user settings live in one small SharedPreferences file. */
object Prefs {
    private const val FILE = "settings"

    const val BUTTON_ENABLED = "button_enabled"
    const val BUTTON_SIZE_DP = "button_size_dp"
    const val LAUNCH_AT_BOOT = "launch_at_boot"
    const val ALWAYS_PASTE = "always_paste"
    const val AU_SPELLING = "au_spelling"
    const val POS_X = "button_x"
    const val POS_Y = "button_y"

    const val MIN_SIZE_DP = 44
    const val MAX_SIZE_DP = 96
    const val DEFAULT_SIZE_DP = 60

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun buttonEnabled(c: Context) = of(c).getBoolean(BUTTON_ENABLED, false)
    fun buttonSizeDp(c: Context) =
        of(c).getInt(BUTTON_SIZE_DP, DEFAULT_SIZE_DP).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
    fun launchAtBoot(c: Context) = of(c).getBoolean(LAUNCH_AT_BOOT, false)
    fun alwaysPaste(c: Context) = of(c).getBoolean(ALWAYS_PASTE, false)
    fun auSpelling(c: Context) = of(c).getBoolean(AU_SPELLING, true)

    fun set(c: Context, key: String, value: Boolean) = of(c).edit().putBoolean(key, value).apply()
    fun set(c: Context, key: String, value: Int) = of(c).edit().putInt(key, value).apply()
}
