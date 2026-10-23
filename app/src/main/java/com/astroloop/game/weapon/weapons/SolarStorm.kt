package com.astroloop.game.weapon.weapons

import com.astroloop.game.core.GameConfig
import com.astroloop.game.core.GameState
import com.astroloop.game.entity.*
import com.astroloop.game.data.ShipDefinitions
import com.astroloop.game.weapon.Weapon
import com.astroloop.game.weapon.firecontrol.FireControl
import com.astroloop.game.tuning.KnobsWeapons

class SolarStorm : Weapon(
    id = "solar_storm",
    name = "Solar Storm"
) {
    override val knobs = KnobsWeapons.solarStorm
    override val beatPhaseOffsetMs: Long = 500L

    companion object {
        /** A strike's hit and draw radius. The Scorcher's and the fleet's strikes use it too. */
        const val STRIKE_RADIUS = 60f
        /** How long a strike's lightning stays up (s). */
        const val STRIKE_SECONDS = 0.4f
    }

    override fun getDamage(state: GameState): Float = baseDamage * state.damageMultiplier

    private fun getTargetCount(): Int = baseProjectileCount + (level - 1)

    override fun fire(
        firer: Firer,
        state: GameState,
        projectilePool: EntityPool<Projectile>,
        targets: List<Entity>,
        fireControl: FireControl
    ) {
        if (!canFire()) return

        var damage = getDamage(state)

        // Apply passive damage bonuses (same as collision path)
        if (state.momentumDamageBonus > 0f && firer.velocity.lengthSquared() > 100f) {
            damage *= (1f + state.momentumDamageBonus)
        }
        val targetCount = getTargetCount()

        // 只打击屏幕内、可伤害的目标；目标选择走火控的威胁排序，不再随机。
        val halfW = state.screenWidth / 2f
        val halfH = state.screenHeight / 2f
        val candidates = targets.filter {
            it.isActive &&
            it.position.x > firer.position.x - halfW && it.position.x < firer.position.x + halfW &&
            it.position.y > firer.position.y - halfH && it.position.y < firer.position.y + halfH &&
            (it !is Asteroid || it.fragmentImmunityTimer <= 0f) &&
            // The boss's own gate, applied here too. This is the one weapon that damages by
            // calling takeDamage directly rather than by landing a projectile, so it never met the
            // invulnerable/shielded check in the projectile-boss loop — which meant it struck
            // through the deflector shield that stops every other weapon, and drew a teleporting
            // dodge out of a boss that was supposed to be untouchable.
            (it !is Boss || (!it.isInvulnerable && !it.shielded))
        }
        // 瞬间落雷没有飞行时间，因此不需要提前量；这里只做威胁排序与目标分配。
        val validTargets = fireControl.selectTargets(firer, candidates, targetCount)

        val weaponColor = ShipDefinitions.getWeaponColor("solar_storm", state.isCorruptionRun)
        for (target in validTargets) {
            // Lucky Rounds: crit chance per target
            var targetDamage = damage
            val isCrit = state.rollCrit()
            if (isCrit) targetDamage *= GameConfig.CRIT_DAMAGE_MULTIPLIER
            target.takeDamage(targetDamage)

            // Lightning visual ON the target (not at ship)
            val projectile = projectilePool.obtain()
            projectile.initialize(
                x = target.position.x,
                y = target.position.y,
                vx = 0f,
                vy = 0f,
                projectileType = ProjectileType.LIGHTNING,
                projectileDamage = targetDamage,  // Store damage for damage number display (includes crit)
                projectileLifetime = STRIKE_SECONDS
            )
            projectile.isEnemyProjectile = firer.isEnemyFirer
            projectile.weaponId = id
            projectile.color = weaponColor
            projectile.radius = STRIKE_RADIUS
            projectile.bounceCount = 99  // Mark as instant-hit (won't fork or do collision damage)
            projectile.isCrit = isCrit
        }

        cooldownTimer = getCooldown(state)
    }

    override fun canEvolve(state: GameState): Boolean {
        return level >= GameConfig.WEAPON_MAX_LEVEL &&
               state.getPassiveStacks("phoenix_core") > 0
    }
    override fun getEvolutionId(): String = "phoenix_flare"
    override fun getRequiredPassive(): String = "phoenix_core"
}
