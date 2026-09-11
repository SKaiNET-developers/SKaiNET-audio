package sk.ainet.audio.wav

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WavTest {

    @Test
    fun encodeDecodeRoundTrips() {
        val sr = 16_000
        val pcm = FloatArray(sr) { (0.5f * sin(2.0 * PI * 440.0 * it / sr)).toFloat() }

        val decoded = Wav.decode(Wav.encode(pcm, sr))

        assertEquals(sr, decoded.sampleRate)
        assertEquals(pcm.size, decoded.pcm.size)
        var maxAbs = 0f
        for (i in pcm.indices) { val d = abs(pcm[i] - decoded.pcm[i]); if (d > maxAbs) maxAbs = d }
        // rounding (≤0.5 LSB) + the 32767-encode / 32768-decode scale mismatch (≤|x| LSB)
        assertTrue(maxAbs < 2f / 32768f, "16-bit round-trip error too large: $maxAbs")
    }

    @Test
    fun decodeMixesStereoToMono() {
        // Hand-build a 2-channel WAV where L = 0.5, R = -0.5 → mono 0.
        val sr = 8_000
        val samples = 100
        val mono = Wav.encode(FloatArray(samples), sr)
        // patch: channels=2, and rebuild data with interleaved L/R
        val dataLen = samples * 2 * 2
        val out = ByteArray(44 + dataLen)
        mono.copyInto(out, 0, 0, 44)
        out[22] = 2 // channels
        out[40] = (dataLen and 0xff).toByte(); out[41] = ((dataLen shr 8) and 0xff).toByte()
        val l = (0.5f * 32767f).toInt(); val r = (-0.5f * 32767f).toInt()
        for (i in 0 until samples) {
            val o = 44 + i * 4
            out[o] = l.toByte(); out[o + 1] = (l shr 8).toByte()
            out[o + 2] = r.toByte(); out[o + 3] = (r shr 8).toByte()
        }
        out[4] = ((36 + dataLen) and 0xff).toByte(); out[5] = (((36 + dataLen) shr 8) and 0xff).toByte()

        val decoded = Wav.decode(out)
        assertEquals(samples, decoded.pcm.size)
        assertTrue(decoded.pcm.all { abs(it) < 1e-4f }, "L/R should cancel to ~0")
    }
}
