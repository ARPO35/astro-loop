package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.AsteroidSize
import com.astroloop.game.entity.AsteroidType
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.EntityPools
import com.astroloop.game.entity.Projectile
import com.astroloop.game.entity.Ship
import com.astroloop.game.entity.VisualEffectManager
import com.astroloop.game.tuning.KnobsWeapons
import com.astroloop.game.util.Vector2
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A saw hit throws no shards (owner, 2026-10-22). Each hit used to throw 3 that hit for 4 each
 * and, starting inside the rock, nearly always landed: up to +120 DPS on top of the saw's own
 * 80-120, invisible to the Lab. Only the contact sparks remain.
 */
class SawContactShardsTest {

    @After
    fun tearDown() = EntityPools.projectiles.freeAll()

    @Test
    fun `a saw hit deals its own damage and throws no shards`() {
        EntityPools.projectiles.freeAll()
        val state = GameState().also {
            it.reset(); it.screenWidth = 1080f; it.screenHeight = 1920f; it.recalculateStats()
        }
        val ship = Ship().also { it.position.set(0f, 0f); it.rotation = 0f }
        val weapons = WeaponSystem(EntityPool({ Projectile() }, 16))
        weapons.addWeapon("energy_saw", state)
        val rock = Asteroid().also {
            it.initialize(KnobsWeapons.sawReach[0].value, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2(0f, 0f))
        }
        val healthBefore = rock.health
        val saws = SawDamageSystem(state, ship, weapons, VisualEffectManager(), { it to false }, {}, {})

        saws.update(1f / 60f, emptyList(), listOf(rock))

        assertEquals(KnobsWeapons.energySaw.damage.value, healthBefore - rock.health, 1e-4f)
        assertTrue("no shards", EntityPools.projectiles.getAllInUse().none { it.isActive && it.weaponId == "energy_saw" })
        assertTrue("sparks still fly", saws.sawSparks.isNotEmpty())
    }
}
