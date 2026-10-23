package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.data.EnemyType
import com.astroloop.game.entity.EnemyShip
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.Projectile
import com.astroloop.game.entity.Ship
import com.astroloop.game.tuning.Tunables
import com.astroloop.game.weapon.weapons.EnergySaw
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Rusher's saw is the player's L1 saw (owner, 2026-10-20). UNIT-7's crewmate flies the Ripper,
 * whose Energy Saw never fires a projectile; the saw loops skipped crewmates, so that crewmate had
 * no working weapon at all.
 */
class EnemySawMirrorTest {

    @Before fun defaults() = Tunables.resetAll()
    @After fun cleanup() = Tunables.resetAll()

    private val system = EnemyWeaponSystem(EntityPool({ Projectile() }, 4))

    private fun enemyOf(type: EnemyType) = EnemyShip().also {
        it.initialize(100f, 200f, type, emptyList())
        it.rotation = 0.5f
    }

    @Test
    fun `the Rusher flies the player's L1 saw`() {
        val enemy = enemyOf(EnemyType.RIPPER)
        val saw = system.sawOf(enemy)!!
        val player = EnergySaw()   // L1
        assertEquals(player.reach, saw.reach, 0f)
        assertEquals(player.discRadius, saw.discRadius, 0f)
        assertEquals(player.getTickRate(), saw.getTickRate(), 0f)
        assertEquals(player.getDamage(GameState.createNeutral()), system.sawDamage(enemy, saw), 0f)
        assertEquals(player.getDiscPositions(100f, 200f, 0.5f), system.getSawDiscPositions(enemy))
    }

    @Test
    fun `no other enemy has a saw`() {
        for (type in EnemyType.values()) {
            if (type == EnemyType.RIPPER) continue
            assertNull(type.name, system.sawOf(enemyOf(type)))
        }
    }

    @Test
    fun `the Rusher's saw ticks down with the enemy`() {
        val enemy = enemyOf(EnemyType.RIPPER).also { it.spawnTime = it.warpInDuration }
        val saw = system.sawOf(enemy)!!
        saw.cooldownTimer = saw.getTickRate()
        system.update(listOf(enemy), saw.getTickRate() + 0.01f)
        assertTrue(saw.canFire())
    }

    @Test
    fun `a crewmate on the Ripper carries a working saw`() {
        val encounter = CrewmateEncounter(Ship(), EntityPool({ Projectile() }, 8))
        encounter.spawnCrewmateForAstro(GameState(), "pilot_unit7", "ship_magenta", EntityPool({ EnemyShip() }, 2))
        val crewmate = encounter.crewmateShip!!
        assertEquals("energy_saw", crewmate.weaponId)
        val saw = system.sawOf(crewmate)
        assertNotNull(saw)
        // The crewmate's own update ticks the saw (EnemyWeaponSystem skips crewmates).
        saw!!.cooldownTimer = saw.getTickRate()
        encounter.update(GameState(), saw.getTickRate() + 0.01f)
        assertTrue(saw.canFire())
    }

    @Test
    fun `a crewmate carries its starting weapon`() {
        val encounter = CrewmateEncounter(Ship(), EntityPool({ Projectile() }, 8))
        encounter.spawnCrewmateForAstro(GameState(), "pilot_medic", "ship_blue", EntityPool({ EnemyShip() }, 2))
        val crewmate = encounter.crewmateShip!!
        assertEquals("pulse_cannon", crewmate.weaponId)
        assertEquals("pulse_cannon", crewmate.weapon!!.id)
        assertNull(system.sawOf(crewmate))
    }
}
