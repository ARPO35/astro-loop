package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Ship
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Nano Repair 的护盾部分：按回血速度的一半回复，且不能绕过 Glass Cannon 的护盾封锁。
 */
class MovementSystemNanoShieldTest {

    private lateinit var system: MovementSystem
    private lateinit var state: GameState
    private lateinit var ship: Ship

    @Before
    fun setup() {
        system = MovementSystem()
        state = GameState()
        state.reset()
        ship = Ship().apply {
            maxHealth = 100f
            health = 50f
            maxShield = 50f
            currentShield = 0f
            // 隔离自然回盾，只观察 Nano Repair 自己加的那部分
            shieldRegenDelay = 1000f
        }
    }

    @Test
    fun `nano repair heals shields at half its health rate`() {
        state.addPassive("nano_repair")  // 0.3 HP/s

        system.updateShip(ship, state, 2f)

        assertEquals(50.6f, ship.health, 0.001f)
        assertEquals(0.3f, ship.currentShield, 0.001f)
    }

    @Test
    fun `shield half keeps ticking while health is full`() {
        state.addPassive("nano_repair")
        ship.health = ship.maxHealth

        system.updateShip(ship, state, 2f)

        assertEquals(100f, ship.health, 0.001f)
        assertEquals(0.3f, ship.currentShield, 0.001f)
    }

    @Test
    fun `shield regen never exceeds max shield`() {
        state.addPassive("nano_repair")
        ship.currentShield = ship.maxShield

        system.updateShip(ship, state, 2f)

        assertEquals(50f, ship.currentShield, 0.001f)
    }

    @Test
    fun `glass cannon blocks nano repair shield regen`() {
        state.addPassive("nano_repair")
        ship.shieldCap = 0f
        ship.shieldRegenDisabled = true

        system.updateShip(ship, state, 2f)

        assertEquals(50.6f, ship.health, 0.001f)
        assertEquals(0f, ship.currentShield, 0.001f)
    }
}
