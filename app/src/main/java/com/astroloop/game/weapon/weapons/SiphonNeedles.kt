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

class SiphonNeedles : Weapon(
    id = "siphon_needles",
    name = "Siphon Needles"
) {
    override val knobs = KnobsWeapons.siphonNeedles

    /**
     * The count knob is what fires, plus Duplicator: 7 (owner, 2026-10-22). The inherited level
     * bonus added 3 on top, since an evolution is always level 5.
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
        val baseAngle = playerAimAngle(firer, targets, speed, fireControl) ?: firer.rotation

        for (i in 0 until count) {
            // Own spread knob; its default matches NeedleGun's so the arcs start identical
            val spreadAngle = (i - (count - 1) / 2f) * KnobsWeapons.siphonSpread.value
            val angle = baseAngle + spreadAngle
            val direction = Vector2.fromAngle(angle)

            val projectile = projectilePool.obtain()
            projectile.initialize(
                x = firer.position.x + direction.x * firer.radius,
                y = firer.position.y + direction.y * firer.radius,
                vx = direction.x * speed,
                vy = direction.y * speed,
                projectileType = ProjectileType.BULLET,
                projectileDamage = damage,
                projectileLifetime = KnobsWeapons.siphonLifetime.value
            )
            projectile.isEnemyProjectile = firer.isEnemyFirer
            // weaponId set to "siphon_needles" so the hit system can detect and apply healing
            projectile.weaponId = "siphon_needles"
            projectile.piercing = true
            projectile.maxPierces = KnobsWeapons.siphonPierce.value
            projectile.length = 14f
            projectile.width = 2f
            projectile.color = ShipDefinitions.getEvolutionColor("needle_gun", state.isCorruptionRun)
        }

        cooldownTimer = getCooldown(state)
    }
}
