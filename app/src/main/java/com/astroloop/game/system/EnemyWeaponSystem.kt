package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.core.SoundManager
import com.astroloop.game.data.EnemyType
import com.astroloop.game.entity.*
import com.astroloop.game.tuning.KnobsEnemies
import com.astroloop.game.weapon.weapons.EnergySaw
import com.astroloop.game.weapon.weapons.HomingMissiles
import com.astroloop.game.weapon.weapons.IonOrbiters
import com.astroloop.game.weapon.weapons.SolarStorm
import com.astroloop.game.weapon.weapons.SpaceMines
import kotlin.math.*

/**
 * Regular enemies fire the player's own weapon classes at L1 with no upgrades (owner, 2026-10-20:
 * "exact copies of an L1 weapon on a ship without upgrades"). What stays enemy-only: when each
 * type fires, that it aims at the player, that its shots are red, the Scorcher's warning ring,
 * and the Phantom's damage knob.
 */
class EnemyWeaponSystem(
    private val projectilePool: EntityPool<Projectile>
) {
    companion object {
        val CORRUPTION_COLOR = 0xFFAA2222.toInt()

        /** The Shotgunner only fires this close. */
        const val SCATTER_FIRE_RANGE = 150f
        /** The Bomber only fires this close (it dives in first). */
        const val NOVA_FIRE_RANGE = 80f
        /** The Weaver only drops mines while moving at least this fast (px/s). */
        const val MINE_DROP_MIN_SPEED = 50f
        /** The Phantom fires only this far into its aim phase (it decloaks over the first 0.5 s). */
        const val RAIL_CHARGE_SECONDS = 1.0f
        /** The Scorcher only targets the player this close. */
        const val SOLAR_RANGE = 800f
        /** Out of range, the Scorcher looks again after this long (s). */
        const val SOLAR_RETRY_SECONDS = 0.5f
        /** The warning ring before a Scorcher's strike lands (s). */
        const val SOLAR_TELEGRAPH_SECONDS = 0.5f

        /**
         * True when [p] may hurt the player: a live enemy shot, except an orbiter fading out
         * after its Guardian was removed (its last 0.5 s), or any shot being force-faded out.
         */
        fun canHitPlayer(p: Projectile): Boolean =
            p.isActive && p.isEnemyProjectile && !p.shouldFadeOut &&
                !(p.type == ProjectileType.ORBITER && p.lifetime - p.age <= 0.5f)

        /**
         * True when an enemy proximity-fused shell at ([x], [y]) is close enough to [ship] to burst:
         * [Projectile.PROXIMITY_FUSE_RANGE] plus the ship's radius, the rule the player's shells
         * use against their targets.
         */
        fun proximityFuseTriggered(x: Float, y: Float, ship: Entity): Boolean {
            val dx = x - ship.position.x
            val dy = y - ship.position.y
            val range = Projectile.PROXIMITY_FUSE_RANGE + ship.radius
            return dx * dx + dy * dy <= range * range
        }
    }

    /** No upgrades; red shots. Its damage multiplier is set per shot by [firingStateFor]. */
    private val firingState = GameState.createNeutral().also { it.isCorruptionRun = true }
    private val aimedFirer = AimedFirer(isEnemyFirer = true)
    private val targets = ArrayList<Entity>(1)

    /** The state [enemy]'s next shot fires with: no upgrades, and the Phantom's damage knob. */
    fun firingStateFor(enemy: EnemyShip): GameState {
        firingState.damageMultiplier =
            if (enemy.type == EnemyType.SPECTER) KnobsEnemies.phantomDamage.value else 1f
        // The Hunter's missiles fly at the knob's speed (multiplier is the only speed input).
        val weapon = enemy.weapon
        firingState.projectileSpeedMultiplier =
            if (weapon is HomingMissiles) KnobsEnemies.missileSpeed.value / weapon.baseProjectileSpeed else 1f
        return firingState
    }

    fun update(enemies: List<EnemyShip>, deltaTime: Float) {
        for (enemy in enemies) {
            if (!enemy.isActive || enemy.isWarping || enemy.isCrewmate) continue
            val weapon = enemy.weapon ?: continue
            weapon.update(deltaTime)
            when (weapon) {
                is IonOrbiters -> weapon.updateOrbiters(enemy.position, deltaTime)
                is SpaceMines -> weapon.updatePendingMines(deltaTime, projectilePool, firingStateFor(enemy))
            }
            // Solar Storm telegraph: tick down and strike on expiry
            if (enemy.solarTelegraphActive) {
                enemy.solarTelegraphTimer -= deltaTime
                if (enemy.solarTelegraphTimer <= 0f) {
                    enemy.solarTelegraphActive = false
                    if (weapon is SolarStorm) strike(enemy, weapon)
                }
            }
        }
    }

    fun fire(enemy: EnemyShip, ship: Ship) {
        val weapon = enemy.weapon ?: return
        if (!weapon.canFire()) return

        val dx = ship.position.x - enemy.position.x
        val dy = ship.position.y - enemy.position.y
        val dist = sqrt(dx * dx + dy * dy)

        // When each enemy fires is its own rule; what it fires is the player's weapon.
        when (weapon) {
            is EnergySaw -> return  // contact damage, in GameSurfaceView
            is SolarStorm -> {
                startSolarTelegraph(enemy, weapon, ship, dist)
                return
            }
            else -> {}
        }
        when (enemy.weaponId) {
            "scatter_shot" -> if (dist > SCATTER_FIRE_RANGE) return
            "nova_blast" -> if (dist > NOVA_FIRE_RANGE) return
            "space_mines" -> if (enemy.velocity.length() < MINE_DROP_MIN_SPEED) return
            "railgun" -> if (enemy.isCloaked || enemy.chargeUpTimer < RAIL_CHARGE_SECONDS) return
        }

        // Shots aim at the player. Mines drop behind a moving enemy, as the player's drop behind
        // the player (whose ship faces the way it moves).
        val aim = if (weapon is SpaceMines) atan2(enemy.velocity.y, enemy.velocity.x) else atan2(dy, dx)
        targets.clear()
        targets.add(ship)
        weapon.fire(aimedFirer.aimFrom(enemy, aim), firingStateFor(enemy), projectilePool, targets)
        // Enemy fire reuses the player's weapon sound at reduced volume (off-beat: enemies fire on
        // their own cooldowns).
        SoundManager.playSFX("sfx_weapon_${enemy.weaponId}", 0.4f)
    }

    /**
     * The Scorcher cannot call [SolarStorm.fire]: it damages its target directly, which would skip
     * the player's invulnerability, dodge and death check. It locks the player's position, shows
     * the warning ring, and [strike]s when the ring runs out, on the player's L1 cooldown.
     */
    private fun startSolarTelegraph(enemy: EnemyShip, weapon: SolarStorm, ship: Ship, dist: Float) {
        if (dist > SOLAR_RANGE) {
            weapon.cooldownTimer = SOLAR_RETRY_SECONDS
            return
        }
        enemy.solarTelegraphActive = true
        enemy.solarTelegraphTimer = SOLAR_TELEGRAPH_SECONDS
        enemy.solarTelegraphX = ship.position.x
        enemy.solarTelegraphY = ship.position.y
        // Counted from the ring's start: a strike every L1 cooldown.
        weapon.cooldownTimer = weapon.getCooldown(firingStateFor(enemy))
        SoundManager.playSFX("sfx_weapon_${enemy.weaponId}", 0.4f)
    }

    /** The player's L1 Solar Storm strike, landing through the normal enemy-projectile hit path. */
    private fun strike(enemy: EnemyShip, weapon: SolarStorm) {
        val lightning = projectilePool.obtain()
        lightning.initialize(
            x = enemy.solarTelegraphX, y = enemy.solarTelegraphY,
            vx = 0f, vy = 0f,
            projectileType = ProjectileType.LIGHTNING,
            projectileDamage = weapon.getDamage(firingStateFor(enemy)),
            projectileLifetime = SolarStorm.STRIKE_SECONDS
        )
        lightning.isEnemyProjectile = true
        lightning.color = CORRUPTION_COLOR
        lightning.radius = SolarStorm.STRIKE_RADIUS
        lightning.bounceCount = 99  // instant-hit marker, as on the player's strike
    }

    fun onEnemyRemoved(enemy: EnemyShip) {
        // Orbiters fade out over 0.5 s rather than vanish (no instant disappearance).
        (enemy.weapon as? IonOrbiters)?.fadeOutOrbiters()
    }

    /**
     * The contact saw [enemy] flies, if any: the Rusher's, or a crewmate's on the Ripper (UNIT-7).
     * Its `cooldownTimer` is the contact tick.
     */
    fun sawOf(enemy: EnemyShip): EnergySaw? = enemy.weapon as? EnergySaw

    /** Damage per tick from [enemy]'s [saw]: the player's L1 saw. */
    fun sawDamage(enemy: EnemyShip, saw: EnergySaw): Float = saw.getDamage(firingStateFor(enemy))

    /** Where [enemy]'s saw disc sits: the player's L1 reach along the enemy's facing. */
    fun getSawDiscPositions(enemy: EnemyShip): List<Pair<Float, Float>> =
        sawOf(enemy)?.getDiscPositions(enemy.position.x, enemy.position.y, enemy.rotation) ?: emptyList()
}
