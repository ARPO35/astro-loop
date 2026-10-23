package com.astroloop.game.system

import com.astroloop.game.entity.Asteroid
import com.astroloop.game.tuning.KnobsPassives
import com.astroloop.game.util.Vector2
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cryo Field slows things more the closer they get (#57): full speed where they first
 * touch the field, down to 1 - centre_slow at the ship's centre.
 */
class CryoSlowTest {

    private val edge = 200f

    @Test
    fun `full speed at the edge of the field and outside it`() {
        assertEquals(1f, CryoSlow.speedFactor(edge, edge, 0.75f, 1f), 1e-6f)
        assertEquals(1f, CryoSlow.speedFactor(edge + 50f, edge, 0.75f, 1f), 1e-6f)
    }

    @Test
    fun `the slow deepens evenly towards the ship on a curve of 1`() {
        assertEquals(0.25f, CryoSlow.speedFactor(0f, edge, 0.75f, 1f), 1e-6f)
        assertEquals(0.625f, CryoSlow.speedFactor(edge / 2f, edge, 0.75f, 1f), 1e-6f)
        assertEquals(0.8125f, CryoSlow.speedFactor(edge * 0.75f, edge, 0.75f, 1f), 1e-6f)
    }

    @Test
    fun `a curve above 1 holds the slow back until close to the ship`() {
        // halfway in: depth 0.5, squared 0.25
        assertEquals(1f - 0.75f * 0.25f, CryoSlow.speedFactor(edge / 2f, edge, 0.75f, 2f), 1e-6f)
        assertEquals(0.25f, CryoSlow.speedFactor(0f, edge, 0.75f, 2f), 1e-6f)
    }

    @Test
    fun `capping keeps the direction and never speeds anything up`() {
        val v = Vector2(30f, 40f)                  // speed 50
        CryoSlow.capSpeed(v, 25f)
        assertEquals(15f, v.x, 1e-4f)
        assertEquals(20f, v.y, 1e-4f)

        CryoSlow.capSpeed(v, 100f)                 // above its speed: untouched
        assertEquals(15f, v.x, 1e-4f)
        assertEquals(20f, v.y, 1e-4f)
    }

    private fun rock(speed: Float) = Asteroid().apply {
        isActive = true
        radius = 30f
        velocity.set(speed, 0f)
    }

    @Test
    fun `a rock slows as it closes in and does not compound frame to frame`() {
        val r = rock(100f)
        CryoSlow.slowRock(r, edge, edge, 0.75f, 1f)
        assertEquals(100f, r.velocity.length(), 1e-3f)

        repeat(30) { CryoSlow.slowRock(r, edge / 2f, edge, 0.75f, 1f) }
        assertEquals(62.5f, r.velocity.length(), 1e-3f)

        CryoSlow.slowRock(r, edge / 4f, edge, 0.75f, 1f)
        assertEquals(100f * (1f - 0.75f * 0.75f), r.velocity.length(), 1e-3f)
    }

    @Test
    fun `a rock that leaves keeps its slowest speed, and coming back does not slow it twice`() {
        val r = rock(100f)
        CryoSlow.slowRock(r, edge / 2f, edge, 0.75f, 1f)       // 62.5
        CryoSlow.slowRock(r, edge, edge, 0.75f, 1f)            // back at the edge: no speed-up
        assertEquals(62.5f, r.velocity.length(), 1e-3f)

        CryoSlow.slowRock(r, edge / 2f, edge, 0.75f, 1f)       // same depth again
        assertEquals(62.5f, r.velocity.length(), 1e-3f)       // capped against its first speed
    }

    @Test
    fun `a reused rock starts fresh`() {
        val r = rock(100f)
        CryoSlow.slowRock(r, edge / 2f, edge, 0.75f, 1f)
        r.reset()
        r.isActive = true
        r.velocity.set(40f, 0f)
        CryoSlow.slowRock(r, edge / 2f, edge, 0.75f, 1f)
        assertEquals(25f, r.velocity.length(), 1e-3f)
    }

    @Test
    fun `the defaults slow to a tenth of the speed at the centre, evenly`() {
        assertEquals(0.9f, KnobsPassives.cryoCentreSlow.defaultRaw, 0f)
        assertEquals(1f, KnobsPassives.cryoCurve.defaultRaw, 0f)
    }
}
