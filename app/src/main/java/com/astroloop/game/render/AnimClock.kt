package com.astroloop.game.render

/**
 * The clock for looping draw-time animations (pulses, flickers, waves, spins): seconds since the
 * app started, as a Double. It never wraps, so a wave never snaps back to its start, and a Double
 * stays precise for centuries.
 *
 * Don't use `System.currentTimeMillis() / 1000f` (about 1.7e9 as a Float, which only changes
 * every two minutes), and don't wrap it with `% 10000L` either: every wave whose speed doesn't fit
 * a whole number of turns into 10 s then jumps back every 10 s (the desert beach foam did).
 */
object AnimClock {
    private val startNanos = System.nanoTime()

    /** Seconds since the app started. */
    fun seconds(): Double = (System.nanoTime() - startNanos) / 1_000_000_000.0
}
