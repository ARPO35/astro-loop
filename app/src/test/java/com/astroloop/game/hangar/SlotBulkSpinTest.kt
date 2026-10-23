package com.astroloop.game.hangar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.data.PersistenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 20 连抽的账：一次点击结算 20 次单抽，不能出现只扣不发、只发不扣或余额不足时只做一半。
 *
 * 每次开奖都是独立的随机事件，所以断言只钉“钱和次数”的恒等式：
 * 结余 = 原余额 - 20 × 单抽价 + 汇总赢得的现金；赌场次数 +20。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SlotBulkSpinTest {

    private lateinit var persistence: PersistenceManager
    private lateinit var view: HangarSurfaceView

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        persistence = PersistenceManager(context)
        persistence.resetAllProgress()
        persistence.setYen(100_000)
        view = HangarSurfaceView(context) { _, _ -> }
        view.state.actualYen = persistence.getYen()
    }

    @Test
    fun `an unaffordable 20 pull charges nothing and reports nothing`() {
        val short = StorePageRenderer.BULK_SPIN_COST - 1
        persistence.setYen(short)
        view.state.actualYen = short
        val spinsBefore = persistence.getTotalCasinoSpins()

        view.handleBulkSlotSpin()

        assertEquals("the wallet must not be touched at all", short, persistence.getYen())
        assertEquals(short, view.state.actualYen)
        assertEquals("no summary may be published", 0L, view.state.bulkSpinTime)
        assertEquals(0, view.state.bulkSpinCount)
        assertEquals("not even one spin may be charged", spinsBefore, persistence.getTotalCasinoSpins())
    }

    @Test
    fun `exactly the bulk price buys all 20 spins`() {
        val stake = StorePageRenderer.BULK_SPIN_COST
        persistence.setYen(stake)
        view.state.actualYen = stake
        val spinsBefore = persistence.getTotalCasinoSpins()

        view.handleBulkSlotSpin()

        assertEquals(StorePageRenderer.BULK_SPIN_COUNT, view.state.bulkSpinCount)
        assertEquals(spinsBefore + StorePageRenderer.BULK_SPIN_COUNT, persistence.getTotalCasinoSpins())
        assertEquals(
            "all 2000 is spent, then exactly the winnings come back",
            view.state.bulkSpinTotalYen, persistence.getYen()
        )
        assertEquals(persistence.getYen(), view.state.actualYen)
        assertTrue("a completed batch must publish a summary", view.state.bulkSpinTime > 0)
    }

    @Test
    fun `a bulk pull settles 20 single spins and pays their sum`() {
        val before = persistence.getYen()
        val spinsBefore = persistence.getTotalCasinoSpins()

        view.handleBulkSlotSpin()

        assertEquals(StorePageRenderer.BULK_SPIN_COUNT, view.state.bulkSpinCount)
        assertEquals(spinsBefore + StorePageRenderer.BULK_SPIN_COUNT, persistence.getTotalCasinoSpins())
        assertEquals(
            before - StorePageRenderer.BULK_SPIN_COST + view.state.bulkSpinTotalYen,
            persistence.getYen()
        )
        assertEquals(persistence.getYen(), view.state.actualYen)
    }

    @Test
    fun `a bulk pull is refused while the reels are still spinning`() {
        persistence.setYen(100_000)
        view.state.actualYen = 100_000
        view.handleSlotSpin(roll = 0.9f)
        assertTrue("the single spin must be in flight", view.state.isSpinning)
        val yenAfterSingle = persistence.getYen()
        val spinsAfterSingle = persistence.getTotalCasinoSpins()

        view.handleBulkSlotSpin()

        assertEquals("a second trigger must not charge anything", yenAfterSingle, persistence.getYen())
        assertEquals(spinsAfterSingle, persistence.getTotalCasinoSpins())
        assertEquals(0L, view.state.bulkSpinTime)
    }
}
