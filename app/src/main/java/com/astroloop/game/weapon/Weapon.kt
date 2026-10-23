package com.astroloop.game.weapon

import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Entity
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.EntityPools
import com.astroloop.game.entity.Firer
import com.astroloop.game.entity.Projectile
import com.astroloop.game.tuning.WeaponKnobs
import com.astroloop.game.weapon.firecontrol.FireControl

abstract class Weapon(
    val id: String,
    val name: String
) {
    var level: Int = 1
    var cooldownTimer: Float = 0f
    var beatSynced: Boolean = false
    open val beatPhaseOffsetMs: Long = 0L

    /** This weapon's knobs in the tuning registry; the base stats below read through them. */
    abstract val knobs: WeaponKnobs

    // Base stats (before level scaling)
    open val baseDamage: Float get() = knobs.damage.value
    open val baseCooldown: Float get() = knobs.cooldownSeconds
    open val baseProjectileSpeed: Float get() = knobs.projectileSpeed
    open val baseProjectileCount: Int get() = knobs.projectileCount

    /** The area multiplier from passives, scaled by this weapon's own area knob. */
    protected fun areaOf(state: GameState): Float = state.areaMultiplier * knobs.areaScale

    // Get scaled stats based on level.
    // Levels that add a projectile (3 and 5) get no damage bonus;
    // their multiplier is held flat from the previous level.
    open fun getDamage(state: GameState): Float {
        val damageBonus = when (level) {
            1 -> 1.0f
            2 -> 1.30f   // +30% damage (no projectile added)
            3 -> 1.30f   // flat — adds +1 projectile instead
            4 -> 1.55f   // +25% on top of level-2 bonus, +1 projectile
            5 -> 1.55f   // flat — adds +1 projectile instead
            else -> 1.55f
        }
        return baseDamage * damageBonus * state.damageMultiplier
    }

    open fun getCooldown(state: GameState): Float {
        return baseCooldown * state.cooldownMultiplier
    }

    open fun getProjectileSpeed(state: GameState): Float {
        return baseProjectileSpeed * state.projectileSpeedMultiplier
    }

    open fun getProjectileCount(state: GameState): Int {
        val base = baseProjectileCount + (level - 1) * 3 / 4
        return base + state.extraProjectiles
    }

    open fun getArea(state: GameState): Float {
        return areaOf(state)
    }

    fun update(deltaTime: Float) {
        if (cooldownTimer > 0) {
            cooldownTimer -= deltaTime
        }
    }

    fun canFire(): Boolean = cooldownTimer <= 0

    /**
     * 开火。[fireControl] 由 WeaponSystem 每帧传入并共享目标分配；一次性调用（测试、敌机、
     * 舰队）可以省略，默认新建一个只服务本次开火的实例。
     */
    abstract fun fire(
        firer: Firer,
        state: GameState,
        projectilePool: EntityPool<Projectile>,
        targets: List<Entity>,
        fireControl: FireControl = FireControl()
    )

    open fun onLevelUp() {
        level++
    }

    // For evolutions - override in specific weapons
    open fun canEvolve(state: GameState): Boolean = false
    open fun getEvolutionId(): String? = null
    open fun getRequiredPassive(): String? = null
}
