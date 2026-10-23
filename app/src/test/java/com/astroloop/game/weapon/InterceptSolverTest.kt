package com.astroloop.game.weapon

import com.astroloop.game.util.Vector2
import com.astroloop.game.weapon.firecontrol.InterceptSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterceptSolverTest {

    @Test
    fun `静止目标按距离除以弹速命中`() {
        val solution = InterceptSolver.solve(
            Vector2(0f, 0f), Vector2(100f, 0f), Vector2(), 200f, 0f, 10f
        )
        assertTrue(solution.valid)
        assertEquals(0.5f, solution.time, 0.001f)
        assertEquals(100f, solution.x, 0.001f)
        assertEquals(0f, solution.y, 0.001f)
    }

    @Test
    fun `横向移动目标需要向上提前`() {
        val solution = InterceptSolver.solve(
            Vector2(0f, 0f), Vector2(100f, 0f), Vector2(0f, 100f), 200f, 0f, 10f
        )
        assertTrue(solution.valid)
        // 100² + (100t)² = (200t)² → t = 1/√3 ≈ 0.577，提前量 y ≈ 57.7
        assertEquals(0.577f, solution.time, 0.01f)
        assertEquals(57.7f, solution.y, 1f)
    }

    @Test
    fun `目标半径让命中点提前`() {
        val solution = InterceptSolver.solve(
            Vector2(0f, 0f), Vector2(100f, 0f), Vector2(), 200f, 20f, 10f
        )
        assertTrue(solution.valid)
        assertEquals(0.4f, solution.time, 0.001f)
    }

    @Test
    fun `追不上的目标退回当前位置并标记无效`() {
        val solution = InterceptSolver.solve(
            Vector2(0f, 0f), Vector2(100f, 0f), Vector2(300f, 0f), 200f, 0f, 10f
        )
        assertFalse(solution.valid)
        assertEquals(100f, solution.x, 0f)
        assertEquals(0f, solution.time, 0f)
    }
}
