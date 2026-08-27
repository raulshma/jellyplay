package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Web actuals of the detail platform seams (wave 16C) — bodies copied from
 * DetailPlatform.jvm.kt, which is the matching no-embed/no-share posture:
 *
 * Share: no web share sheet v1 — no-op (documented dead-click,
 * subtitle-tester settings-row precedent; the options-menu entry stays
 * visible).
 */
@Composable
internal actual fun rememberShareMediaAction(itemId: String, chooserTitle: String): () -> Unit =
    remember(itemId) {
        { /* no-op on web v1 */ }
    }

/**
 * Web actual of the trailer-host seam: no in-app YouTube embed (the WebView
 * host is android-only, dies at Phase X) — fire the embed-failed path so call
 * sites take their existing fallback (external browser link / autoplay
 * overlay hidden), exactly like the desktop actual and like an Android
 * WebView embed failure. On web that fallback is uriHandler.openUri(...) —
 * LocalUriHandler must be provisioned by the shell for entries composing this
 * screen.
 */
@Composable
internal actual fun InlineTrailerPlayerHost(
    videoKey: String,
    modifier: Modifier,
    muted: Boolean,
    showControls: Boolean,
    autoplay: Boolean,
    focusable: Boolean,
    cropToFill: Boolean,
    onEmbedFailed: () -> Unit,
) {
    LaunchedEffect(videoKey) { onEmbedFailed() }
}
