package app.pocketdictate

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

/** The one model we use: the same file and checksum as Handy's catalogue. */
object ModelSpec {
    const val DISPLAY_NAME = "Parakeet Unified EN 0.6B (Q8_0)"
    const val FILE_NAME = "parakeet-unified-en-0.6b-Q8_0.gguf"
    const val URL =
        "https://huggingface.co/handy-computer/parakeet-unified-en-0.6b-gguf/resolve/main/parakeet-unified-en-0.6b-Q8_0.gguf"
    const val SIZE_BYTES = 731_357_568L
    const val SHA256 = "4b50b6dd862bf6e346929aaf4f5eaacec003bfa3f56462d6c874b41ef2f38795"
}

object ModelFiles {
    /** App-private internal storage; nothing else on the phone can read it. */
    fun dir(c: Context) = File(c.filesDir, "models").apply { mkdirs() }
    fun model(c: Context) = File(dir(c), ModelSpec.FILE_NAME)
    fun partial(c: Context) = File(dir(c), ModelSpec.FILE_NAME + ".part")

    /** Fast check used before every load; the full SHA-256 runs after download. */
    fun isReady(c: Context) = model(c).length() == ModelSpec.SIZE_BYTES

    fun deleteAll(c: Context) {
        model(c).delete()
        partial(c).delete()
    }
}

/** Download progress, shared between the download service and the settings screen. */
object DownloadState {
    sealed interface State
    data object Idle : State
    data class Running(val downloaded: Long, val total: Long) : State
    data object Verifying : State
    data object Done : State
    data class Failed(val message: String) : State

    @Volatile
    var current: State = Idle
        private set

    private val listeners = CopyOnWriteArraySet<(State) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun set(s: State) {
        current = s
        main.post { listeners.forEach { it(s) } }
    }

    fun observe(l: (State) -> Unit) {
        listeners += l
        l(current)
    }

    fun remove(l: (State) -> Unit) {
        listeners -= l
    }
}
