package com.raulshma.jellyplay.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Pure utility for checking and requesting the [Settings.ACTION_MANAGE_OVERLAY_PERMISSION]
 * required to display a [android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 * window.
 *
 * On Android 6 (API 23) and above, apps must explicitly request overlay permission.
 * Below API 23, the permission is granted at install time.
 *
 * Extracted as a standalone object so the permission logic is unit-testable
 * (the [Context] / [Settings] calls are delegated to injectable lambdas).
 */
object OverlayPermissionChecker {

    /**
     * Returns `true` when the app is allowed to draw overlays.
     *
     * On API < 23 (pre-M), overlay permission is implicitly granted.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Creates the [Intent] that opens the system "Display over other apps"
     * settings screen for this app. The caller should `startActivity(intent)`.
     */
    fun createPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
