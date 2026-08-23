package com.raulshma.jellyplay.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual of the [PlatformIntents] seam — the pre-migration
 * AboutScreen / NotificationSettingsScreen intent bodies, verbatim, just
 * funnelled through one object reached from the composable's LocalContext.
 */
internal class AndroidPlatformIntents(
    private val context: Context,
) : PlatformIntents {
    override fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    override fun shareLogFile(subject: String, chooserTitle: String, fileUri: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, Uri.parse(fileUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }

    override fun shareJson(subject: String, chooserTitle: String, body: String) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(share, chooserTitle))
    }

    override fun canOpenSystemNotificationSettings(): Boolean = true

    override fun openSystemNotificationSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }
}

@Composable
internal actual fun rememberPlatformIntents(): PlatformIntents {
    val context = LocalContext.current
    return remember(context) { AndroidPlatformIntents(context) }
}
