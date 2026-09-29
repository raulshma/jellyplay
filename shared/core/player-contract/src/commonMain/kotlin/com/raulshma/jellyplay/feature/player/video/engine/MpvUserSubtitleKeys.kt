package com.raulshma.jellyplay.feature.player.video.engine

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
     * Top-level `sub-*` keys set by [text]. Parsed per-line rather than via
     * [parseMpvConfigOptions] because ownership must be section-aware: keys
     * under a `[profile]` header are NOT attributed — mpv applies them only
     * when the profile activates, and the engines never write them back, so
     * claiming them would yield the key to nobody (user styling AND app
     * styling both skipped). A section header runs to the next header or EOF,
     * matching mpv.conf semantics.
     */
    private fun keysFrom(text: String): Set<String> {
        var inProfileSection = false
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                if (line.startsWith("[") && line.endsWith("]")) {
                    inProfileSection = true
                    return@mapNotNull null
                }
                if (inProfileSection) return@mapNotNull null
                val eq = line.indexOf('=')
                val key = if (eq >= 0) line.substring(0, eq).trim() else line
                key.takeIf { it.isNotEmpty() && it.startsWith("sub-") && it !in APP_OWNED_ALWAYS }
            }
            .toSet()
    }
}
