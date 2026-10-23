package com.astroloop.game.system

import com.astroloop.game.core.Camera
import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.AsteroidType
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.Ship
import com.astroloop.game.tuning.KnobsField
import com.astroloop.game.tuning.Tunables
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Issue #58: every rock flew at the ship's exact position. Rocks now aim at a random point within
 * `field.aim_radius` of the ship; Magnetic rocks, the hunters, still come straight for it.
 */
class RockAimTest {

    @Before fun defaults() = Tunables.resetAll()
    @After fun cleanup() = Tunables.resetAll()

    // ── Pure geometry ─────────────────────────────────────────────────────────

    @Test
    fun `radius zero aims dead on`() {
        for (u in listOf(0f, 0.5f, 0.999f)) for (v in listOf(0f, 0.25f, 0.9f)) {
            val o = SpawnSystem.aimOffset(0f, u, v)
            assertEquals(0f, o.x, 0f)
            assertEquals(0f, o.y, 0f)
        }
    }

    @Test
    fun `aim points stay inside the circle and reach its rim`() {
        val r = 250f
        var farthest = 0f
        for (i in 0..20) for (j in 0..20) {
            val len = SpawnSystem.aimOffset(r, i / 20f * 0.999f, j / 20f).length()
            assertTrue("offset $len outside $r", len <= r + 0.001f)
            farthest = maxOf(farthest, len)
        }
        assertTrue("a draw near u = 1 lands near the rim, got $farthest", farthest > r * 0.99f)
    }

    @Test
    fun `aim points spread evenly over the disc, not bunched at the centre`() {
        // Uniform over area: a quarter of evenly spaced draws land inside half the radius.
        val r = 100f
        val n = 100
        val inner = (0 until n).count { SpawnSystem.aimOffset(r, (it + 0.5f) / n, 0.3f).length() < r / 2f }
        assertEquals(25, inner)
    }

    @Test
    fun `magnetic rocks ignore the spread`() {
        Tunables.set(KnobsField.aimRadius, 400f)
        assertEquals(0f, SpawnSystem.aimRadiusFor(AsteroidType.MAGNETIC), 0f)
        for (t in AsteroidType.values().filter { it != AsteroidType.MAGNETIC }) {
            assertEquals("$t", 400f, SpawnSystem.aimRadiusFor(t), 0f)
        }
    }

    // ── Through the real spawner ──────────────────────────────────────────────

    /** Distance from the ship to the straight line a rock travels on. */
    private fun missDistance(rock: Asteroid, ship: Ship): Float {
        val vx = rock.velocity.x
        val vy = rock.velocity.y
        val dx = ship.position.x - rock.position.x
        val dy = ship.position.y - rock.position.y
        return abs(dx * vy - dy * vx) / sqrt(vx * vx + vy * vy)
    }

    /** [batches] spawn waves at 10 minutes in (every type unlocked), one rock per wave. */
    private fun spawnRocks(batches: Int): Pair<List<Asteroid>, Ship> {
        val system = SpawnSystem(EntityPool(factory = { Asteroid() }, initialSize = 20))
        system.initialize(1080f, 2400f)
        val state = GameState().apply { reset(); difficultyMultiplier = 1f; survivalTime = 600f }
        val ship = Ship()
        val camera = Camera().apply { initialize(1080f, 2400f); update(ship) }
        val rocks = mutableListOf<Asteroid>()
        repeat(batches) { rocks += system.update(10f, state, ship, camera) }
        return rocks to ship
    }

    @Test
    fun `every rock's path passes within the aim radius`() {
        val (rocks, ship) = spawnRocks(200)
        assertTrue(rocks.isNotEmpty())
        for (rock in rocks) {
            assertTrue("${rock.type} misses by ${missDistance(rock, ship)}", missDistance(rock, ship) <= 250f + 0.5f)
        }
        assertTrue("with a spread, not every rock is dead on", rocks.any { missDistance(it, ship) > 50f })
    }

    @Test
    fun `at radius zero every rock flies straight at the ship`() {
        Tunables.set(KnobsField.aimRadius, 0f)
        val (rocks, ship) = spawnRocks(50)
        for (rock in rocks) assertTrue("${rock.type} misses by ${missDistance(rock, ship)}", missDistance(rock, ship) < 0.5f)
    }

    @Test
    fun `magnetic rocks fly straight at the ship at any spread`() {
        Tunables.set(KnobsField.aimRadius, 1000f)
        val (rocks, ship) = spawnRocks(400)
        val magnetic = rocks.filter { it.type == AsteroidType.MAGNETIC }
        assertTrue("400 waves at 10 min should include Magnetic rocks", magnetic.isNotEmpty())
        for (rock in magnetic) assertTrue("misses by ${missDistance(rock, ship)}", missDistance(rock, ship) < 0.5f)
    }
}
