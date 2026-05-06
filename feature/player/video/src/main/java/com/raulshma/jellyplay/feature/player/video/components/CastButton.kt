package com.raulshma.jellyplay.feature.player.video.components

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun CastButton(isCasting: Boolean, onCast: () -> Unit) {
    val context = LocalContext.current
    val isActive = isCasting
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else Color.White.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp),
        ) {
            IconButton(
                onClick = {
                    if (isCasting) {
                        try {
                            val castContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
                            castContext.sessionManager.endCurrentSession(true)
                        } catch (_: Exception) {}
                    } else {
                        try {
                            val castContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
                            val sessionManager = castContext.sessionManager
                            val session = sessionManager.currentCastSession
                            if (session?.isConnected == true) {
                                sessionManager.endCurrentSession(true)
                            } else {
                                val activity = context as? Activity ?: return@IconButton
                                val routeSelector = castContext.mergedSelector ?: return@IconButton
                                val dialog = androidx.mediarouter.app.MediaRouteChooserDialog(activity)
                                dialog.routeSelector = routeSelector
                                dialog.show()
                            }
                        } catch (_: Exception) {
                            onCast()
                        }
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                    contentDescription = "Cast",
                    tint = if (isCasting) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Cast",
            color = if (isCasting) MaterialTheme.colorScheme.primary else Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
