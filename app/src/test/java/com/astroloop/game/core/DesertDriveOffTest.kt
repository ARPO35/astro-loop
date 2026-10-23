package com.astroloop.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #59: the good ending's drive-off softlocked when the player was already near the beach.
 *
 * The drive-off holds the camera and ends once both tanks are [GameSurfaceView.DESERT_DRIVE_OFF_EXIT_MARGIN]
 * below the frame. The tanks can never pass the waterline clamp or the beached landing craft, so a
 * camera held where it stood near the beach put that exit line in the sea and the scene never ended.
 */
class DesertDriveOffTest {

    // A Pixel 9 Pro's design space.
    private val w = 960f
    private val h = 2142f
    private val spawnY = 0f
    private val tankRadius = GameConfig.SHIP_BASE_SIZE

    private val span get() = GameSurfaceView.desertWorldSpan(w, h)
    private val southLimit get() = GameSurfaceView.desertTankSouthLimitY(spawnY, span, tankRadius)

    /** The camera the game follows with: centred on the ship. */
    private fun centredCameraY(shipY: Float, screenHeight: Float) = shipY - screenHeight / 2f

    /** True when a tank parked at [southLimit] counts as having left a frame held at [cameraY]. */
    private fun tankCanLeave(cameraY: Float, screenHeight: Float) =
        southLimit > cameraY + screenHeight + GameSurfaceView.DESERT_DRIVE_OFF_EXIT_MARGIN

    @Test
    fun `holding the camera where it stood at the beach is the softlock`() {
        // Documents the bug: parked at the limit, the old hold leaves the exit line out of reach.
        assertTrue(!tankCanLeave(centredCameraY(southLimit, h), h))
    }

    @Test
    fun `the tanks can always leave the held frame, even starting at the limit`() {
        for (screenHeight in listOf(h, w)) {          // upright and rotated
            val held = GameSurfaceView.desertDriveOffCameraY(
                centredCameraY(southLimit, screenHeight), screenHeight, southLimit
            )
            assertTrue("rotated=${screenHeight == w}", tankCanLeave(held, screenHeight))
        }
    }

    @Test
    fun `with room to spare the shot is unchanged`() {
        val camera = centredCameraY(spawnY, h)       // a tank still at the spawn point
        assertEquals(camera, GameSurfaceView.desertDriveOffCameraY(camera, h, southLimit), 0f)
    }

    @Test
    fun `the held camera only ever moves north`() {
        for (shipY in listOf(spawnY - 2000f, spawnY, southLimit - 600f, southLimit)) {
            val camera = centredCameraY(shipY, h)
            assertTrue(GameSurfaceView.desertDriveOffCameraY(camera, h, southLimit) <= camera)
        }
    }

    /**
     * Drives one tank south the way the drive-off does: speed from [GameSurfaceView.desertDriveOffSpeed],
     * then the shore clamp. Returns how many frames it spent pressed against the shore while
     * still moving (dust and track marks: speed above 5) and its final speed.
     */
    private fun driveSouth(startGap: Float, startSpeed: Float, dt: Float): Pair<Int, Float> {
        val stopY = 0f
        var y = stopY - startGap
        var speed = startSpeed
        var strainingFrames = 0
        repeat((12f / dt).toInt()) {
            speed = GameSurfaceView.desertDriveOffSpeed(speed, stopY - y, 1f, dt)
            y = minOf(y + speed * dt, stopY)
            if (y >= stopY - 0.5f && speed > 5f) strainingFrames++
        }
        return strainingFrames to speed
    }

    @Test
    fun `a tank that reaches the shore parks instead of grinding against it`() {
        for (dt in listOf(1f / 60f, 1f / 120f, 1f / 30f)) {
            for (gap in listOf(0f, 20f, 150f, 600f, 3000f)) {
                for (startSpeed in listOf(0f, 200f, GameSurfaceView.DESERT_DRIVE_OFF_SPEED)) {
                    val (straining, finalSpeed) = driveSouth(gap, startSpeed, dt)
                    val case = "dt=$dt gap=$gap start=$startSpeed"
                    assertTrue("$case strained for $straining frames", straining <= 1)
                    assertEquals(case, 0f, finalSpeed, 0f)
                }
            }
        }
    }

