package com.raulshma.jellyplay.deeplink

import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.core.ui.navigation.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLinkHandler @Inject constructor() {

    private val supportedHosts = setOf("media")

    fun parse(intent: Intent): Route? {
        val uri = intent.data ?: return null
        return parseUri(uri)
    }

    private fun parseUri(uri: Uri): Route? {
        return when (uri.scheme) {
            SCHEME_CUSTOM -> parseCustomScheme(uri)
            SCHEME_HTTPS -> parseHttpsScheme(uri)
            else -> null
        }
    }

    private fun parseCustomScheme(uri: Uri): Route? {
        if (uri.host !in supportedHosts) return null
        val itemId = uri.lastPathSegment ?: return null
        return routeForPath(uri.host ?: return null, itemId)
    }

    private fun parseHttpsScheme(uri: Uri): Route? {
        if (uri.host != HOST_WEB) return null
        val segments = uri.pathSegments
        if (segments.size < 2) return null
        val type = segments[0]
        val itemId = segments[1]
        return routeForPath(type, itemId)
    }

    private fun routeForPath(type: String, itemId: String): Route? {
        return when (type) {
            "media" -> Route.MediaDetail(itemId)
            else -> null
        }
    }

    companion object {
        const val SCHEME_CUSTOM = "jellyplay"
        const val SCHEME_HTTPS = "https"
        const val HOST_WEB = "jellyplay.app"

        fun createMediaLink(itemId: String): String {
            return "$SCHEME_CUSTOM://media/$itemId"
        }

        fun createWebMediaLink(itemId: String): String {
            return "$SCHEME_HTTPS://$HOST_WEB/media/$itemId"
        }
    }
}
