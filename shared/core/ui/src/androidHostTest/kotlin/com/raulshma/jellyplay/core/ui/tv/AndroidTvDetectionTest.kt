package com.raulshma.jellyplay.core.ui.tv

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Pins [Context.isTv]: TV-ness is decided solely by the platform's leanback
 * system features — [PackageManager.FEATURE_LEANBACK] or
 * [PackageManager.FEATURE_LEANBACK_ONLY]. A handset (neither feature) must not
 * be classified as TV.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidTvDetectionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Deterministic handset baseline: Robolectric's default feature set is
        // not guaranteed to omit leanback.
        val pm = shadowOf(context.packageManager)
        pm.setSystemFeature(PackageManager.FEATURE_LEANBACK, false)
        pm.setSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY, false)
    }

    @Test
    fun `device with neither leanback feature is not a tv`() {
        assertFalse(context.isTv())
    }

    @Test
    fun `device with FEATURE_LEANBACK is a tv`() {
        shadowOf(context.packageManager)
            .setSystemFeature(PackageManager.FEATURE_LEANBACK, true)

        assertTrue(context.isTv())
    }

    @Test
    fun `device with only FEATURE_LEANBACK_ONLY is a tv`() {
        shadowOf(context.packageManager)
            .setSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY, true)

        assertTrue(context.isTv())
    }

    @Test
    fun `removing the leanback feature flips the classification back`() {
        shadowOf(context.packageManager)
            .setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        assertTrue(context.isTv())

        shadowOf(context.packageManager)
            .setSystemFeature(PackageManager.FEATURE_LEANBACK, false)

        assertFalse(context.isTv())
    }
}
