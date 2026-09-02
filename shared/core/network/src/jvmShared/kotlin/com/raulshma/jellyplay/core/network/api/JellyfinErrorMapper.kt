package com.raulshma.jellyplay.core.network.api

import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal object JellyfinErrorMapper {

    fun map(throwable: Throwable): String = when (throwable) {
        is UnknownHostException -> "Unable to reach server. Check the URL and your network connection."
        is ConnectException -> "Could not connect to server. Ensure it is running and reachable."
        is SocketTimeoutException, is TimeoutException -> "Connection timed out. The server took too long to respond."
        is InvalidStatusException -> when (throwable.status) {
            401 -> "Authentication required. Please sign in again."
            403 -> "You don't have permission to access this item."
            404 -> "Item not found."
            in 500..599 -> "Server error (${throwable.status}). Please try again later."
            else -> "Request failed (${throwable.status})."
        }
        is IOException -> "Network error. Check your connection and try again."
        else -> throwable.message ?: throwable.javaClass.simpleName
    }
}
