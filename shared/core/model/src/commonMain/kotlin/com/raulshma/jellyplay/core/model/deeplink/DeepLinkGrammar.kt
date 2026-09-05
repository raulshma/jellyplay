package com.raulshma.jellyplay.core.model.deeplink

/**
 * Canonical grammar for the app's deep links. Pure Kotlin — no Android
 * imports — so every module that emits a link (notifications, TV Watch Next,
 * share sheet) and the :app router build/parse through this single source of
 * truth instead of private string literals.
 *
 * Shapes:
 *   jellyplay://media/{itemId}
 *   jellyplay://newsletter/{section}
 *   jellyplay://seerr/{tmdbId}/{mediaType}   (mediaType may also ride as
 *                                             ?type= or default to movie at
 *                                             parse time)
 *   jellyplay://search|settings|downloads|library   (argument-less)
 *   https://{HOST_WEB}/{PATH_PREFIX}/{...}   (web mirror of the same paths)
 *
 * Builders and parsers round-trip; the emitted strings are byte-identical to
 * the literals this grammar replaced.
 */
object DeepLinkGrammar {

    // --- URL furniture ---
    const val SCHEME_CUSTOM = "jellyplay"
    const val SCHEME_HTTPS = "https"
    const val HOST_WEB = "raulshma.github.io"

    /** First path segment of the web mirror, e.g. https://{HOST_WEB}/jellyplay/... */
    const val PATH_PREFIX = "jellyplay"

    // --- destinations ---
    const val HOST_MEDIA = "media"
    const val HOST_NEWSLETTER = "newsletter"
    const val HOST_SEERR = "seerr"
    const val HOST_SEARCH = "search"
    const val HOST_SETTINGS = "settings"
    const val HOST_DOWNLOADS = "downloads"
    const val HOST_LIBRARY = "library"

    val supportedHosts: Set<String> = setOf(
        HOST_MEDIA, HOST_NEWSLETTER, HOST_SEERR,
        // Top-level destinations reachable via deep link, so
        // widgets/notifications/launcher shortcuts can jump anywhere.
        HOST_SEARCH, HOST_SETTINGS, HOST_DOWNLOADS, HOST_LIBRARY,
    )

    const val NEWSLETTER_SECTION_CONTINUE_WATCHING = "CONTINUE_WATCHING"

    const val SEERR_TYPE_QUERY_PARAM = "type"
    const val SEERR_DEFAULT_MEDIA_TYPE = "movie"

    // --- builders ---

    /** jellyplay://media/{itemId} */
    fun mediaLink(itemId: String): String = "$SCHEME_CUSTOM://$HOST_MEDIA/$itemId"

    /** https://{HOST_WEB}/{PATH_PREFIX}/media/{itemId} */
    fun webMediaLink(itemId: String): String =
        "$SCHEME_HTTPS://$HOST_WEB/$PATH_PREFIX/$HOST_MEDIA/$itemId"

    /** jellyplay://newsletter/{section} */
    fun newsletterSectionLink(section: String): String =
        "$SCHEME_CUSTOM://$HOST_NEWSLETTER/$section"

    fun continueWatchingLink(): String =
        newsletterSectionLink(NEWSLETTER_SECTION_CONTINUE_WATCHING)

    /** jellyplay://seerr/{tmdbId}/{mediaType} */
    fun seerrLink(tmdbId: Int, mediaType: String): String =
        "$SCHEME_CUSTOM://$HOST_SEERR/$tmdbId/$mediaType"

    /** Argument-less top-level destinations. */
    fun searchLink(): String = "$SCHEME_CUSTOM://$HOST_SEARCH"
    fun settingsLink(): String = "$SCHEME_CUSTOM://$HOST_SETTINGS"
    fun downloadsLink(): String = "$SCHEME_CUSTOM://$HOST_DOWNLOADS"
    fun libraryLink(): String = "$SCHEME_CUSTOM://$HOST_LIBRARY"

    // --- parsing ---

    /**
     * Parses a custom-scheme link from its already-extracted parts. [host]
     * and [pathSegments] mirror android.net.Uri's host/pathSegments (and any
     * non-Android equivalent); [queryParameters] maps decoded query names to
     * decoded values. Returns null for unknown hosts and malformed payloads.
     */
    fun parseCustom(
        host: String?,
        pathSegments: List<String>,
        queryParameters: Map<String, String> = emptyMap(),
    ): DeepLinkTarget? {
        if (host == null || host !in supportedHosts) return null
        return when (host) {
            HOST_MEDIA -> pathSegments.firstOrNull()?.let(DeepLinkTarget::MediaDetail)
            HOST_NEWSLETTER -> pathSegments.firstOrNull()?.let(DeepLinkTarget::NewsletterSection)
            HOST_SEERR -> {
                val tmdbId = pathSegments.getOrNull(0)?.toIntOrNull() ?: return null
                val mediaType = pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() }
                    ?: queryParameters[SEERR_TYPE_QUERY_PARAM]?.takeIf { it.isNotBlank() }
                    ?: SEERR_DEFAULT_MEDIA_TYPE
                DeepLinkTarget.SeerrDetail(tmdbId, mediaType)
            }
            // Argument-less top-level destinations
            HOST_SEARCH -> DeepLinkTarget.Search
            HOST_SETTINGS -> DeepLinkTarget.Settings
            HOST_DOWNLOADS -> DeepLinkTarget.Downloads
            HOST_LIBRARY -> DeepLinkTarget.Library
            else -> null
        }
    }

    /**
     * Parses the web mirror from its path segments; the caller must first
     * verify the host is [HOST_WEB]. Only [PATH_PREFIX] + destination type are
     * required: item-bearing routes (media/newsletter) take the trailing id
     * segment, argument-less routes ignore it (absent → empty string,
     * mirroring the router's pinned behaviour).
     */
    fun parseWeb(pathSegments: List<String>): DeepLinkTarget? {
        if (pathSegments.firstOrNull() != PATH_PREFIX) return null
        val type = pathSegments.getOrNull(1) ?: return null
        val itemId = pathSegments.getOrNull(2)
        return routeForPath(type, itemId ?: "")
    }

    private fun routeForPath(type: String, itemId: String): DeepLinkTarget? {
        return when (type) {
            HOST_MEDIA -> DeepLinkTarget.MediaDetail(itemId)
            HOST_NEWSLETTER -> DeepLinkTarget.NewsletterSection(itemId)
            HOST_SEARCH -> DeepLinkTarget.Search
            HOST_SETTINGS -> DeepLinkTarget.Settings
            HOST_DOWNLOADS -> DeepLinkTarget.Downloads
            HOST_LIBRARY -> DeepLinkTarget.Library
            else -> null
        }
    }
}

/** Destination carried by a deep link, independent of any navigation type. */
sealed class DeepLinkTarget {
    data class MediaDetail(val itemId: String) : DeepLinkTarget()
    data class NewsletterSection(val section: String) : DeepLinkTarget()
    data class SeerrDetail(val tmdbId: Int, val mediaType: String) : DeepLinkTarget()
    data object Search : DeepLinkTarget()
    data object Settings : DeepLinkTarget()
    data object Downloads : DeepLinkTarget()
    data object Library : DeepLinkTarget()
}
