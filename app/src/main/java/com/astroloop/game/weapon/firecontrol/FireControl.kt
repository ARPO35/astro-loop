package com.astroloop.game.weapon.firecontrol

import com.astroloop.game.core.GameConfig
import com.astroloop.game.data.EnemyDefinitions
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.Boss
import com.astroloop.game.entity.EnemyShip
import com.astroloop.game.entity.Entity
import com.astroloop.game.entity.Firer
import com.astroloop.game.util.Vector2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 一帧内共享的火控系统：威胁排序、目标分配和提前量瞄准。
 *
 * 设计要点：
 * - [beginFrame] 每帧调用一次，按当前目标重新计算威胁；同一帧内各武器通过 [aimAngle]、
 *   [allocate] 和 [selectTargets] 共享分配计数，避免所有火力同时扑向同一个目标。
 * - 单发武器也通过 [aimAngle] 占用分配额度；多目标武器使用 [allocate] 或 [selectTargets]
 *   获得一组互不重复（目标不足时按份额均摊）的目标。
 * - 敌机/Boss 的权重会随“上次受伤后的时间”恢复：武器充足时部分火力可以分给小行星，
 *   武器不足时敌机重新回到高优先级。
 * - 同一目标被分配多枚弹丸时，瞄准点会沿视线垂直方向错开，形成不同弹道，而不是重叠。
 *
 * 只由玩家的 `WeaponSystem` 使用；敌机、舰队和僚机保持原版武器逻辑。
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

    /** 上一帧看到的敌机/Boss 生命值，用于检测“这一帧实际掉了血”。 */
    private val lastHealth = HashMap<Entity, Float>()
    /** 最近一次观察到生命值下降的时间；没有记录表示从未受伤。 */
    private val lastDamagedAt = HashMap<Entity, Float>()
    private var ready = false

    /** 同一目标连续被选中的分数衰减系数；越大越倾向于把火力分散到其他目标。 */
    private val sharePenalty = 1.5f

    /**
     * 每帧开始时重算威胁排序并清空分配记录。
     *
     * [nowSeconds] 是游戏内时间，用于计算敌机距离上次受伤已经多久；生产环境由
     * `WeaponSystem` 传入 `GameState.survivalTime`。
     */
    fun beginFrame(
        targets: List<Entity>,
        origin: Vector2,
        facing: Float = 0f,
        playerVelocity: Vector2? = null,
        nowSeconds: Float = 0f,
    ) {
        ranked.clear()
        assigned.clear()
        trackDamage(targets, nowSeconds)
        for (target in targets) {
            if (target.isActive) {
                ranked.add(
                    RankedTarget(
                        target,
                        threatOf(target, origin, facing, playerVelocity, nowSeconds),
                    )
                )
            }
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
     * 单发瞄准：按共享分配选当前分数最高的目标，返回带提前量的世界角度。
     * 没有可用目标时返回 [Float.NaN]，调用方应退回自己的朝向。
     *
     * 单发武器也占用一次分配额度。多个单发武器在同一帧开火时，后面的武器会避开已经分到
     * 火力的目标，避免“武器很多但全部打同一个敌机/小行星”。
     */
    fun aimAngle(firer: Firer, targets: List<Entity>, projectileSpeed: Float, maxTime: Float = 8f): Float {
        ensureFrame(targets, firer.position, firer.rotation, firer.velocity)
        val candidate = pickBest(null) ?: return Float.NaN
        val angle = aimAngleAt(firer, candidate.target, projectileSpeed, maxTime)
        if (!angle.isNaN()) {
            val lane = assigned[candidate.target] ?: 0
            assigned[candidate.target] = lane + 1
        }
        return angle
    }

    /** 第二目标的角度，用于 Railgun 尾射等需要另一条射线的武器。 */
    fun secondaryAimAngle(firer: Firer, targets: List<Entity>, projectileSpeed: Float, maxTime: Float = 8f): Float {
        ensureFrame(targets, firer.position, firer.rotation, firer.velocity)
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
        requireEnemyFirst: Boolean = false,
    ): List<Lock> {
        ensureFrame(targets, firer.position, firer.rotation, firer.velocity)
        if (count <= 0) return emptyList()

        val enemyTargets = if (requireEnemyFirst) {
            ranked.asSequence().map { it.target }.filter { isEnemyTarget(it) }.toHashSet()
        } else {
            null
        }

        val locks = ArrayList<Lock>(count)
        repeat(count) { slot ->
            val allowed = if (slot == 0 && enemyTargets != null && enemyTargets.isNotEmpty()) {
                enemyTargets
            } else {
                null
            }
            val candidate = pickBest(allowed) ?: return@repeat
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
        ensureFrame(allowed.toList(), firer.position, firer.rotation, firer.velocity)
        if (count <= 0) return emptyList()

        val allowedSet = allowed.toHashSet()
        val picked = ArrayList<Entity>(count)
        repeat(count) {
            val candidate = pickBest(allowedSet) ?: return@repeat
            // 瞬间武器每个目标只打一次；目标不足时就是少打几个，不重复命中同一目标。
            allowedSet.remove(candidate.target)
            val lane = assigned[candidate.target] ?: 0
            assigned[candidate.target] = lane + 1
            picked.add(candidate.target)
        }
        return picked
    }

    /** 导弹优先目标：可伤害的敌机或 Boss；跃迁中的敌机不算。 */
    private fun isEnemyTarget(entity: Entity): Boolean = when (entity) {
        is EnemyShip -> entity.isActive && !entity.isWarping
        is Boss -> entity.isActive
        else -> false
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

    /**
     * 逐帧比较敌机/Boss 的生命值，记录最近一次实际掉血的时间。
     *
     * 只在 [beginFrame] 里做差，不侵入所有伤害调用点：护盾吸收、闪避、无敌等没有掉血的事件
     * 不会被误算成受伤；对象池重置导致生命值回升时会清除记录，避免旧数据污染新目标。
     */
    private fun trackDamage(targets: List<Entity>, nowSeconds: Float) {
        val seen = HashSet<Entity>(targets.size)
        for (target in targets) {
            if (!target.isActive) continue
            seen.add(target)
            if (target !is EnemyShip && target !is Boss) continue

            val previous = lastHealth[target]
            val current = target.health
            when {
                previous == null -> {
                    // 第一次看到就已经带伤，说明它在本火控接管前受过伤。
                    if (current < target.maxHealth - HEALTH_EPSILON) {
                        lastDamagedAt[target] = nowSeconds
                    }
                }
                current < previous - HEALTH_EPSILON -> {
                    lastDamagedAt[target] = nowSeconds
                }
                current > previous + HEALTH_EPSILON -> {
                    // 治疗或对象池重置：新的一轮，不再沿用旧伤时间。
                    lastDamagedAt.remove(target)
                }
            }
            lastHealth[target] = current
        }
        lastHealth.keys.retainAll(seen)
        lastDamagedAt.keys.retainAll(seen)
    }

    /**
     * 敌机/Boss 的火力集中系数：刚受伤时降到 [ENEMY_FOCUS_MIN]，随时间指数恢复到
     * [ENEMY_FOCUS_MAX]。从未受伤的目标直接取上限。
     *
     * 武器多 → 命中间隔短 → 系数低 → 多余火力可以分给小行星；武器少 → 命中间隔长 →
     * 系数恢复 → 火力重新集中在敌机。Boss 的基础类型权重更高，因此同等受伤时间内仍压过
     * 普通敌机。
     */
    private fun focusFor(entity: Entity, nowSeconds: Float): Float {
        if (entity !is EnemyShip && entity !is Boss) return 1f
        val damagedAt = lastDamagedAt[entity]
        val age = if (damagedAt == null) {
            Float.POSITIVE_INFINITY
        } else {
            (nowSeconds - damagedAt).coerceAtLeast(0f)
        }
        val recovery = 1f - exp((-age / ENEMY_FOCUS_RAMP_SECONDS).toDouble()).toFloat()
        return ENEMY_FOCUS_MIN + (ENEMY_FOCUS_MAX - ENEMY_FOCUS_MIN) * recovery
    }

    private fun ensureFrame(
        targets: List<Entity>,
        origin: Vector2,
        facing: Float,
        playerVelocity: Vector2?,
    ) {
        if (!ready) beginFrame(targets, origin, facing, playerVelocity)
    }

    /**
     * 威胁分以“预计撞击本机的时间”为主，其次才是距离、本机朝向和类型。
     *
     * - 撞击时间用目标与玩家的相对速度求最小正根；不在碰撞航线上时退化为最近接近时间。
     * - 本机转向：目标偏离船头越远，附加的转向时间越长，威胁越低。
     * - 距离权重高于旧版：越近的目标分数越高，近处敌人尤其明显。
     * - 敌机/Boss 还乘以“距离上次受伤的时间”恢复系数：刚受伤时降权，长时间未受伤时
     *   回到高优先级。武器多、命中密集时，多余火力会自然分给小行星。
     * - 跃迁中/出生护盾/Boss 无敌等暂时不可伤害状态继续降权。
     */
    fun threatOf(
        entity: Entity,
        origin: Vector2,
        facing: Float = 0f,
        playerVelocity: Vector2? = null,
        nowSeconds: Float = 0f,
    ): Float {
        val dx = entity.position.x - origin.x
        val dy = entity.position.y - origin.y
        val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)

        val bearing = atan2(dy, dx)
        val angleOffset = abs(wrapAngle(bearing - facing))
        val turnPenalty = (angleOffset / PI.toFloat()) * TURN_PENALTY_SECONDS

        val impactTime = timeToImpact(entity, origin, playerVelocity)
        val urgency = 1f / (impactTime + turnPenalty + URGENCY_PADDING)

        val proximity = 1f + DISTANCE_WEIGHT / (distance + DISTANCE_FALLOFF)
        val facingWeight = 0.75f + 0.25f * cos(angleOffset)
        val typeWeight = when (entity) {
            is Boss -> 2f
            is EnemyShip -> {
                val tier = EnemyDefinitions.getDef(entity.type).tier.coerceIn(1, 4)
                1.4f + tier * 0.05f
            }
            is Asteroid -> 1f
            else -> 0.8f
        }
        val status = when (entity) {
            is EnemyShip -> when {
                entity.isWarping -> 0.05f
                entity.isSpawnShielded -> 0.6f
                else -> 1f
            }
            is Boss -> if (entity.shielded || entity.isInvulnerable) 0.7f else 1.1f
            is Asteroid -> if (entity.fragmentImmunityTimer > 0f) 0.2f else 1f
            else -> 1f
        }

        return urgency * proximity * facingWeight * typeWeight * status * focusFor(entity, nowSeconds)
    }

    /**
     * 目标与玩家按当前速度继续运动时，最早接触的时间。
     * 没有碰撞解时退化为最近接近时间；正在远离或静止的目标按距离给一个有限值。
     */
    private fun timeToImpact(entity: Entity, origin: Vector2, playerVelocity: Vector2?): Float {
        val px = playerVelocity?.x ?: 0f
        val py = playerVelocity?.y ?: 0f
        val dx = entity.position.x - origin.x
        val dy = entity.position.y - origin.y
        val distance = sqrt(dx * dx + dy * dy)
        val radius = entity.radius + GameConfig.SHIP_BASE_SIZE
        if (distance <= radius) return 0f

        val rvx = entity.velocity.x - px
        val rvy = entity.velocity.y - py
        val a = rvx * rvx + rvy * rvy
        if (a < 0.0001f) return distance / 100f + 2f

        // |r + rv·t| = playerRadius + targetRadius
        val b = 2f * (dx * rvx + dy * rvy)
        val c = distance * distance - radius * radius
        val discriminant = b * b - 4f * a * c
        if (discriminant >= 0f) {
            val root = sqrt(discriminant)
            val t1 = (-b - root) / (2f * a)
            val t2 = (-b + root) / (2f * a)
            var first = -1f
            if (t1 >= 0f) first = t1
            if (t2 >= 0f && (first < 0f || t2 < first)) first = t2
            if (first >= 0f) return first
        }

        val closestTime = -(dx * rvx + dy * rvy) / a
        if (closestTime <= 0f) return distance / 100f + 2f

        val missX = dx + rvx * closestTime
        val missY = dy + rvy * closestTime
        val missDistance = sqrt(missX * missX + missY * missY)
        return closestTime + missDistance / sqrt(a)
    }

    private fun wrapAngle(angle: Float): Float {
        var wrapped = angle
        while (wrapped > PI) wrapped -= (2f * PI).toFloat()
        while (wrapped < -PI) wrapped += (2f * PI).toFloat()
        return wrapped
    }

    companion object {
        /** 目标在正后方时额外计入的转向时间（秒）。 */
        private const val TURN_PENALTY_SECONDS = 0.8f
        /** 防止撞击时间为 0 时威胁分无限大。 */
        private const val URGENCY_PADDING = 0.35f

        /** 距离权重：越大，近处目标越优先。旧版为 250。 */
        private const val DISTANCE_WEIGHT = 400f
        /** 距离衰减基准：越大，远近差距越平缓。旧版为 250。 */
        private const val DISTANCE_FALLOFF = 200f

        /** 敌机刚受伤时的火力集中系数；低于小行星，允许分火。 */
        private const val ENEMY_FOCUS_MIN = 0.55f
        /** 敌机长时间未受伤时的火力集中系数；保持“敌机最高优先级”。 */
        private const val ENEMY_FOCUS_MAX = 3f
        /** 从刚受伤恢复到满权重的指数时间常数（秒）。 */
        private const val ENEMY_FOCUS_RAMP_SECONDS = 1.2f
        /** 生命值变化小于该值视为抖动，不刷新受伤时间。 */
        private const val HEALTH_EPSILON = 0.01f
    }
}
