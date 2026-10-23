package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.data.EnemyType
import com.astroloop.game.entity.EnemyShip
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.Firer
import com.astroloop.game.entity.Projectile
import com.astroloop.game.entity.ProjectileType
import com.astroloop.game.entity.Ship
import com.astroloop.game.tuning.KnobsEnemies
import com.astroloop.game.tuning.KnobsWeapons
import com.astroloop.game.tuning.Tunables
import com.astroloop.game.weapon.WeaponFactory
import com.astroloop.game.weapon.firecontrol.FireControl
import com.astroloop.game.weapon.weapons.SolarStorm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Owner, 2026-10-20: enemy weapons are "exact copies of an L1 weapon on a ship without upgrades".
 * Each enemy fires once; the player's L1 weapon fires once from the same spot, with the same aim
 * and no upgrades; the projectiles must match field by field. Only the colour (enemy shots are
 * red) and the Phantom's damage knob differ.
 */
class EnemyWeaponMirrorTest {

    @Before fun defaults() = Tunables.resetAll()
    @After fun cleanup() = Tunables.resetAll()

    /** The enemy type flying the ship that starts with each projectile weapon. */
    private val enemyFor = mapOf(
        "pulse_cannon" to EnemyType.SCOUT,
        "homing_missiles" to EnemyType.TRACER,
        "scatter_shot" to EnemyType.SHRAPNEL,
        "ion_orbiters" to EnemyType.SENTINEL,
        "space_mines" to EnemyType.TRAP,
        "flak_cannon" to EnemyType.DEVASTATOR,
        "needle_gun" to EnemyType.HEDGEHOG,
        "nova_blast" to EnemyType.QUASAR,
        "cluster_bomb" to EnemyType.DREADNOUGHT,
        "railgun" to EnemyType.SPECTER,
    )

    private val enemyX = 500f
    private val enemyY = 500f
    private val bearing = 0.7f   // the player sits off the axes, so aim bugs show

    /** The player's ship, [dist] from the enemy: 60 px is inside every enemy's firing range. */
    private fun shipAt(dist: Float = 60f) = Ship().also {
        it.position.set(enemyX + cos(bearing) * dist, enemyY + sin(bearing) * dist)
        it.isActive = true
    }

    /** An enemy of [type], past its warp-in, moving (Weaver), decloaked mid-aim (Phantom). */
    private fun enemyOf(type: EnemyType, tweak: (EnemyShip) -> Unit = {}) = EnemyShip().also {
        it.initialize(enemyX, enemyY, type, emptyList())
        it.spawnTime = it.warpInDuration
        it.velocity.set(-120f, 60f)
        it.chargeUpTimer = 1.5f
        it.isCloaked = false
        tweak(it)
    }

    private fun enemyVolley(
        weaponId: String,
        ship: Ship,
        tweak: (EnemyShip) -> Unit = {},
    ): Pair<EnemyShip, List<Projectile>> {
        val pool = EntityPool({ Projectile() }, 64)
        val enemy = enemyOf(enemyFor.getValue(weaponId), tweak)
        assertEquals(weaponId, enemy.weaponId)
        EnemyWeaponSystem(pool).fire(enemy, ship)
        return enemy to pool.getActiveEntities()
    }

    /** The player's L1 weapon, no upgrades, fired from the enemy's spot along the enemy's aim. */
    private fun playerVolley(weaponId: String, ship: Ship, enemy: EnemyShip): List<Projectile> {
        val pool = EntityPool({ Projectile() }, 64)
        val aim = if (weaponId == "space_mines") atan2(enemy.velocity.y, enemy.velocity.x)
                  else atan2(ship.position.y - enemy.position.y, ship.position.x - enemy.position.x)
        val firer = object : Firer {
            override val position = enemy.position
            override val velocity = enemy.velocity
            override val rotation = aim
            override val radius = enemy.radius
            override val isEnemyFirer = true
        }
        WeaponFactory.createWeapon(weaponId)!!.fire(firer, GameState.createNeutral(), pool, listOf(ship), FireControl())
        return pool.getActiveEntities()
    }

    @Test
    fun `every enemy fires exactly what the player's L1 weapon fires`() {
        for (weaponId in enemyFor.keys) {
            val ship = shipAt()
            val (enemy, enemyShots) = enemyVolley(weaponId, ship)
            val playerShots = playerVolley(weaponId, ship, enemy)
            val scale = if (weaponId == "railgun") KnobsEnemies.phantomDamage.value else 1f
            val withSpeed = weaponId != "scatter_shot" && weaponId != "homing_missiles"
            assertTrue("$weaponId fired nothing", playerShots.isNotEmpty())
            assertEquals(
                weaponId,
                playerShots.map { projectileSignature(it, ship, scale, withSpeed) }.sorted(),
                enemyShots.map { projectileSignature(it, ship, 1f, withSpeed) }.sorted(),
            )
        }
    }

