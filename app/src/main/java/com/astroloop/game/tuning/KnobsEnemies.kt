package com.astroloop.game.tuning

/**
 * Enemy weapons are the player's own L1 weapons with no upgrades (owner, 2026-10-20), so they have
 * no weapon knobs of their own. Two exceptions: the Phantom's railgun hits for half, and the
 * Hunter's homing missiles fly slower (as exact copies they would outrun the ship).
 */
object KnobsEnemies {
    private val phantom = KnobGroup("Enemies/Phantom", "enemies")
    val phantomDamage = phantom.float("phantom_damage", "Phantom damage ×", 0.5f, min = 0f, max = 2f)
    private val hunter = KnobGroup("Enemies/Hunter", "enemies")
    val missileSpeed = hunter.float("missile_speed", "Hunter missile speed (px/s)", 250f, min = 50f, max = 700f)
}
