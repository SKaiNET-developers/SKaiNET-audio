package sk.ainet.audio.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sk.ainet.audio.wav.Wav
import java.io.File

/**
 * [AudioSource] backed by a 16-bit PCM WAV file — the canonical file example. Emits
 * [chunkSamples]-sized chunks (default 1 s = 16000 samples; shrink for lower streaming latency).
 * The file's own sample rate is exposed as [sampleRate]; resample with `Resampler` if a consumer
 * needs 16 kHz.
 */
public class WavAudioSource(
    path: String,
    private val chunkSamples: Int = 16_000,
) : AudioSource {

    override val sampleRate: Int
    private val pcm: FloatArray

    init {
        val audio = Wav.decode(File(path).readBytes())
        sampleRate = audio.sampleRate
        pcm = audio.pcm
    }

    override fun pcmChunks(): Flow<FloatArray> = flow {
        var off = 0
        while (off < pcm.size) {
            val n = minOf(chunkSamples, pcm.size - off)
            emit(pcm.copyOfRange(off, off + n))
            off += n
        }
    }
}
