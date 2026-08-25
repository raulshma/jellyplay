package com.raulshma.jellyplay.feature.auth

import androidx.compose.runtime.Composable

/**
 * Desktop actual of the add-server screen's local-network seam: the desktop
 * JVM has no Android-17-style local-network runtime permission — SSDP
 * multicast and LAN sockets work out of the box — so the platform reports a
 * non-enforcing, always-granted state. The rationale banner never renders
 * (`enforced` false) and discovery auto-starts unchanged;
 * [LocalNetworkAccessState.requestAccess] is null because there is no
 * request surface to launch.
 */
@Composable
internal actual fun rememberLocalNetworkAccess(): LocalNetworkAccessState =
    LocalNetworkAccessState(
        enforced = false,
        isGranted = true,
        requestAccess = null,
    )
