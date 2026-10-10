package com.raulshma.jellyplay.feature.player.video.engine.mpv

/**
 * Read-side snapshot of the user's custom-mpv-config subtitle ownership —
 * what the UI shows while the engine's write paths yield per
 * [MpvUserSubtitleKeys]. Engines derive it from the SAME two sources
 * (on-disk mpv.conf text, in-app extra-config text) their setters skip
 * owned keys by, so the notice the subtitle-style UI renders can never
 * disagree with the keys the engine actually skips.
 *
 *  · [ownedStyleKeys] — `sub-*` styling keys the user's config sets. Every
 *    matching in-app subtitle control (color chips, position slider, ASS
 *    Respect/Force choice) is a no-op for those keys: the engine never
 *    writes them, the config value wins for the whole session.
 *  · [confKeysDroppedByParser] — owned keys whose mpv.conf value mpv's own
 *    parser destroys (an unquoted `#` starts a comment). Owned AND
 *    valueless: the app correctly stays silent, mpv falls back to its own
 *    default — the one combination that looks like a broken control from
 *    outside, so it carries its own notice line.
 */
data class MpvSubtitleOwnership(
    val ownedStyleKeys: Set<String> = emptySet(),
    val confKeysDroppedByParser: Set<String> = emptySet(),
) {
    /** Any user config participates — gates the UI notice card. */
    val isActive: Boolean get() = ownedStyleKeys.isNotEmpty() || confKeysDroppedByParser.isNotEmpty()

    companion object {
        /** No user config participates — every non-mpv engine's value. */
        val NONE = MpvSubtitleOwnership()
    }
}

/**
 * Per-key mpv.conf ownership for subtitle styling (issue #165).
 *
 * The app unconditionally writes a full `sub-*` slate at init time
 * (`setOptionString` — command-line priority, beats mpv.conf) and re-applies
 * it at runtime (`setPropertyString` — after mpv.conf is parsed). Either write
 * alone clobbers any `sub-*` styling the user put in their `<config-dir>/mpv.conf`
 * or the in-app Advanced MPV Configuration text. This helper derives the set of
 * `sub-*` keys the user **explicitly owns** from those two sources so the
 * engines can skip writing exactly those keys — the user's value (conf or raw
 * line) then wins for the whole session.
 *
 * Scope: **styling keys only**. A functional set the player UI drives at
 * runtime (subtitle visibility toggle, delay sync, the aspect-ratio margin
 * pair) plus the Android font-provider workarounds stay app-owned regardless
 * of user config, so in-app controls keep working. Only top-level `sub-*` keys
 * claim ownership: profile-section keys inside `[profile]` blocks are not
 * attributed (mpv applies them only when the profile activates; the engines
 * never write those keys back, so nothing to yield).
 *
 * Lives in player-contract (same `engine` package, zero import churn) since
 * [MpvSubtitleStyleApplier] became the one apply choreography both mpv
 * engines run — the contract cannot depend on the feature module.
 */
object MpvUserSubtitleKeys {

    /**
     * The mpv key the ASS Respect/Force chips map to. Shared by the UI's
     * ownership check, [MpvStyleMapping]'s style/reset entries, and the
     * engines so the literal cannot drift between them.
     */
    const val ASS_OVERRIDE_KEY = "sub-ass-override"

    /**
     * `sub-*` keys that never become user-owned: the player surface writes
     * them programmatically per session state. Yielding these would break
     * in-app subtitle show/hide, subtitle delay sync, or the aspect-ratio
     * caption handling. `sub-font-provider` / `sub-fonts-dir` encode the
     * Android libass font-provider workaround (an empty-bitmap caption bug
     * when fontconfig initializes) — platform-required, not styling.
     */
    private val APP_OWNED_ALWAYS: Set<String> = setOf(
        "sub-visibility",
        "sub-delay",
        "secondary-sub-delay",
        "sub-use-margins",
        "sub-ass-force-margins",
        "sub-font-provider",
        "sub-fonts-dir",
    )

