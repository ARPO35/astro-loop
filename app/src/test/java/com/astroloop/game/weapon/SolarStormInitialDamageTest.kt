package com.astroloop.game.weapon

import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.AsteroidSize
import com.astroloop.game.entity.AsteroidType
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.Firer
import com.astroloop.game.entity.Projectile
import com.astroloop.game.util.Vector2
import com.astroloop.game.weapon.firecontrol.FireControl
import org.junit.Assert.assertEquals
import org.junit.Test

class SolarStormInitialDamageTest {

    private fun firer() = object : Firer {
        override val position = Vector2()
        override val velocity = Vector2()
        override val rotation = 0f
        override val radius = 12f
        override val isEnemyFirer = false
        override val isPlayerFirer = true
    }

    @Test
    fun `L1 太阳风暴刚好一发击杀初始大型岩石`() {
        val state = GameState.createNeutral()
        val rock = Asteroid().also {
            it.initialize(300f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2())
        }
        val weapon = WeaponFactory.createWeapon("solar_storm")!!
        val pool = EntityPool({ Projectile() }, 8)

        assertEquals(rock.maxHealth, weapon.getDamage(state), 0.001f)

        weapon.fire(firer(), state, pool, listOf(rock), FireControl())

        assertEquals(0f, rock.health, 0.001f)
    }
}
