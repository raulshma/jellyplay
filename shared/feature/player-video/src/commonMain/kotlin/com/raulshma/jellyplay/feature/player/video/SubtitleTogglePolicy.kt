package com.raulshma.jellyplay.feature.player.video

/**
 * Pure decision table for the subtitle TOGGLE: the mpv-style
 * "subtitles visibility" flip whose OFF arm remembers the last non-Off
 * selection and whose ON arm silently restores exactly that track. Distinct
 * from the per-item/per-series default pins — this latch lives in
 * [TrackSelectionHelper] beside the other track-selection state and is pure
 * session memory (never persisted).
 *
 * Extracted from [TrackSelectionHelper.toggleSubtitles] so the branch shape
 * (on→off+remember, off→restore-or-nothing, restore resolution ladder) is
 * JVM-testable without an engine, per the [TrackSelectionPolicy] precedent.
 */
internal object SubtitleTogglePolicy {

    /**
     * True when a REAL (engine-track) subtitle is currently selected — i.e.
     * the toggle's next press means "turn off". The Off/None placeholder
     * (index < 0) and "no selection flag" both count as off.
     */
    fun isOn(selected: TrackOption?): Boolean = selected != null && selected.index >= 0

    /** The currently selected picker row, or null when none is flagged. */
    fun selectedOption(tracks: List<TrackOption>): TrackOption? = tracks.firstOrNull { it.isSelected }

    /**
     * The Off row to select when turning subtitles off: the list's own
     * placeholder when one exists (preserving its label — "Off" populated /
     * "None" empty), else a synthetic -1 row so the engine deselect still
     * runs when the picker list is momentarily empty.
     */
    fun offOption(tracks: List<TrackOption>): TrackOption =
        tracks.firstOrNull { it.index < 0 } ?: TrackOption(-1, "Off", null, true)

    /**
     * Resolves the remembered track back into the CURRENT picker list, or
     * null when the restore must no-op (nothing remembered, or the
     * remembered track no longer resolves — item switch, re-enumeration).
     *
     * Identity is SEMANTIC, never bare engine-index: a re-published or
     * item-switched list routinely puts a different track at the remembered
     * position, and "exactly that track" is the toggle's contract. Match
     * order: label+language (the same identity the stored-selection
     * revalidation uses) → container stream index (blank-label tracks) →
     * give up. Only real tracks (index >= 0) ever restore.
     */
    fun resolveRestore(remembered: TrackOption?, tracks: List<TrackOption>): TrackOption? {
        if (remembered == null || remembered.index < 0) return null
        val candidates = tracks.filter { it.index >= 0 }
        if (remembered.label.isNotBlank()) {
            return candidates.firstOrNull {
                it.label == remembered.label &&
                    (remembered.language == null || it.language == remembered.language)
            }
        }
        val streamIndex = remembered.streamIndex ?: return null
        return candidates.firstOrNull { it.streamIndex == streamIndex }
    }
}
