package com.raulshma.jellyplay.feature.admin.plugins

/**
 * Catalog filter and trust constants for the plugins screens, mirroring
 * jellyfin-web's plugin dashboard (`pluginCategory.ts`, `plugin.tsx`).
 */

/** The official Jellyfin plugin repository. Installs from here skip the disclaimer. */
internal const val TRUSTED_REPO_URL = "https://repo.jellyfin.org/"

/**
 * Whether a plugin version's source repository is trusted (official). Installs
 * from untrusted repos must prompt the user with a security disclaimer before
 * proceeding, matching jellyfin-web's `onInstall` flow.
 */
internal fun isTrustedRepository(repositoryUrl: String?): Boolean {
    if (repositoryUrl.isNullOrBlank()) return false
    return repositoryUrl.startsWith(TRUSTED_REPO_URL, ignoreCase = true)
}

/** Plugin catalog categories shown as filter chips (mirror `pluginCategory.ts`). */
enum class PluginCategory(val displayName: String) {
    ALL("All"),
    ADMINISTRATION("Administration"),
    GENERAL("General"),
    ANIME("Anime"),
    BOOKS("Books"),
    LIVE_TV("Live TV"),
    MOVIES_AND_SHOWS("Movies & Shows"),
    MUSIC("Music"),
    SUBTITLES("Subtitles"),
    OTHER("Other");

    companion object {
        /** Maps a server-reported category string to a chip value, defaulting to OTHER. */
        fun fromServer(category: String?): PluginCategory {
            if (category.isNullOrBlank()) return OTHER
            return when (category.lowercase().replace(" ", "").replace("&", "and")) {
                "administration" -> ADMINISTRATION
                "general" -> GENERAL
                "anime" -> ANIME
                "books" -> BOOKS
                "livetv" -> LIVE_TV
                "moviesandshows" -> MOVIES_AND_SHOWS
                "music" -> MUSIC
                "subtitles" -> SUBTITLES
                else -> OTHER
            }
        }
    }
}
