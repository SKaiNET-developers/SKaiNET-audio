package sk.ainet.audio.mel

import sk.ainet.audio.core.FeatureFrames
import sk.ainet.audio.core.FeatureExtractor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10

/**
 * Mel front-end parameters. Presets cover the ASR models this family supports; anything else is a
 * different instance, not a different implementation.
 */
public data class MelConfig(
    val nMels: Int,
    val nFft: Int = 400,
    val hop: Int = 160,
    /** Crop/zero-pad the time axis to exactly this many frames; null = natural length. */
    val maxFrames: Int? = null,
    /** Drop the trailing STFT frame (`stft[..., :-1]`, Whisper convention). */
    val dropLastFrame: Boolean = true,
    /** Post-processing of the mel power spectrum. */
    val norm: MelNorm = MelNorm.WHISPER,
) {
    public companion object {
        /** Whisper: 80 mel × (nFft/2+1) filterbank, log10 → clamp(max−8) → (x+4)/4. */
        public fun whisper(maxFrames: Int = 3000): MelConfig = MelConfig(nMels = 80, maxFrames = maxFrames)

        /** Parakeet/NeMo FastConformer: 128 mel, natural log, natural length. */
        public fun parakeet(): MelConfig =
            MelConfig(nMels = 128, nFft = 512, maxFrames = null, dropLastFrame = false, norm = MelNorm.LOG)
    }
}

/** Mel post-processing variants. */
public enum class MelNorm {
    /** `log10 → clamp(max − 8) → (x + 4) / 4` — matches `whisper.log_mel_spectrogram`. */
    WHISPER,

    /** Natural log with a small floor (`ln(x + 1e-5)`), NeMo-style; per-feature norm is a model concern. */
    LOG,

    /** Raw mel power, no post-processing. */
    NONE,
}

/**
 * Log-mel spectrogram, pure Kotlin (reflect-pad ±nFft/2, periodic Hann, nFft-point FFT, mel
 * filterbank, then [MelNorm]). With `MelConfig.whisper()` and Whisper's shipped filterbank this is
 * verified against `whisper.log_mel_spectrogram` (cosine ≈ 1.0) — the generalized port of the
 * proven `WhisperMel`.
 *
 * @param filterbank flattened `[nMels * (nFft/2+1)]` mel filters (e.g. Whisper's
 *   `mel_filters_80_16k`, or [MelFilterbanks.slaney]).
 */
public class MelSpectrogram(
    filterbank: FloatArray,
    private val config: MelConfig,
) : FeatureExtractor {

    private val nMels = config.nMels
    private val nFft = config.nFft
    private val hop = config.hop
    private val nBins = nFft / 2 + 1
    private val filters = filterbank

    init { require(filters.size == nMels * nBins) { "filterbank ${filters.size} != $nMels*$nBins" } }

    // Each mel filter is non-zero on a narrow band of bins only: summing over [bandStart, bandEnd) skips exact zeros,
    // so the result is the same as over all bins, at a fraction of the cost.
    private val bandStart = IntArray(nMels) { m -> (0 until nBins).firstOrNull { filters[m * nBins + it] != 0f } ?: 0 }
    private val bandEnd = IntArray(nMels) { m -> ((nBins - 1 downTo 0).firstOrNull { filters[m * nBins + it] != 0f } ?: -1) + 1 }

    // periodic Hann (denominator nFft, matching torch.hann_window(periodic=true))
    private val hann = FloatArray(nFft) { (0.5 - 0.5 * cos(2.0 * PI * it / nFft)).toFloat() }

    // mixed-radix FFT: nFft = 400 (Whisper) is 4·4·5·5, so no power-of-two restriction
    private val spectrum = PowerSpectrum(nFft)

    override fun extract(pcm16k: FloatArray): FeatureFrames = logMel(pcm16k)

    public fun logMel(pcm: FloatArray): FeatureFrames {
        val pad = nFft / 2
        val padded = reflectPad(pcm, pad)
        val totalFrames = 1 + (padded.size - nFft) / hop
        val frames = (if (config.dropLastFrame) totalFrames - 1 else totalFrames).coerceAtLeast(0)

        val mel = FloatArray(nMels * frames)
        val frame = FloatArray(nFft)
        val power = FloatArray(nBins)
        val workspace = spectrum.workspace()
        for (f in 0 until frames) {
            val off = f * hop
            for (n in 0 until nFft) frame[n] = padded[off + n] * hann[n]
            spectrum.power(frame, power, workspace)
            for (m in 0 until nMels) {
                var s = 0f; val base = m * nBins
                for (k in bandStart[m] until bandEnd[m]) s += filters[base + k] * power[k]
                mel[m * frames + f] = s
            }
        }

        when (config.norm) {
            MelNorm.WHISPER -> {
                // log10 → clamp(max−8) → (x+4)/4, max taken over all frames.
                var logMax = Float.NEGATIVE_INFINITY
                for (i in mel.indices) { val v = log10(maxOf(mel[i], 1e-10f)); mel[i] = v; if (v > logMax) logMax = v }
                val floor = logMax - 8f
                for (i in mel.indices) { var v = mel[i]; if (v < floor) v = floor; mel[i] = (v + 4f) / 4f }
            }
            MelNorm.LOG -> for (i in mel.indices) mel[i] = kotlin.math.ln(mel[i] + 1e-5f)
            MelNorm.NONE -> {}
        }

        return cropOrPad(mel, frames)
    }

    // [nMels, frames] row-major → [nMels, maxFrames] (truncate or zero-pad columns); null = as-is.
    private fun cropOrPad(mel: FloatArray, frames: Int): FeatureFrames {
        val maxFrames = config.maxFrames ?: return FeatureFrames(nMels, frames, mel)
        if (frames == maxFrames) return FeatureFrames(nMels, maxFrames, mel)
        val out = FloatArray(nMels * maxFrames)
        val copy = minOf(frames, maxFrames)
        for (m in 0 until nMels) for (f in 0 until copy) out[m * maxFrames + f] = mel[m * frames + f]
        return FeatureFrames(nMels, maxFrames, out)
    }

    private fun reflectPad(x: FloatArray, pad: Int): FloatArray {
        val out = FloatArray(x.size + 2 * pad)
        for (i in 0 until pad) out[i] = x[pad - i]                        // left reflect (excl. edge)
        for (i in x.indices) out[pad + i] = x[i]
        for (j in 0 until pad) out[pad + x.size + j] = x[x.size - 2 - j] // right reflect (excl. edge)
        return out
    }
}
