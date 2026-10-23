package com.astroloop.game.hangar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.data.PersistenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On a TV the nav row ends [HangarMetrics.NAV_BOTTOM_CLEARANCE] above the overscan margin; everywhere else it stays where it was. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HangarNavOverscanTest {

    @Test
    fun `on a television the nav row ends NAV_BOTTOM_CLEARANCE above the margin and is unchanged without it`() {
        assertEquals(950f - HangarMetrics.NAV_BOTTOM_CLEARANCE, HangarMetrics.navLabelY(1000f, 0.05f), 0.001f)
        assertEquals(950f, HangarMetrics.navLabelY(1000f), 0.001f)
        assertEquals(920f - HangarMetrics.NAV_BOTTOM_CLEARANCE, HangarMetrics.navBandTop(1000f, 0.05f), 0.001f)
        assertEquals(920f, HangarMetrics.navBandTop(1000f), 0.001f)
    }

    private fun navBottoms(): Pair<List<Float>, Float> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PersistenceManager(context).resetAllProgress()
        val view = HangarSurfaceView(context) { _, _ -> }
        view.surfaceChanged(view.holder, 0, 1920, 1080)
        view.publishFocusPass()
        val bottoms = view.hangarFocus.targets().filter { it.id.startsWith("nav:") }.map { it.rect.bottom }
        return bottoms to view.layout.height
    }

    @Test
    @Config(qualifiers = "television")
    fun `on a television the nav focus targets end above the overscan margin`() {
        val (bottoms, height) = navBottoms()
        assertEquals(3, bottoms.size)
        bottoms.forEach { assertTrue("$it must be above ${height * 0.95f}", it < height * 0.95f) }
        bottoms.forEach { assertEquals(height * 0.95f - HangarMetrics.NAV_BOTTOM_CLEARANCE + 15f, it, 0.01f) }
    }

    @Test
    fun `off a television the nav focus targets keep today's position`() {
        val (bottoms, height) = navBottoms()
        bottoms.forEach { assertEquals(height * 0.95f + 15f, it, 0.01f) }
    }

    @Test
    @Config(qualifiers = "television")
    fun `on a television the nav row clears the launch chevrons and ends inside the margin`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PersistenceManager(context).resetAllProgress()
        val view = HangarSurfaceView(context) { _, _ -> }
        view.surfaceChanged(view.holder, 0, 1920, 1080)
        val height = view.layout.height
        val labelY = HangarMetrics.navLabelY(height, com.astroloop.game.core.TvOverscan.fraction(context))
        assertTrue("labels top ${labelY - 20f} must clear chevrons ${view.state.shipRestingY + 180f}",
            labelY - 20f > view.state.shipRestingY + 180f)
        assertTrue(labelY + HangarMetrics.NAV_BOTTOM_CLEARANCE <= height * 0.95f + 0.01f)
    }
}
