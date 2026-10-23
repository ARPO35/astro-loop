package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.AsteroidSize
import com.astroloop.game.entity.AsteroidType
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.tuning.KnobsAsteroids
import com.astroloop.game.tuning.Tunables
import com.astroloop.game.util.Vector2
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

/**
 * Issue #58: fragments used to appear on the parent's rim, so the one nearest the player popped up
 * closer than the rock's own edge had been. They now start inside the parent's outline.
 */
class FragmentSpawnTest {

    @Before fun defaults() = Tunables.resetAll()
    @After fun cleanup() = Tunables.resetAll()

    private fun system() = SpawnSystem(EntityPool(factory = { Asteroid() }, initialSize = 40))

    private val state = GameState().apply { reset() }

    private fun parent(size: AsteroidSize, type: AsteroidType = AsteroidType.ROCK) = Asteroid().apply {
        initialize(100f, -50f, size, type, Vector2(1f, 0f))
    }

    private fun distanceFromParent(parent: Asteroid, fragment: Asteroid): Float {
        val dx = fragment.position.x - parent.position.x
        val dy = fragment.position.y - parent.position.y
        return sqrt(dx * dx + dy * dy)
    }

    @Test
    fun `fragments start fully inside the rock they came from`() {
        for (size in listOf(AsteroidSize.LARGE, AsteroidSize.MEDIUM)) {
            for (type in listOf(AsteroidType.ROCK, AsteroidType.ICE, AsteroidType.METAL)) {
                repeat(20) {
                    val p = parent(size, type)
                    val fragments = system().spawnSplitAsteroids(p, state)
                    assertTrue("$size $type should split", fragments.isNotEmpty())
                    for (f in fragments) {
                        val outerEdge = distanceFromParent(p, f) + f.radius
                        assertTrue("$size $type fragment edge $outerEdge past parent ${p.radius}", outerEdge <= p.radius + 0.01f)
                    }
                }
            }
        }
    }

    @Test
    fun `fragments start at the knob's share of the parent radius`() {
        val p = parent(AsteroidSize.LARGE)
        for (f in system().spawnSplitAsteroids(p, state)) {
            assertEquals(p.radius * 0.3f, distanceFromParent(p, f), 0.01f)
        }
    }

    @Test
    fun `an offset of one is the old rim placement`() {
        Tunables.set(KnobsAsteroids.fragmentOffset, 1f)
        val p = parent(AsteroidSize.LARGE)
        for (f in system().spawnSplitAsteroids(p, state)) {
            assertEquals(p.radius, distanceFromParent(p, f), 0.01f)
        }
    }

    @Test
    fun `fragments still fly outward from the centre`() {
        val p = parent(AsteroidSize.LARGE).also { it.velocity.set(0f, 0f) }
        for (f in system().spawnSplitAsteroids(p, state)) {
            val outX = f.position.x - p.position.x
            val outY = f.position.y - p.position.y
            assertTrue("fragment heads away from the parent's centre", outX * f.velocity.x + outY * f.velocity.y > 0f)
        }
    }
}
