package com.astroloop.game.weapon

import com.astroloop.game.data.EnemyType
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.AsteroidSize
import com.astroloop.game.entity.AsteroidType
import com.astroloop.game.entity.Boss
import com.astroloop.game.entity.EnemyShip
import com.astroloop.game.entity.Entity
import com.astroloop.game.entity.Firer
import com.astroloop.game.util.Vector2
import com.astroloop.game.weapon.firecontrol.FireControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FireControlTest {

    private fun firer(x: Float = 0f, y: Float = 0f) = object : Firer {
        override val position = Vector2(x, y)
        override val velocity = Vector2()
        override val rotation = 0f
        override val radius = 12f
        override val isEnemyFirer = false
    }

    private fun enemy(
        type: EnemyType,
        x: Float,
        y: Float = 0f,
        vx: Float = 0f,
        vy: Float = 0f,
    ): EnemyShip = EnemyShip().also {
        it.initialize(x, y, type, emptyList())
        it.spawnTime = it.warpInDuration
        it.spawnShieldTimer = 0f
        it.velocity.set(vx, vy)
    }

    private fun dummyTarget(
        x: Float,
        y: Float = 0f,
        vx: Float = 0f,
        vy: Float = 0f,
        radius: Float = 10f,
    ): Entity = object : Entity() {
        init {
            position.set(x, y)
            velocity.set(vx, vy)
            this.radius = radius
            maxHealth = 100f
            health = 100f
        }
    }

    @Test
    fun `威胁排序偏向更近、更快、更高 tier 的敌人`() {
        val farFodder = enemy(EnemyType.SCOUT, 800f, vx = -50f)
        val nearElite = enemy(EnemyType.SPECTER, 200f, vx = -200f)

        val control = FireControl()
        control.beginFrame(listOf(farFodder, nearElite), Vector2())

        val ranked = control.rankedTargets()
        assertEquals(nearElite, ranked[0].target)
        assertTrue(ranked[0].threat > ranked[1].threat)
    }

    @Test
    fun `撞击时间优先于距离`() {
        val nearButIdle = Asteroid().also {
            it.initialize(200f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2())
            it.velocity.zero()
        }
        val farButIncoming = Asteroid().also {
            it.initialize(300f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2())
            it.velocity.set(-400f, 0f)
        }

        val control = FireControl()
        control.beginFrame(listOf(nearButIdle, farButIncoming), Vector2())

        assertEquals(farButIncoming, control.rankedTargets().first().target)
    }

    @Test
    fun `目标偏离船头越远威胁越低`() {
        val front = enemy(EnemyType.SCOUT, 200f, vx = -200f)
        val behind = enemy(EnemyType.SCOUT, -200f, vx = 200f)

        val control = FireControl()
        control.beginFrame(listOf(front, behind), Vector2(), facing = 0f)

        assertEquals(front, control.rankedTargets().first().target)
    }

    @Test
    fun `同等条件下更近的敌机优先`() {
        val near = enemy(EnemyType.SCOUT, 150f)
        val far = enemy(EnemyType.SCOUT, 600f)

        val control = FireControl()
        control.beginFrame(listOf(far, near), Vector2())

        assertEquals(near, control.rankedTargets().first().target)
    }

    @Test
    fun `未受伤的敌机仍优先于更近的小行星`() {
        val farEnemy = enemy(EnemyType.SCOUT, 500f)
        val closeRock = Asteroid().also {
            it.initialize(100f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2())
        }

        val control = FireControl()
        control.beginFrame(listOf(closeRock, farEnemy), Vector2())

        assertEquals(farEnemy, control.rankedTargets().first().target)
    }

    @Test
    fun `刚受伤的敌机会暂时让位给小行星`() {
        val enemy = enemy(EnemyType.SCOUT, 300f)
        val rock = Asteroid().also {
            it.initialize(250f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2())
        }
        val control = FireControl()

        control.beginFrame(listOf(enemy, rock), Vector2(), nowSeconds = 0f)
        assertEquals(enemy, control.rankedTargets().first().target)

        enemy.takeDamage(10f)
        control.beginFrame(listOf(enemy, rock), Vector2(), nowSeconds = 0.01f)
        assertEquals(rock, control.rankedTargets().first().target)

        control.beginFrame(listOf(enemy, rock), Vector2(), nowSeconds = 2f)
        assertEquals(enemy, control.rankedTargets().first().target)
    }

    @Test
    fun `敌机刚受伤时多发武器会分出火力打小行星`() {
        val enemy = enemy(EnemyType.SCOUT, 300f)
        val rock = Asteroid().also {
            it.initialize(250f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2())
        }
        val control = FireControl()
        control.beginFrame(listOf(enemy, rock), Vector2(), nowSeconds = 0f)
        enemy.takeDamage(10f)
        control.beginFrame(listOf(enemy, rock), Vector2(), nowSeconds = 0.01f)

        val locks = control.allocate(firer(), listOf(enemy, rock), 3, 500f, 5f)

        assertTrue("至少一发拦截小行星", locks.any { it.target is Asteroid })
        assertTrue("至少一发继续打敌机", locks.any { it.target is EnemyShip })
    }

    @Test
    fun `同一条件下敌机权重高于小行星`() {
        val shipTarget = enemy(EnemyType.SCOUT, 200f)
        val rock = Asteroid().also {
            it.initialize(200f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2())
        }

        val control = FireControl()
        control.beginFrame(listOf(rock, shipTarget), Vector2())

        assertEquals(shipTarget, control.rankedTargets().first().target)
    }

    @Test
    fun `导弹齐射要求第一发锁定敌机`() {
        val rock = Asteroid().also {
            it.initialize(100f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2())
        }
        val farEnemy = enemy(EnemyType.SCOUT, 400f, vx = -100f)

        val control = FireControl()
        control.beginFrame(listOf(rock, farEnemy), Vector2())
        val locks = control.allocate(
            firer(), listOf(rock, farEnemy), 3, 500f, 5f,
            requireEnemyFirst = true,
        )

        assertTrue(locks.first().target is EnemyShip)
    }

    @Test
    fun `Boss 的威胁高于普通敌机`() {
        val boss = Boss().also {
            it.position.set(300f, 0f)
            it.velocity.set(-150f, 0f)
            it.isActive = true
            it.isInvulnerable = false
            it.shielded = false
        }
        val fodder = enemy(EnemyType.SCOUT, 100f)

        val control = FireControl()
        control.beginFrame(listOf(fodder, boss), Vector2())

        assertEquals(boss, control.rankedTargets().first().target)
    }

    @Test
    fun `跃迁中的目标会被降权`() {
        val warping = enemy(EnemyType.SCOUT, 100f).also { it.spawnTime = 0f }
        val active = enemy(EnemyType.SCOUT, 300f)

        val control = FireControl()
        control.beginFrame(listOf(warping, active), Vector2())

        assertEquals(active, control.rankedTargets().first().target)
    }

    @Test
    fun `五枚导弹对三个同等威胁目标时全部目标都被覆盖且不过度集中`() {
        val targets = listOf(
            enemy(EnemyType.SCOUT, 200f),
            enemy(EnemyType.SCOUT, 220f),
            enemy(EnemyType.SCOUT, 240f),
        )
        val control = FireControl()
        control.beginFrame(targets, Vector2())

        val locks = control.allocate(firer(), targets, 5, 500f, 5f)
        val counts = locks.groupingBy { it.target }.eachCount()

        assertEquals(5, locks.size)
        assertEquals(3, counts.size)
        assertTrue("单目标分到 ${counts.values}", counts.values.maxOrNull()!! <= 2)
    }

    @Test
    fun `同一帧内多门单发武器会避开已分配目标`() {
        val left = enemy(EnemyType.SCOUT, 300f, y = -100f)
        val right = enemy(EnemyType.SCOUT, 300f, y = 100f)
        val control = FireControl()
        control.beginFrame(listOf(left, right), Vector2())

        val first = control.aimAngle(firer(), listOf(left, right), 500f)
        val second = control.aimAngle(firer(), listOf(left, right), 500f)

        assertTrue("两门单发武器仍打同一目标", abs(first - second) > 0.1f)
    }

    @Test
    fun `同一目标的弹道瞄准点会横向错开`() {
        val target = dummyTarget(300f, radius = 20f)
        val control = FireControl()
        control.beginFrame(listOf(target), Vector2())

        val locks = control.allocate(firer(), listOf(target), 3, 500f, 5f)
        val offsets = locks.map { it.aimPoint.y }

        assertEquals(3, locks.size)
        assertEquals(3, offsets.distinct().size)
        assertEquals(0f, offsets[0], 0.001f)
        assertTrue(offsets.any { it > 0f })
        assertTrue(offsets.any { it < 0f })
        for (lock in locks) assertTrue(abs(lock.aimPoint.x - 300f) < 0.01f)
    }

    @Test
    fun `瞬间武器只选择允许列表内的目标`() {
        val first = enemy(EnemyType.SCOUT, 100f)
        val second = enemy(EnemyType.SCOUT, 200f)
        val control = FireControl()
        control.beginFrame(listOf(first, second), Vector2())

        val picked = control.selectTargets(firer(), listOf(second), 2)

        assertEquals(listOf(second), picked)
    }

    @Test
    fun `小行星也可以作为火控目标`() {
        val rock = Asteroid().also {
            it.initialize(250f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2())
        }
        val control = FireControl()
        control.beginFrame(listOf(rock), Vector2())

        val locks = control.allocate(firer(), listOf(rock), 1, 600f, 5f)
        assertEquals(rock, locks.single().target)
    }
}
