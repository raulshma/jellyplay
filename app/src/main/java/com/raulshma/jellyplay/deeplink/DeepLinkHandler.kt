package com.raulshma.jellyplay.deeplink

import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.core.ui.navigation.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLinkHandler @Inject constructor() {

    private val supportedHosts = setOf("media", "newsletter", "seerr")

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
        val host = uri.host ?: return null
        if (host !in supportedHosts) return null
        val segments = uri.pathSegments
        return when (host) {
            "media" -> {
                val itemId = segments.firstOrNull() ?: uri.lastPathSegment
                itemId?.let { routeForPath("media", it) }
            }
            "newsletter" -> {
                val section = segments.firstOrNull() ?: uri.lastPathSegment
                section?.let { routeForPath("newsletter", it) }
            }
            "seerr" -> {
                val tmdbId = segments.getOrNull(0)?.toIntOrNull() ?: return null
                val mediaType = segments.getOrNull(1)?.takeIf { it.isNotBlank() }
                    ?: uri.getQueryParameter("type")?.takeIf { it.isNotBlank() }
                    ?: "movie"
                Route.SeerrDetail(tmdbId = tmdbId, mediaType = mediaType)
            }
            else -> null
        }
    }

    private fun parseHttpsScheme(uri: Uri): Route? {
        if (uri.host != HOST_WEB) return null
        val segments = uri.pathSegments
        if (segments.firstOrNull() != PATH_PREFIX) return null
        if (segments.size < 3) return null
        val type = segments[1]
        val itemId = segments[2]
        return routeForPath(type, itemId)
    }

    private fun routeForPath(type: String, itemId: String): Route? {
        return when (type) {
            "media" -> Route.MediaDetail(itemId)
            "newsletter" -> Route.NewsletterSectionList(itemId)
            else -> null
        }
    }

    companion object {
        const val SCHEME_CUSTOM = "jellyplay"
        const val SCHEME_HTTPS = "https"
        const val HOST_WEB = "raulshma.github.io"
        const val PATH_PREFIX = "jellyplay"

        fun createMediaLink(itemId: String): String {
            return "$SCHEME_CUSTOM://media/$itemId"
        }

        fun createWebMediaLink(itemId: String): String {
            return "$SCHEME_HTTPS://$HOST_WEB/$PATH_PREFIX/media/$itemId"
        }

        fun createContinueWatchingLink(): String {
            return "$SCHEME_CUSTOM://newsletter/CONTINUE_WATCHING"
        }

        fun createSeerrLink(tmdbId: Int, mediaType: String): String {
            return "$SCHEME_CUSTOM://seerr/$tmdbId/$mediaType"
        }
    }
}
