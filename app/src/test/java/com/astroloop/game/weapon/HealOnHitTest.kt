package com.astroloop.game.weapon

import com.astroloop.game.tuning.KnobsWeapons
import com.astroloop.game.tuning.Tunables
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Leech Burst's card always said "Pellets that heal you on hit", but nothing ever healed (owner,
 * 2026-10-20: add it, the same way Siphon Needles heals). One helper answers for both hit paths.
 */
class HealOnHitTest {

    @Before fun defaults() = Tunables.resetAll()
    @After fun cleanup() = Tunables.resetAll()

    @Test
    fun `leech burst pellets heal a tenth of a point per hit`() {
        assertEquals(0.1f, HealOnHit.amount("leech_burst", 5f), 0f)
    }

    @Test
    fun `siphon needles keep their heal`() {
        assertEquals(0.1f, HealOnHit.amount("siphon_needles", 5f), 0f)
    }

    @Test
    fun `other weapons do not heal`() {
        for (id in listOf("scatter_shot", "needle_gun", "pulse_cannon", "railgun", null)) {
            assertEquals("$id", 0f, HealOnHit.amount(id, 5f), 0f)
        }
    }

    @Test
    fun `the heal follows its knob`() {
        Tunables.set(KnobsWeapons.leechHealPerHit, 0.4f)
        assertEquals(0.4f, HealOnHit.amount("leech_burst", 5f), 0f)
    }

    @Test
    fun `a hit that dealt no damage heals nothing`() {
        for (id in listOf("leech_burst", "siphon_needles")) {
            assertEquals(id, 0f, HealOnHit.amount(id, 0f), 0f)
        }
    }
}
