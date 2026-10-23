package com.astroloop.game.entity

import com.astroloop.game.util.Vector2

interface Firer {
    val position: Vector2
    val velocity: Vector2
    val rotation: Float
    val radius: Float
    /** True when this firer is an enemy — weapons use this to mark spawned projectiles. */
    val isEnemyFirer: Boolean
    /**
     * True only for the player's ship. Fire control, lead and proportional guidance are
     * player-only; enemies, fleet ships and crewmates keep the original weapon behaviour.
     */
    val isPlayerFirer: Boolean get() = false
}
