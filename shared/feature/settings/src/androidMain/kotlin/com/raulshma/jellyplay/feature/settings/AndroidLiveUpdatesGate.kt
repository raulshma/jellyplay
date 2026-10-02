package com.raulshma.jellyplay.feature.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual of [rememberLiveUpdatesGate]: API 36+ (the Live Updates /
 * progress-centric notification rollout), the
 * [NotificationManager.canPostPromotedNotifications] probe, and the
 * `ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` deep link. Null below API 36 —
 * the settings row renders nothing there.
 */
@Composable
internal actual fun rememberLiveUpdatesGate(): LiveUpdatesGate? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return null
    val context = LocalContext.current
    return remember(context) {
        object : LiveUpdatesGate {
            private val manager
                get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            override fun isPromoted(): Boolean = manager.canPostPromotedNotifications()

            override fun openGrantScreen() {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
