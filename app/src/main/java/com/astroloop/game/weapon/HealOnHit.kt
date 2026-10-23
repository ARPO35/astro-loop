package com.astroloop.game.weapon

import com.astroloop.game.tuning.KnobsWeapons

/**
 * How much the ship heals when one of its projectiles lands, by weapon. Both hit paths (asteroids
 * and enemies) ask here, so the heal stays identical against both (weapon parity).
 *
 * Siphon Needles always healed. Leech Burst's card promised the same from the start but nothing
 * ever applied it; it heals since 1.5 (owner, 2026-10-20).
 */
object HealOnHit {
    /** [damageDealt] is what the hit actually did: a dodged or spawn-shielded hit (0) heals nothing. */
    fun amount(weaponId: String?, damageDealt: Float): Float = if (damageDealt <= 0f) 0f else when (weaponId) {
        "siphon_needles" -> KnobsWeapons.siphonHealPerHit.value
        "leech_burst" -> KnobsWeapons.leechHealPerHit.value
        else -> 0f
    }
}
