package com.raulshma.jellyplay.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Composable-side platform seam for the Android 17+ local network permission
 * (search-module `rememberVoiceSearchLauncher` shape): what the add-server
 * screen needs to know about the grant and how to re-request it.
 *
 * Legacy shape: `LocalContext` + `LocalNetworkAccess.isGranted(context)` +
 * `rememberLauncherForActivityResult(RequestPermission)` inline in the
 * screen. The Android actual below bridges the legacy :core:ui
 * LocalNetworkAccess object; the desktop actual reports a non-enforcing,
 * always-granted platform (no permission system — SSDP multicast and LAN
 * sockets work out of the box), so the rationale banner never renders and
 * discovery auto-starts unchanged.
 */
@Stable
class LocalNetworkAccessState(
    /** Whether the platform enforces a local-network runtime permission. */
    val enforced: Boolean,
    /** Whether that permission is currently held (true when not enforced). */
    val isGranted: Boolean,
    /**
     * Launches the system permission request, or `null` when this platform
     * has no request surface (never granted-denied there — `enforced` is
     * false too).
     */
    val requestAccess: (() -> Unit)?,
)

@Composable
internal expect fun rememberLocalNetworkAccess(): LocalNetworkAccessState
