package com.astroloop.game.weapon

import com.astroloop.game.core.GameState
import com.astroloop.game.data.EnemyType
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.AsteroidSize
import com.astroloop.game.entity.AsteroidType
import com.astroloop.game.entity.EnemyShip
import com.astroloop.game.entity.Entity
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.Firer
import com.astroloop.game.entity.Projectile
import com.astroloop.game.entity.ProjectileType
import com.astroloop.game.util.Vector2
import com.astroloop.game.weapon.firecontrol.FireControl
import com.astroloop.game.weapon.weapons.HomingMissiles
import com.astroloop.game.weapon.weapons.PulseCannon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2

class WeaponLeadTest {

    private fun firer() = object : Firer {
        override val position = Vector2()
        override val velocity = Vector2()
        override val rotation = 0f
        override val radius = 12f
        override val isEnemyFirer = false
        override val isPlayerFirer = true
    }

    private fun movingTarget(x: Float, y: Float, vx: Float, vy: Float, radius: Float = 10f): Entity =
        object : Entity() {
            init {
                position.set(x, y)
                velocity.set(vx, vy)
                this.radius = radius
                maxHealth = 100f
                health = 100f
            }
        }

    @Test
    fun `Pulse Cannon 朝目标未来位置开火`() {
        val target = movingTarget(300f, 0f, 0f, 150f)
        val state = GameState.createNeutral()
        val pool = EntityPool({ Projectile() }, 8)

        PulseCannon().fire(firer(), state, pool, listOf(target), FireControl())

        val shot = pool.getActiveEntities().single()
        val angle = atan2(shot.velocity.y, shot.velocity.x)
        // 直接用 600px/s 打 300px 外的横向目标，提前角约 0.25rad。
        assertTrue("Pulse Cannon 没有提前量：$angle", angle > 0.1f)
        assertEquals(0.25f, angle, 0.08f)
    }

    @Test
    fun `玩家导弹没有提前量并按原版扇形向前发射`() {
        val target = movingTarget(300f, 0f, 0f, 150f)
        val state = GameState.createNeutral()
        val pool = EntityPool({ Projectile() }, 8)

        HomingMissiles().fire(firer(), state, pool, listOf(target), FireControl())

        val shot = pool.getActiveEntities().single()
        assertEquals(0f, shot.velocity.angle(), 0.001f)
    }

    @Test
    fun `玩家导弹齐射至少一发攻击敌机`() {
        val rock = Asteroid().also {
            it.initialize(120f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2())
        }
        val enemy = EnemyShip().also {
            it.initialize(400f, 0f, EnemyType.SCOUT, emptyList())
            it.spawnTime = it.warpInDuration
            it.spawnShieldTimer = 0f
            it.velocity.set(-80f, 0f)
        }
        val state = GameState.createNeutral()
        val pool = EntityPool({ Projectile() }, 32)
        val weapon = HomingMissiles().also { it.level = 3 }

        weapon.fire(firer(), state, pool, listOf(rock, enemy), FireControl())

        val shots = pool.getActiveEntities()
        assertEquals(3, shots.size)
        assertTrue("至少一发应锁定敌机", shots.any { it.target is EnemyShip })
    }

    @Test
    fun `Homing Missiles 的多枚导弹分配到不同目标`() {
        val targets = listOf(
            movingTarget(400f, -100f, 0f, 0f, 12f),
            movingTarget(400f, 0f, 0f, 0f, 12f),
            movingTarget(400f, 100f, 0f, 0f, 12f),
        )
        val state = GameState.createNeutral()
        val pool = EntityPool({ Projectile() }, 32)
        val weapon = HomingMissiles().also { it.level = 5 }

        weapon.fire(firer(), state, pool, targets, FireControl())

        val shots = pool.getActiveEntities()
        assertEquals(5, shots.size)
        assertTrue(shots.all { it.type == ProjectileType.MISSILE })
        assertTrue(shots.all { it.target != null })
        assertEquals(3, shots.mapNotNull { it.target }.distinct().size)
    }
}