    @Test
    fun `the Hunter's missiles fly at the missile speed knob, not the player's`() {
        assertEquals(250f, KnobsEnemies.missileSpeed.value, 0f)
        for (p in enemyVolley("homing_missiles", shipAt()).second) {
            assertEquals(250f, p.velocity.length(), 0.5f)
        }
    }

    @Test
    fun `enemy fire aims at the player's current position, not a lead`() {
        val ship = shipAt().also { it.velocity.set(0f, 200f) }
        val (enemy, shots) = enemyVolley("pulse_cannon", ship)
        val expected = atan2(ship.position.y - enemy.position.y, ship.position.x - enemy.position.x)
        for (shot in shots) assertEquals(expected, shot.velocity.angle(), 0.001f)
    }

    @Test
    fun `the missile speed follows its knob and no other enemy's shot speed changes`() {
        val before = enemyFor.keys.filter { it != "homing_missiles" && it != "scatter_shot" }
            .associateWith { id -> enemyVolley(id, shipAt()).second.map { it.velocity.length() } }
        Tunables.set(KnobsEnemies.missileSpeed, 400f)
        for (p in enemyVolley("homing_missiles", shipAt()).second) assertEquals(400f, p.velocity.length(), 0.5f)
        for ((id, speeds) in before) {
            val after = enemyVolley(id, shipAt()).second.map { it.velocity.length() }
            assertEquals(id, speeds.size, after.size)
            speeds.zip(after).forEach { (a, b) -> assertEquals(id, a, b, 0.01f) }
        }
    }

    @Test
    fun `a Guardian's orbiters follow it`() {
        val pool = EntityPool({ Projectile() }, 16)
        val system = EnemyWeaponSystem(pool)
        val enemy = enemyOf(EnemyType.SENTINEL)
        system.fire(enemy, shipAt())
        val orbs = pool.getActiveEntities()
        assertEquals(2, orbs.size)
        enemy.position.set(900f, -300f)
        system.update(listOf(enemy), 1f / 60f)
        for (o in orbs) {
            assertEquals(900f, o.orbitCenter!!.x, 0f)
            assertEquals(-300f, o.orbitCenter!!.y, 0f)
        }
    }

    @Test
    fun `fading orbiters cannot hit the player, live shots and live orbiters can`() {
        val pool = EntityPool({ Projectile() }, 16)
        val system = EnemyWeaponSystem(pool)
        val enemy = enemyOf(EnemyType.SENTINEL)
        system.fire(enemy, shipAt())
        val orbs = pool.getActiveEntities()
        for (o in orbs) assertTrue(EnemyWeaponSystem.canHitPlayer(o))
        system.onEnemyRemoved(enemy)
        for (o in orbs) assertFalse(EnemyWeaponSystem.canHitPlayer(o))
        val shot = enemyVolley("pulse_cannon", shipAt()).second.single()
        assertTrue(EnemyWeaponSystem.canHitPlayer(shot))
        shot.isActive = false
        assertFalse(EnemyWeaponSystem.canHitPlayer(shot))
        shot.isActive = true
        shot.isEnemyProjectile = false
        assertFalse(EnemyWeaponSystem.canHitPlayer(shot))
        shot.isEnemyProjectile = true
        assertTrue(EnemyWeaponSystem.canHitPlayer(shot))
        shot.shouldFadeOut = true
        assertFalse(EnemyWeaponSystem.canHitPlayer(shot))
    }

    @Test
    fun `the Shotgunner's pellets keep the player's random cone and speed spread`() {
        val ship = shipAt()
        val (enemy, shots) = enemyVolley("scatter_shot", ship)
        val aim = atan2(ship.position.y - enemy.position.y, ship.position.x - enemy.position.x)
        val speed = WeaponFactory.createWeapon("scatter_shot")!!.getProjectileSpeed(GameState.createNeutral())
        for (p in shots) {
            val off = atan2(p.velocity.y, p.velocity.x) - aim
            assertTrue("pellet outside the cone: $off", abs(off) <= KnobsWeapons.scatterCone.value / 2f + 1e-4f)
            assertTrue(p.velocity.length() in speed * 0.9f - 0.01f..speed * 1.1f + 0.01f)
        }
    }

    @Test
    fun `enemy shots are red`() {
        for (weaponId in enemyFor.keys) {
            for (p in enemyVolley(weaponId, shipAt()).second) {
                assertEquals(weaponId, EnemyWeaponSystem.CORRUPTION_COLOR, p.color)
            }
        }
    }

    @Test
    fun `every enemy fires at the player's L1 rate`() {
        for (weaponId in enemyFor.keys) {
            val (enemy, _) = enemyVolley(weaponId, shipAt())
            val expected = WeaponFactory.createWeapon(weaponId)!!.getCooldown(GameState.createNeutral())
            assertEquals(weaponId, expected, enemy.weapon!!.cooldownTimer, 0f)
        }
    }

