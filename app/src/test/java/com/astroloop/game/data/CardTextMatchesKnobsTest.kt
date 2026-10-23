package com.astroloop.game.data

import com.astroloop.game.tuning.KnobsPassives
import com.astroloop.game.tuning.KnobsWeapons
import com.astroloop.game.tuning.Tunables
import com.astroloop.game.tuning.WeaponProbe
import com.astroloop.game.tuning.forLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.roundToInt

/**
 * The upgrade cards' wording is a second copy of numbers that live in the knobs, so it drifts
 * silently whenever a default changes (1.5 moved Scatter Shot to 8 pellets +3 per level while the
 * card still said "5 pellets", "+2 pellets"). These checks read the real values, so a balance
 * change that makes a card lie fails here.
 */
class CardTextMatchesKnobsTest {

    @Before fun defaults() = Tunables.resetAll()
    @After fun cleanup() = Tunables.resetAll()

    private val count = Regex("""^(\+?)(\d+) (\w+)""")

    /** What a weapon's card line counts: bomblets come out of the bomb, everything else is fired. */
    private fun counted(id: String, noun: String, level: Int): Int =
        if (noun.startsWith("bomblet")) WeaponProbe.bomblets(id, level)!! else WeaponProbe.fired(id, level)

    @Test
    fun `every count on a base weapon's level cards matches what it fires`() {
        for (def in WeaponDefinitions.getBaseWeapons()) {
            for (level in 1..5) {
                val line = def.getLevelDescription(level)
                val m = count.find(line) ?: continue
                val (plus, n, noun) = m.destructured
                if (noun.startsWith("blade")) continue   // the saw fires no projectiles; its blade is drawn, not counted
                val now = counted(def.id, noun, level)
                val expected = if (plus.isEmpty()) now else now - counted(def.id, noun, level - 1)
                assertEquals("${def.id} L$level card says \"$line\"", expected, n.toInt())
            }
        }
    }

    @Test
    fun `a card that promises a bigger area really grows that level`() {
        val radiusAt: Map<String, (Int) -> Float> = mapOf(
            "energy_saw" to { l -> KnobsWeapons.sawDiscRadius.forLevel(l) },
            "railgun" to { l -> KnobsWeapons.railgunShotRadius.forLevel(l) },
            "space_mines" to { l -> KnobsWeapons.minesExplosionRadius.forLevel(l) },
            "nova_blast" to { l -> KnobsWeapons.novaBlastRadius.forLevel(l) },
        )
        val grows = Regex("""^(Bigger|Wider|Beam-wide)""")
        for (def in WeaponDefinitions.getBaseWeapons()) {
            for (level in 2..5) {
                val line = def.getLevelDescription(level)
                if (!grows.containsMatchIn(line)) continue
                val radius = radiusAt[def.id] ?: error("${def.id} L$level says \"$line\" but has no radius mapped here")
                assertTrue("${def.id} L$level card says \"$line\"", radius(level) > radius(level - 1))
            }
        }
    }

    private fun percent(f: Float) = "+${(f * 100).roundToInt()}%"

    @Test
    fun `passive cards state their knob values`() {
        val expected = mapOf(
            "nano_repair" to "+${KnobsPassives.nanoRegenPerStack.value} HP/sec",
            "duplicator_core" to "+${KnobsPassives.duplicatorExtraProjectiles.value} projectile",
            "magnet_field" to "${percent(KnobsPassives.magnetRangePerStack.value)} pickup range",
            "momentum_drive" to "${percent(KnobsPassives.momentumDamagePerStack.value)} damage",
            "cryo_field" to "${percent(KnobsPassives.cryoRadiusPerStack.value)} radius",
            "lucky_star" to "${percent(KnobsPassives.luckyDropBonus.value)} drops",
            "vampiric_core" to "+${KnobsPassives.vampiricRangePerStack.value.roundToInt()} drain range",
            "glass_cannon" to "${percent(KnobsPassives.glassDamageBonus.value)} damage",
        )
        for ((id, phrase) in expected) {
            val card = PassiveDefinitions.getPassiveDef(id)!!.effectPerStack
            assertTrue("$id card \"$card\" should state \"$phrase\"", card.replace('\n', ' ').contains(phrase))
        }
    }
}
