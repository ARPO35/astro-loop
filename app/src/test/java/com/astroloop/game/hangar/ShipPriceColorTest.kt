package com.astroloop.game.hangar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.core.DesignSpace
import com.astroloop.game.data.PersistenceManager
import com.astroloop.game.data.ShipDefinitions
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shipyard carousel greys a ship's price you cannot pay yet, as the shop does (owner,
 * 2026-10-23: "We made the shop yen grey out if you cant afford something, let's do the same in
 * the ship carrousel"). Tapping an unaffordable ship does nothing, so the price says why.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShipPriceColorTest {

    private lateinit var persistence: PersistenceManager

    /** Tracer, the next ship in line on a fresh save. */
    private val nextShip = 1
    private val cost = ShipDefinitions.getShipByIndex(nextShip)!!.cost

    @Before
    fun setup() {
        persistence = PersistenceManager(ApplicationProvider.getApplicationContext())
        persistence.resetAllProgress()
    }

    /** The colour the carousel drew the next ship's price in, with [yen] in the wallet. */
    private fun priceColorWith(yen: Int): Int {
        val m = DesignSpace.metricsFor(1280f, 2844f)
        val p = DesignSpace.portraitShaped(m.layout)
        val roomWidth = HangarMetrics.roomWidth(p.width, p.content.width, 427)
        val renderer = HangarRenderer(persistence)
        renderer.initialize(m.layout, roomWidth)
        val state = HangarState(persistence)
        state.pilotScreenWidth = m.layout.width
        state.pilotScreenHeight = m.layout.height
        state.roomWidth = roomWidth
        state.currentPage = 1
        state.phase = HangarPhase.BROWSING
        state.selectedShipIndex = nextShip
        state.actualYen = yen
        val bitmap = Bitmap.createBitmap(m.layout.width.toInt(), m.layout.height.toInt(), Bitmap.Config.ARGB_8888)
        renderer.render(Canvas(bitmap), state)
        return renderer.drawnShipPriceColor
    }

    @Test
    fun `a ship you can pay for is priced in gold`() {
        assertEquals(StorePriceColor.AFFORDABLE, priceColorWith(yen = cost))
    }

    @Test
    fun `a ship you cannot pay for yet is priced in grey`() {
        assertEquals(StorePriceColor.UNAFFORDABLE, priceColorWith(yen = cost - 1))
    }
}
