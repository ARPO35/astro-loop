package com.astroloop.game.entity

import com.astroloop.game.util.Vector2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Ion Orbiters and Frost Ring orbs hit each rock or ship once per pass, and other piercing shots
 * each target once (owner, 2026-10-22).
 *
 * Orbs used to forget every target on every frame, so they hit on each frame they overlapped
 * something, and their damage doubled on a 120 Hz screen. Piercing shots did the same to enemy
 * ships and the boss, though never to rocks.
 */
class PiercingHitOnceTest {

    private val centre = Vector2(0f, 0f)

    /** An orb on a 100 px orbit around the origin, placed at [angle] (0 = (100, 0)). */
    private fun orb(angle: Float = 0f): Projectile = Projectile().apply {
        initialize(0f, 0f, 0f, 0f, ProjectileType.ORBITER, 19f, 999f)
        piercing = true
        maxPierces = 1000
        radius = 10f
        orbitCenter = centre
        orbitRadius = 100f
        orbitSpeed = 0f
        orbitAngle = angle
        update(0f)
    }

    private fun rockAt(x: Float, y: Float) = Asteroid().apply {
        position.set(x, y)
        radius = 20f
        isActive = true
    }

    private fun Projectile.moveTo(angle: Float) {
        orbitAngle = angle
        update(1f / 120f)
    }

    @Test
    fun `an orb that stays on a rock hits it once, not on every frame`() {
        val p = orb()
        val rock = rockAt(100f, 0f)
        assertTrue(p.collidesWith(rock))
        assertFalse(p.alreadyHit(rock))
        assertTrue(p.onHit(rock))

        repeat(60) {
            p.update(1f / 120f)
            assertTrue(p.collidesWith(rock))
            assertTrue("frame $it", p.alreadyHit(rock))
            assertFalse("frame $it", p.onHit(rock))
        }
    }

    @Test
    fun `once the orb has left the rock, its next pass hits again`() {
        val p = orb()
        val rock = rockAt(100f, 0f)
        p.onHit(rock)

        p.moveTo(PI.toFloat())          // other side of the ship, clear of the rock
        assertFalse(p.collidesWith(rock))
        assertFalse(p.alreadyHit(rock))

        p.moveTo(0f)                    // back round
        assertTrue(p.collidesWith(rock))
        assertFalse(p.alreadyHit(rock))
        assertTrue(p.onHit(rock))
    }

    @Test
    fun `a rock that is gone is forgotten, so a pooled rock reused in place is hit`() {
        val p = orb()
        val rock = rockAt(100f, 0f)
        p.onHit(rock)

        rock.isActive = false
        p.update(1f / 120f)
        rock.isActive = true            // the pool hands the same object out again
        assertFalse(p.alreadyHit(rock))
    }

    @Test
    fun `each orb keeps its own pass, so two orbs both hit`() {
        val a = orb()
        val b = orb()
        val rock = rockAt(100f, 0f)
        assertTrue(a.onHit(rock))
        assertFalse(b.alreadyHit(rock))
        assertTrue(b.onHit(rock))
    }

    @Test
    fun `a piercing shot hits each target once and never forgets it`() {
        val needle = Projectile().apply {
            initialize(100f, 0f, 0f, 0f, ProjectileType.BULLET, 5f, 2f)
            piercing = true
            maxPierces = 3
        }
        val rock = rockAt(100f, 0f)
        assertFalse(needle.alreadyHit(rock))
        needle.onHit(rock)
        repeat(10) {
            needle.update(1f / 120f)
            assertTrue("frame $it", needle.alreadyHit(rock))
        }
        assertFalse("a second target is fresh", needle.alreadyHit(rockAt(140f, 0f)))
    }

    @Test
    fun `a shot that stops on its first hit is never held back`() {
        val bullet = Projectile().apply { initialize(100f, 0f, 0f, 0f, ProjectileType.BULLET, 5f, 2f) }
        val rock = rockAt(100f, 0f)
        assertFalse(bullet.alreadyHit(rock))
    }

    @Test
    fun `a beam keeps hitting, frame after frame`() {
        val beam = Projectile().apply {
            initialize(100f, 0f, 0f, 0f, ProjectileType.BEAM, 5f, 2f)
            piercing = true
            maxPierces = 1000
        }
        val rock = rockAt(100f, 0f)
        beam.onHit(rock)
        beam.update(1f / 120f)
        assertFalse(beam.alreadyHit(rock))
    }
}
