package com.astroloop.game.weapon.weapons

import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Entity
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.Projectile
import com.astroloop.game.entity.ProjectileType
import com.astroloop.game.entity.Firer
import com.astroloop.game.util.Vector2
import com.astroloop.game.data.ShipDefinitions
import com.astroloop.game.weapon.Weapon
import com.astroloop.game.weapon.firecontrol.FireControl
import kotlin.random.Random
import com.astroloop.game.tuning.KnobsWeapons

class LeechBurst : Weapon(
    id = "leech_burst",
    name = "Leech Burst"
) {
    companion object {
        /**
         * The same cone Scatter Shot throws, re-exported rather than redeclared.
         *
         * An evolution that scattered wider than the weapon it replaced would be a downgrade, and
         * the two used to be kept in step by a comment alone.
         */
        val SPREAD_CONE_RADIANS: Float get() = ScatterShot.SPREAD_CONE_RADIANS
    }

    override val knobs = KnobsWeapons.leechBurst

    /**
     * The count knob is what fires, plus Duplicator: 20, Scatter Shot's L5 volley (owner,
     * 2026-10-22). The inherited level bonus added 3 on top, since an evolution is always level 5.
     */
    override fun getProjectileCount(state: GameState): Int = baseProjectileCount + state.extraProjectiles

    override fun fire(
        firer: Firer,
        state: GameState,
        projectilePool: EntityPool<Projectile>,
        targets: List<Entity>,
        fireControl: FireControl
    ) {
        if (!canFire()) return

        val damage = getDamage(state)
        val speed = getProjectileSpeed(state)
        val count = getProjectileCount(state)

        // Read from ScatterShot rather than copied, so this cannot drift wider than the weapon it
        // evolves from — an evolution that scattered worse than its base would be a downgrade.
        val spreadAngle = SPREAD_CONE_RADIANS * areaOf(state)
        val baseAngle = playerAimAngle(firer, targets, speed, fireControl) ?: firer.rotation

        for (i in 0 until count) {
            val angle = baseAngle + (Random.nextFloat() - 0.5f) * spreadAngle
            val direction = Vector2.fromAngle(angle)

            // Slight speed variation for organic scatter feel
            val projectileSpeed = speed * (0.9f + Random.nextFloat() * 0.2f)

            val projectile = projectilePool.obtain()
            projectile.initialize(
                x = firer.position.x + direction.x * firer.radius,
                y = firer.position.y + direction.y * firer.radius,
                vx = direction.x * projectileSpeed,
                vy = direction.y * projectileSpeed,
                projectileType = ProjectileType.BULLET,
                projectileDamage = damage,
                projectileLifetime = KnobsWeapons.leechLifetime.value
            )
            projectile.isEnemyProjectile = firer.isEnemyFirer
            // weaponId set to "leech_burst" so hit flashes and telemetry attribute it
            projectile.weaponId = "leech_burst"
            projectile.radius = KnobsWeapons.leechPelletRadius.value
            projectile.color = ShipDefinitions.getEvolutionColor("scatter_shot", state.isCorruptionRun)
        }

        cooldownTimer = getCooldown(state)
    }
}
