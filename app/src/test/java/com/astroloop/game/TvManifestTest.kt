package com.astroloop.game

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TvManifestTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun onlyTheGame() = assumeTrue(BuildConfig.FLAVOR == "game")

    @Test
    fun `the game is on the TV home screen and the phone launcher`() {
        val pm = ctx.packageManager
        val tv = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(ctx.packageName), 0)
        val phone = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(ctx.packageName), 0)
        assertEquals(listOf(MainActivity::class.java.name), tv.map { it.activityInfo.name })
        assertEquals(listOf(MainActivity::class.java.name), phone.map { it.activityInfo.name })
    }

    @Test
    fun `the app has a TV banner`() {
        assertNotEquals(0, ctx.applicationInfo.banner)
    }

    @Test
    fun `the app is filed under games`() {
        assertEquals(android.content.pm.ApplicationInfo.CATEGORY_GAME, ctx.applicationInfo.category)
    }

    @Test
    fun `no hardware a TV lacks is required`() {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_CONFIGURATIONS)
        val features = info.reqFeatures.orEmpty().associateBy { it.name }
        for (name in listOf("android.hardware.touchscreen", "android.hardware.faketouch", "android.hardware.gamepad", "android.software.leanback")) {
            val f = features[name]
            assertTrue("$name must be declared", f != null)
            assertEquals("$name must not be required", 0, f!!.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED)
        }
    }
}
