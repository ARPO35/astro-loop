package com.astroloop.game.system

import com.astroloop.game.core.GameConfig
import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.LeechParticle
import com.astroloop.game.entity.Ship
import kotlin.math.sqrt
import com.astroloop.game.tuning.KnobsPassives

class VampiricLeecherSystem(
    private val onAsteroidDestroyed: (Asteroid) -> Unit
) {
    val particles = mutableListOf<LeechParticle>()
    private var tickTimer = 0f
    private var spawnTimer = 0f
    private val inRangeAsteroids = HashSet<Asteroid>()

    // Reused rather than allocated per frame — this runs at up to 120Hz.
    private val targetScratch = ArrayList<Asteroid>(32)

    /**
     * Whole-stream opacity, 1 while the player is alive and ramping to 0 through [fadeOut].
     * The per-particle alpha in `renderLeechParticles` is multiplied by this.
     */
    var fadeAlpha: Float = 1f
        private set

    companion object {
        /** How long the stream takes to break off once the player dies. */
        const val DEATH_FADE_SECONDS = 0.25f
        const val TICK_INTERVAL = 0.2f
        /**
         * Defaults for the `vampiric_core.base_range` and `range_per_stack` knobs, measured from the
         * asteroid's edge: 60 at one stack, growing to 140 at five (owner, 2026-10-22). Five stacks
         * reach exactly as far as the flat 140 before; every stack drains the same [LEECH].
         */
        const val BASE_RANGE = 60f
        const val RANGE_PER_STACK = 20f
        /**
         * Default for the `vampiric_core.leech` knob: HP per asteroid per tick, the same at any stack.
         * 0.4 = 2 HP/s per asteroid, at most 12 HP/s with six targets. Doubled from 0.2 on the
         * owner's 2026-10-25 request; the tick interval stays 0.2s so the stream keeps its cadence.
         */
        const val LEECH = 0.4f

        /** How far the drain reaches with [stacks] stacks. Gameplay and the effect ring both read this. */
        fun rangeFor(stacks: Int): Float =
            KnobsPassives.vampiricBaseRange.value +
                KnobsPassives.vampiricRangePerStack.value * (stacks.coerceAtLeast(1) - 1)
        // Rock asteroids have ±30% shape variance — push start past max spike (1.3×)
        private const val EDGE_SPAWN_MULTIPLIER = 1.35f
        const val PARTICLE_SPEED = 1.2f           // t-units/sec; full gap in ~0.83s
        const val PARTICLE_SPAWN_INTERVAL = 0.2f  // 1 new particle per asteroid per 0.2s → ~4 in flight

        /**
         * The most asteroids one tick may drain.
         *
         * Uncapped, the passive scaled with the crowd: a field thick with debris made it unlimited
         * sustain. Six is deliberately generous — the passive has to be able to out-heal Medic in
         * the situation it is built for, a crowd pressing the hull — but it no longer grows without
         * bound as the field fills.
         */
        const val MAX_TARGETS = 6
    }

    fun update(ship: Ship, asteroids: List<Asteroid>, state: GameState, deltaTime: Float) {
        val stacks = state.passiveStacks["vampiric_core"] ?: 0
        updateParticles(ship, asteroids, stacks, deltaTime)

        tickTimer -= deltaTime
        if (tickTimer <= 0f) {
            tickTimer += KnobsPassives.vampiricTick.value
            if (stacks > 0) tick(ship, asteroids, stacks)
        }
    }

    /** Distance from the ship to this asteroid's edge — the same measure [rangeFor] is in. */
    private fun edgeDistance(ship: Ship, asteroid: Asteroid): Float {
        val dx = ship.position.x - asteroid.position.x
        val dy = ship.position.y - asteroid.position.y
        return sqrt(dx * dx + dy * dy) - asteroid.radius
    }

    /**
     * The at-most-[MAX_TARGETS] nearest drainable asteroids, nearest first.
     *
     * The drain tick and the particle stream both read this, so the visual can never show a feed
     * from a rock the tick is not draining. The returned list is a reused buffer — consume it
     * before calling again.
     */
    private fun collectTargets(ship: Ship, asteroids: List<Asteroid>, range: Float): List<Asteroid> {
        val cap = KnobsPassives.vampiricMaxTargets.value
        targetScratch.clear()
        for (asteroid in asteroids) {
            if (!asteroid.isActive || asteroid.fragmentImmunityTimer > 0f) continue
            val dist = edgeDistance(ship, asteroid)
            if (dist > range) continue

            // Insert into the nearest-first scratch buffer, capped at MAX_TARGETS, instead of
            // collecting everything and sorting — that boxed every comparison and delegated to
            // Collections.sort's toArray/copy-back. At most MAX_TARGETS elements are ever held,
            // so the shift on insert/removeAt is cheap and allocation-free.
            if (targetScratch.size < cap) {
                var insertAt = targetScratch.size
                while (insertAt > 0 && edgeDistance(ship, targetScratch[insertAt - 1]) > dist) insertAt--
                targetScratch.add(insertAt, asteroid)
            } else if (dist < edgeDistance(ship, targetScratch[cap - 1])) {
                var insertAt = cap - 1
                while (insertAt > 0 && edgeDistance(ship, targetScratch[insertAt - 1]) > dist) insertAt--
                targetScratch.add(insertAt, asteroid)
                targetScratch.removeAt(cap)
            }
        }
        return targetScratch
    }

    private fun updateParticles(ship: Ship, asteroids: List<Asteroid>, stacks: Int, deltaTime: Float) {
        if (stacks > 0) {
            spawnTimer -= deltaTime
            val newInRange = HashSet<Asteroid>()

            for (asteroid in collectTargets(ship, asteroids, rangeFor(stacks))) {
                val dx = ship.position.x - asteroid.position.x
                val dy = ship.position.y - asteroid.position.y
                val dist = sqrt(dx * dx + dy * dy)

                newInRange.add(asteroid)
                val norm = if (dist > 0f) 1f / dist else 0f
                val ex = asteroid.position.x + dx * norm * asteroid.radius * EDGE_SPAWN_MULTIPLIER
                val ey = asteroid.position.y + dy * norm * asteroid.radius * EDGE_SPAWN_MULTIPLIER

                if (!inRangeAsteroids.contains(asteroid)) {
                    // First frame in range: seed 4 staggered particles so stream looks full immediately
                    for (i in 0..3) particles.add(LeechParticle(ex, ey, i * 0.25f))
                } else if (spawnTimer <= 0f) {
                    // Ongoing: spawn 1 particle per interval to keep stream flowing
                    particles.add(LeechParticle(ex, ey, 0f))
                }
            }

            if (spawnTimer <= 0f) spawnTimer += PARTICLE_SPAWN_INTERVAL
            inRangeAsteroids.clear()
            inRangeAsteroids.addAll(newInRange)
        } else {
            inRangeAsteroids.clear()
        }

        // Advance and expire all particles every frame (after seeding so new particles get their first step)
        advanceParticles(deltaTime)
    }

    /**
     * The player is dead: break the stream off.
     *
     * Called from the death play-out rather than `update`, which stops with `updatePlaying`.
     * Particles keep their motion — a stream that froze mid-air would read as a dropped frame —
     * but the whole thing dims out over [DEATH_FADE_SECONDS] instead of flying home, which would
     * read as the wreck still feeding.
     */
    fun fadeOut(deltaTime: Float) {
        if (fadeAlpha <= 0f) return
        advanceParticles(deltaTime)
        fadeAlpha = (fadeAlpha - deltaTime / DEATH_FADE_SECONDS).coerceAtLeast(0f)
        if (fadeAlpha <= 0f) particles.clear()
    }

    private fun advanceParticles(deltaTime: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.t += PARTICLE_SPEED * deltaTime
            if (p.t >= 1f) iter.remove()
        }
    }

    private fun tick(ship: Ship, asteroids: List<Asteroid>, stacks: Int) {
        val healingHealth = ship.health < ship.maxHealth
        val shieldCeiling = minOf(ship.maxShield, ship.shieldCap)
        // 生命已满时改为吸护盾：按吸血量的一半转化，且不能绕过 Glass Cannon。
        val healingShield = !healingHealth && !ship.shieldRegenDisabled &&
            ship.currentShield < shieldCeiling
        if (!healingHealth && !healingShield) return

        val leech = KnobsPassives.vampiricLeech.value
        var totalHeal = 0f

        for (asteroid in collectTargets(ship, asteroids, rangeFor(stacks))) {
            asteroid.health -= leech
            totalHeal += leech

            if (asteroid.health <= 0f) {
                onAsteroidDestroyed(asteroid)
            }
        }

        if (totalHeal > 0f) {
            if (healingHealth) {
                ship.health = (ship.health + totalHeal).coerceAtMost(ship.maxHealth)
            } else {
                ship.currentShield = (ship.currentShield +
                    totalHeal * GameConfig.VAMPIRIC_SHIELD_LEECH_FACTOR).coerceAtMost(shieldCeiling)
            }
        }
    }

    fun reset() {
        particles.clear()
        inRangeAsteroids.clear()
        tickTimer = 0f
        spawnTimer = 0f
        fadeAlpha = 1f
    }
}
