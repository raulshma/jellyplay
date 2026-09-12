package com.raulshma.jellyplay.feature.auth

import androidx.compose.runtime.Composable

/**
 * Web actual of the add-server screen's local-network seam: the browser has
 * no Android-17-style local-network runtime permission — fetch/SSDP-style
 * LAN traffic is not runtime-gated there — so the platform reports a
 * non-enforcing, always-granted state (desktop JVM precedent). The rationale
 * banner never renders (`enforced` false) and discovery auto-starts
 * unchanged; [LocalNetworkAccessState.requestAccess] is null because there
 * is no request surface to launch.
 */
@Composable
internal actual fun rememberLocalNetworkAccess(): LocalNetworkAccessState =
    LocalNetworkAccessState(
        enforced = false,
        isGranted = true,
        requestAccess = null,
    )
