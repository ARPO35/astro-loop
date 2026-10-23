package com.astroloop.game.hangar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * A shop price you cannot pay yet is greyed (#62, #48): pressing an unaffordable tile only
 * turns the card over, and nothing said why.
 */
class StorePriceColorTest {

    @Test
    fun `a price you can pay is gold, one you cannot is grey`() {
        assertEquals(StorePriceColor.AFFORDABLE, StorePriceColor.of(cost = 1000, yen = 1000))
        assertEquals(StorePriceColor.AFFORDABLE, StorePriceColor.of(cost = 1000, yen = 5000))
        assertEquals(StorePriceColor.UNAFFORDABLE, StorePriceColor.of(cost = 1000, yen = 999))
    }

    @Test
    fun `the grey price stays readable on the tile`() {
        assertTrue(contrast(StorePriceColor.UNAFFORDABLE, StorePriceColor.TILE_BACKGROUND) >= 4.5)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
