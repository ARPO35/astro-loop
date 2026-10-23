package com.astroloop.game.weapon.firecontrol

import com.astroloop.game.util.Vector2
import kotlin.math.abs
import kotlin.math.sqrt

/** 提前量解算结果。坐标为世界坐标；[valid] 为 false 时退化为目标当前位置。 */
data class InterceptSolution(
    val x: Float,
    val y: Float,
    val time: Float,
    val valid: Boolean,
)

/**
 * 匀速直线运动的拦截点解算。
 *
 * 弹丸从 [origin] 以恒定速率 [projectileSpeed] 飞行，目标从 [targetPosition] 以
 * [targetVelocity] 匀速运动。把命中条件写成 |r + v·t| = s·t + R（R 为目标半径），
 * 平方后得到一元二次方程，取最小正根。目标半径参与计算，因此大目标会比点目标更早被命中。
 *
 * 方程无正根（目标比弹丸快且正在远离）时返回目标当前位置并标记 invalid，让调用方
 * 自行决定是否仍然开火。制导武器会继续用末制导修正，直线武器则至少保持原朝向。
 */
object InterceptSolver {
    private const val EPSILON = 1e-4f

    fun solve(
        origin: Vector2,
        targetPosition: Vector2,
        targetVelocity: Vector2,
        projectileSpeed: Float,
        targetRadius: Float = 0f,
        maxTime: Float = 8f,
    ): InterceptSolution {
        val direct = InterceptSolution(
            targetPosition.x,
            targetPosition.y,
            0f,
            false,
        )
        if (projectileSpeed <= EPSILON) return direct

        val rx = targetPosition.x - origin.x
        val ry = targetPosition.y - origin.y
        val vx = targetVelocity.x
        val vy = targetVelocity.y
        val radius = targetRadius.coerceAtLeast(0f)

        // (v² - s²)t² + 2(r·v - sR)t + (r² - R²) = 0
        val a = vx * vx + vy * vy - projectileSpeed * projectileSpeed
        val b = 2f * (rx * vx + ry * vy - projectileSpeed * radius)
        val c = rx * rx + ry * ry - radius * radius

        var time = -1f
        if (abs(a) <= EPSILON) {
            if (abs(b) > EPSILON) {
                val candidate = -c / b
                if (candidate > 0f) time = candidate
            }
        } else {
            val discriminant = b * b - 4f * a * c
            if (discriminant >= 0f) {
                val root = sqrt(discriminant)
                val t1 = (-b - root) / (2f * a)
                val t2 = (-b + root) / (2f * a)
                time = smallestPositive(t1, t2)
            }
        }

        if (time <= 0f || !time.isFinite()) return direct

        return InterceptSolution(
            x = targetPosition.x + vx * time,
            y = targetPosition.y + vy * time,
            time = time,
            valid = time <= maxTime,
        )
    }

    private fun smallestPositive(t1: Float, t2: Float): Float {
        val first = if (t1 > 0f) t1 else -1f
        val second = if (t2 > 0f) t2 else -1f
        return when {
            first > 0f && second > 0f -> minOf(first, second)
            first > 0f -> first
            second > 0f -> second
            else -> -1f
        }
    }
}
