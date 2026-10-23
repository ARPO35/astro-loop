package com.astroloop.game.lab

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = LabApp::class)
class LabTvManifestTest {
    @Test
    fun `the Lab has no TV launcher entry`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tv = ctx.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(ctx.packageName), 0)
        assertEquals(emptyList<String>(), tv.map { it.activityInfo.name })
    }
}
