package sk.ainet.audio.mel

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P1 gate: the parameterized [MelSpectrogram] with the Whisper config reproduces the SAME golden
 * output the original `WhisperMel` was verified against (`whisper.log_mel_spectrogram` on jfk).
 */
class MelSpectrogramTest {

    private fun resource(name: String): FloatArray {
        val bytes = javaClass.getResourceAsStream(name)!!.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { bb.float }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i].toDouble() * a[i]; nb += b[i].toDouble() * b[i] }
        return dot / (sqrt(na) * sqrt(nb) + 1e-12)
    }

    @Test
    fun whisperConfigMatchesWhisperGolden() {
        val filters = resource("/mel_filters.bin")     // 80 x 201
        val pcm = resource("/jfk_4s_pcm.bin")          // 64000 samples (2 s speech + 2 s silence)
        val golden = resource("/jfk_cc_mel.bin")       // 80 x 400 (whisper.log_mel_spectrogram)

        val mel = MelSpectrogram(filters, MelConfig.whisper(maxFrames = 400)).logMel(pcm)
        assertEquals(80 * 400, mel.data.size)

        val cos = cosine(mel.data, golden)
        var maxAbs = 0f
        for (i in golden.indices) { val d = abs(mel.data[i] - golden[i]); if (d > maxAbs) maxAbs = d }
        println("MEL cos vs whisper golden = $cos  maxAbsDiff = $maxAbs")
        assertTrue(cos > 0.99, "mel cosine vs whisper golden too low: $cos")
    }

    @Test
    fun generatedSlaneyFilterbankApproximatesWhispersShipped() {
        val shipped = resource("/mel_filters.bin")     // 80 x 201, librosa slaney
        val generated = MelFilterbanks.slaney(nMels = 80, nFft = 400, sampleRate = 16_000)

        assertEquals(shipped.size, generated.size)
        val cos = cosine(shipped, generated)
        println("filterbank cos generated-vs-shipped = $cos")
        assertTrue(cos > 0.999, "generated slaney filterbank diverges from whisper's shipped one: $cos")
    }

    @Test
    fun parakeetConfigProducesNaturalLength128Mel() {
        val filters = MelFilterbanks.slaney(nMels = 128, nFft = 512, sampleRate = 16_000)
        val pcm = FloatArray(16_000) { 0.01f }          // 1 s

        val mel = MelSpectrogram(filters, MelConfig.parakeet()).logMel(pcm)

        assertEquals(128, mel.rows)
        assertTrue(mel.cols > 90, "expected ~100 frames for 1 s at hop=160, got ${mel.cols}")
    }
}
