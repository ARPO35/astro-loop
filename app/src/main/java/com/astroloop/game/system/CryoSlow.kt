package com.astroloop.game.system

import com.astroloop.game.entity.Asteroid
import com.astroloop.game.util.Vector2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Cryo Field's slow, which deepens the closer something gets (#57; owner, 2026-10-22).
 *
 * Full speed where a rock or ship first touches the field, falling to `1 - centreSlow` at the
 * ship's centre. Nothing reaches the centre, since it hits the hull first, so the centre figure
 * is where the slope ends rather than a speed anything is seen at.
 *
 * The slow is always a cap against a fixed speed, never a multiply on the current one, so it
 * cannot compound from frame to frame.
 */
object CryoSlow {

    /**
     * The speed multiplier for something whose centre is [distance] from the ship's, where it first
     * touches the field at [edgeDistance] (the field's radius plus its own). A [curve] of 1 slows
     * evenly with depth; above 1 the slow holds off until it is close to the hull.
     */
    fun speedFactor(distance: Float, edgeDistance: Float, centreSlow: Float, curve: Float): Float {
        if (edgeDistance <= 0f || distance >= edgeDistance) return 1f
        val depth = (1f - distance / edgeDistance).coerceIn(0f, 1f)
        return 1f - centreSlow * depth.pow(curve)
    }

    /** Slows [velocity] to [maxSpeed] if it is faster, keeping its direction. */
    fun capSpeed(velocity: Vector2, maxSpeed: Float) {
        val speed = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
        if (speed > maxSpeed && speed > 0.001f) {
            val scale = maxSpeed / speed
            velocity.x *= scale
            velocity.y *= scale
        }
    }

    /**
     * Slows a rock inside the field. The cap is against the speed it had the first time it met the
     * field, so a rock keeps the slowest speed it has reached, and passing through twice does not
     * slow it twice.
     */
    fun slowRock(rock: Asteroid, distance: Float, edgeDistance: Float, centreSlow: Float, curve: Float) {
        if (rock.cryoBaseSpeed <= 0f) rock.cryoBaseSpeed = rock.velocity.length()
        capSpeed(rock.velocity, rock.cryoBaseSpeed * speedFactor(distance, edgeDistance, centreSlow, curve))
    }
}
