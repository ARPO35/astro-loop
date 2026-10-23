package com.astroloop.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sin

/**
 * Looping draw-time animations used `(System.currentTimeMillis() % 10000L) / 1000f` as their clock.
 * It wrapped every 10 s, and any wave whose speed didn't fit a whole number of turns into 10 s
 * jumped back each time (the owner saw it on the desert beach foam, 2026-10-20).
 */
class AnimClockTest {

    @Test
    fun `the animation clock counts on and never runs backwards`() {
        var last = AnimClock.seconds()
        assertTrue(last >= 0.0)
        repeat(1000) {
            val now = AnimClock.seconds()
            assertTrue("clock went backwards: $last -> $now", now >= last)
            last = now
        }
    }

    @Test
    fun `no animation uses a wrapping seconds clock any more`() {
        // `(millis % 10000L) / 1000f`, `% 100000L`, `/ 1000.0`: a seconds clock that wraps. Loops that
        // restart on purpose (`% 500) / 500f`, a 0..1 sawtooth) are a different thing and allowed.
        val wrappedSeconds = Regex("""% *10{4,}L\) */ *1000(f|\.0)""")
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "AnimClock.kt" }
            .filter { wrappedSeconds.containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()
        assertEquals("use AnimClock.seconds() instead", emptyList<String>(), offenders)
    }

    @Test
    fun `the thruster flame keeps the level it has shown since May 2026`() {
        // The old flicker: speeds written per millisecond but read per second, on a 10 s clock.
        fun oldMean(freq: Float, phase: Float): Float {
            val samples = 100_000
            var sum = 0.0
            for (i in 0 until samples) {
                val t = 10.0 * i / samples
                sum += 0.7 + 0.3 * sin(t * freq + phase)
            }
            return (sum / samples).toFloat()
        }
        for ((freq, phase) in listOf(0.020f to 0f, 0.025f to 1f, 0.030f to 2f, 0.045f to 0f)) {
            assertEquals("freq=$freq phase=$phase", oldMean(freq, phase), ThrusterDesigns.flameLevel(freq, phase), 1e-3f)
        }
    }

    @Test
    fun `the thruster flame no longer depends on the time`() {
        val first = ThrusterDesigns.flameLevel(0.020f)
        Thread.sleep(20)
        assertEquals(first, ThrusterDesigns.flameLevel(0.020f), 0f)
    }
}
