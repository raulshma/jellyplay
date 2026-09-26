package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.AmbientColorBackdrop

/**
 * The idle "Ready to play" ambient surface — the in-scaffold overlay
 * DesktopNavScaffold cross-fades over the whole content area when
 * [DesktopIdleMonitor.isIdle] turns true.
 *
 * Deliberately NOT a NavKey route (avoids shared-contract/R8 churn for a
 * desktop-only surface): an AnimatedVisibility overlay with the SAME fade
 * pair NavTransitionPolicy gives ambient routes (defaultFade in / fastFade
 * out). Any input dismisses — the scaffold's root preview-key handler and
 * this surface's tap handler both feed [DesktopIdleMonitor.onUserInput].
 *
 * Backdrop: the shared [AmbientColorBackdrop] with its default deep-tone
 * palette — the exact layer feature:home's `HomeBackdrop` renders on the
 * desktop, where the artwork-palette seam (`rememberArtworkColors`) is a
 * documented no-op and the ambient gradient is the backdrop. A rotating
 * artwork carousel over the home hero set would need the home screen's
 * view-model state, which the shell cannot cheaply reach from the scaffold;
 * the drifting-blob field carries the ambient mood instead.
 *
 * The session line counts OTHER sessions currently playing something (from
 * the `Sessions` WS push the receiver's socket already receives) — the
 * cheap, honest form of "remote activity" the plan allowed for.
 */
@Composable
internal fun DesktopIdleOverlay(
    serverName: String?,
    userName: String?,
    activeSessionCount: Int,
    onAnyInput: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Any pointer press dismisses (the key path runs through the
            // scaffold's root preview handler, which feeds the same hook).
            .pointerInput(onAnyInput) {
                detectTapGestures { onAnyInput() }
            },
    ) {
        AmbientColorBackdrop(colors = emptyList())
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Ready to play on this device",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            val identity = listOfNotNull(
                serverName?.takeIf { it.isNotBlank() },
                userName?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (identity.isNotBlank()) {
                Text(
                    text = identity,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (activeSessionCount > 0) {
                Text(
                    text = "$activeSessionCount remote session${if (activeSessionCount == 1) "" else "s"} active",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
