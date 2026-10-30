package com.raulshma.jellyplay.core.i18ntools

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guard against locale drift in Compose Multiplatform string resources:
 * every module ships `composeResources/values/strings.xml` (the default
 * locale) beside `values-<lang>/strings.xml` translations, and a key added
 * to the default file without its locale entries silently falls back to
 * English for that locale's users — no build error, no lint gate. Counted
 * keys are the `<string name="…">` AND `<plurals name="…">` elements (a
 * plurals key counts once, whatever its quantity items); this test walks
 * every module's resource tree and asserts each locale's MISSING-key
 * count never rises above the per-module+locale baseline in
 * [baselineMissingKeys]. Modules may split their strings across several
 * `strings_*.xml` family files per qualifier dir (CMP merges them) —
 * the scan reads every `*.xml` in the dir, so a split cannot hide a gap.
 *
 * This is a drift guard, not full parity enforcement of the raw key sets: the
 * counted set already excludes the conventions below, so the goal is that
 * the numbers only ever go down. When a module reaches 0 for every locale,
 * full parity has arrived there.
 *
 * Counted-set exemptions (a key in these classes is not a missing
 * translation — [CountedKeyGrammar.isExempt] is the one home of the rule):
 *  - `translatable="false"` keys: brand names, URL/email placeholders and
 *    acronyms that the default file itself marks as deliberately
 *    untranslated (settings_radarr, auth_app_name, subtitle_tester_mode_hdr,
 *    …). The Android resource convention is that these carry no locale
 *    entries; requiring one would be wrong, so they never count.
 *  - keys starting with `diff_`: the recorded convention for intentionally
 *    default-locale-only strings — the prefix is prophylactic: a new `diff_`
 *    key is exempt by convention, anything else must translate or baseline.
 *  - the grammar's proper-noun list: product/service proper nouns that must
 *    not be translated. Belt-and-braces for the day one of them loses its
 *    `translatable="false"` marker.
 *  - PENDING-marked entries: keys [StringsPropagator] inserted (verbatim
 *    untranslated copies under a `<!-- PENDING TRANSLATION: … -->` heading)
 *    still count MISSING — [CountedKeyGrammar.translatedKeys] is the read
 *    here — so a repo-wide propagation cannot fake parity; the numbers fall
 *    only when real translations land.
 *
 * Module discovery is [I18nResourceDiscovery] — the one walk this test and
 * the root build's repo-wide propagateStrings/checkStrings tasks share:
 * repo root located by walking up to `settings.gradle.kts`, then every
 * `composeResources/values/strings.xml` under `shared/` and `apps/` (build
 * output excluded) is a guarded module — a new module with resources is
 * guarded automatically, no hand list to forget.
 *
 * To accept a (temporary) gap: add an entry to [baselineMissingKeys] keyed
 * `"relative/module/path:locale-tag"` with the current missing count. To
 * LOWER a baseline: translate the keys, delete
 * or shrink the entry. Never raise a number except for an accepted gap.
 */
class I18nParityRatchetTest {

    /**
     * Allowed MISSING-key count per module+locale. Keys are
     * `"module/dir:locale"`, e.g. `"shared/feature/settings:de"`. Absent key
     * means 0 — no missing keys allowed.
     *
     * Zero-tolerance: the core/ui PreferenceEnumNames 15-key translation
     * follow-up landed 2026-10-05, so the core/ui entries below read 0; the
     * map is kept as documentation of the accepted-gap mechanism. A new
     * untranslated key must be translated (preferred) or added here as a
     * deliberate baseline entry — never raise an existing number, and keep
     * the entry's KDoc pointed at the tracking issue.
     *
     * Accepted gaps recorded 2026-10-12 (multi-feature branch landed feature
     * strings ahead of their translation pass; the numbers must never rise):
     *  - `shared/feature/details` 13 keys × 8 locales — the home-detail
     *    UI strings; translations
     *    tracked as a details-module follow-up.
     *  - `shared/feature/home` 6 keys × 8 locales — home section/hero strings
     *    from the same branch; translations tracked as a home-module follow-up.
     *  - `shared/feature/settings` 98 keys × 8 locales — large settings
     *    surface landed untranslated; translations tracked as a
     *    settings-module follow-up.
     */
    private val baselineMissingKeys: Map<String, Int> = mapOf(
        "shared/core/ui:de" to 0,
        "shared/core/ui:es" to 0,
        "shared/core/ui:fr" to 0,
        "shared/core/ui:it" to 0,
        "shared/core/ui:ja" to 0,
        "shared/core/ui:ko" to 0,
        "shared/core/ui:pt" to 0,
        "shared/core/ui:zh" to 0,
        "shared/feature/details:de" to 13,
        "shared/feature/details:es" to 13,
        "shared/feature/details:fr" to 13,
        "shared/feature/details:it" to 13,
        "shared/feature/details:ja" to 13,
        "shared/feature/details:ko" to 13,
        "shared/feature/details:pt" to 13,
        "shared/feature/details:zh" to 13,
        "shared/feature/home:de" to 6,
        "shared/feature/home:es" to 6,
        "shared/feature/home:fr" to 6,
        "shared/feature/home:it" to 6,
        "shared/feature/home:ja" to 6,
        "shared/feature/home:ko" to 6,
        "shared/feature/home:pt" to 6,
        "shared/feature/home:zh" to 6,
        // 98-baseline gap translated 2026-10-26 (259 settings keys x8 locales
        // landed with it) — entries deleted per the lower-a-baseline rule.
    )

    @Test
    fun `locale missing-key counts never rise above baseline`() {
        val root = I18nResourceDiscovery.repoRoot(File(System.getProperty("user.dir")))
        val resourceRoots = I18nResourceDiscovery.discoverResourceRoots(root)
        assertTrue(
            resourceRoots.size >= 20,
            "i18n ratchet discovered only ${resourceRoots.size} resource roots under shared/ and apps/ — " +
                "discovery is broken or the tree moved; fix discovery, do not lower this bound to fit",
        )

        val failures = mutableListOf<String>()
        for (resourceRoot in resourceRoots.sortedBy { it.path }) {
            // .../<module>/src/commonMain/composeResources → <module> (the
            // dir carrying build.gradle.kts), 3 levels up from the root.
            val module = resourceRoot.parentFile!!.parentFile!!.parentFile!!
            val modulePath = module.relativeTo(root).path.replace('\\', '/')
            val counted = CountedKeyGrammar.countedKeys(resourceRoot.resolve("values"))
            val locales = resourceRoot
                .listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
                .orEmpty()
                .filter { it.resolve("strings.xml").isFile }
                .sortedBy { it.name }
            for (localeDir in locales) {
                val missing = counted - CountedKeyGrammar.translatedKeys(localeDir)
                val key = "$modulePath:${localeDir.name.removePrefix("values-")}"
                val allowed = baselineMissingKeys.getOrDefault(key, 0)
                if (missing.size > allowed) {
                    failures += "$key: ${missing.size} missing (baseline $allowed): " +
                        missing.sorted().joinToString(", ")
                }
            }
        }
        assertTrue(
            failures.isEmpty(),
            "i18n parity ratchet tripped — new untranslated keys (translate them, or add a " +
                "deliberate baseline entry with KDoc; never raise an existing one):\n" +
                failures.joinToString("\n"),
        )
    }
}
