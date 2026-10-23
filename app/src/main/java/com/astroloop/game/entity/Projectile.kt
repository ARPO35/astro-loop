package com.astroloop.game.entity

import com.astroloop.game.util.Vector2
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ProjectileType {
    BULLET,         // Standard projectile
    MISSILE,        // Homing projectile
    BEAM,           // Continuous laser segment
    ORBITER,        // Orbiting projectile
    MINE,           // Stationary explosive
    LIGHTNING,      // Chain lightning segment
    TORPEDO,        // Slow, high damage
    FLAK,           // Exploding shell
    GRAVITY,        // Gravity well effect
    PLASMA,         // Plasma whip segment
    BOMBLET,        // Cluster bomb sub-munition
    FRAGMENT,       // L5 cluster bomb fragment
    RECALL_SHOT     // Boss railgun shot that pauses and returns
}

class Projectile : Entity() {

    var type: ProjectileType = ProjectileType.BULLET
    var damage: Float = 10f
    var lifetime: Float = 2f
    var age: Float = 0f
    var piercing: Boolean = false
    var pierceCount: Int = 0
    var maxPierces: Int = 1
    var ownerId: Int = 0  // To identify source
    var weaponId: String = ""
    var isEnemyProjectile: Boolean = false  // True if fired by enemy ship
    var ignoresSpawnShield: Boolean = false

    // For homing projectiles
    var target: Entity? = null
    var homingStrength: Float = 0f
    /** 只有玩家弹丸使用比例制导；敌机/舰队/僚机保持原版追瞄。 */
    var playerGuidance: Boolean = false

    // For orbiters
    var orbitAngle: Float = 0f
    var orbitRadius: Float = 0f
    var orbitSpeed: Float = 0f
    var orbitCenter: Vector2? = null

    // For attached beams like Plasma Lance
    var beamOrigin: Vector2? = null

    // For chain lightning
    var bounceCount: Int = 0
    var maxBounces: Int = 3
    var hitEntities: MutableSet<Entity> = mutableSetOf()

    // For explosions
    var explodeOnDeath: Boolean = false
    var explosionRadius: Float = 0f
    var explosionDamage: Float = 0f
    var proximityFuse: Boolean = false  // Flak: detonate when near enemy

    // For cluster bomb bomblets/fragments
    var bombletCount: Int = 0
    var bombletDamage: Float = 0f
    var bombletExplosionRadius: Float = 0f
    var hasFragments: Boolean = false
    var fragmentDamage: Float = 0f
    var isVisualOnly: Boolean = false

    // Trail-emitting projectiles (napalm trail, scatter trails)
    var leavesTrail: Boolean = false
    var trailTimer: Float = 0f
    var trailInterval: Float = 0.4f
    var trailRadius: Float = 30f
    var trailDamage: Float = 5f
    var trailDuration: Float = 1.5f

    // Gravity sink mines (Space Mines L5)
    var gravitySink: Boolean = false
    var gravitySinkTimer: Float = 0f

    // Recall shot data
    var isRecalling: Boolean = false
    var recallPauseTimer: Float = 0f
    var recallRetargeted: Boolean = false
    var ricochetCount: Int = 0

    // Deceleration
    var decelerationRate: Float = 0f  // Speed reduction per second; 0 = no decel

    // Visual
    var length: Float = 10f
    var width: Float = 2f
    var color: Int = 0xFFFFFF00.toInt()
    var fadeAlpha: Float = 1f  // For fade-in effects (0→1 over time)

    // For beam weapons - store rotation angle when velocity is 0
    var beamAngle: Float = 0f

    // Flag to trigger disappear effect
    var expiredNaturally: Boolean = false

    // Crit flag for damage number display (used by instant-hit weapons like SolarStorm)
    var isCrit: Boolean = false
    var shouldFadeOut: Boolean = false  // Force fade-out and expiry; bypasses RECALL_SHOT exemption

