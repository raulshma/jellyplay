package com.raulshma.jellyplay.core.ui.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

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
     * Recognizes IPv4 private/loopback/link-local ranges, the IPv6 loopback,
     * `.local` mDNS hostnames, and bare hostnames (common for LAN Jellyfin
     * servers configured without a domain). Strips a scheme and port first.
     */
    fun isLocalAddress(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        // Strip scheme and path/port to isolate the host. Bracketed IPv6
        // (e.g. [::1] or [::1]:8096) must be unwrapped between the brackets
        // *before* port stripping, otherwise the first ':' inside the
        // address is mistaken for the host/port separator.
        val noScheme = host.substringAfter("://", host)
        val authority = noScheme.substringBefore('/').trim()
        val hostOnly = if (authority.startsWith("[")) {
            authority.substringAfter('[').substringBefore(']')
        } else {
            authority.substringBefore(':')
        }
        if (hostOnly.isBlank()) return false

        // .local mDNS / NetBIOS-style names (no dot, or ending in .local).
        if (hostOnly.equals("localhost", ignoreCase = true)) return true
        if (hostOnly.endsWith(".local", ignoreCase = true)) return true
        if ('.' !in hostOnly && hostOnly.firstOrNull()?.isLetter() == true) return true // bare hostname

        // IPv6 loopback.
        if (hostOnly == "::1") return true

        // IPv4 numeric checks.
        val parts = hostOnly.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return when {
                a == 10 -> true                       // 10.0.0.0/8
                a == 172 && b in 16..31 -> true       // 172.16.0.0/12
                a == 192 && b == 168 -> true          // 192.168.0.0/16
                a == 127 -> true                      // 127.0.0.0/8 loopback
                a == 169 && b == 254 -> true          // 169.254.0.0/16 link-local
                else -> false
            }
        }
        return false
    }
}
