package com.raulshma.jellyplay.core.model

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Robustly matches language codes by normalizing them using java.util.Locale.
 * Supports ISO-639-1 (2-letter), ISO-639-2 (3-letter), and region/bcp47 tags (e.g. en-US).
 */
fun isLanguageMatch(trackLang: String?, prefLang: String?): Boolean {
    if (trackLang == null || prefLang == null) return false
    if (trackLang.equals(prefLang, ignoreCase = true)) return true
    return try {
        val trackClean = trackLang.split('-')[0].split('_')[0].trim()
        val prefClean = prefLang.split('-')[0].split('_')[0].trim()
        val trackISO3 = resolveIso3(trackClean)
        val prefISO3 = resolveIso3(prefClean)
        trackISO3.equals(prefISO3, ignoreCase = true)
    } catch (_: Exception) {
        trackLang.startsWith(prefLang, ignoreCase = true) || prefLang.startsWith(trackLang, ignoreCase = true)
    }
}

/**
 * Memoised ISO-639 resolution for [isLanguageMatch]: the cleaned tag is the
 * only input and the result (JDK-resolved, or the tag itself when the JDK
 * cannot resolve it) is pure, so cached values equal a fresh
 * forLanguageTag/isO3Language run for every input.
 */
private val iso3Cache = ConcurrentHashMap<String, String>()

private fun resolveIso3(clean: String): String =
    iso3Cache.getOrPut(clean) {
        try { Locale.forLanguageTag(clean).isO3Language } catch (_: Exception) { clean }
    }

// Compiled once and reused (was recompiled inside parseLanguageFromLabel on
// every media-open / track-switch call). Mirrors the cached-regex pattern used
// in MediaRepositoryImpl and DownloadRepositoryImpl.
private val NON_ALPHANUMERIC_SPLIT = Regex("[^a-zA-Z0-9]+")

private val localeLookupMap: Map<String, String> by lazy {
    val map = mutableMapOf<String, String>()
    for (locale in Locale.getAvailableLocales()) {
        val iso3 = try { locale.isO3Language } catch (_: Exception) { continue }
        if (iso3.isBlank()) continue
        
        val displayName = locale.getDisplayLanguage(Locale.ENGLISH).lowercase()
        val localDisplayName = locale.getDisplayLanguage(locale).lowercase()
        if (displayName.isNotBlank()) {
            map[displayName] = iso3
        }
        if (localDisplayName.isNotBlank()) {
            map[localDisplayName] = iso3
        }
        
        val langCode = locale.language.lowercase()
        if (langCode.isNotEmpty()) {
            map[langCode] = iso3
        }
        if (iso3.isNotEmpty()) {
            map[iso3.lowercase()] = iso3
        }
    }
    // Add common fallback language mappings in case they are not in the locale list
    val fallbacks = mapOf(
        "english" to "eng",
        "spanish" to "spa",
        "french" to "fra",
        "german" to "deu",
        "italian" to "ita",
        "portuguese" to "por",
        "russian" to "rus",
        "chinese" to "zho",
        "japanese" to "jpn",
        "korean" to "kor"
    )
    for ((key, value) in fallbacks) {
        if (!map.containsKey(key)) {
            map[key] = value
        }
    }
    map
}

/**
 * Parses language codes or display names from a track label string and returns
 * the corresponding 3-letter ISO-639-2 language code, or null if no match is found.
 */
fun parseLanguageFromLabel(label: String?): String? {
    if (label.isNullOrBlank()) return null
    val normalizedLabel = label.lowercase()
    
    // Split by non-alphanumeric characters to isolate words
    val words = normalizedLabel.split(NON_ALPHANUMERIC_SPLIT)
    for (word in words) {
        if (word.isNotBlank()) {
            val matchedIso = localeLookupMap[word]
            if (matchedIso != null) {
                return matchedIso
            }
        }
    }
    
    // Fall back to scanning the whole string for display name matches (longer names first)
    for ((name, iso) in longNameEntries) {
        if (normalizedLabel.contains(name)) {
            return iso
        }
    }

    return null
}

/**
 * [localeLookupMap] entries whose key is longer than 3 chars, in map order —
 * the only ones the substring fallback scan above can match.
 */
private val longNameEntries: List<Pair<String, String>> by lazy {
    localeLookupMap.mapNotNull { (name, iso) -> if (name.length > 3) name to iso else null }
}

