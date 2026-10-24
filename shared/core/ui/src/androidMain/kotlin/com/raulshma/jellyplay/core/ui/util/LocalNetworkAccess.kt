package com.raulshma.jellyplay.core.ui.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.raulshma.jellyplay.core.model.isLocalServerHost

/**
 * Helper for the Android 17 (API 37) local network permission.
 *
 * Starting with Android 17, apps that target SDK 37+ are blocked from
 * communicating with devices on the local network (LAN) unless they hold
 * [Manifest.permission.ACCESS_LOCAL_NETWORK]. This covers both multicast
 * discovery (SSDP/DLNA) and direct TCP/HTTP connections to private-range
 * hosts (e.g. a Jellyfin server at `192.168.x.x`).
 *
 * The permission is a runtime permission: it must be declared in the manifest
 * *and* requested from the user. See
 * https://developer.android.com/privacy-and-security/local-network-permission
 */
object LocalNetworkAccess {

    /** Whether the platform enforces the local network permission for this app. */
    val enforced: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

    /** The runtime permission string to request. */
    const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

    /**
     * Whether the app currently holds the local network permission.
     * Always `true` on platforms that don't enforce it (the implicit access
     * via INTERNET still applies there).
     */
    fun isGranted(context: Context): Boolean {
        if (!enforced) return true
        return context.checkPermission(
            PERMISSION,
            android.os.Process.myPid(),
            android.os.Process.myUid(),
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Heuristic for whether a host string refers to a local-network target.
     *
     * Used to decide whether a connection failure is *plausibly* caused by the
     * local network permission being denied — public hosts (and their DNS
     * resolution) are unaffected by the permission, so surfacing a
     * "local network blocked" message for them would be misleading.
     *
     * Delegates to the shared classifier ([isLocalServerHost] in core:model)
     * — the single home for the case table, also read by the network-split
     * bitrate cap.
     */
    fun isLocalAddress(host: String?): Boolean = host != null && isLocalServerHost(host)
}
