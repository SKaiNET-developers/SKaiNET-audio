package sk.ainet.audio.wav

/** Decoded WAV audio: [sampleRate] Hz, channels already mixed down to mono float `[-1, 1]`. */
public class WavAudio(
    public val sampleRate: Int,
    public val pcm: FloatArray,
)

/**
 * Minimal RIFF/WAV codec, pure Kotlin over ByteArray so it runs on every KMP target. Decodes
 * 16-bit PCM (any channel count, mixed to mono); encodes 16-bit PCM mono. This is the single
 * shared reader that replaces the per-app inline copies.
 */
public object Wav {

    /** Decode a RIFF/WAV byte stream (16-bit PCM) → mono float `[-1, 1]` + sample rate. */
    public fun decode(bytes: ByteArray): WavAudio {
        require(bytes.size > 44 && bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 4) == "WAVE") {
            "not a RIFF/WAVE stream (${bytes.size} bytes)"
        }
        var pos = 12
        var channels = 1
        var rate = 16_000
        var bitsPerSample = 16
        var dataOff = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = bytes.ascii(pos, 4)
            val sz = bytes.leInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    channels = bytes.leShort(body + 2)
                    rate = bytes.leInt(body + 4)
                    bitsPerSample = bytes.leShort(body + 14)
                }
                "data" -> { dataOff = body; dataLen = minOf(sz, bytes.size - body) }
            }
            if (dataOff >= 0 && id == "data") break
            pos = body + sz + (sz and 1)
        }
        require(dataOff >= 0) { "no data chunk in WAV stream" }
        require(bitsPerSample == 16) { "only 16-bit PCM supported, got $bitsPerSample-bit" }
        val nSamples = dataLen / 2
        val out = FloatArray(nSamples / channels)
        var o = 0
        var p = dataOff
        var i = 0
        while (i + channels <= nSamples) {
            var acc = 0f
            for (c in 0 until channels) { acc += bytes.leShort16(p) / 32768f; p += 2 }
            out[o++] = acc / channels
            i += channels
        }
        return WavAudio(rate, out)
    }

    /** Encode mono float `[-1, 1]` PCM as a 16-bit RIFF/WAV byte stream. */
    public fun encode(pcm: FloatArray, sampleRate: Int): ByteArray {
        val dataLen = pcm.size * 2
        val out = ByteArray(44 + dataLen)
        out.putAscii(0, "RIFF"); out.putLeInt(4, 36 + dataLen); out.putAscii(8, "WAVE")
        out.putAscii(12, "fmt "); out.putLeInt(16, 16)
        out.putLeShort(20, 1)                    // PCM
        out.putLeShort(22, 1)                    // mono
        out.putLeInt(24, sampleRate)
        out.putLeInt(28, sampleRate * 2)         // byte rate
        out.putLeShort(32, 2)                    // block align
        out.putLeShort(34, 16)                   // bits per sample
        out.putAscii(36, "data"); out.putLeInt(40, dataLen)
        for (i in pcm.indices) {
            val scaled = pcm[i].coerceIn(-1f, 1f) * 32767f
            val v = (if (scaled >= 0f) scaled + 0.5f else scaled - 0.5f).toInt()   // round half away from zero
            out.putLeShort(44 + i * 2, v)
        }
        return out
    }

    private fun ByteArray.ascii(off: Int, len: Int): String =
        buildString { for (i in 0 until len) append(this@ascii[off + i].toInt().toChar()) }

    private fun ByteArray.leInt(o: Int): Int =
        (this[o].toInt() and 0xff) or ((this[o + 1].toInt() and 0xff) shl 8) or
            ((this[o + 2].toInt() and 0xff) shl 16) or ((this[o + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.leShort(o: Int): Int =
        (this[o].toInt() and 0xff) or ((this[o + 1].toInt() and 0xff) shl 8)

    /** Signed 16-bit little-endian sample. */
    private fun ByteArray.leShort16(o: Int): Int = leShort(o).toShort().toInt()

    private fun ByteArray.putAscii(off: Int, s: String) { for (i in s.indices) this[off + i] = s[i].code.toByte() }
    private fun ByteArray.putLeInt(o: Int, v: Int) {
        this[o] = v.toByte(); this[o + 1] = (v shr 8).toByte()
        this[o + 2] = (v shr 16).toByte(); this[o + 3] = (v shr 24).toByte()
    }
    private fun ByteArray.putLeShort(o: Int, v: Int) { this[o] = v.toByte(); this[o + 1] = (v shr 8).toByte() }
}
