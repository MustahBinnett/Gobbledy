package app.pocketdictate.engine

import java.io.Closeable
import java.io.File

/** Thin Kotlin face of the JNI bridge in app/src/main/cpp/pocket_dictate_jni.cpp. */
object NativeEngine {
    init {
        System.loadLibrary("pocketdictate_jni")
    }

    external fun nativeVersion(): String
    external fun nativeLoad(path: String, threads: Int): Long
    external fun nativeTranscribe(handle: Long, pcm: FloatArray, language: String?): String
    external fun nativeFree(handle: Long)
}

/**
 * A loaded model plus session. NOT thread-safe: create, use and close it on one
 * thread (DictationService uses a single "engine" thread for this).
 */
class Transcriber private constructor(private var handle: Long) : Closeable {

    fun transcribe(pcm16kMono: FloatArray): String {
        check(handle != 0L) { "Transcriber is closed" }
        return NativeEngine.nativeTranscribe(handle, pcm16kMono, "en")
    }

    override fun close() {
        if (handle != 0L) {
            NativeEngine.nativeFree(handle)
            handle = 0L
        }
    }

    companion object {
        fun load(modelFile: File): Transcriber {
            // The S25 Ultra has 8 cores; use them all for the encoder.
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
            return Transcriber(NativeEngine.nativeLoad(modelFile.absolutePath, threads))
        }
    }
}
