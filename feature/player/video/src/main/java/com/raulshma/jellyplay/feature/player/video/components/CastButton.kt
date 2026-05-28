package com.raulshma.jellyplay.feature.player.video.components

import android.app.AlertDialog
import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

/**
 * A pure Compose cast button that avoids all [androidx.mediarouter.app] UI components
 * (MediaRouteButton, MediaRouteChooserDialog, etc.) which crash when the Activity theme
 * lacks an opaque `android:windowBackground`.
 *
 * Instead, tapping the button shows a simple [AlertDialog] listing available cast routes,
 * built with the platform [AlertDialog] which has no theme dependency on the Activity.
 */
@Composable
internal fun CastButton() {
    val context = LocalContext.current
    var isConnected by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                isConnected = true
            }
            override fun onSessionEnded(session: CastSession, error: Int) {
                isConnected = false
            }
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                isConnected = true
            }
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                isConnected = false
            }
            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionResumeFailed(session: CastSession, error: Int) {}
            override fun onSessionStartFailed(session: CastSession, error: Int) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
        }

        try {
            val castContext = CastContext.getSharedInstance(context)
            castContext.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
            isConnected = castContext.sessionManager.currentCastSession?.isConnected == true
        } catch (_: Exception) {
            // Cast SDK unavailable on this device
        }

        onDispose {
            try {
                CastContext.getSharedInstance(context)
                    .sessionManager
                    .removeSessionManagerListener(listener, CastSession::class.java)
            } catch (_: Exception) { }
        }
    }

    if (showDialog) {
        DisposableEffect(Unit) {
            val dialog = buildRouteListDialog(context) { showDialog = false }
            dialog?.show()
            onDispose { dialog?.dismiss() }
        }
    }

    IconButton(
        onClick = {
            try {
                val sessionManager = CastContext.getSharedInstance(context).sessionManager
                val session = sessionManager.currentCastSession
                if (session != null && session.isConnected) {
                    sessionManager.endCurrentSession(true)
                } else {
                    showDialog = true
                }
            } catch (_: Exception) { }
        },
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = if (isConnected) Tabler.Outline.Cast else Tabler.Outline.Cast,
            contentDescription = if (isConnected) "Cast connected" else "Cast",
            tint = Color.White,
        )
    }
}

/**
 * Builds a platform [AlertDialog] listing discovered cast routes.
 * Uses [MediaRouter] directly — no [androidx.mediarouter.app] theme-dependent components.
 */
private fun buildRouteListDialog(
    context: Context,
    onDismiss: () -> Unit,
): AlertDialog? {
    val router: MediaRouter
    try {
        val castContext = CastContext.getSharedInstance(context)
        router = MediaRouter.getInstance(context)
    } catch (_: Exception) {
        return null
    }

    val routes = router.routes.filter { route ->
        route.isEnabled && route.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE
    }

    if (routes.isEmpty()) {
        return AlertDialog.Builder(context)
            .setTitle("Cast")
            .setMessage("No cast devices found. Make sure your device is on the same network.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss(); onDismiss() }
            .setOnDismissListener { onDismiss() }
            .create()
    }

    val routeNames = routes.map { it.name }.toTypedArray()
    return AlertDialog.Builder(context)
        .setTitle("Cast to device")
        .setItems(routeNames) { dialog, which ->
            val route = routes[which]
            router.selectRoute(route)
            dialog.dismiss()
            onDismiss()
        }
        .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss(); onDismiss() }
        .setOnDismissListener { onDismiss() }
        .create()
}


