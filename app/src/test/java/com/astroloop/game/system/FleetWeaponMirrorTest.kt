package com.astroloop.game.system

import com.astroloop.game.core.GameConfig
import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Boss
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.Firer
import com.astroloop.game.entity.Projectile
import com.astroloop.game.entity.ProjectileType
import com.astroloop.game.entity.Ship
import com.astroloop.game.entity.VisualEffectManager
import com.astroloop.game.tuning.Tunables
import com.astroloop.game.util.Vector2
import com.astroloop.game.weapon.WeaponFactory
import com.astroloop.game.weapon.firecontrol.FireControl
import com.astroloop.game.weapon.weapons.SolarStorm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.atan2

/**
 * The finale fleet and Past Astro fire the player's own L1 weapon classes at the boss, at 0
 * damage (the boss is always invulnerable; its defeat is scripted), each at its L1 rate.
 */
class FleetWeaponMirrorTest {

    @Before fun defaults() = Tunables.resetAll()
    @After fun cleanup() = Tunables.resetAll()

    private val shotWeapons = listOf(
        "pulse_cannon", "homing_missiles", "scatter_shot", "flak_cannon", "space_mines",
        "nova_blast", "cluster_bomb", "needle_gun", "railgun",
    )
    private val origin = Vector2(400f, 300f)

    private class Rig(val fleet: FleetSystem, val boss: Boss, val pool: EntityPool<Projectile>)

    private fun rig(): Rig {
        val pool = EntityPool({ Projectile() }, 64)
        val boss = Boss().also { it.position.set(0f, 0f); it.isActive = true }
        return Rig(FleetSystem(boss, Ship(), pool, VisualEffectManager()), boss, pool)
    }

    /** The player's L1 weapon, no upgrades and no damage, fired from [origin] at the boss. */
    private fun playerVolley(weaponId: String, boss: Boss): List<Projectile> {
        val pool = EntityPool({ Projectile() }, 64)
        val firer = object : Firer {
            override val position = origin
            override val velocity = Vector2()
            override val rotation = atan2(boss.position.y - origin.y, boss.position.x - origin.x)
            override val radius = GameConfig.SHIP_BASE_SIZE
            override val isEnemyFirer = false
        }
        val state = GameState.createNeutral().also { it.damageMultiplier = 0f }
        WeaponFactory.createWeapon(weaponId)!!.fire(firer, state, pool, listOf(boss), FireControl())
        return pool.getActiveEntities()
    }

    @Test
    fun `the fleet fires exactly what the player's L1 weapon fires, at no damage`() {
        for (id in shotWeapons) {
            val r = rig()
            r.fleet.fireWeaponAtBoss(origin, id, 0)
            val fleetShots = r.pool.getActiveEntities()
            val withSpeed = id != "scatter_shot"
            assertTrue("$id fired nothing", fleetShots.isNotEmpty())
            assertEquals(
                id,
                playerVolley(id, r.boss).map { projectileSignature(it, r.boss, withSpeed = withSpeed) }.sorted(),
                fleetShots.map { projectileSignature(it, r.boss, withSpeed = withSpeed) }.sorted(),
            )
            for (p in fleetShots) assertEquals(id, 0f, p.damage, 0f)
        }
    }

    @Test
    fun `every call fires a volley, because the fleet's own timer paces the shots`() {
        val r = rig()
        r.fleet.fireWeaponAtBoss(origin, "railgun", 0)
        r.fleet.fireWeaponAtBoss(origin, "railgun", 0)
        assertEquals(2, r.pool.getActiveEntities().size)
    }

    @Test
    fun `each fleet ship and Past Astro fire at the player's L1 rate`() {
        val r = rig()
        r.fleet.arrive(GameState(), "ship_white", 1080f, 2400f)
        val neutral = GameState.createNeutral()
        for (fs in r.fleet.fleetShips) {
            if (fs.weaponId == "energy_saw" || fs.weaponId == "ion_orbiters") continue
            val expected = WeaponFactory.createWeapon(fs.weaponId)!!.getCooldown(neutral)
            assertEquals(fs.weaponId, expected, fs.fireCooldown, 0f)
            assertEquals(fs.weaponId, expected, r.fleet.shotCooldown(fs.weaponId), 0f)
        }
        val rail = WeaponFactory.createWeapon("railgun")!!.getCooldown(neutral)
        assertEquals(rail, r.fleet.shotCooldown("railgun"), 0f)
    }

    @Test
    fun `a Solar Storm ship strikes the boss with the player's strike, at no damage`() {
        val r = rig()
        r.fleet.fireWeaponAtBoss(origin, "solar_storm", 0)
        val strike = r.pool.getActiveEntities().single()
        assertEquals(ProjectileType.LIGHTNING, strike.type)
        assertEquals(SolarStorm.STRIKE_RADIUS, strike.radius, 0f)
        assertEquals(SolarStorm.STRIKE_SECONDS, strike.lifetime, 0f)
        assertEquals(0f, strike.damage, 0f)
        assertEquals(r.boss.position.x, strike.position.x, 0f)
        assertEquals(r.boss.position.y, strike.position.y, 0f)
    }

    @Test
    fun `saw and orbiter ships fire no shot`() {
        for (id in listOf("energy_saw", "ion_orbiters")) {
            val r = rig()
            r.fleet.fireWeaponAtBoss(origin, id, 0)
            assertTrue(id, r.pool.getActiveEntities().isEmpty())
        }
    }
}
