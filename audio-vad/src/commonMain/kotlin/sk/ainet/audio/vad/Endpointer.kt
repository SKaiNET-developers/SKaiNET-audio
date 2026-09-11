package sk.ainet.audio.vad

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Line-oriented speech events — the VAD-side counterpart of a streaming ASR cartridge's
 * `LineUpdated`/`LineCompleted`: one *line* per detected utterance.
 */
public sealed interface SpeechEvent {
    public val lineIndex: Int

    /** Speech detected — a new line opens. [startSample] is the absolute input sample position. */
    public data class LineOpened(override val lineIndex: Int, val startSample: Long) : SpeechEvent

    /** PCM belonging to the open line (includes the [Endpointer.lookbackFrames] pre-roll). */
    public class Pcm(override val lineIndex: Int, public val samples: FloatArray) : SpeechEvent

    /** The open line endpointed after [Endpointer.hangoverFrames] of trailing silence. */
    public data class LineClosed(override val lineIndex: Int, val totalSamples: Long) : SpeechEvent
}

/**
 * Turns a continuous PCM chunk stream into endpointed utterance *lines*: frames of [frameSamples]
 * are classified by [vad]; a line opens on [onsetFrames] consecutive speech frames (with
 * [lookbackFrames] of pre-roll so onsets aren't clipped) and closes after [hangoverFrames]
 * consecutive silence frames. A line still open when the input completes is closed.
 *
 * This is application-agnostic endpointing (when speech starts/stops), NOT session policy (what a
 * host does with it) — that stays in the adapter layer per the cartridge contract.
 */
public class Endpointer(
    private val vad: VoiceActivityDetector = EnergyVad(),
    private val frameSamples: Int = 320,       // 20 ms @ 16 kHz
    private val onsetFrames: Int = 2,          // 40 ms of speech to open
    private val hangoverFrames: Int = 25,      // 500 ms of silence to close
    private val lookbackFrames: Int = 5,       // 100 ms pre-roll kept before the onset
) {
    public fun segments(audio: Flow<FloatArray>): Flow<SpeechEvent> = flow {
        val frame = FloatArray(frameSamples)
        var frameFill = 0
        var absSample = 0L

        val lookback = ArrayDeque<FloatArray>()
        var line = -1
        var open = false
        var speechRun = 0
        var silenceRun = 0
        var lineSamples = 0L

        suspend fun kotlinx.coroutines.flow.FlowCollector<SpeechEvent>.processFrame(f: FloatArray) {
            val speech = vad.isSpeech(f)
            if (!open) {
                speechRun = if (speech) speechRun + 1 else 0
                lookback.addLast(f.copyOf())
                while (lookback.size > lookbackFrames + onsetFrames) lookback.removeFirst()
                if (speechRun >= onsetFrames) {
                    open = true; line++
                    silenceRun = 0; lineSamples = 0
                    val preRoll = lookback.sumOf { it.size }
                    emit(SpeechEvent.LineOpened(line, absSample - preRoll))
                    for (pre in lookback) { lineSamples += pre.size; emit(SpeechEvent.Pcm(line, pre)) }
                    lookback.clear(); speechRun = 0
                }
            } else {
                silenceRun = if (speech) 0 else silenceRun + 1
                lineSamples += f.size
                emit(SpeechEvent.Pcm(line, f.copyOf()))
                if (silenceRun >= hangoverFrames) {
                    emit(SpeechEvent.LineClosed(line, lineSamples))
                    open = false; speechRun = 0; silenceRun = 0
                }
            }
        }

        audio.collect { chunk ->
            var off = 0
            while (off < chunk.size) {
                val take = minOf(frameSamples - frameFill, chunk.size - off)
                chunk.copyInto(frame, frameFill, off, off + take)
                frameFill += take; off += take; absSample += take
                if (frameFill == frameSamples) { processFrame(frame); frameFill = 0 }
            }
        }
        if (open) emit(SpeechEvent.LineClosed(line, lineSamples))
    }
}
