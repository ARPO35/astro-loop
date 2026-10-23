package com.astroloop.game.weapon

import com.astroloop.game.weapon.firecontrol.FireControl
import com.astroloop.game.weapon.weapons.Railgun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailgunTest {

    private fun railgunAt(level: Int) = Railgun().also { it.level = level }

    @Test
    fun `pierce count is flat 10 at every level`() {
        for (level in 1..5) assertEquals(10, railgunAt(level).getPierceCount())
    }

    @Test
    fun `shot collision radius widens per level`() {
        assertEquals(4f, railgunAt(1).getShotRadius(), 0.001f)
        assertEquals(7f, railgunAt(2).getShotRadius(), 0.001f)
        assertEquals(10f, railgunAt(3).getShotRadius(), 0.001f)
        assertEquals(13f, railgunAt(4).getShotRadius(), 0.001f)
        assertEquals(16f, railgunAt(5).getShotRadius(), 0.001f)
    }

    @Test
    fun `shot visual width widens per level`() {
        assertEquals(3f, railgunAt(1).getShotWidth(), 0.001f)
        assertEquals(6f, railgunAt(2).getShotWidth(), 0.001f)
        assertEquals(10f, railgunAt(3).getShotWidth(), 0.001f)
        assertEquals(14f, railgunAt(4).getShotWidth(), 0.001f)
        assertEquals(18f, railgunAt(5).getShotWidth(), 0.001f)
    }
}

/**
 * Owner, 2026-10-20: with Duplicator Core the railgun's second slug fires straight BEHIND the ship
 * instead of beside the first in a forward cone.
 */
class RailgunFrontBackTest {

    private val facing = 1.0f

    private fun firer() = object : com.astroloop.game.entity.Firer {
        override val position = com.astroloop.game.util.Vector2(100f, 200f)
        override val velocity = com.astroloop.game.util.Vector2(0f, 0f)
        override val rotation = facing
        override val radius = 25f
        override val isEnemyFirer = false
    }

    /** Fires one volley with [extra] extra projectiles; returns each slug's heading off the ship's facing. */
    private fun volley(extra: Int): List<Pair<Float, com.astroloop.game.entity.Projectile>> {
        val state = com.astroloop.game.core.GameState().also { it.reset(); it.extraProjectiles = extra }
        val pool = com.astroloop.game.entity.EntityPool({ com.astroloop.game.entity.Projectile() }, 20)
        Railgun().also { it.level = 5 }.fire(firer(), state, pool, emptyList(), FireControl())
        return pool.getActiveEntities().map { p ->
            val heading = kotlin.math.atan2(p.velocity.y, p.velocity.x)
            var off = heading - facing
            while (off > Math.PI) off -= (2 * Math.PI).toFloat()
            while (off < -Math.PI) off += (2 * Math.PI).toFloat()
            off to p
        }
    }

    private fun isBehind(off: Float) = kotlin.math.abs(kotlin.math.abs(off) - Math.PI.toFloat()) < 0.2f

    @Test
    fun `without Duplicator one slug flies straight ahead`() {
        val shots = volley(extra = 0)
        assertEquals(1, shots.size)
        assertEquals(0f, shots[0].first, 0.0001f)
    }

    @Test
    fun `with Duplicator one slug flies straight ahead and one straight behind`() {
        val shots = volley(extra = 1).map { it.first }.sorted()
        assertEquals(2, shots.size)
        assertEquals(0f, shots.minBy { kotlin.math.abs(it) }, 0.0001f)
        assertEquals(Math.PI.toFloat(), kotlin.math.abs(shots.maxBy { kotlin.math.abs(it) }), 0.0001f)
    }

    @Test
    fun `the rear slug leaves from the back of the ship`() {
        val (_, rear) = volley(extra = 1).first { isBehind(it.first) }
        val behindX = 100f - kotlin.math.cos(facing) * 25f
        val behindY = 200f - kotlin.math.sin(facing) * 25f
        assertEquals(behindX, rear.position.x, 0.01f)
        assertEquals(behindY, rear.position.y, 0.01f)
    }

    @Test
    fun `larger Lab counts alternate front and back, the front taking the odd one`() {
        for (extra in 2..5) {
            val shots = volley(extra)
            val count = 1 + extra
            val back = shots.count { isBehind(it.first) }
            assertEquals("count $count", count / 2, back)
            assertEquals("count $count", count - count / 2, shots.size - back)
            assertTrue("count $count: every slug is near the front or back line",
                shots.all { kotlin.math.abs(it.first) < 0.4f || isBehind(it.first) })
        }
    }
}
