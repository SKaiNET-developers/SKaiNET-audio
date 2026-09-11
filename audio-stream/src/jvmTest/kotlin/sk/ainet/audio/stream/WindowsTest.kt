package sk.ainet.audio.stream

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsTest {

    @Test
    fun exactMultipleMarksLastFullWindowTerminal() = runTest {
        val out = windows(flowOf(FloatArray(8) { it.toFloat() }), windowSamples = 4).toList()

        assertEquals(2, out.size)
        assertEquals(listOf(false, true), out.map { it.isLast })
        assertEquals(listOf(0, 1), out.map { it.index })
        assertTrue(out[1].samples.contentEquals(floatArrayOf(4f, 5f, 6f, 7f)))
    }

    @Test
    fun trailingPartialWindowIsTerminal() = runTest {
        val out = windows(flowOf(FloatArray(10) { it.toFloat() }), windowSamples = 4).toList()

        assertEquals(3, out.size)
        assertEquals(listOf(false, false, true), out.map { it.isLast })
        assertEquals(2, out.last().samples.size)
    }

    @Test
    fun singleSubWindowUtteranceYieldsOneTerminalWindow() = runTest {
        val out = windows(flowOf(FloatArray(3)), windowSamples = 8).toList()

        assertEquals(1, out.size)
        assertTrue(out.single().isLast)
        assertEquals(3, out.single().samples.size)
    }

    @Test
    fun chunkBoundariesDoNotAffectWindows() = runTest {
        val chunked = windows(flowOf(FloatArray(3) { it.toFloat() }, FloatArray(3) { (it + 3).toFloat() }, FloatArray(2) { (it + 6).toFloat() }), 4).toList()
        val whole = windows(flowOf(FloatArray(8) { it.toFloat() }), 4).toList()

        assertEquals(whole.size, chunked.size)
        for (i in whole.indices) {
            assertTrue(whole[i].samples.contentEquals(chunked[i].samples))
            assertEquals(whole[i].isLast, chunked[i].isLast)
        }
    }
}
