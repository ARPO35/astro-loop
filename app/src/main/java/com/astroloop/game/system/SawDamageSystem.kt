package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.core.SoundManager
import com.astroloop.game.data.ShipDefinitions
import com.astroloop.game.entity.*
import com.astroloop.game.weapon.weapons.EnergySaw
import com.astroloop.game.weapon.weapons.WarpSaw
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SawDamageSystem(
    private val state: GameState,
    private val ship: Ship,
    private val weaponSystem: WeaponSystem,
    private val visualEffects: VisualEffectManager,
    private val applyDamageModifiers: (Float) -> Pair<Float, Boolean>,
    private val onAsteroidDestroyed: (Asteroid) -> Unit,
    private val onEnemyDestroyed: (EnemyShip) -> Unit
) {
    val sawSparks = mutableListOf<SawSpark>()
    private val sawDamageCooldowns = mutableMapOf<Entity, Float>()
    private var sawSoundCooldown = 0f

    private companion object {
        const val SAW_SOUND_COOLDOWN = 0.15f
    }

    fun reset() {
        sawSparks.clear()
        sawDamageCooldowns.clear()
        sawSoundCooldown = 0f
    }

    fun updateSparks(deltaTime: Float) {
        val sparkIterator = sawSparks.iterator()
        while (sparkIterator.hasNext()) {
            val spark = sparkIterator.next()
            spark.x += spark.vx * deltaTime
            spark.y += spark.vy * deltaTime
            spark.age += deltaTime
            if (spark.age >= spark.lifetime) sparkIterator.remove()
        }
    }

    private fun isOnScreen(entity: Entity): Boolean {
        val margin = 50f
        val cameraX = ship.position.x - state.screenWidth / 2f
        val cameraY = ship.position.y - state.screenHeight / 2f
        return entity.position.x >= cameraX - margin &&
               entity.position.x <= cameraX + state.screenWidth + margin &&
               entity.position.y >= cameraY - margin &&
               entity.position.y <= cameraY + state.screenHeight + margin
    }

    fun update(deltaTime: Float, enemies: List<EnemyShip>, asteroids: List<Asteroid>) {
        if (sawSoundCooldown > 0f) sawSoundCooldown -= deltaTime

        // Update per-entity damage cooldowns
        val cooldownIter = sawDamageCooldowns.iterator()
        while (cooldownIter.hasNext()) {
            val entry = cooldownIter.next()
            entry.setValue(entry.value - deltaTime)
            if (entry.value <= 0f) cooldownIter.remove()
        }

        val weaponColor = ShipDefinitions.getWeaponColor("energy_saw", state.isCorruptionRun)

        // Check each active saw weapon
        for (weaponId in weaponSystem.getActiveWeaponIds()) {
            val weapon = weaponSystem.getWeapon(weaponId) ?: continue

            val discPositions: List<Pair<Float, Float>>
            val discR: Float
            val tickRate: Float
            val damage: Float

            when (weapon) {
                is EnergySaw -> {
                    discPositions = weapon.getDiscPositions(ship.position.x, ship.position.y, ship.rotation)
                    discR = weapon.discRadius
                    tickRate = weapon.getTickRate()
                    damage = weapon.getDamage(state)
                }
                is WarpSaw -> {
                    weapon.updateDiscs(deltaTime, enemies, asteroids, ship.position.x, ship.position.y, ship.rotation)
                    discPositions = weapon.getDiscPositions(ship.position.x, ship.position.y, ship.rotation)
                    discR = weapon.discRadius
                    tickRate = weapon.getTickRate()
                    damage = weapon.getDamage(state)
                }
                else -> continue
            }
            // I2: getDiscPositions() allocates a new list each call — this is pre-existing in the
            // weapon API and not introduced by this task. Changing the weapon API is out of scope.

            val (effectiveDamage, isCrit) = applyDamageModifiers(damage)

            // I3: discIndex is never used — iterate discPositions directly
            for (discPos in discPositions) {
                val discX = discPos.first
                val discY = discPos.second

                // Damage enemies
                for (enemy in enemies) {
                    if (!enemy.isActive || enemy.isWarping) continue
                    if (!enemy.isCrewmate && !isOnScreen(enemy)) continue
                    val dx = enemy.position.x - discX
                    val dy = enemy.position.y - discY
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < discR + enemy.radius) {
                        // M2: single map lookup via elvis instead of double lookup
                        if ((sawDamageCooldowns[enemy] ?: 0f) <= 0f) {
                            state.telemetryDamageByWeapon[weaponId] = (state.telemetryDamageByWeapon[weaponId] ?: 0f) + effectiveDamage
                            state.telemetryTotalDamageDealt += effectiveDamage
                            if (isCrit) { state.telemetryCritsThisMinute++; state.telemetryCritsTotal++ }
                            val destroyed = enemy.takeDamage(effectiveDamage)
                            sawDamageCooldowns[enemy] = tickRate

                            if (!enemy.perfectDodge && !enemy.isSpawnShielded) {
                                visualEffects.addDamageNumber(
                                    enemy.position.x + (kotlin.random.Random.nextFloat() - 0.5f) * 20f,
                                    enemy.position.y - enemy.radius,
                                    effectiveDamage.toInt(),
                                    weaponColor,
                                    isCrit
                                )
                            }
                            spawnSawSparks(discX, discY, enemy.position.x, enemy.position.y, weaponId, playSound = true)

                            if (destroyed) {
                                onEnemyDestroyed(enemy)
                            }
                        }
                    }
                }

                // Damage asteroids
                for (asteroid in asteroids) {
                    if (!asteroid.isActive) continue
                    val dx = asteroid.position.x - discX
                    val dy = asteroid.position.y - discY
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < discR + asteroid.radius) {
                        // M2: single map lookup via elvis
                        if ((sawDamageCooldowns[asteroid] ?: 0f) <= 0f) {
                            state.telemetryDamageByWeapon[weaponId] = (state.telemetryDamageByWeapon[weaponId] ?: 0f) + effectiveDamage
                            state.telemetryTotalDamageDealt += effectiveDamage
                            if (isCrit) { state.telemetryCritsThisMinute++; state.telemetryCritsTotal++ }
                            val destroyed = asteroid.takeDamage(effectiveDamage)
                            sawDamageCooldowns[asteroid] = tickRate

                            visualEffects.addDamageNumber(
                                asteroid.position.x + (kotlin.random.Random.nextFloat() - 0.5f) * 20f,
                                asteroid.position.y - asteroid.radius,
                                effectiveDamage.toInt(),
                                weaponColor,
                                isCrit
                            )
                            spawnSawSparks(discX, discY, asteroid.position.x, asteroid.position.y, weaponId, playSound = false)

                            if (destroyed) {
                                onAsteroidDestroyed(asteroid)
                            }
                        }
                    }
                }
            }
        }
    }

    // playSound is true only for enemy contact — the saw is silent against asteroids (by request).
    private fun spawnSawSparks(discX: Float, discY: Float, targetX: Float, targetY: Float, weaponId: String, playSound: Boolean) {
        if (playSound && sawSoundCooldown <= 0f) {
            SoundManager.playSFX("sfx_weapon_$weaponId", SoundManager.getWeaponSfxVolume(weaponId))
            sawSoundCooldown = SAW_SOUND_COOLDOWN
        }
        val angle = atan2(targetY - discY, targetX - discX)
        for (i in 0 until 5) {
            // M1: use kotlin.random instead of Math.random()
            val sparkAngle = angle + (kotlin.random.Random.nextFloat() - 0.5f) * PI.toFloat() * 0.5f
            val speed = 100f + kotlin.random.Random.nextFloat() * 150f
            sawSparks.add(SawSpark(
                x = (discX + targetX) / 2f,
                y = (discY + targetY) / 2f,
                vx = cos(sparkAngle) * speed,
                vy = sin(sparkAngle) * speed
            ))
        }
    }
}
