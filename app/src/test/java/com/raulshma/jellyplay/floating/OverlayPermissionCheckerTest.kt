package com.raulshma.jellyplay.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the overlay-permission gate that fronts the floating-player service:
 * [OverlayPermissionChecker.canDrawOverlays] mirrors the system
 * `Settings.canDrawOverlays` verdict, and the permission intent opens the
 * system "Display over other apps" screen scoped to this package with
 * FLAG_ACTIVITY_NEW_TASK so it can be launched from a non-activity context.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OverlayPermissionCheckerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `canDrawOverlays is false without the permission`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false)

        assertFalse(OverlayPermissionChecker.canDrawOverlays(context))
    }

    @Test
    fun `canDrawOverlays is true once the system grant is set`() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)

        assertTrue(OverlayPermissionChecker.canDrawOverlays(context))
    }

    @Test
    fun `permission intent opens the manage-overlay screen for this package`() {
        val intent = OverlayPermissionChecker.createPermissionIntent(context)

        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals(
            Uri.parse("package:${context.packageName}"),
            intent.data,
        )
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }
}
