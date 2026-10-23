package com.astroloop.game.system

import com.astroloop.game.entity.Entity
import com.astroloop.game.entity.Projectile
import kotlin.math.roundToInt

/**
 * Everything a projectile carries that a mirrored weapon must reproduce, as one comparable line.
 * Leaves out colour (enemy shots are red) and position (some weapons scatter their spawn point).
 * [damageScale] scales this side's damage (the Phantom knob, applied to the reference side);
 * [withSpeed] is false for weapons that randomise speed (Scatter Shot).
 */
internal fun projectileSignature(
    p: Projectile,
    target: Entity?,
    damageScale: Float = 1f,
    withSpeed: Boolean = true,
): String = listOf(
    p.type, p.weaponId, p.isEnemyProjectile,
    p.damage * damageScale, p.radius, p.length, p.width, p.lifetime,
    p.piercing, p.maxPierces, p.homingStrength, p.target === target,
    p.explodeOnDeath, p.explosionRadius, p.explosionDamage * damageScale, p.proximityFuse,
    p.bombletCount, p.bombletDamage * damageScale, p.bombletExplosionRadius,
    p.hasFragments, p.fragmentDamage * damageScale, p.orbitRadius,
    if (withSpeed) (p.velocity.length() * 10f).roundToInt() else "-",
).joinToString(" ")
