package com.raulshma.jellyplay.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.raulshma.jellyplay.core.ui.util.LocalNetworkAccess

/**
 * Android actual of the add-server screen's local-network seam: bridges the
 * legacy :core:ui LocalNetworkAccess object (single source of truth for the
 * Android 17 permission logic) and owns the runtime re-request launcher that
 * commonMain cannot see. The grant state is a Compose state read inside the
 * composable body, so a grant/deny through the launcher recomposes the
 * screen exactly like the legacy inline `localNetworkGranted` var did.
 */
@Composable
internal actual fun rememberLocalNetworkAccess(): LocalNetworkAccessState {
    val context = LocalContext.current

    // Android 17+: local network access is required for SSDP discovery. Track
    // the grant state so we can (a) gate auto-discovery on it and (b) show a
    // rationale banner + re-request affordance when denied. On non-enforcing
    // platforms this short-circuits to granted and the banner never appears.
    var localNetworkGranted by remember(context) {
        mutableStateOf(LocalNetworkAccess.isGranted(context))
    }
    val localNetworkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Just record the result; discovery is kicked off by the
        // LaunchedEffect keyed on the grant flag in AddServerScreen, which is
        // the single trigger for both initial and post-grant scans.
        localNetworkGranted = granted
    }

    return LocalNetworkAccessState(
        enforced = LocalNetworkAccess.enforced,
        isGranted = localNetworkGranted,
        requestAccess = { localNetworkLauncher.launch(LocalNetworkAccess.PERMISSION) },
    )
}
