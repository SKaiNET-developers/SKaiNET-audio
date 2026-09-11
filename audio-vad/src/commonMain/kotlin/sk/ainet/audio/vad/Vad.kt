package sk.ainet.audio.vad

import kotlin.math.sqrt

/**
 * Voice-activity decision over one PCM frame. Implementations range from the default [EnergyVad]
 * to model-based detectors (e.g. a Silero-style net) — the endpointer only sees this interface.
 */
public fun interface VoiceActivityDetector {
    /** True when [frame] (mono float `[-1, 1]`) contains speech. */
    public fun isSpeech(frame: FloatArray): Boolean
}

/**
 * Simple adaptive RMS-energy VAD: a frame is speech when its RMS exceeds
 * `max(minThreshold, noiseFloor * ratio)`, where the noise floor is a slow exponential moving
 * average of non-speech frame energy. Deliberately boring and dependency-free; good enough for
 * push-to-talk endpointing, replaceable behind [VoiceActivityDetector] when it isn't.
 */
public class EnergyVad(
    private val minThreshold: Float = 0.01f,
    private val ratio: Float = 3f,
    private val noiseAdapt: Float = 0.05f,
) : VoiceActivityDetector {

    private var noiseFloor: Float = minThreshold

    override fun isSpeech(frame: FloatArray): Boolean {
        var sum = 0.0
        for (s in frame) sum += s.toDouble() * s
        val rms = sqrt(sum / frame.size.coerceAtLeast(1)).toFloat()
        val speech = rms > maxOf(minThreshold, noiseFloor * ratio)
        if (!speech) noiseFloor += noiseAdapt * (rms - noiseFloor)   // track the floor in silence only
        return speech
    }
}
