package com.raulshma.jellyplay.feature.player.video.engine

import java.util.Locale

/**
 * Identical to the androidMain actual (same java.util.Locale API on desktop);
 * duplicated rather than a shared jvmShared source set because it is the only
 * android+jvm-verbatim code in this module.
 */
internal actual fun platformLanguageDisplayName(bcp47Tag: String): String? = try {
    Locale.forLanguageTag(bcp47Tag).displayLanguage
} catch (_: Exception) {
    null
}
