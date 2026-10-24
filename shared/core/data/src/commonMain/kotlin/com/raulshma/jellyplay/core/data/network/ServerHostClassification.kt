package com.raulshma.jellyplay.core.data.network

/**
 * Pure host classifier deciding whether a server address points at the
 * LOCAL network — the network-split bitrate cap's input (#19: a home
 * Wi-Fi server honours the `home_network_streaming_quality` tier, a remote
 * (relayed / cellular-out) server stays on the wifi/cellular ladder).
 *
 * Deliberately a DUPLICATE of core:ui's `LocalNetworkAccess.isLocalAddress`
 * (same RFC1918/loopback/link-local/`.local`/bare-hostname table, same IPv6
 * bracket parsing): core:ui does not depend on core:data (and must not —
 * the edge would cycle the module graph), so the table lives twice. The
 * jvmTest here pins the SAME case table as the core:ui Robolectric suite so
 * the two copies cannot drift silently.
 *
 * Recognizes IPv4 private/loopback/link-local ranges, the IPv6 loopback,
 * `.local` mDNS hostnames, and bare hostnames (common for LAN Jellyfin
 * servers configured without a domain). Strips a scheme and port first.
 */
fun isLocalServerHost(host: String): Boolean {
    if (host.isBlank()) return false
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

/**
 * Platform-neutral host extraction from a server URL (the same string parse
 * on every target): everything after the `scheme://` separator, minus the
 * path, userinfo credentials (`user:pass@host`) and port. Returns `null`
 * when the URL carries no host part. Fed to [isLocalServerHost] by the
 * playback-resolution callers.
 */
fun serverHostOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val noScheme = url.substringAfter("://", url)
    val authority = noScheme.substringBefore('/', noScheme).substringBefore('?', noScheme).trim()
    // Credentials live before the LAST '@' of the authority (a password may
    // itself contain '@').
    val hostPort = authority.substringAfterLast('@', authority)
    if (hostPort.startsWith("[")) {
        // Bracketed IPv6 — the address ends at the closing bracket; a port
        // may follow (e.g. `[::1]:8096`).
        val inside = hostPort.substringAfter('[', hostPort).substringBefore(']', "")
        return inside.ifBlank { null }
    }
    val host = hostPort.substringBefore(':').trim()
    return host.ifBlank { null }
}
