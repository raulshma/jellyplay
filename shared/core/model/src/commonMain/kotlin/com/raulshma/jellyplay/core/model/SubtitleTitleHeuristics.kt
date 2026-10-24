package com.raulshma.jellyplay.core.model

/**
 * What kind of subtitle a track's TITLE says it is — the discrimination
 * source for the rule engine's per-rule [SubtitleContentKindFilter]
 * (issue #23). Servers rarely tag tracks structurally ("this one is the
 * signs/songs track"); the only signal is the title string ("English
 * (Signs & Songs)", "[SDH]", "Director's Commentary"). Pure title
 * heuristics: no player dependency, shared by the player-video matcher and
 * readable from settings/tests.
 */
enum class SubtitleTitleClass {
    /** Full-dialogue subtitle — including SDH/CC accessibility tracks. */
    FULL_SUBTITLE,

    /** Signs/songs track (on-screen text + lyrics only, no dialogue). */
    SIGNS_SONGS,

    /** A commentary track (director/crew talk-over). */
    COMMENTARY,

    /** Forced-narrative track (foreign-language dialogue lines only). */
    FORCED,

    /** No title, or a title carrying none of the markers below. */
    UNKNOWN,
}

/**
 * The title heuristics table for [SubtitleTitleClass]. Pure and
 * side-effect-free. Case-insensitive throughout; word-bounded so "ending"
 * matches but "legendary" and "editor" do not, and "cc" does not fire inside
 * longer words.
 *
 * Precedence when several markers appear in one title (most specific intent
 * wins):
 *
 *  1. **COMMENTARY** — "…(Commentary)" / "Director's Commentary": a
 *     commentary track may also carry SDH or signs markers ("English SDH
 *     Commentary"); the commentary intent is the load-bearing one.
 *  2. **SIGNS_SONGS** — signs / songs / OP / ED / opening / ending / lyrics /
 *     credits markers (the same family [TrackSelectionPreset]-era matching
 *     used — see player-video's `TrackResolutionEngine.SIGNS_TITLE_REGEX`,
 *     whose word list this table mirrors so both classifiers agree).
 *  3. **FORCED** — "forced" (a signs-shaped track; the class is kept
 *     distinct so rules can still tell a bare "English Forced" from an
 *     explicit "Signs & Songs").
 *  4. **FULL_SUBTITLE** — accessibility / full markers: "sdh", "cc",
 *     "hearing impaired", "dialogue", "subtitles".
 *  5. **UNKNOWN** — everything else (including null/blank titles).
 */
object SubtitleTitleHeuristics {

    private val COMMENTARY_REGEX = Regex("""(?i)\bcommentary\b""")
    private val SIGNS_REGEX = Regex("""(?i)\b(signs?|songs? ?tracks?|op|ed|opening|ending|lyrics?|credits?)\b""")
    private val FORCED_REGEX = Regex("""(?i)\bforced\b""")
    private val FULL_REGEX = Regex("""(?i)\b(sdh|cc|hearing ?impaired|dialogue|subtitles?)\b""")

    /**
     * Classifies [title]. Null/blank → [SubtitleTitleClass.UNKNOWN]. A title
     * may carry several markers; the precedence ladder in the class KDoc
     * decides.
     */
    fun classifySubtitleTitle(title: String?): SubtitleTitleClass {
        val t = title?.trim().orEmpty()
        if (t.isEmpty()) return SubtitleTitleClass.UNKNOWN
        return when {
            COMMENTARY_REGEX.containsMatchIn(t) -> SubtitleTitleClass.COMMENTARY
            SIGNS_REGEX.containsMatchIn(t) -> SubtitleTitleClass.SIGNS_SONGS
            FORCED_REGEX.containsMatchIn(t) -> SubtitleTitleClass.FORCED
            FULL_REGEX.containsMatchIn(t) -> SubtitleTitleClass.FULL_SUBTITLE
            else -> SubtitleTitleClass.UNKNOWN
        }
    }
}
