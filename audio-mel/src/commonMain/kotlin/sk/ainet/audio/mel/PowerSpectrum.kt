package sk.ainet.audio.mel

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Power spectrum `|X_k|²`, `k = 0..n/2`, of a real frame of length [n], by a mixed-radix FFT: `n` is factored into
 * 4s, 2s, 3s and 5s (any remaining factor is handled by a direct DFT of that size), so Whisper's 400-point frame
 * (4·4·5·5) and NeMo's 512-point frame both run in `O(n·Σfactors)` instead of the direct DFT's `O(n²/2)`.
 *
 * Recursive decimation in time over a precomputed twiddle table `e^(−2πi·j/n)`; twiddles are computed in double
 * precision. The instance is immutable and can be shared; the buffers of one transform live in a [Workspace].
 */
internal class PowerSpectrum(private val n: Int) {
    init { require(n >= 1) { "n must be positive: $n" } }

    private val factors: IntArray = factor(n)
    private val wRe = FloatArray(n) { cos(-2.0 * PI * it / n).toFloat() }
    private val wIm = FloatArray(n) { sin(-2.0 * PI * it / n).toFloat() }
    private val maxRadix = factors.maxOrNull() ?: 1

    /** The buffers of one transform: one per thread, reusable across frames. */
    class Workspace internal constructor(n: Int, radix: Int) {
        internal val re = FloatArray(n)
        internal val im = FloatArray(n)
        internal val tRe = FloatArray(radix)
        internal val tIm = FloatArray(radix)
    }

    fun workspace(): Workspace = Workspace(n, maxRadix)

    /** Writes `|X_k|²` of [frame] (length [n]) to [out] (length ≥ `n/2 + 1`). */
    fun power(frame: FloatArray, out: FloatArray, ws: Workspace = workspace()) {
        require(frame.size == n) { "frame has ${frame.size} samples, this transform takes $n" }
        transform(frame, 0, 1, n, 0, 0, ws)
        val re = ws.re; val im = ws.im
        for (k in 0..n / 2) out[k] = re[k] * re[k] + im[k] * im[k]
    }

    /**
     * Transform of the [len] samples `x[inOff + j·stride]` into `re/im[outOff ..< outOff + len]`: the `p` interleaved
     * sub-sequences are transformed recursively into consecutive blocks of `m = len/p`, then combined.
     */
    private fun transform(x: FloatArray, inOff: Int, stride: Int, len: Int, outOff: Int, level: Int, ws: Workspace) {
        val re = ws.re; val im = ws.im; val tRe = ws.tRe; val tIm = ws.tIm
        if (len == 1) { re[outOff] = x[inOff]; im[outOff] = 0f; return }
        val p = factors[level]
        val m = len / p
        for (q in 0 until p) transform(x, inOff + q * stride, stride * p, m, outOff + q * m, level + 1, ws)

        val step = n / len                     // twiddle W_len^j = w[j·step]
        val rot = n / p                        // W_p^j = w[j·rot]
        for (k in 0 until m) {
            // t_q = W_len^{q·k} · Y_q[k]
            var wi = 0
            for (q in 0 until p) {
                val yr = re[outOff + q * m + k]; val yi = im[outOff + q * m + k]
                tRe[q] = yr * wRe[wi] - yi * wIm[wi]
                tIm[q] = yr * wIm[wi] + yi * wRe[wi]
                wi += k * step
            }
            // X[k + r·m] = Σ_q t_q · W_p^{q·r}: closed forms for 2, 4 and 5, the plain sum otherwise
            val o = outOff + k
            when (p) {
                2 -> {
                    re[o] = tRe[0] + tRe[1]; im[o] = tIm[0] + tIm[1]
                    re[o + m] = tRe[0] - tRe[1]; im[o + m] = tIm[0] - tIm[1]
                }
                4 -> {
                    val a0r = tRe[0] + tRe[2]; val a0i = tIm[0] + tIm[2]
                    val a1r = tRe[0] - tRe[2]; val a1i = tIm[0] - tIm[2]
                    val a2r = tRe[1] + tRe[3]; val a2i = tIm[1] + tIm[3]
                    val a3r = tRe[1] - tRe[3]; val a3i = tIm[1] - tIm[3]
                    re[o] = a0r + a2r; im[o] = a0i + a2i
                    re[o + m] = a1r + a3i; im[o + m] = a1i - a3r            // a1 − i·a3
                    re[o + 2 * m] = a0r - a2r; im[o + 2 * m] = a0i - a2i
                    re[o + 3 * m] = a1r - a3i; im[o + 3 * m] = a1i + a3r    // a1 + i·a3
                }
                5 -> {
                    val s7r = tRe[1] + tRe[4]; val s7i = tIm[1] + tIm[4]
                    val s10r = tRe[1] - tRe[4]; val s10i = tIm[1] - tIm[4]
                    val s8r = tRe[2] + tRe[3]; val s8i = tIm[2] + tIm[3]
                    val s9r = tRe[2] - tRe[3]; val s9i = tIm[2] - tIm[3]
                    re[o] = tRe[0] + s7r + s8r; im[o] = tIm[0] + s7i + s8i
                    val s5r = tRe[0] + s7r * C1 + s8r * C2; val s5i = tIm[0] + s7i * C1 + s8i * C2
                    val s6r = s10i * S1 + s9i * S2; val s6i = -s10r * S1 - s9r * S2
                    re[o + m] = s5r + s6r; im[o + m] = s5i + s6i                // s6 = −i·(S1·s10 + S2·s9)
                    re[o + 4 * m] = s5r - s6r; im[o + 4 * m] = s5i - s6i
                    val s11r = tRe[0] + s7r * C2 + s8r * C1; val s11i = tIm[0] + s7i * C2 + s8i * C1
                    val s12r = -s10i * S2 + s9i * S1; val s12i = s10r * S2 - s9r * S1
                    re[o + 2 * m] = s11r - s12r; im[o + 2 * m] = s11i - s12i    // s12 = i·(S2·s10 − S1·s9)
                    re[o + 3 * m] = s11r + s12r; im[o + 3 * m] = s11i + s12i
                }
                else -> for (r in 0 until p) {
                    var sr = 0f; var si = 0f
                    var j = 0                          // (q·r mod p)·rot, advanced without a division
                    for (q in 0 until p) {
                        sr += tRe[q] * wRe[j] - tIm[q] * wIm[j]
                        si += tRe[q] * wIm[j] + tIm[q] * wRe[j]
                        j += r * rot; if (j >= n) j -= n
                    }
                    re[o + r * m] = sr
                    im[o + r * m] = si
                }
            }
        }
    }

    internal companion object {
        // W_5 = e^(−2πi/5): C1 + i·(−S1), W_5² = C2 + i·(−S2)
        private val C1 = cos(2.0 * PI / 5).toFloat()
        private val S1 = sin(2.0 * PI / 5).toFloat()
        private val C2 = cos(4.0 * PI / 5).toFloat()
        private val S2 = sin(4.0 * PI / 5).toFloat()

        /** `n` as a product of 4s, 2s, 3s, 5s and whatever remains (prime factors > 5, each its own stage). */
        fun factor(n: Int): IntArray {
            val out = ArrayList<Int>()
            var rest = n
            for (p in intArrayOf(4, 2, 3, 5)) while (rest % p == 0) { out += p; rest /= p }
            var d = 7
            while (rest > 1 && d * d <= rest) { while (rest % d == 0) { out += d; rest /= d }; d += 2 }
            if (rest > 1) out += rest
            return out.toIntArray()
        }
    }
}
