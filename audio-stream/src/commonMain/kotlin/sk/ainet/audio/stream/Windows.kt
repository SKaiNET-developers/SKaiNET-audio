package sk.ainet.audio.stream

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * One buffered audio window. [isLast] is true exactly once — on the final window before the input
 * flow completes (which may be a shorter, trailing partial window).
 */
public class AudioWindow(
    public val index: Int,
    public val samples: FloatArray,
    public val isLast: Boolean,
)

/**
 * Core windowing logic, decoupled from any model: buffers [audio] chunks into [windowSamples]
 * windows and emits one [AudioWindow] each. The last window is flagged [AudioWindow.isLast] — a
 * single sub-window utterance (the common command-and-control case) yields exactly one terminal
 * window. Extracted from the proven whisper-KMP `windowedTranscription`, with the transcription
 * callback lifted out so any consumer (batch ASR, VAD, metering) can reuse it.
 */
public fun windows(audio: Flow<FloatArray>, windowSamples: Int): Flow<AudioWindow> = flow {
    require(windowSamples > 0) { "windowSamples must be > 0" }
    val buf = ArrayList<Float>(windowSamples)
    var index = 0
    var held: FloatArray? = null    // most-recent full window, emitted once we know if it's terminal

    suspend fun FlowCollector<AudioWindow>.emitHeld(last: Boolean) {
        val w = held ?: return
        emit(AudioWindow(index, w, last))
        index++; held = null
    }

    audio.collect { chunk ->
        var off = 0
        while (off < chunk.size) {
            val take = minOf(windowSamples - buf.size, chunk.size - off)
            for (i in 0 until take) buf.add(chunk[off + i])
            off += take
            if (buf.size == windowSamples) {
                emitHeld(last = false)           // previous full window was not terminal after all
                held = FloatArray(windowSamples) { buf[it] }
                buf.clear()
            }
        }
    }
    if (buf.isNotEmpty()) {                      // a trailing partial window is the terminal one
        emitHeld(last = false)
        held = buf.toFloatArray()
        emitHeld(last = true)
    } else {
        emitHeld(last = true)                    // no remainder → the last full window is terminal
    }
}
