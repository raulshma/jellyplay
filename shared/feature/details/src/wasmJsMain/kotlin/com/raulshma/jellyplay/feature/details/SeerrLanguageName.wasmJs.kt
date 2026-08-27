package com.raulshma.jellyplay.feature.details

/**
 * Web actual of [languageDisplayName] over `Intl.DisplayNames`
 * (type:'language') — same approach as core:player-contract's
 * LanguageDisplayName.wasmJs.kt (wave 12D), replicated module-locally because
 * that expect is `internal` to its module.
 *
 * Behavior contract: ANY failure returns null (never throws) — unsupported
 * engines (no Intl.DisplayNames), missing navigator, malformed/unknown tags
 * (RangeError from malformed input, `undefined` from `of()` under
 * fallback:'none' for unknown-but-valid tags). The SeerrDetailScreen call
 * site falls back to the raw tag, which is exactly what java's
 * displayLanguage produced for unresolvable tags. Instances are cached per UI
 * locale ([navigatorLanguageOrNull], defaulting to "en"); plain HashMap is
 * fine — the wasm event loop is single-threaded in browsers.
 */
internal actual fun languageDisplayName(languageTag: String): String? = try {
    if (languageTag.isEmpty() || !displayNamesSupported()) {
        null
    } else {
        val localeTag = navigatorLanguageOrNull() ?: "en"
        val names = displayNamesCache.getOrPut(localeTag) { newLanguageDisplayNames(localeTag) }
        names?.let { displayNameOf(it, languageTag) }
    }
} catch (_: Throwable) {
    // Malformed tags surface here as RangeError (a JS Throwable on wasm).
    null
}

private val displayNamesCache = HashMap<String, JsAny?>()

/** Engine probe: Intl.DisplayNames is baseline since ~2022 but not universal. */
private fun displayNamesSupported(): Boolean =
    js("typeof Intl !== 'undefined' && typeof Intl.DisplayNames === 'function'")

/** Page locale ("en-US"); absent off-window (workers/others) → null. */
private fun navigatorLanguageOrNull(): String? =
    js("(typeof navigator !== 'undefined' && typeof navigator.language === 'string') ? navigator.language : null")

private fun newLanguageDisplayNames(localeTag: String): JsAny? =
    js("new Intl.DisplayNames([localeTag], { type: 'language', fallback: 'none' })")

/**
 * Returns the resolved name, or null when the engine answered undefined
 * (unknown-but-well-formed tag under fallback:'none').
 */
private fun displayNameOf(displayNames: JsAny, tag: String): String? =
    js("displayNames.of(tag)")
