package com.astroloop.game.weapon.firecontrol

import com.astroloop.game.data.EnemyDefinitions
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.Boss
import com.astroloop.game.entity.EnemyShip
import com.astroloop.game.entity.Entity
import com.astroloop.game.entity.Firer
import com.astroloop.game.util.Vector2
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 一帧内共享的火控系统：威胁排序、目标分配和提前量瞄准。
 *
 * 设计要点：
 * - [beginFrame] 每帧调用一次，按当前目标重新计算威胁；同一帧内各武器通过 [allocate]
 *   共享分配计数，避免多枚导弹同时扑向同一个目标。
 * - 单发武器使用 [aimAngle]，只取威胁最高的目标，不占用分配额度；多目标武器使用
 *   [allocate] 或 [selectTargets] 获得一组互不重复（目标不足时按份额均摊）的目标。
 * - 同一目标被分配多枚弹丸时，瞄准点会沿视线垂直方向错开，形成不同弹道，而不是重叠。
 */
class FireControl {

    data class RankedTarget(val target: Entity, val threat: Float)

    /** 一次目标分配：目标、带提前量的瞄准点、预计命中时间、威胁分和同一目标的弹道序号。 */
    data class Lock(
        val target: Entity,
        val aimPoint: Vector2,
        val timeToHit: Float,
        val threat: Float,
        val laneIndex: Int,
        val validIntercept: Boolean,
    )

    private val ranked = ArrayList<RankedTarget>()
    private val assigned = HashMap<Entity, Int>()
    private var ready = false

    /** 同一目标连续被选中的分数衰减系数；越大越倾向于把火力分散到其他目标。 */
    private val sharePenalty = 1.5f

    /** 每帧开始时重算威胁排序并清空分配记录。 */
    fun beginFrame(targets: List<Entity>, origin: Vector2) {
        ranked.clear()
        assigned.clear()
        for (target in targets) {
            if (target.isActive) ranked.add(RankedTarget(target, threatOf(target, origin)))
        }
        ranked.sortWith(
            compareByDescending<RankedTarget> { it.threat }
                .thenBy { it.target.position.distanceSquared(origin) }
        )
        ready = true
    }

    /** 威胁排序快照，供调试和测试读取。 */
    fun rankedTargets(): List<RankedTarget> = ranked.toList()

    /**
     * 单发瞄准：选威胁最高的目标，返回带提前量的世界角度。
     * 没有可用目标时返回 [Float.NaN]，调用方应退回自己的朝向。
     */
    fun aimAngle(firer: Firer, targets: List<Entity>, projectileSpeed: Float, maxTime: Float = 8f): Float {
        ensureFrame(targets, firer.position)
        val target = ranked.firstOrNull()?.target ?: return Float.NaN
        return aimAngleAt(firer, target, projectileSpeed, maxTime)
    }

    /** 第二目标的角度，用于 Railgun 尾射等需要另一条射线的武器。 */
    fun secondaryAimAngle(firer: Firer, targets: List<Entity>, projectileSpeed: Float, maxTime: Float = 8f): Float {
        ensureFrame(targets, firer.position)
        val target = ranked.getOrNull(1)?.target ?: return Float.NaN
        return aimAngleAt(firer, target, projectileSpeed, maxTime)
    }

    private fun aimAngleAt(firer: Firer, target: Entity, projectileSpeed: Float, maxTime: Float): Float {
        val solution = InterceptSolver.solve(
            firer.position, target.position, target.velocity, projectileSpeed, target.radius, maxTime
        )
        val dx = solution.x - firer.position.x
        val dy = solution.y - firer.position.y
        if (dx * dx + dy * dy < 1f) return Float.NaN
        return atan2(dy, dx)
    }

    /**
     * 为多发武器分配目标。目标按威胁从高到低选，已分配过的目标分数会被 [sharePenalty]
     * 衰减，因此目标数量足够时不会所有弹丸都打同一个目标；目标不足时才按份额循环。
     */
    fun allocate(
        firer: Firer,
        targets: List<Entity>,
        count: Int,
        projectileSpeed: Float,
        maxTime: Float = 8f,
    ): List<Lock> {
        ensureFrame(targets, firer.position)
        if (count <= 0) return emptyList()

        val locks = ArrayList<Lock>(count)
        repeat(count) {
            val candidate = pickBest(null) ?: return@repeat
            val lane = assigned[candidate.target] ?: 0
            assigned[candidate.target] = lane + 1
            val solution = aimPoint(firer, candidate.target, projectileSpeed, maxTime, lane)
            locks.add(
                Lock(
                    target = candidate.target,
                    aimPoint = solution.point,
                    timeToHit = solution.time,
                    threat = candidate.threat,
                    laneIndex = lane,
                    validIntercept = solution.valid,
                )
            )
        }
        return locks
    }

