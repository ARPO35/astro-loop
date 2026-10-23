package com.astroloop.game.system

import com.astroloop.game.data.EnemyType
import com.astroloop.game.entity.EnemyShip
import com.astroloop.game.entity.Entity
import com.astroloop.game.entity.Projectile
import com.astroloop.game.entity.ProjectileType
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HomingRetargetTest {

    private fun target(x: Float, y: Float, active: Boolean = true): Entity = object : Entity() {
        init {
            position.set(x, y)
            radius = 10f
            isActive = active
        }
    }

    private fun missile(target: Entity?, playerGuided: Boolean = true): Projectile = Projectile().also {
        it.initialize(0f, 0f, 100f, 0f, ProjectileType.MISSILE, 10f, 4f)
        it.homingStrength = 3f
        it.isEnemyProjectile = !playerGuided
        it.playerGuidance = playerGuided
        it.target = target
    }

    @Test
    fun `player missile retargets the nearest live target after its target dies`() {
        val dead = target(0f, 0f, active = false)
        val near = target(50f, 0f)
        val far = target(200f, 0f)
        val shot = missile(dead)

        MovementSystem().retargetHomingProjectiles(listOf(shot), listOf(far, near))

        assertSame(near, shot.target)
    }

    @Test
    fun `a non-player missile does not retarget`() {
        val dead = target(0f, 0f, active = false)
        val shot = missile(dead, playerGuided = false)

        MovementSystem().retargetHomingProjectiles(listOf(shot), listOf(target(50f, 0f)))

        assertSame(dead, shot.target)
    }

    @Test
    fun `an active target is kept`() {
        val live = target(50f, 0f)
        val nearer = target(10f, 0f)
        val shot = missile(live)

        MovementSystem().retargetHomingProjectiles(listOf(shot), listOf(nearer))

        assertSame(live, shot.target)
    }

    @Test
    fun `a warping target counts as lost`() {
        val warping = EnemyShip().also {
            it.initialize(10f, 0f, EnemyType.SCOUT, emptyList())
            it.spawnTime = 0f
        }
        val live = target(100f, 0f)
        val shot = missile(warping)

        MovementSystem().retargetHomingProjectiles(listOf(shot), listOf(live))

        assertSame(live, shot.target)
    }

    @Test
    fun `a non-homing torpedo is left alone`() {
        val dead = target(0f, 0f, active = false)
        val shot = Projectile().also {
            it.initialize(0f, 0f, 100f, 0f, ProjectileType.TORPEDO, 10f, 4f)
            it.homingStrength = 0f
            it.playerGuidance = true
            it.target = dead
        }

        MovementSystem().retargetHomingProjectiles(listOf(shot), listOf(target(50f, 0f)))

        assertSame(dead, shot.target)
    }

    @Test
    fun `no candidates clears a lost target`() {
        val dead = target(0f, 0f, active = false)
        val shot = missile(dead)

        MovementSystem().retargetHomingProjectiles(listOf(shot), emptyList())

        assertNull(shot.target)
    }
}
