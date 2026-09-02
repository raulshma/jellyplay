package com.raulshma.jellyplay.feature.player.video.engine

import java.util.Locale

/**
 * Verbatim the pre-W.3 TrackLabelFormatter body: Locale.forLanguageTag is
 * lenient (never throws on two/three-letter codes — it parses what it can and
 * leaves the rest undefined, yielding a blank display name, which the common
 * caller turns into the raw-tag fallback). The try/catch stays defensive.
 */
internal actual fun platformLanguageDisplayName(bcp47Tag: String): String? = try {
    Locale.forLanguageTag(bcp47Tag).displayLanguage
} catch (_: Exception) {
    null
}