    /**
     * 只选择目标、不生成瞄准点，供瞬间生效的武器（Solar Storm / Phoenix Flare）使用。
     * [allowed] 用于排除屏幕外或不可伤害的目标，同时保留与其他武器共享的分配计数。
     */
    fun selectTargets(firer: Firer, allowed: Collection<Entity>, count: Int): List<Entity> {
        ensureFrame(allowed.toList(), firer.position)
        if (count <= 0) return emptyList()

        val allowedSet = allowed.toHashSet()
        val picked = ArrayList<Entity>(count)
        repeat(count) {
            val candidate = pickBest(allowedSet) ?: return@repeat
            val lane = assigned[candidate.target] ?: 0
            assigned[candidate.target] = lane + 1
            picked.add(candidate.target)
        }
        return picked
    }

    private fun pickBest(allowed: Set<Entity>?): RankedTarget? {
        var best: RankedTarget? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (candidate in ranked) {
            if (!candidate.target.isActive) continue
            if (allowed != null && candidate.target !in allowed) continue
            val uses = assigned[candidate.target] ?: 0
            val score = candidate.threat / (1f + uses * sharePenalty)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private data class AimPoint(
        val point: Vector2,
        val time: Float,
        val valid: Boolean,
    )

    private fun aimPoint(
        firer: Firer,
        target: Entity,
        projectileSpeed: Float,
        maxTime: Float,
        lane: Int,
    ): AimPoint {
        val solution = InterceptSolver.solve(
            firer.position, target.position, target.velocity, projectileSpeed, target.radius, maxTime
        )
        var x = solution.x
        var y = solution.y

        if (lane > 0) {
            val dx = solution.x - firer.position.x
            val dy = solution.y - firer.position.y
            val length = sqrt(dx * dx + dy * dy)
            if (length > 1f) {
                // 弹道横向错开。间距跟目标半径走，避免小目标被让到碰撞体之外。
                val spacing = (target.radius * 0.35f).coerceIn(3f, 18f)
                val offset = laneOffset(lane) * spacing
                x += -dy / length * offset
                y += dx / length * offset
            }
        }

        return AimPoint(Vector2(x, y), solution.time, solution.valid)
    }

    /** 0 → 0，1 → +1，2 → -1，3 → +2，4 → -2…… 让同一目标的弹道均匀铺开。 */
    private fun laneOffset(lane: Int): Float {
        val ring = (lane + 1) / 2
        val sign = if (lane % 2 == 1) 1f else -1f
        return sign * ring
    }

    private fun ensureFrame(targets: List<Entity>, origin: Vector2) {
        if (!ready) beginFrame(targets, origin)
    }

    /**
     * 威胁分。不是单纯的距离排序：高 tier、射速快、正在接近、并且更近的目标更危险；
     * Boss 单独加权。已经可以被打穿/正在跃迁的目标降权，避免把整轮火力浪费在无敌单位上。
     */
    fun threatOf(entity: Entity, origin: Vector2): Float {
        val dx = entity.position.x - origin.x
        val dy = entity.position.y - origin.y
        val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)

        // 目标朝玩家方向的速度分量：越大，越可能先撞上或先进入射程。
        val closing = ((entity.velocity.x * -dx + entity.velocity.y * -dy) / distance).coerceAtLeast(0f)

        val base = when (entity) {
            is Boss -> 45f
            is EnemyShip -> {
                val def = EnemyDefinitions.getDef(entity.type)
                val tier = def.tier.coerceAtLeast(1)
                val shotsPerSecond = 1f / def.fireRate.coerceAtLeast(0.25f)
                4f + tier * 4f + shotsPerSecond * 1.5f
            }
            is Asteroid -> 2.5f * entity.getDamageScale()
            else -> 2f
        }

        val proximity = 1f + 600f / (distance + 200f)
        val approach = 1f + closing / 250f
        val healthFactor = if (entity.maxHealth > 0f) {
            0.75f + 0.25f * (entity.health / entity.maxHealth).coerceIn(0f, 1f)
        } else {
            1f
        }
        val status = when (entity) {
            is EnemyShip -> when {
                entity.isWarping -> 0.1f
                entity.isSpawnShielded -> 0.5f
                else -> 1f
            }
            is Boss -> if (entity.shielded || entity.isInvulnerable) 0.6f else 1.25f
            is Asteroid -> if (entity.fragmentImmunityTimer > 0f) 0.2f else 1f
            else -> 1f
        }

        return base * proximity * approach * healthFactor * status
    }
}
