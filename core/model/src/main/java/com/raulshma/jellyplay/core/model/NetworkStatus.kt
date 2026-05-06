package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
@Immutable
enum class NetworkStatus {
    /** Device has internet access. */
    Online,

    /** Device is on a local LAN (WiFi/Ethernet) but no internet. */
    Local,

    /** Device has no active network connection. */
    Offline,
    ;

    val isOnline: Boolean get() = this == Online
    val isLocal: Boolean get() = this == Local
    val isOffline: Boolean get() = this == Offline
    val hasNetwork: Boolean get() = this != Offline
}
