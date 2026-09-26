package com.raulshma.jellyplay.core.model.subtitle

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM (android/jvm) actuals for the [SubtitleLanguageCodes] resolvers:
 * everything derives from the runtime locale set (`Locale.getAvailableLocales()`
 * — the JDK table on desktop, ICU on Android), preserving the historical
 * behavior byte-for-byte. `forLanguageTag` is used only for 2-letter/BCP-47
 * prefixes; see the common declaration for why 3-letter codes never go near it.
 */

/**
 * Memo for the short-code branch of [platformShortCodeToIso3]: caches
 * JDK-resolved ISO 639-3 values only. Unresolved inputs (null results, e.g.
 * blank or undetermined tags) re-run the original forLanguageTag/isO3Language
 * path each time, so fallback behavior is unchanged.
 */
private val shortCodeToIso3: MutableMap<String, String> = ConcurrentHashMap()

/**
 * Lookup tables built once from the JDK locale set. Maps ISO 639-3 → 639-1
 * and ISO 639-3 → English display name. Built lazily on first use; the first
 * locale wins to keep the mapping deterministic.
 */
private val iso3ToIso1: Map<String, String> by lazy {
    val map = mutableMapOf<String, String>()
    for (locale in Locale.getAvailableLocales()) {
        val iso3 = try { locale.isO3Language } catch (_: Exception) { continue }
        if (iso3.isBlank() || locale.language.isBlank()) continue
        if (iso3 !in map) map[iso3] = locale.language
    }
    map
}

private val iso3ToDisplay: Map<String, String> by lazy {
    val map = mutableMapOf<String, String>()
    for (locale in Locale.getAvailableLocales()) {
        val iso3 = try { locale.isO3Language } catch (_: Exception) { continue }
        if (iso3.isBlank()) continue
        val name = locale.getDisplayLanguage(Locale.ENGLISH)
        if (name.isNotBlank() && iso3 !in map) map[iso3] = name
    }
    map
}

internal actual fun platformShortCodeToIso3(cleaned: String): String? {
    shortCodeToIso3[cleaned]?.let { return it }
    val resolved = try {
        Locale.forLanguageTag(cleaned)
            .takeIf { it.language.isNotBlank() && it.language != "und" }
            ?.isO3Language?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        cleaned.lowercase().ifBlank { null }
    }
    if (resolved != null) shortCodeToIso3[cleaned] = resolved
    return resolved
}

internal actual fun platformShortCodeToIso1(code: String): String? = try {
    Locale.forLanguageTag(code.trim().replace('_', '-').substringBefore('-'))
        .takeIf { it.language.isNotBlank() && it.language != "und" }?.language
} catch (_: Exception) {
    null
}

internal actual fun platformIso3ToIso1(iso3: String): String? = iso3ToIso1[iso3]

internal actual fun platformIso3DisplayName(iso3: String): String? = iso3ToDisplay[iso3]