    override fun update(deltaTime: Float) {
        age += deltaTime

        if (fadeAlpha < 1f) {
            fadeAlpha = (fadeAlpha + deltaTime * 2f).coerceAtMost(1f)  // 0.5s fade
        }

        // Fade out in last 0.5 seconds of life (orbiters always; others when force-expired)
        if (type == ProjectileType.ORBITER || shouldFadeOut) {
            val remaining = lifetime - age
            if (remaining < 0.5f) {
                fadeAlpha = (remaining / 0.5f).coerceIn(0f, 1f)
            }
        }

        // shouldFadeOut bypasses the RECALL_SHOT lifetime exemption
        if (shouldFadeOut && age >= lifetime) {
            isActive = false
            return
        }

        if (age >= lifetime && type != ProjectileType.RECALL_SHOT) {
            expiredNaturally = true  // Flag for visual effect
            isActive = false
            return
        }

        // Apply deceleration before position update
        if (decelerationRate > 0f) {
            val speed = velocity.length()
            if (speed > 0f) {
                val newSpeed = (speed - decelerationRate * deltaTime).coerceAtLeast(0f)
                velocity.mul(newSpeed / speed)
                // Zero out deceleration once stopped so mine repulsion can work
                if (newSpeed == 0f && type == ProjectileType.MINE) {
                    decelerationRate = 0f
                }
            }
        }

        when (type) {
            ProjectileType.MISSILE, ProjectileType.TORPEDO -> updateHoming(deltaTime)
            ProjectileType.ORBITER -> updateOrbiter(deltaTime)
            ProjectileType.BEAM -> {
                // Update attached beam position from origin
                beamOrigin?.let { origin ->
                    position.set(origin)
                }
                // Reset hit tracking each frame so beams can deal continuous damage
                hitEntities.clear()
            }
            ProjectileType.PLASMA -> {
                super.update(deltaTime)
                // If attached to a ship via orbitCenter, follow its movement
                orbitCenter?.let { center ->
                    beamOrigin?.let { origin ->
                        position.add(center.x - origin.x, center.y - origin.y)
                        origin.set(center)
                    }
                }
            }
            else -> super.update(deltaTime)
        }
    }

    private fun updateHoming(deltaTime: Float) {
        val t = target
        if (playerGuidance) {
            updateProportionalNavigation(t, deltaTime)
        } else {
            updatePursuit(t, deltaTime)
        }
        super.update(deltaTime)
    }

    private fun updateProportionalNavigation(t: Entity?, deltaTime: Float) {
        if (t == null || !t.isActive || homingStrength <= 0f) return
        val dx = t.position.x - position.x
        val dy = t.position.y - position.y
        val rangeSq = dx * dx + dy * dy
        val speed = velocity.length()

        if (rangeSq > 1f && speed > 1f) {
            val range = sqrt(rangeSq)
            val relVx = t.velocity.x - velocity.x
            val relVy = t.velocity.y - velocity.y
            // 接近速度与视线角速度，都是比例制导的输入。
            val closing = -(dx * relVx + dy * relVy) / range
            val losRate = (dx * relVy - dy * relVx) / rangeSq
            val maxTurn = homingStrength * deltaTime

            val desiredTurn = if (closing > MIN_CLOSING_SPEED && range > ENDGAME_RANGE) {
                // 纯比例制导：转向角速度 = N · Vc · λdot / Vm。
                NAVIGATION_CONSTANT * closing * losRate / speed * deltaTime
            } else {
                // 目标正在远离、横向漂移，或已进入末段：退化为追瞄，保证最终收敛。
                val angleError = wrapAngle(atan2(dy, dx) - velocity.angle())
                angleError * PURSUIT_GAIN * deltaTime
            }

            val turn = desiredTurn.coerceIn(-maxTurn, maxTurn)
            if (turn != 0f) {
                velocity.set(Vector2.fromAngle(velocity.angle() + turn, speed))
            }
        }
    }

    /** 原版制导：始终朝目标当前位置转向，敌机、舰队和僚机使用。 */
    private fun updatePursuit(t: Entity?, deltaTime: Float) {
        if (t == null || !t.isActive || homingStrength <= 0f) return
        val dx = t.position.x - position.x
        val dy = t.position.y - position.y
        val speed = velocity.length()
        if (dx * dx + dy * dy <= 1f || speed <= 1f) return

        val angleError = wrapAngle(atan2(dy, dx) - velocity.angle())
        val maxTurn = homingStrength * deltaTime
        val turn = angleError.coerceIn(-maxTurn, maxTurn)
        if (turn != 0f) {
            velocity.set(Vector2.fromAngle(velocity.angle() + turn, speed))
        }
    }

    private fun wrapAngle(angle: Float): Float {
        var wrapped = angle
        while (wrapped > PI) wrapped -= (2f * PI).toFloat()
        while (wrapped < -PI) wrapped += (2f * PI).toFloat()
        return wrapped
    }

