package com.raulshma.jellyplay.core.model.subtitle

/**
 * Language-code normalization for the subtitle-provider pipeline.
 *
 * Internal representation is **ISO 639-3** (3-letter, e.g. `eng`) — the form
 * Jellyfin's cultures and the app's existing [com.raulshma.jellyplay.core.model.RemoteSubtitleInfo]
 * use. Each external provider speaks a different dialect:
 *
 * - **Wyzie**: ISO 639-1 (2-letter, e.g. `en`) — comma-separated list.
 * - **OpenSubtitles**: ISO 639-1 (2-letter) for the `/subtitles` `languages`
 *   filter (verified against the live API: `eng` returns 0 results, `en`
 *   returns matches). Region variants use BCP-47-style tags the API echoes
 *   in `subtitles_counts` (e.g. `pt-BR`, `pt-PT`).
 *
 * The 2-letter↔3-letter resolution is platform data (the JDK/ICU locale set
 * on android/jvm; a table generated from the same JDK source on wasmJs), so
 * the resolvers are [expect][platformShortCodeToIso3] declarations and this
 * object lives in commonMain — the core:ui subtitle metadata row needs
 * [toIso1] on every target, including wasmJs.
 *
 * 639-1 ↔ 639-3 conversion on the JVM goes via lookup tables built from
 * `Locale.getAvailableLocales()` (which exposes both `.language` = 639-1 and
 * `.isO3Language` = 639-3/terminologic). Avoids `Locale.forLanguageTag` for
 * 3-letter inputs, which only accepts BCP-47 (2-letter or reserved 3-letter)
 * subtags and silently mangles ordinary ISO 639-3 codes.
 */
object SubtitleLanguageCodes {

    /**
     * ISO 639-3 (terminologic/T) → ISO 639-2B (bibliographic/B) overrides where
     * the two differ. Codes absent here are identical in both standards and pass
     * through unchanged. See https://en.wikipedia.org/wiki/List_of_ISO_639-2_codes.
     */
    private val ISO3_TO_2B: Map<String, String> = mapOf(
        "bod" to "tib", "deu" to "ger", "ell" to "gre", "fas" to "per",
        "fra" to "fre", "hye" to "arm", "isl" to "ice", "mkd" to "mac",
        "mri" to "mao", "msa" to "may", "mya" to "bur", "nld" to "dut",
        "ron" to "rum", "slk" to "slo", "sqi" to "alb", "hbs" to "scr",
        "cym" to "wel", "zho" to "chi", "ces" to "cze", "kat" to "geo",
        "eus" to "baq", "srp" to "scc",
    )

    /** Reversed: ISO 639-2B (B) → ISO 639-3 (T) for the differing codes. */
    private val ISO2B_TO_3: Map<String, String> = ISO3_TO_2B.entries.associate { (a, b) -> b to a }

    /**
     * Memo for the short-code branch of [toIso3] lives in the nonWeb actual
     * (a ConcurrentHashMap — see SubtitleLanguageCodes.jvm.kt); the wasmJs
     * actual resolves from a static map and needs no memo.
     */

    /** Converts an arbitrary language code (1/2/3-letter or BCP-47) to ISO 639-3. */
    fun toIso3(code: String?): String? {
        if (code.isNullOrBlank()) return null
        val cleaned = code.trim().replace('_', '-').substringBefore('-')
        // 2-letter (639-1) or longer BCP-47 prefix → resolve via the platform.
        if (cleaned.length <= 2) {
            return platformShortCodeToIso3(cleaned)
        }
        // 3-letter: could be 639-2B (B) or 639-3 (T). Normalize B→T, else passthrough.
        val lower = cleaned.lowercase()
        return ISO2B_TO_3[lower] ?: lower.ifBlank { null }
    }

    /** Converts an arbitrary language code to ISO 639-1 (2-letter). Null if unmappable. */
    fun toIso1(code: String?): String? {
        val iso3 = toIso3(code) ?: return null
        // Prefer the platform table; fall back to a direct 2-letter parse of
        // the raw input (the platform table may miss codes a BCP-47 parse of
        // the original tag would still yield).
        platformIso3ToIso1(iso3)?.let { return it }
        return platformShortCodeToIso1(code ?: return null)
    }

    /** Converts an arbitrary language code to ISO 639-2B (OpenSubtitles). */
    fun toIso2B(code: String?): String? {
        val iso3 = toIso3(code) ?: return null
        return ISO3_TO_2B[iso3] ?: iso3
    }

    /**
     * Joins [codes] (any dialect) into a comma-separated list in the target
     * dialect, dropping any that fail to convert. Returns "" for empty input so
     * callers can append `&language=` unconditionally without trailing junk.
     */
    fun join(codes: List<String>, convert: (String) -> String?): String =
        codes.mapNotNull(convert).filter { it.isNotBlank() }.joinToString(",")

    /** Human-readable display name for a code, e.g. `eng` → "English". */
    fun displayName(code: String?): String? {
        if (code.isNullOrBlank()) return null
        val iso3 = toIso3(code) ?: return code
        return platformIso3DisplayName(iso3) ?: iso3
    }
}

/**
 * Resolves a 2-letter (639-1) or BCP-47 prefix to ISO 639-3. Null when the
 * platform cannot map it. The nonWeb actual memoizes resolved values.
 */
internal expect fun platformShortCodeToIso3(cleaned: String): String?

/**
 * Second-chance ISO 639-1 resolution for [SubtitleLanguageCodes.toIso1] when
 * the platform's 639-3 → 639-1 table missed: parses the raw user input as a
 * short/BCP-47 tag.
 */
internal expect fun platformShortCodeToIso1(code: String): String?

/** Platform table lookup: ISO 639-3 → ISO 639-1. */
internal expect fun platformIso3ToIso1(iso3: String): String?

/** Platform table lookup: ISO 639-3 → English display name. */
internal expect fun platformIso3DisplayName(iso3: String): String?
