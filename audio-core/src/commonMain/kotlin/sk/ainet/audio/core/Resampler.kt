package sk.ainet.audio.core

/**
 * Linear-interpolation resampling of mono PCM. Good enough for speech front-ends (the models'
 * own mel/conv front-ends are far more aggressive low-pass filters than the interpolation error);
 * swap in a windowed-sinc implementation behind the same function if a use-case demands it.
 */
public object Resampler {

    /** Resample mono [pcm] from [fromRate] Hz to [toRate] Hz. Returns [pcm] unchanged when equal. */
    public fun resample(pcm: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        require(fromRate > 0 && toRate > 0) { "rates must be positive: $fromRate -> $toRate" }
        if (fromRate == toRate || pcm.isEmpty()) return pcm
        val outLen = ((pcm.size.toLong() * toRate) / fromRate).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        val step = fromRate.toDouble() / toRate
        for (i in out.indices) {
            val pos = i * step
            val i0 = pos.toInt()
            val frac = (pos - i0).toFloat()
            val a = pcm[minOf(i0, pcm.size - 1)]
            val b = pcm[minOf(i0 + 1, pcm.size - 1)]
            out[i] = a + (b - a) * frac
        }
        return out
    }

    /** Convenience: resample to the 16 kHz mono the ASR front-ends expect. */
    public fun to16k(pcm: FloatArray, fromRate: Int): FloatArray = resample(pcm, fromRate, 16_000)
}
