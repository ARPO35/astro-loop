package com.astroloop.game.hangar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.data.PersistenceManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The hangar does not start drawing before it has a surface to draw on (#65).
 *
 * Coming back to the app calls `onResume` before Android has made the new surface. `resume()`
 * used to start the render thread right then, and the thread tried to lock the old, released
 * surface every 100 ms until `surfaceCreated` arrived: up to 0.7 s on the emulator. Android 14
 * refuses those quietly; GrapheneOS on Android 17 logs each one as "Surface has already been
 * released". The game view has always checked for a valid surface first; the hangar now does too,
 * and `surfaceCreated` starts the thread when the surface arrives.
 *
 * Robolectric gives the view no surface, which is exactly the state a resume finds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HangarResumeWithoutSurfaceTest {

    private lateinit var view: HangarSurfaceView

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PersistenceManager(context).resetAllProgress()
        view = HangarSurfaceView(context) { _, _ -> }
    }

    @After
    fun tearDown() {
        view.pause() // stops the thread if a regression started one
    }

    @Test
    fun `resuming before the surface exists does not start the render thread`() {
        view.resume()

        assertFalse(
            "the render thread must wait for surfaceCreated, or it locks a released surface",
            view.renderThreadRunning
        )
    }
}
