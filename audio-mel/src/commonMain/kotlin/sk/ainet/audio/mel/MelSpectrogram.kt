package sk.ainet.audio.mel

import sk.ainet.audio.core.FeatureFrames
import sk.ainet.audio.core.FeatureExtractor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

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
 * Log-mel spectrogram, pure Kotlin (reflect-pad ±nFft/2, periodic Hann, nFft-point DFT, mel
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

    // periodic Hann (denominator nFft, matching torch.hann_window(periodic=true))
    private val hann = FloatArray(nFft) { (0.5 - 0.5 * cos(2.0 * PI * it / nFft)).toFloat() }

    // DFT tables: cos/sin(2π k n / nFft); sign irrelevant since we take magnitude².
    private val cosT = Array(nBins) { k -> FloatArray(nFft) { n -> cos(2.0 * PI * k * n / nFft).toFloat() } }
    private val sinT = Array(nBins) { k -> FloatArray(nFft) { n -> sin(2.0 * PI * k * n / nFft).toFloat() } }

    override fun extract(pcm16k: FloatArray): FeatureFrames = logMel(pcm16k)

    public fun logMel(pcm: FloatArray): FeatureFrames {
        val pad = nFft / 2
        val padded = reflectPad(pcm, pad)
        val totalFrames = 1 + (padded.size - nFft) / hop
        val frames = (if (config.dropLastFrame) totalFrames - 1 else totalFrames).coerceAtLeast(0)

        val mel = FloatArray(nMels * frames)
        val frame = FloatArray(nFft)
        val power = FloatArray(nBins)
        for (f in 0 until frames) {
            val off = f * hop
            for (n in 0 until nFft) frame[n] = padded[off + n] * hann[n]
            for (k in 0 until nBins) {
                var re = 0f; var im = 0f
                val ck = cosT[k]; val sk = sinT[k]
                for (n in 0 until nFft) { re += frame[n] * ck[n]; im += frame[n] * sk[n] }
                power[k] = re * re + im * im
            }
            for (m in 0 until nMels) {
                var s = 0f; val base = m * nBins
                for (k in 0 until nBins) s += filters[base + k] * power[k]
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
