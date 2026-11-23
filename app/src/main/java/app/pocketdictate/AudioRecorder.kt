package app.pocketdictate

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log

/** Records 16 kHz mono 16-bit PCM, which is what Parakeet expects. */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val MAX_SECONDS = 10 * 60
        private const val TAG = "AudioRecorder"
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val chunks = ArrayList<ShortArray>()
    private var total = 0

    /** Caller must already hold RECORD_AUDIO. Returns false if the mic couldn't start. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val rec = try {
            AudioRecord(
                // Tuned for speech recognition: little or no extra processing.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, SAMPLE_RATE / 5 * 2)
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord failed", e); return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); return false
        }
        try {
            rec.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e); rec.release(); return false
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release(); return false
        }
        synchronized(chunks) { chunks.clear(); total = 0 }
        record = rec
        running = true
        thread = Thread({ readLoop(rec) }, "pd-audio").also { it.start() }
        return true
    }

    private fun readLoop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = ShortArray(SAMPLE_RATE / 10)
        while (running) {
            val n = rec.read(buf, 0, buf.size)
            if (n < 0) break
            if (n == 0) continue
            synchronized(chunks) {
                if (total < SAMPLE_RATE * MAX_SECONDS) {
                    chunks.add(buf.copyOf(n)); total += n
                }
            }
        }
    }

    val isRecording get() = running

    /** Stops and returns float samples in [-1, 1]. */
    fun stop(): FloatArray {
        running = false
        val rec = record ?: return FloatArray(0)
        record = null
        try { rec.stop() } catch (_: Exception) {}
        thread?.join(1000)
        thread = null
        rec.release()
        synchronized(chunks) {
            val out = FloatArray(total)
            var i = 0
            for (c in chunks) for (s in c) out[i++] = s / 32768f
            chunks.clear(); total = 0
            return out
        }
    }

    /** Stops and throws the audio away (used when a press turns into a drag). */
    fun cancel() {
        stop()
    }
}
