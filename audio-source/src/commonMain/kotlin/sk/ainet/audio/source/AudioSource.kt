package sk.ainet.audio.source

import kotlinx.coroutines.flow.Flow

/**
 * A streaming source of mono PCM audio in `[-1, 1]` at [sampleRate] Hz, delivered as **chunks** as
 * they become available. The abstraction fits any producer — a WAV file ([WavAudioSource]), the
 * Android mic (`MicAudioSource`, AudioRecord), a socket, … — so consumers are decoupled from where
 * audio comes from. Chunk size is the producer's choice (e.g. `sampleRate` samples = 1 s;
 * smaller = lower latency).
 */
public interface AudioSource {
    /** Sample rate (frequency) in Hz. The SKaiNET ASR front-ends expect 16000. */
    public val sampleRate: Int

    /** PCM chunks (mono float, `[-1, 1]`) as they arrive; the [Flow] completes when the source ends. */
    public fun pcmChunks(): Flow<FloatArray>
}
