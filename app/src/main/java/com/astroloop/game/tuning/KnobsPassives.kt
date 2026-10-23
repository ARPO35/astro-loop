package com.astroloop.game.tuning

import com.astroloop.game.core.GameConfig
import com.astroloop.game.entity.Drone
import com.astroloop.game.system.VampiricLeecherSystem

/**
 * Passive effects, per stack unless the label says otherwise. Stack caps are not knobs, and
 * instant-max passives stay instant-max. Revenge's fire-rate doubling is not a knob either: any
 * factor but 2 would take every weapon off the beat grid.
 *
 * Everything that feeds `GameState.recalculateStats()` applies when the editor closes, since the
 * stats are recalculated then.
 */
object KnobsPassives {
    private val nano = KnobGroup("Passives/Nano Repair", "nano_repair")
    val nanoRegenPerStack = nano.float("regen_per_stack", "Regen per stack (HP/s)", 0.3f, applies = Applies.ON_EDITOR_CLOSE)

    private val duplicator = KnobGroup("Passives/Duplicator Core", "duplicator_core")
    val duplicatorExtraProjectiles = duplicator.int("extra_projectiles", "Extra projectiles", 1, min = 0, max = 10, applies = Applies.ON_EDITOR_CLOSE)

    private val magnet = KnobGroup("Passives/Magnet Field", "magnet_field")
    val magnetRangePerStack = magnet.float("range_per_stack", "Pickup range + per stack", 0.3f, applies = Applies.ON_EDITOR_CLOSE)

    private val phoenix = KnobGroup("Passives/Phoenix Core", "phoenix_core")
    val phoenixExtraLives = phoenix.int("extra_lives", "Extra lives", 1, min = 0, max = 5, applies = Applies.ON_EDITOR_CLOSE)

    private val momentum = KnobGroup("Passives/Momentum Drive", "momentum_drive")
    val momentumDamagePerStack = momentum.float("damage_per_stack", "Damage + per stack while moving", 0.15f, applies = Applies.ON_EDITOR_CLOSE)

    private val cryo = KnobGroup("Passives/Cryo Field", "cryo_field")
    // Replaced `cryo_field.slow` (a flat 0.35) in 1.5: the slow now deepens towards the ship (#57).
    // 0.9 (was 0.75 when the gradient landed): the owner's buff, 2026-10-22.
    val cryoCentreSlow = cryo.float("centre_slow", "Slow at the ship's centre (0 at the edge)", 0.9f, min = 0f, max = 0.9f, applies = Applies.ON_EDITOR_CLOSE)
    val cryoCurve = cryo.float("curve", "Slow curve (1 even, below 1 sooner, above 1 later)", 1f, min = 0.25f, max = 4f)
    val cryoRadiusPerStack = cryo.float("radius_per_stack", "Radius + per stack", 0.25f, applies = Applies.ON_EDITOR_CLOSE)
    val cryoBaseRadius = cryo.float("base_radius", "Base radius", GameConfig.CRYO_BASE_RADIUS)

    private val lucky = KnobGroup("Passives/Lucky Star", "lucky_star")
    val luckyDropBonus = lucky.float("drop_bonus", "Upgrade drop rate +", 0.5f, applies = Applies.ON_EDITOR_CLOSE)

    private val revenge = KnobGroup("Passives/Revenge Protocol", "revenge_protocol")
    val revengeSecondsPerStack = revenge.float("seconds_per_stack", "Burst seconds per stack", 2f)

    private val vampiric = KnobGroup("Passives/Vampiric Core", "vampiric_core")
    // 1.5: `leech_per_stack` and the flat `range` became a flat leech and a range that grows per stack.
    val vampiricLeech = vampiric.float("leech", "Leech per asteroid per tick (HP)", VampiricLeecherSystem.LEECH)
    val vampiricTick = vampiric.float("tick", "Tick (s)", VampiricLeecherSystem.TICK_INTERVAL)
    val vampiricBaseRange = vampiric.float("base_range", "Range at 1 stack (from asteroid edge)", VampiricLeecherSystem.BASE_RANGE)
    val vampiricRangePerStack = vampiric.float("range_per_stack", "Range + per stack", VampiricLeecherSystem.RANGE_PER_STACK, min = 0f, max = 200f)
    val vampiricMaxTargets = vampiric.int("max_targets", "Max asteroids drained", VampiricLeecherSystem.MAX_TARGETS, max = 30)

    private val glass = KnobGroup("Passives/Glass Cannon", "glass_cannon")
    val glassDamageBonus = glass.float("damage_bonus", "Damage +", 1f, applies = Applies.ON_EDITOR_CLOSE)

    private val drone = KnobGroup("Passives/Combat Drone", "drone")
    val droneDamage = drone.float("damage", "Bullet damage", 8f)
    val droneSpeed = drone.float("speed", "Bullet speed", 450f, min = 100f, max = 1800f)
    /** Shots per second. The drone was never on the beat grid; this keeps it where it was. */
    val droneFireRate = drone.float("fire_rate", "Shots per second", Drone.DEFAULT_FIRE_RATE)
    val droneSpread = drone.float("spread", "Spread (rad)", 0.15f)
    val droneEvolvedDamage = drone.float("evolved_damage", "Ace missile damage", 45f)
    val droneEvolvedSpeed = drone.float("evolved_speed", "Ace missile speed", 500f, min = 100f, max = 2000f)
    val droneEvolvedFireRate = drone.float("evolved_fire_rate", "Ace shots per second", 2f)
    val droneEvolvedHoming = drone.float("evolved_homing", "Ace homing strength", 2.5f)
}
