package com.raulshma.jellyplay.tile

/**
 * Pure three-state fold for the QS media tile (extracted verbatim from
 * JellyPlayTileService's `updateTile` so the truth table is JVM-testable).
 * The service keeps only the Android Tile plumbing: it maps the returned
 * [Presentation.state] onto `Tile.STATE_*` and resolves the [Presentation.label]
 * / [Presentation.description] selectors against string resources.
 *
 * The tile reflects three states — playing, paused (a track is loaded but not
 * playing), and inactive (no session). QS tiles are binary
 * (ACTIVE/INACTIVE), so the paused state is surfaced via the label and
 * content description rather than the tile icon:
 *
 *  - state: playing → [TileState.ACTIVE], otherwise [TileState.INACTIVE];
 *  - label: paused shows the paused template around the track title; a
 *    session in any other state shows the track title; no session shows the
 *    app name. Empty titles fall back to the app name ([Label.TrackTitle.title]
 *    / [Label.PausedTemplate.title] arrive pre-fallen-back as `null` — the
 *    service resolves its app-name string lazily, exactly as the inline
 *    `title.ifEmpty { getString(...) }` did);
 *  - content description: playing → playing; a session (paused) → paused; no
 *    session → app name. Note the first term consults [TilePlaybackState.policy]'s
 *    `isPlaying` regardless of session, matching the original fold.
 */
internal object TilePlaybackState {

    /** The two tile states QS offers — the service maps onto `Tile.STATE_*`. */
    enum class TileState { ACTIVE, INACTIVE }

    /** Label selector — the service resolves each variant to a string. */
    sealed interface Label {
        /**
         * Paused template around the track title; `null` title → the service
         * substitutes the app name.
         */
        data class PausedTemplate(val title: String?) : Label

        /** The track title alone; `null` title → the service substitutes the app name. */
        data class TrackTitle(val title: String?) : Label

        /** Just the app name. */
        data object AppName : Label
    }

    /** Content-description selector — playing / paused / app name. */
    enum class Description { PLAYING, PAUSED, APP_NAME }

    /** What the service renders for one `updateTile` pass. */
    data class Presentation(
        val state: TileState,
        val label: Label,
        val description: Description,
    )

    /**
     * Folds one (isPlaying, hasSession, title) observation into the tile
     * presentation. Pure — no Android types, no resource lookups.
     */
    fun policy(isPlaying: Boolean, hasSession: Boolean, title: String): Presentation {
        val trackTitle = title.ifEmpty { null }
        return Presentation(
            state = if (isPlaying) TileState.ACTIVE else TileState.INACTIVE,
            label = when {
                // Paused: a track is loaded but not playing.
                hasSession && !isPlaying -> Label.PausedTemplate(trackTitle)
                // Playing: show the track title.
                hasSession -> Label.TrackTitle(trackTitle)
                // No session: just the app name.
                else -> Label.AppName
            },
            description = when {
                isPlaying -> Description.PLAYING
                hasSession -> Description.PAUSED
                else -> Description.APP_NAME
            },
        )
    }
}
