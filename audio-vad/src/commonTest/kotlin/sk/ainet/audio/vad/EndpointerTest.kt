package sk.ainet.audio.vad

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndpointerTest {

    private fun tone(samples: Int, amp: Float = 0.5f): FloatArray =
        FloatArray(samples) { (amp * sin(2.0 * PI * 440.0 * it / 16_000)).toFloat() }

    private fun silence(samples: Int): FloatArray = FloatArray(samples)

    @Test
    fun segmentsTwoUtterancesIntoTwoLines() = runTest {
        // 0.5 s silence, 1 s speech, 1 s silence, 1 s speech, 0.5 s silence
        val audio = flowOf(
            silence(8_000), tone(16_000), silence(16_000), tone(16_000), silence(8_000),
        )

        val events = Endpointer(EnergyVad(minThreshold = 0.02f)).segments(audio).toList()

        val opened = events.filterIsInstance<SpeechEvent.LineOpened>()
        val closed = events.filterIsInstance<SpeechEvent.LineClosed>()
        assertEquals(2, opened.size, "expected 2 lines, events: ${events.map { it::class.simpleName }.distinct()}")
        assertEquals(2, closed.size)
        assertEquals(listOf(0, 1), opened.map { it.lineIndex })

        // each line's PCM covers at least the spoken second (minus onset trim, plus pre-roll)
        for (lineIdx in 0..1) {
            val pcm = events.filterIsInstance<SpeechEvent.Pcm>().filter { it.lineIndex == lineIdx }
            val total = pcm.sumOf { it.samples.size }
            assertTrue(total >= 14_000, "line $lineIdx PCM too short: $total samples")
        }
    }

    @Test
    fun openLineIsClosedWhenInputEnds() = runTest {
        val audio = flowOf(silence(4_000), tone(8_000))   // ends mid-speech

        val events = Endpointer(EnergyVad(minThreshold = 0.02f)).segments(audio).toList()

        assertEquals(1, events.filterIsInstance<SpeechEvent.LineOpened>().size)
        assertEquals(1, events.filterIsInstance<SpeechEvent.LineClosed>().size)
        assertTrue(events.last() is SpeechEvent.LineClosed)
    }

    @Test
    fun pureSilenceEmitsNothing() = runTest {
        val events = Endpointer(EnergyVad(minThreshold = 0.02f)).segments(flowOf(silence(32_000))).toList()
        assertTrue(events.isEmpty(), "silence produced: $events")
    }
}