    @Test
    fun `the Phantom's rail hits for half the player's, and follows its knob`() {
        assertEquals(0.5f, KnobsEnemies.phantomDamage.value, 0f)
        val railL1 = WeaponFactory.createWeapon("railgun")!!.getDamage(GameState.createNeutral())
        assertEquals(railL1 * 0.5f, enemyVolley("railgun", shipAt()).second.single().damage, 0f)
        Tunables.set(KnobsEnemies.phantomDamage, 1f)
        assertEquals(railL1, enemyVolley("railgun", shipAt()).second.single().damage, 0f)
    }

    @Test
    fun `the Bomber's blast follows the Bomber, as the player's follows the player`() {
        val (enemy, shots) = enemyVolley("nova_blast", shipAt())
        for (p in shots) assertSame(enemy.position, p.orbitCenter)
    }

    @Test
    fun `enemies keep their own rules for when to fire`() {
        assertTrue(enemyVolley("scatter_shot", shipAt(EnemyWeaponSystem.SCATTER_FIRE_RANGE + 10f)).second.isEmpty())
        assertTrue(enemyVolley("nova_blast", shipAt(EnemyWeaponSystem.NOVA_FIRE_RANGE + 10f)).second.isEmpty())
        assertTrue(enemyVolley("space_mines", shipAt()) { it.velocity.set(0f, 0f) }.second.isEmpty())
        assertTrue(enemyVolley("railgun", shipAt()) { it.isCloaked = true }.second.isEmpty())
        assertTrue(enemyVolley("railgun", shipAt()) { it.chargeUpTimer = 0.5f }.second.isEmpty())
    }

    @Test
    fun `a Guardian's orbiters fade out when it is removed`() {
        val pool = EntityPool({ Projectile() }, 16)
        val system = EnemyWeaponSystem(pool)
        val enemy = enemyOf(EnemyType.SENTINEL)
        system.fire(enemy, shipAt())
        val orbs = pool.getActiveEntities()
        assertEquals(2, orbs.size)
        system.onEnemyRemoved(enemy)
        for (o in orbs) {
            assertTrue("still visible while fading", o.isActive)
            assertEquals(o.age + 0.5f, o.lifetime, 1e-4f)
        }
    }

    @Test
    fun `the Scorcher warns, then strikes with the player's L1 Solar Storm`() {
        val pool = EntityPool({ Projectile() }, 8)
        val system = EnemyWeaponSystem(pool)
        val ship = shipAt(300f)
        val enemy = enemyOf(EnemyType.TEMPEST)
        assertEquals("solar_storm", enemy.weaponId)
        system.fire(enemy, ship)
        assertTrue(enemy.solarTelegraphActive)
        assertTrue("no strike before the ring", pool.getActiveEntities().isEmpty())
        val player = WeaponFactory.createWeapon("solar_storm")!!
        val neutral = GameState.createNeutral()
        assertEquals(player.getCooldown(neutral), enemy.weapon!!.cooldownTimer, 0f)
        repeat(31) { system.update(listOf(enemy), 1f / 60f) }   // just past the 0.5 s ring
        val strike = pool.getActiveEntities().single()
        assertEquals(ProjectileType.LIGHTNING, strike.type)
        assertEquals(player.getDamage(neutral), strike.damage, 0f)
        assertEquals(SolarStorm.STRIKE_RADIUS, strike.radius, 0f)
        assertEquals(SolarStorm.STRIKE_SECONDS, strike.lifetime, 0f)
        assertTrue(strike.isEnemyProjectile)
        assertEquals(EnemyWeaponSystem.CORRUPTION_COLOR, strike.color)
    }

    @Test
    fun `the Scorcher holds fire out of range`() {
        val pool = EntityPool({ Projectile() }, 8)
        val enemy = enemyOf(EnemyType.TEMPEST)
        EnemyWeaponSystem(pool).fire(enemy, shipAt(EnemyWeaponSystem.SOLAR_RANGE + 10f))
        assertFalse(enemy.solarTelegraphActive)
        assertEquals(EnemyWeaponSystem.SOLAR_RETRY_SECONDS, enemy.weapon!!.cooldownTimer, 0f)
    }

    @Test
    fun `enemy shells burst at the player's fuse range plus the ship's radius`() {
        val ship = Ship().also { it.position.set(0f, 0f) }
        val edge = com.astroloop.game.entity.Projectile.PROXIMITY_FUSE_RANGE + ship.radius
        assertTrue(EnemyWeaponSystem.proximityFuseTriggered(edge - 1f, 0f, ship))
        assertFalse(EnemyWeaponSystem.proximityFuseTriggered(edge + 1f, 0f, ship))
    }

    @Test
    fun `a respawned enemy gets a fresh L1 weapon`() {
        val enemy = EnemyShip()
        enemy.initialize(0f, 0f, EnemyType.SENTINEL, emptyList())
        val first = enemy.weapon
        enemy.reset()
        assertNull(enemy.weapon)
        enemy.initialize(0f, 0f, EnemyType.SENTINEL, emptyList())
        assertNotSame(first, enemy.weapon)
        assertEquals(1, enemy.weapon!!.level)
    }
}
