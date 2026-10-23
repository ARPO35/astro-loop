package com.astroloop.game.weapon

import com.astroloop.game.entity.Entity
import com.astroloop.game.entity.Projectile
import com.astroloop.game.entity.ProjectileType
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectileHomingTest {

    @Test
    fun `比例制导能拦截横向移动目标`() {
        val target = object : Entity() {
            init {
                position.set(400f, 0f)
                velocity.set(0f, 120f)
                radius = 12f
            }
        }
        val missile = Projectile().also {
            it.initialize(0f, 0f, 600f, 0f, ProjectileType.MISSILE, 10f, 4f)
            it.homingStrength = 3.5f
            it.target = target
        }

        var hit = false
        for (i in 0 until 480) {
            missile.update(1f / 120f)
            target.update(1f / 120f)
            if (missile.collidesWith(target)) {
                hit = true
                break
            }
        }

        assertTrue("导弹在 4 秒内没有命中横向目标", hit)
    }

    @Test
    fun `没有目标时导弹保持直线`() {
        val missile = Projectile().also {
            it.initialize(0f, 0f, 600f, 0f, ProjectileType.MISSILE, 10f, 4f)
            it.homingStrength = 3.5f
        }

        missile.update(0.5f)

        assertTrue(missile.velocity.y == 0f)
        assertTrue(missile.velocity.x > 0f)
    }
}