    private fun updateOrbiter(deltaTime: Float) {
        orbitCenter?.let { center ->
            orbitAngle += orbitSpeed * deltaTime
            position.x = center.x + cos(orbitAngle) * orbitRadius
            position.y = center.y + sin(orbitAngle) * orbitRadius
        }
        // One hit per pass: forget a target once this orb has left it (or it is gone), so the
        // orb hits it again on its next time round. Forgetting every target on every frame made
        // the orb hit on each frame it overlapped something, so its damage followed the frame rate.
        val iterator = hitEntities.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next()
            if (!entity.isActive || !collidesWith(entity)) iterator.remove()
        }
    }

    /**
     * True when this piercing shot has already hit [entity] and may not hit it again (owner,
     * 2026-10-22). A piercing shot hits each rock or ship once; the asteroid path has always
     * enforced that through [onHit], and the enemy and boss paths check this before dealing damage.
     * An orb forgets a target once it has left it, so it hits once per pass. A beam clears its list
     * every frame, so it keeps hitting.
     */
    fun alreadyHit(entity: Entity): Boolean = piercing && hitEntities.contains(entity)

    fun initialize(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        projectileType: ProjectileType,
        projectileDamage: Float,
        projectileLifetime: Float
    ) {
        position.set(x, y)
        velocity.set(vx, vy)
        type = projectileType
        damage = projectileDamage
        lifetime = projectileLifetime
        age = 0f
        isActive = true
        piercing = false
        pierceCount = 0
        maxPierces = 1
        isEnemyProjectile = false
        ignoresSpawnShield = false
        target = null
        homingStrength = 0f
        playerGuidance = false
        orbitCenter = null
        bounceCount = 0
        hitEntities.clear()
        explodeOnDeath = false

        // Set visual properties based on type
        when (type) {
            ProjectileType.BULLET -> {
                length = 8f
                width = 2f
                radius = 4f
            }
            ProjectileType.MISSILE -> {
                length = 12f
                width = 4f
                radius = 6f
            }
            ProjectileType.TORPEDO -> {
                length = 16f
                width = 6f
                radius = 8f
            }
            ProjectileType.ORBITER -> {
                length = 6f
                width = 6f
                radius = 6f
            }
            ProjectileType.MINE -> {
                length = 10f
                width = 10f
                radius = 10f
            }
            ProjectileType.BEAM -> {
                length = 20f
                width = 3f
                radius = 3f
            }
            ProjectileType.LIGHTNING -> {
                length = 15f
                width = 2f
                radius = 5f
            }
            ProjectileType.FLAK -> {
                length = 8f
                width = 4f
                radius = 5f
            }
            ProjectileType.GRAVITY -> {
                length = 20f
                width = 20f
                radius = 20f
            }
            ProjectileType.PLASMA -> {
                length = 12f
                width = 4f
                radius = 6f
            }
            ProjectileType.BOMBLET -> {
                length = 5f
                width = 5f
                radius = 5f
            }
            ProjectileType.FRAGMENT -> {
                length = 3f
                width = 3f
                radius = 3f
            }
            ProjectileType.RECALL_SHOT -> {
                length = 14f
                width = 3f
                radius = 5f
            }
        }

        fadeAlpha = 1f
        beamAngle = 0f
        expiredNaturally = false
        isCrit = false
        shouldFadeOut = false
    }

    fun onHit(entity: Entity): Boolean {
        if (piercing) {
            if (hitEntities.contains(entity)) {
                return false  // Already hit this one
            }
            hitEntities.add(entity)
            pierceCount++
            if (pierceCount >= maxPierces) {
                isActive = false
            }
            return true
        } else {
            isActive = false
            return true
        }
    }

    fun canBounce(): Boolean = bounceCount < maxBounces

    fun recordBounce() {
        bounceCount++
    }

    override fun reset() {
        super.reset()
        type = ProjectileType.BULLET
        damage = 10f
        lifetime = 2f
        age = 0f
        piercing = false
        pierceCount = 0
        maxPierces = 1
        target = null
        homingStrength = 0f
        playerGuidance = false
        orbitAngle = 0f
        orbitRadius = 0f
        orbitSpeed = 0f
        orbitCenter = null
        beamOrigin = null
        bounceCount = 0
        maxBounces = 3
        hitEntities.clear()
        explodeOnDeath = false
        explosionRadius = 0f
        explosionDamage = 0f
        proximityFuse = false
        bombletCount = 0
        bombletDamage = 0f
        bombletExplosionRadius = 0f
        hasFragments = false
        fragmentDamage = 0f
        isVisualOnly = false

        leavesTrail = false
        trailTimer = 0f
        trailInterval = 0.4f
        trailRadius = 30f
        trailDamage = 5f
        trailDuration = 1.5f
        gravitySink = false
        gravitySinkTimer = 0f
        decelerationRate = 0f
        isRecalling = false
        recallPauseTimer = 0f
        recallRetargeted = false
        ricochetCount = 0
        weaponId = ""
        isEnemyProjectile = false
        ignoresSpawnShield = false
        fadeAlpha = 1f
        beamAngle = 0f
        expiredNaturally = false
        isCrit = false
        shouldFadeOut = false
    }

    companion object {
        /** A proximity-fused shell bursts this far from a target's edge (flak, the player's and enemies'). */
        const val PROXIMITY_FUSE_RANGE = 40f
        const val GRAVITY_SINK_DURATION = 0.3f
        const val GRAVITY_SINK_RADIUS = 150f
        const val GRAVITY_SINK_FORCE = 180f

        /** 比例制导的导航常数 N，3 是拦截匀速目标的常用值。 */
        private const val NAVIGATION_CONSTANT = 3f
        /** 追瞄回退时的角速度增益（1/s）。 */
        private const val PURSUIT_GAIN = 6f
        /** 低于这个接近速度就认为比例制导不可靠，改用追瞄。 */
        private const val MIN_CLOSING_SPEED = 20f
        /** 末段距离内强制追瞄，避免比例制导在极近距离抖动。 */
        private const val ENDGAME_RANGE = 100f
    }
}
