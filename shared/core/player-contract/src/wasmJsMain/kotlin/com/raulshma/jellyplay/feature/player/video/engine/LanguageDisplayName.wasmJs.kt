package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Web actual over `Intl.DisplayNames` (type:'language'). The Kotlin stdlib
 * ships no BCP-47 display-name resolver, so this goes through JS interop:
 * track labels show real names like "English" instead of raw codes ("eng"),
 * localized to the page language.
 *
 * Behavior contract (mirrors the android/jvm actuals):
 *  - ANY failure returns null (never throws): unsupported engines
 *    (Safari < 14.1-era lacking Intl.DisplayNames), missing navigator,
 *    malformed/unknown tags — callers fall back to the raw tag unchanged,
 *    which is exactly the pre-Phase-W web posture, just narrower now.
 *  - `fallback: 'none'` semantics verified against MDN/V8 behavior: known
 *    tags resolve to a name; unknown-but-valid tags return undefined from
 *    `of()` (→ null); genuinely malformed tags throw RangeError (→ null).
 *    'none' beats the 'code' default because 'code' would echo junk back as
 *    if it were a resolved display name instead of degrading to passthrough.
 *  - Instances are cached per UI locale ([navigatorLanguageOrNull], defaulting
 *    to "en"); construction is the only expensive part (ICU resolver setup).
 *    Plain HashMap cache: the JS/wasm event loop is single-threaded today, and
 *    Kotlin/Wasm has no data-race-capable threading in browsers, so there is
 *    nothing to synchronize.
 */
internal actual fun platformLanguageDisplayName(bcp47Tag: String): String? = try {
    if (bcp47Tag.isEmpty() || !displayNamesSupported()) {
        null
    } else {
        val localeTag = navigatorLanguageOrNull() ?: "en"
        // getOrPut re-probes on cached nulls (degraded engines), which is cheap.
        val names = displayNamesCache.getOrPut(localeTag) { newLanguageDisplayNames(localeTag) }
        names?.let { displayNameOf(it, bcp47Tag) }
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