    @Test
    fun `away from the shore the tanks keep the drive-off's pace`() {
        var speed = 0f
        repeat(300) { speed = GameSurfaceView.desertDriveOffSpeed(speed, 5000f, 1f, 1f / 60f) }
        assertEquals(GameSurfaceView.DESERT_DRIVE_OFF_SPEED, speed, 0f)
    }

    @Test
    fun `a tank in the landing craft's column stops at its bow, beside it at the waterline`() {
        val craftTop = GameSurfaceView.desertLandingCraftTopY(spawnY, span)
        val waterStop = GameSurfaceView.desertWaterlineY(spawnY, span) - 25f
        val north = craftTop - 300f
        assertEquals(craftTop - tankRadius,
            GameSurfaceView.desertTankStopY(GameSurfaceView.DESERT_LANDING_CRAFT_X, north, spawnY, span, tankRadius), 0f)
        assertEquals(waterStop, GameSurfaceView.desertTankStopY(-400f, north, spawnY, span, tankRadius), 0f)
        assertTrue("every stop is at or past the line the camera plans for",
            GameSurfaceView.desertTankStopY(GameSurfaceView.DESERT_LANDING_CRAFT_X, north, spawnY, span, tankRadius) >= southLimit)
    }

    @Test
    fun `the limit stops above both the waterline and the landing craft`() {
        val waterline = GameSurfaceView.desertWaterlineY(spawnY, span)
        val craftTop = GameSurfaceView.desertLandingCraftTopY(spawnY, span)
        assertTrue(southLimit <= waterline - 25f)
        assertTrue(southLimit <= craftTop - tankRadius)
    }

    @Test
    fun `a rotation mid drive-off still lets a parked tank leave once the hold is recomputed`() {
        val landscapeH = w   // rotated 2142x960: the frame is 960 tall
        val startCamera = centredCameraY(southLimit, landscapeH)
        // The old, once-only hold for the short frame, then the phone turns upright:
        val staleHold = GameSurfaceView.desertDriveOffCameraY(startCamera, landscapeH, southLimit)
        assertTrue("stale hold softlocks the tall frame", !tankCanLeave(staleHold, h))
        val recomputed = GameSurfaceView.desertDriveOffCameraY(startCamera, h, southLimit)
        assertTrue(tankCanLeave(recomputed, h))
    }

    @Test
    fun `the drive-off is done when both tanks are past the exit line, or after the time cap`() {
        val cam = 0f
        val past = cam + h + GameSurfaceView.DESERT_DRIVE_OFF_EXIT_MARGIN + 1f
        assertTrue(GameSurfaceView.desertDriveOffDone(past, past, cam, h, 0f))
        assertTrue(!GameSurfaceView.desertDriveOffDone(past, 100f, cam, h, 0f))
        assertTrue(!GameSurfaceView.desertDriveOffDone(100f, past, cam, h, 0f))
        val cap = GameSurfaceView.DESERT_DRIVE_OFF_MAX_SECONDS
        assertTrue(!GameSurfaceView.desertDriveOffDone(100f, 100f, cam, h, cap - 0.1f))
        assertTrue(GameSurfaceView.desertDriveOffDone(100f, 100f, cam, h, cap))
    }

    @Test
    fun `a tank turning south that may slide into the craft column brakes for the bow early`() {
        val craftTop = GameSurfaceView.desertLandingCraftTopY(spawnY, span)
        val north = craftTop - 300f
        val oldEdge = GameSurfaceView.DESERT_LANDING_CRAFT_HALF_WIDTH + tankRadius
        val x = GameSurfaceView.DESERT_LANDING_CRAFT_X + oldEdge + 10f   // outside the old column
        assertEquals(craftTop - tankRadius, GameSurfaceView.desertTankStopY(x, north, spawnY, span, tankRadius), 0f)
        val beyond = GameSurfaceView.DESERT_LANDING_CRAFT_X + oldEdge + GameSurfaceView.DESERT_DRIVE_OFF_TURN_DRIFT + 10f
        assertEquals(GameSurfaceView.desertWaterlineY(spawnY, span) - 25f,
            GameSurfaceView.desertTankStopY(beyond, north, spawnY, span, tankRadius), 0f)
    }
}
