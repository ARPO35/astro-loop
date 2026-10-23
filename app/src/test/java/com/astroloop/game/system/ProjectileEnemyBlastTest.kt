package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Projectile
import com.astroloop.game.entity.ProjectileType
import com.astroloop.game.entity.Ship
import com.astroloop.game.entity.VisualEffectManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Enemy blasts are a normal hit: they go through the player-hit callback, not bare takeDamage. */
class ProjectileEnemyBlastTest {

    private fun blast() = Projectile().apply {
        position.set(100f, 100f)
        isActive = false
        expiredNaturally = true
        type = ProjectileType.FLAK
        explodeOnDeath = true
        explosionRadius = 60f
        explosionDamage = 30.8f
        isEnemyProjectile = true
        weaponId = "flak_cannon"
    }

    private fun run(shipX: Float, invulnerable: Boolean = false): Pair<List<Float>, Float> {
        val ship = Ship().apply { position.set(shipX, 100f); isActive = true }
        if (invulnerable) ship.makeInvulnerable(2f)
        val healthBefore = ship.health
        val hits = ArrayList<Float>()
        ProjectileEffectsSystem(
            ship = ship, state = GameState(), collisionSystem = CollisionSystem(),
            visualEffects = VisualEffectManager(),
            applyDamageModifiers = { Pair(it, false) },
            onAsteroidDestroyed = {}, onEnemyDestroyed = {},
            onEnemyBlastHitsPlayer = { hits.add(it) },
            onVolatileDetonation = { _, _, _, _ -> },
        ).processExpired(listOf(blast()), emptyList(), emptyList())
        return hits to (healthBefore - ship.health)
    }

    @Test
    fun `a blast in range calls the hit callback with its damage and does not damage the ship itself`() {
        val (hits, lost) = run(shipX = 120f)
        assertEquals(listOf(30.8f), hits)
        assertEquals(0f, lost, 0f)
    }

    @Test
    fun `a blast out of range or on an invulnerable ship calls nothing`() {
        assertTrue(run(shipX = 1000f).first.isEmpty())
        assertTrue(run(shipX = 120f, invulnerable = true).first.isEmpty())
    }
}
