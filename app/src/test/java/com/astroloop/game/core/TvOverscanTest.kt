package com.astroloop.game.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.data.PersistenceManager
import com.astroloop.game.hangar.HangarSurfaceView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A TV may crop its edges, so on a television the UI keeps 5% of each edge clear while the
 * playfield stays full-bleed. Real insets merge with that margin by max, never by sum, and no
 * inset delivery is needed for the margin to apply (insets only arrive on API 30+).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TvOverscanTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `merge takes the larger of a real inset and the margin, never the sum`() {
        assertEquals(96f, TvOverscan.merge(0f, 1920f, 0.05f), 0f)
        assertEquals(120f, TvOverscan.merge(120f, 1920f, 0.05f), 0f)
        assertEquals(40f, TvOverscan.merge(40f, 1080f, 0f), 0f)
    }

    @Test
    @Config(qualifiers = "television")
    fun `a television asks for a five percent margin`() {
        assertEquals(0.05f, TvOverscan.fraction(context), 0f)
    }

    @Test
    fun `a phone asks for no margin`() {
        assertEquals(0f, TvOverscan.fraction(context), 0f)
    }

    private fun gameView(): GameSurfaceView {
        PersistenceManager(context).resetAllProgress()
        val view = GameSurfaceView(context, "ship_blue", "pilot_medic") { _, _ -> }
        view.surfaceCreated(view.holder)
        view.surfaceChanged(view.holder, 0, 1920, 1080)
        return view
    }

    private fun assertSafeInset(layout: ScreenLayout, leftPx: Float, topPx: Float, rightPx: Float, bottomPx: Float) {
        val scale = DesignSpace.metricsFor(1920f, 1080f).renderScale
        val full = layout.full
        assertEquals("left", full.left + leftPx / scale, layout.safe.left, 0.01f)
        assertEquals("top", full.top + topPx / scale, layout.safe.top, 0.01f)
        assertEquals("right", full.right - rightPx / scale, layout.safe.right, 0.01f)
        assertEquals("bottom", full.bottom - bottomPx / scale, layout.safe.bottom, 0.01f)
    }

    @Test
    @Config(qualifiers = "television")
    fun `on a television the combat HUD keeps a five percent margin with no inset delivery`() {
        val view = gameView()
        assertSafeInset(view.layout, 96f, 54f, 96f, 54f)
    }

    @Test
    fun `on a phone the combat HUD's safe rect is the full rect when no insets arrive`() {
        val view = gameView()
        assertEquals(view.layout.full, view.layout.safe)
    }

    @Test
    @Config(qualifiers = "television")
    fun `on a television a real inset larger than the margin wins on its own edge`() {
        PersistenceManager(context).resetAllProgress()
        val view = GameSurfaceView(context, "ship_blue", "pilot_medic") { _, _ -> }
        view.applyInsets(150f, 0f, 0f, 0f)
        view.surfaceCreated(view.holder)
        view.surfaceChanged(view.holder, 0, 1920, 1080)
        assertSafeInset(view.layout, 150f, 54f, 96f, 54f)
    }

    @Test
    @Config(qualifiers = "television")
    fun `on a television the hangar keeps a five percent margin with no inset delivery`() {
        PersistenceManager(context).resetAllProgress()
        val view = HangarSurfaceView(context) { _, _ -> }
        view.surfaceChanged(view.holder, 0, 1920, 1080)
        assertSafeInset(view.layout, 96f, 54f, 96f, 54f)
    }

    @Test
    fun `on a phone the hangar's safe rect is the full rect when no insets arrive`() {
        PersistenceManager(context).resetAllProgress()
        val view = HangarSurfaceView(context) { _, _ -> }
        view.surfaceChanged(view.holder, 0, 1920, 1080)
        assertEquals(view.layout.full, view.layout.safe)
    }
}
