package com.raulshma.jellyplay.core.model

/**
 * Represents the current network connectivity state of the device.
 *
 * - **Online**: The device has internet access (validated network capability).
 * - **Local**: The device is connected to a LAN/WiFi network but has no internet access.
 *   The Jellyfin server may still be reachable.
 * - **Offline**: The device has no active network connection at all.
 */
enum class NetworkStatus {
    /** Device has internet access. */
    Online,

    /** Device is on a local LAN (WiFi/Ethernet) but no internet. */
    Local,

    /** No active network connection. */
    Offline,
    ;

    val isOnline: Boolean get() = this == Online
    val isLocal: Boolean get() = this == Local
    val isOffline: Boolean get() = this == Offline
    val hasNetwork: Boolean get() = this != Offline
}
