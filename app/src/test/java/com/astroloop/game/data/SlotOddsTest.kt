package com.astroloop.game.data

import org.junit.Assert.*
import org.junit.Test

/**
 * The 50/100/300/500/2000 ladder returns 151 yen per 100 spin honestly, 241 on Rascal's rigged
 * machine. The jackpot slot is still budgeted independently of the prize's value (1,000–50,000),
 * so the numbers do not move as the player's next upgrade gets more expensive.
 */
class SlotOddsTest {

    /** Every value the jackpot's prize can take: the five upgrade tiers, plus the all-maxed cash. */
    private val prizeValues = PersistenceManager.UPGRADE_COSTS + SlotOdds.JACKPOT_CASH_PAYOUT

    @Test
    fun `the base table returns the ladder's expected value at every stage`() {
        for (value in prizeValues) {
            assertEquals(
                "prize value $value must not move the payout",
                151f, SlotOdds.expectedReturn(value, rigged = false), 0.5f
            )
        }
    }

    @Test
    fun `the rigged table pays well and consistently`() {
        for (value in prizeValues) {
            assertEquals(
                "Rascal's machine pays the same premium whatever it is giving away",
                241f, SlotOdds.expectedReturn(value, rigged = true), 0.5f
            )
        }
    }

    @Test
    fun `the rigged table always beats the honest one`() {
        for (value in prizeValues) {
            assertTrue(
                "a rigged machine that paid worse would be a broken story beat",
                SlotOdds.expectedReturn(value, rigged = true) >
                    SlotOdds.expectedReturn(value, rigged = false)
            )
        }
    }

    @Test
    fun `a cheap prize comes up more often than an expensive one`() {
        assertTrue(
            "the 1,000 yen upgrade should be a common win and the 50,000 a rare one",
            SlotOdds.jackpotThreshold(1_000, rigged = false) >
                SlotOdds.jackpotThreshold(50_000, rigged = false)
        )
    }

    @Test
    fun `jackpot odds stay inside probability`() {
        for (value in prizeValues) {
            for (rigged in listOf(false, true)) {
                val p = SlotOdds.jackpotThreshold(value, rigged)
                assertTrue("threshold $p out of range for $value/rigged=$rigged", p in 0f..1f)
            }
        }
    }

    @Test
    fun `a worthless prize cannot produce a certain jackpot`() {
        assertTrue(SlotOdds.jackpotThreshold(0, rigged = false) <= 1f)
        assertTrue(SlotOdds.jackpotThreshold(-1, rigged = false) <= 1f)
    }

    @Test
    fun `the prize ladder runs fifty to two thousand`() {
        assertEquals(listOf(2000, 500, 300, 100, 50), SlotOdds.CASH_TIERS.map { it.second })
    }

    @Test
    fun `the bands never overlap`() {
        for (value in prizeValues) {
            for (rigged in listOf(false, true)) {
                val total = SlotOdds.jackpotThreshold(value, rigged) +
                    SlotOdds.diamondThreshold(rigged) +
                    SlotOdds.CASH_TIERS.drop(1).sumOf { it.first.toDouble() }.toFloat()
                assertTrue("bands sum to $total for $value/rigged=$rigged", total <= 1f)
            }
        }
    }
}
