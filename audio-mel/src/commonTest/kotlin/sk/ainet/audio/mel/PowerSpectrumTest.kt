package sk.ainet.audio.mel

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class PowerSpectrumTest {

    /** `|X_k|²` by the direct DFT, in double precision — the reference. */
    private fun directPower(x: FloatArray): DoubleArray {
        val n = x.size
        return DoubleArray(n / 2 + 1) { k ->
            var re = 0.0; var im = 0.0
            for (j in 0 until n) { val a = -2.0 * PI * k * j / n; re += x[j] * cos(a); im += x[j] * sin(a) }
            re * re + im * im
        }
    }

    private fun assertMatchesDirect(n: Int, seed: Int = n) {
        val rnd = Random(seed)
        val x = FloatArray(n) { rnd.nextFloat() * 2f - 1f }
        val want = directPower(x)
        val got = FloatArray(n / 2 + 1).also { PowerSpectrum(n).power(x, it) }
        val scale = want.maxOrNull()!!.coerceAtLeast(1e-12)
        for (k in want.indices) {
            val err = abs(got[k] - want[k]) / scale
            assertTrue(err < 1e-5, "n=$n k=$k: fft ${got[k]} vs dft ${want[k]} (relative error $err)")
        }
    }

    @Test fun whispersFrameSize() = assertMatchesDirect(400)          // 4·4·5·5
    @Test fun nemosFrameSize() = assertMatchesDirect(512)             // 4·4·4·4·2
    @Test fun everyRadixPath() { for (n in listOf(1, 2, 3, 4, 5, 6, 8, 9, 12, 15, 16, 25, 30, 60, 100, 210, 256)) assertMatchesDirect(n) }
    @Test fun primeAndRepeatedLargeFactors() { for (n in listOf(7, 49, 97, 397)) assertMatchesDirect(n) }

    @Test
    fun aPureToneLandsInItsBin() {
        val n = 400
        val x = FloatArray(n) { cos(2.0 * PI * 25 * it / n).toFloat() }
        val p = FloatArray(n / 2 + 1).also { PowerSpectrum(n).power(x, it) }
        val peak = p.indices.maxByOrNull { p[it] }!!
        assertTrue(peak == 25 && abs(p[25] - (n / 2.0) * (n / 2.0)) / ((n / 2.0) * (n / 2.0)) < 1e-5, "peak at $peak = ${p[peak]}")
    }

    @Test
    fun oneInstanceServesSeveralWorkspaces() {
        val fft = PowerSpectrum(400)
        val x = FloatArray(400) { sin(0.1 * it).toFloat() }
        val a = FloatArray(201).also { fft.power(x, it, fft.workspace()) }
        val b = FloatArray(201).also { fft.power(x, it, fft.workspace()) }
        assertContentEquals(a, b)
    }

    @Test
    fun factorsPreferRadixFour() {
        assertContentEquals(intArrayOf(4, 4, 5, 5), PowerSpectrum.factor(400))
        assertContentEquals(intArrayOf(4, 4, 4, 4, 2), PowerSpectrum.factor(512))
        assertContentEquals(intArrayOf(2, 3, 5, 7), PowerSpectrum.factor(210))
        assertContentEquals(intArrayOf(7, 7), PowerSpectrum.factor(49))
        assertContentEquals(intArrayOf(397), PowerSpectrum.factor(397))
        assertContentEquals(intArrayOf(), PowerSpectrum.factor(1))
    }
}