    /**
     * The user-owned `sub-*` styling keys: the union of the on-disk
     * `mpv.conf` text (Android only — the desktop sets `config=no`) and the
     * in-app `mpvExtraConfig` free-form lines. Either source claims the key.
     * Returns an empty set when neither source sets any `sub-*` key — the
     * common case — so callers can take a zero-cost no-filter fast path.
     */
    fun ownedKeys(mpvConfText: String?, extraConfigText: String?): Set<String> = buildSet {
        mpvConfText?.let { addAll(keysFrom(it)) }
        extraConfigText?.let { addAll(keysFrom(it)) }
    }

    /**
     * Drops the entries whose key the user owns. Applied to every
     * [MpvStyleMapping] pair list (custom AND default/reset branches) before
     * the engines feed their setters, so an owned key is skipped at init
     * (option) and runtime (property) alike.
     */
    fun filterOwned(entries: List<Pair<String, String>>, owned: Set<String>): List<Pair<String, String>> =
        if (owned.isEmpty()) entries else entries.filter { it.first !in owned }

    /**
     * Owned `sub-*` keys whose raw conf value can never survive mpv's own
     * conf parser, so the key ends up "owned" but carrying no value — the
     * player shows mpv's default while the app correctly stays silent.
     *
     * Root cause (issue #165 follow-up): mpv's `parse_configfile.c` cuts an
     * unquoted value at the first `#` — `sub-color=#FF0000` parses as an
     * empty value and is rejected, while `sub-font-size=80` survives. Only
     * meaningful for the on-disk conf text: the in-app extra-config lines
     * reach mpv via the option API, where `#` is not special. Quoted values
     * (`"..."`, `'...'`, `%N%...`) and values that keep a non-empty prefix
     * before the `#` (`80 # note`) parse fine and are not flagged.
     */
    fun unsalvageableConfKeys(mpvConfText: String?): Set<String> {
        mpvConfText ?: return emptySet()
        return buildSet {
            topLevelSubEntries(mpvConfText).forEach { (key, value) ->
                if (value.isNullOrEmpty() || value[0] == '"' || value[0] == '\'' || value[0] == '%') return@forEach
                val hash = value.indexOf('#')
                if (hash >= 0 && value.substring(0, hash).trim().isEmpty()) add(key)
            }
        }
    }

    /**
     * Top-level `sub-*` styling keys of [text], each paired with its raw
     * trimmed value (`null` for a bare `sub-foo` flag line). Parsed per-line
     * rather than via [parseMpvConfigOptions] because ownership must be
     * section-aware: keys under a `[profile]` header are NOT attributed —
     * mpv applies them only when the profile activates, and the engines never
     * write them back, so claiming them would yield the key to nobody (user
     * styling AND app styling both skipped). A section header runs to the
     * next header or EOF, matching mpv.conf semantics. Shared by
     * [ownedKeys] (key set) and [unsalvageableConfKeys] (value inspection)
     * so the two walks cannot drift apart. Conf files saved by some editors
     * carry a UTF-8 BOM; without stripping it, a sub-* key on the first line
     * parses as "\uFEFFsub-…" and claims nothing.
     */
    private fun topLevelSubEntries(text: String): Sequence<Pair<String, String?>> = sequence {
        var inProfileSection = false
        text.removePrefix("\uFEFF").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                if (line.startsWith("[") && line.endsWith("]")) {
                    inProfileSection = true
                    return@forEach
                }
                if (inProfileSection) return@forEach
                val eq = line.indexOf('=')
                val key = if (eq >= 0) line.substring(0, eq).trim() else line
                if (key.startsWith("sub-") && key !in APP_OWNED_ALWAYS) {
                    yield(key to if (eq >= 0) line.substring(eq + 1).trim() else null)
                }
            }
    }

    /**
     * Top-level `sub-*` keys set by [text]: the key half of
     * [topLevelSubEntries].
     */
    private fun keysFrom(text: String): Set<String> = topLevelSubEntries(text).map { it.first }.toSet()
}
