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
import com.astroloop.game.tuning.KnobsWeapons

class HunterKiller : Weapon(
    id = "hunter_killer",
    name = "Hunter-Killer"
) {
    override val knobs = KnobsWeapons.hunterKiller
    override val beatPhaseOffsetMs: Long = 1000L

    override fun getDamage(state: GameState): Float = baseDamage * state.damageMultiplier

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
        val explosionRadius = KnobsWeapons.hunterExplosionRadius.value * areaOf(state) * 1.4f   // mirrors maxed (L5) Cluster Bomb

        val locks = fireControl.allocate(
            firer, targets, count, speed, KnobsWeapons.hunterLifetime.value
        )

        for (i in 0 until count) {
            val lock = locks.getOrNull(i)
            val baseAngle = lock?.let {
                val dx = it.aimPoint.x - firer.position.x
                val dy = it.aimPoint.y - firer.position.y
                kotlin.math.atan2(dy, dx)
            } ?: firer.rotation
            val offsetAngle = (i - (count - 1) / 2f) * 0.2f
            val angle = baseAngle + offsetAngle
            val direction = Vector2.fromAngle(angle)

            val projectile = projectilePool.obtain()
            projectile.initialize(
                x = firer.position.x + direction.x * firer.radius,
                y = firer.position.y + direction.y * firer.radius,
                vx = direction.x * speed,
                vy = direction.y * speed,
                projectileType = ProjectileType.TORPEDO,   // TORPEDO homes via updateHoming and spawns bomblets
                projectileDamage = damage,
                projectileLifetime = KnobsWeapons.hunterLifetime.value
            )
            projectile.isEnemyProjectile = firer.isEnemyFirer
            projectile.weaponId = id
            projectile.radius = 12f
            projectile.homingStrength = KnobsWeapons.hunterHoming.value
            projectile.target = lock?.target

            projectile.explodeOnDeath = true
            projectile.explosionRadius = explosionRadius
            projectile.explosionDamage = damage * KnobsWeapons.hunterExplosionFraction.value

            // Maxed cluster payload: bomblets + fragments, L5 blast scale
            projectile.bombletCount = KnobsWeapons.hunterBomblets.value
            projectile.bombletDamage = damage * KnobsWeapons.hunterBombletFraction.value
            projectile.bombletExplosionRadius = explosionRadius * KnobsWeapons.hunterBombletRadiusFraction.value
            projectile.hasFragments = true
            projectile.fragmentDamage = damage * KnobsWeapons.hunterFragmentFraction.value
            projectile.color = ShipDefinitions.getEvolutionColor("cluster_bomb", state.isCorruptionRun)
        }

        cooldownTimer = getCooldown(state)
    }
}
