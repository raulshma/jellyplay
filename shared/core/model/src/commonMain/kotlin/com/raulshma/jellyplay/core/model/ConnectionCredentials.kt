package com.raulshma.jellyplay.core.model

/**
 * Everything needed to open the authenticated session's realtime socket: the
 * resolved server endpoint, the user's access token, and this client's device
 * identity as the server sees it.
 */
data class ConnectionCredentials(
    val serverAddress: String,
    val accessToken: String,
    val deviceId: String,
    val deviceName: String,
    val clientName: String,
) {
    companion object {
        /**
         * The device name reported to the server on every realtime-socket
         * connect — shell session restore and SyncPlay must build it
         * identically or the server shows two differently-named sessions.
         * Caps: 20 chars of user name, 60 chars total.
         */
        fun deviceNameFor(userName: String): String {
            val model = deviceModel()
            val name = userName.take(20)
            val full = if (name.isNotBlank()) "JellyPlay on $model ($name)" else "JellyPlay on $model"
            return if (full.length > 60) full.take(60) else full
        }
    }
}
