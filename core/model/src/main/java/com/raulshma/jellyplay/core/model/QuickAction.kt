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
 */
@Immutable
enum class QuickAction {
    PLAY,
    MARK_WATCHED,
    MARK_UNWATCHED,
    DOWNLOAD,
    ADD_TO_PLAYLIST,
    DETAILS,
}
