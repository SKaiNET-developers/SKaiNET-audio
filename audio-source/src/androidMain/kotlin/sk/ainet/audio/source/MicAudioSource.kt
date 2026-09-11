package sk.ainet.audio.source

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * [AudioSource] from the Android microphone via `AudioRecord` — the same abstraction as
 * [WavAudioSource], so consumers handle a file or the live mic identically. Records mono 16-bit
 * PCM at [sampleRate] (16 kHz for the ASR front-ends) and emits ~[chunkMs] float chunks. The Flow
 * records until the collecting coroutine is cancelled (stop = cancel the collection). Requires the
 * `RECORD_AUDIO` permission.
 */
public class MicAudioSource(
    override val sampleRate: Int = 16_000,
    private val chunkMs: Int = 500,
    private val audioSource: Int = MediaRecorder.AudioSource.MIC,
) : AudioSource {

    override fun pcmChunks(): Flow<FloatArray> = flow {
        val chunk = sampleRate * chunkMs / 1000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val rec = AudioRecord(
            audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, chunk * 2),
        )
        val shorts = ShortArray(chunk)
        try {
            rec.startRecording()
            while (currentCoroutineContext().isActive) {
                val n = rec.read(shorts, 0, chunk)
                if (n <= 0) break
                emit(FloatArray(n) { shorts[it] / 32768f })
            }
        } finally {
            rec.stop(); rec.release()
        }
    }
}
