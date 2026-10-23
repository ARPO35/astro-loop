package com.astroloop.game.entity

import com.astroloop.game.core.GameConfig
import com.astroloop.game.util.Vector2

/**
 * A [Firer] that shoots from someone else's spot along its own aim. Enemies and the finale fleet
 * fire the player's own weapon classes through it (owner, 2026-10-20: their weapons are exact
 * copies of an L1 weapon), and those classes shoot along `rotation`.
 *
 * [aimFrom] shares the source's position and velocity vectors rather than copying them, so a
 * weapon that keeps the firer's position (Nova Blast's ring follows its ship) keeps following
 * the source.
 */
class AimedFirer(override val isEnemyFirer: Boolean) : Firer {
    private val still = Vector2()
    private var from = Vector2()
    private var moving = still
    private var aim = 0f
    private var size = GameConfig.SHIP_BASE_SIZE

    override val position: Vector2 get() = from
    override val velocity: Vector2 get() = moving
    override val rotation: Float get() = aim
    override val radius: Float get() = size

    /** Fire from [source], aiming along [aim] (radians). */
    fun aimFrom(source: Firer, aim: Float): AimedFirer {
        from = source.position
        moving = source.velocity
        size = source.radius
        this.aim = aim
        return this
    }

    /** Fire from a still, ship-sized point at [origin], aiming along [aim] (radians). */
    fun aimFrom(origin: Vector2, aim: Float): AimedFirer {
        from = origin
        moving = still
        size = GameConfig.SHIP_BASE_SIZE
        this.aim = aim
        return this
    }
}
