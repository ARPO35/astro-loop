package com.astroloop.game.hangar

/**
 * The colour of a shop tile's price: gold when you can pay it, grey when you cannot yet (#62, #48).
 * Holding an unaffordable tile does nothing but turn the card over, so the price says why.
 * The shipyard carousel prices ships the same way (owner, 2026-10-23).
 */
object StorePriceColor {
    const val AFFORDABLE = 0xFFFFD700.toInt()
    const val UNAFFORDABLE = 0xFF8A8A8A.toInt()
    /** The upgrade tile's fill, which the grey must stay readable on. */
    const val TILE_BACKGROUND = 0xFF1A1A2E.toInt()

    fun of(cost: Int, yen: Int): Int = if (yen >= cost) AFFORDABLE else UNAFFORDABLE
}
