package sk.ainet.audio.mel

import kotlin.math.exp
import kotlin.math.ln

/**
 * Mel filterbank generation, matching `librosa.filters.mel(htk=False, norm="slaney")` — the
 * filterbank family Whisper's `mel_filters.bin` and NeMo's default front-end both come from.
 * Prefer a model's shipped filterbank file when exactness against a reference matters; use this
 * when generating one (e.g. 128-mel for Parakeet) is more convenient.
 */
public object MelFilterbanks {

    /** Flattened `[nMels * (nFft/2+1)]` Slaney-normalized triangular filterbank. */
    public fun slaney(nMels: Int, nFft: Int, sampleRate: Int, fMin: Float = 0f, fMax: Float = sampleRate / 2f): FloatArray {
        val nBins = nFft / 2 + 1
        val fftFreqs = FloatArray(nBins) { it.toFloat() * sampleRate / nFft }

        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        val melPoints = FloatArray(nMels + 2) { melMin + (melMax - melMin) * it / (nMels + 1) }
        val hzPoints = FloatArray(nMels + 2) { melToHz(melPoints[it]) }

        val weights = FloatArray(nMels * nBins)
        for (m in 0 until nMels) {
            val fLo = hzPoints[m]; val fCenter = hzPoints[m + 1]; val fHi = hzPoints[m + 2]
            val norm = 2f / (fHi - fLo)                                  // slaney area normalization
            for (k in 0 until nBins) {
                val f = fftFreqs[k]
                val up = (f - fLo) / (fCenter - fLo)
                val down = (fHi - f) / (fHi - fCenter)
                val w = minOf(up, down).coerceAtLeast(0f)
                weights[m * nBins + k] = w * norm
            }
        }
        return weights
    }

    // Slaney mel scale: linear below 1 kHz, logarithmic above.
    private const val F_SP: Float = 200f / 3f
    private const val MIN_LOG_HZ: Float = 1000f
    private const val MIN_LOG_MEL: Float = MIN_LOG_HZ / F_SP
    private val LOGSTEP: Float = (ln(6.4) / 27.0).toFloat()

    private fun hzToMel(hz: Float): Float =
        if (hz < MIN_LOG_HZ) hz / F_SP else MIN_LOG_MEL + ln(hz / MIN_LOG_HZ) / LOGSTEP

    private fun melToHz(mel: Float): Float =
        if (mel < MIN_LOG_MEL) mel * F_SP else MIN_LOG_HZ * exp((mel - MIN_LOG_MEL) * LOGSTEP)
}
