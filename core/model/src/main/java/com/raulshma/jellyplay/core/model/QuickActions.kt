package com.raulshma.jellyplay.core.model

/**
 * Which media types are eligible for quick actions, scoped per screen.
 *
 * - [LIBRARY]: the broadest set — everything playable except photos/unknown
 *   (library, favorites, search, studio detail).
 * - [HOME]: the home-screen subset (no music videos, collections, live tv,
 *   channels — those aren't surfaced on home rails).
 * - [DETAIL]: only the core video navigation types (movie/series/season/episode),
 *   used by the detail/collection/person screens.
 */
enum class MediaQuickActionScope {
    LIBRARY,
    HOME,
    DETAIL,
}

private val LIBRARY_ACTIONABLE_TYPES = setOf(
    MediaType.MOVIE, MediaType.SERIES, MediaType.SEASON, MediaType.EPISODE,
    MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM, MediaType.ARTIST,
    MediaType.MUSIC_VIDEO, MediaType.COLLECTION, MediaType.LIVE_TV, MediaType.CHANNEL,
)

private val HOME_ACTIONABLE_TYPES = setOf(
    MediaType.MOVIE, MediaType.SERIES, MediaType.SEASON, MediaType.EPISODE,
    MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM, MediaType.ARTIST,
)

private val DETAIL_ACTIONABLE_TYPES = setOf(
    MediaType.MOVIE, MediaType.SERIES, MediaType.SEASON, MediaType.EPISODE,
)

private fun MediaQuickActionScope.actionableTypes(): Set<MediaType> = when (this) {
    MediaQuickActionScope.LIBRARY -> LIBRARY_ACTIONABLE_TYPES
    MediaQuickActionScope.HOME -> HOME_ACTIONABLE_TYPES
    MediaQuickActionScope.DETAIL -> DETAIL_ACTIONABLE_TYPES
}

/**
 * Resolves the ordered quick actions available for this [MediaItem] in the given
 * [scope]. Returns an empty list for types outside the scope (e.g. photos).
 *
 * Centralizes the per-screen `when (item.mediaType)` predicate logic that was
 * previously duplicated across library/favorites/search/studio/home/detail/
 * collection/person screens. Execution of each action (navigation, viewModel
 * calls) stays at the call site via [com.raulshma.jellyplay.core.ui.components.MediaQuickActionController].
 *
 * @param includeDownload When true, adds [QuickAction.DOWNLOAD] for audio and
 *   video types. Matches the type half of `MediaOptionsMenu.canDownload`; the
 *   mediaSources half is checked on the detail screen once the action lands.
 * @param includeAddToPlaylist When true, adds [QuickAction.ADD_TO_PLAYLIST] for
 *   video and series/season types (the types a playlist makes sense for).
 * @param includeDelete When true, adds [QuickAction.DELETE] for audio and video
 *   types. Used by the offline hosts where the item is already downloaded and a
 *   destructive "remove download" affordance makes sense; omitted online.
 * @param includeFavorite When true, adds the [QuickAction.FAVORITE]/
 *   [QuickAction.UNFAVORITE] toggle. Gated so the online hosts (which already
 *   expose favorite from the detail screen's action row) keep their current
 *   long-press surface, while the offline hosts opt in to close the parity gap.
 */
fun MediaItem.quickActions(
    scope: MediaQuickActionScope,
    includeDownload: Boolean = false,
    includeAddToPlaylist: Boolean = false,
    includeDelete: Boolean = false,
    includeFavorite: Boolean = false,
): List<QuickAction> {
    if (mediaType !in scope.actionableTypes()) return emptyList()
    return buildList {
        add(QuickAction.PLAY)
        add(if (isPlayed) QuickAction.MARK_UNWATCHED else QuickAction.MARK_WATCHED)
        if (includeFavorite) {
            add(if (isFavorite) QuickAction.UNFAVORITE else QuickAction.FAVORITE)
        }
        if (includeDownload && (mediaType.isAudioType || mediaType.isVideoType)) {
            add(QuickAction.DOWNLOAD)
        }
        if (includeAddToPlaylist &&
            (mediaType.isVideoType || mediaType == MediaType.SERIES || mediaType == MediaType.SEASON)
        ) {
            add(QuickAction.ADD_TO_PLAYLIST)
        }
        if (includeDelete && (mediaType.isVideoType || mediaType == MediaType.SERIES ||
                mediaType == MediaType.SEASON || mediaType.isAudioType)
        ) {
            add(QuickAction.DELETE)
        }
        add(QuickAction.DETAILS)
    }
}
