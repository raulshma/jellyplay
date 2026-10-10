package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * Identity of a quick action surfaced from a media card long-press (phones) or
 * the TV Menu button. Pure enum with no presentation coupling so predicate logic
 * (which actions apply to a [MediaItem]) can live here alongside the model.
 *
 * User-facing label + icon presentation lives in `core:ui` — see
 * [com.raulshma.jellyplay.core.ui.components.QuickAction.labelRes] and
 * [com.raulshma.jellyplay.core.ui.components.QuickAction.icon].
 *
 * `FAVORITE`/`UNFAVORITE` and `MARK_WATCHED`/`MARK_UNWATCHED` are emitted as a
 * toggled pair (the active state is the one shown) so the sheet reads as a
 * state toggle rather than a redundant action.
 */
@Immutable
enum class QuickAction {
    PLAY,
    MARK_WATCHED,
    MARK_UNWATCHED,
    FAVORITE,
    UNFAVORITE,
    DOWNLOAD,
    ADD_TO_PLAYLIST,
    /**
     * Removes the local download for an item (artifacts + offline rows).
     * Named for what it actually does — no host deletes from the server via
     * this surface — and labeled "Delete download" in the sheet.
     */
    REMOVE_DOWNLOAD,
    /**
     * Hides the item from the client-side resume rows (Continue Watching /
     * Next Up / Continue Reading — one overlay set covers them all). A
     * client-side overlay only: native Jellyfin continue-watching data stays
     * untouched; restore lives in the settings Home-display row and the
     * detail screen's ⋮ menu, and the set roams via the `cw` sync namespace.
     */
    HIDE_FROM_CONTINUE_WATCHING,
    /** The un-hide counterpart of [HIDE_FROM_CONTINUE_WATCHING]. */
    SHOW_FROM_CONTINUE_WATCHING,
    DETAILS,
}
