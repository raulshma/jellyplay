package com.raulshma.jellyplay.core.concurrency

import kotlin.test.Test
import kotlin.test.assertTrue
import java.io.File

/**
 * Ratchet against locale drift in Compose Multiplatform string resources:
 * every module ships `composeResources/values/strings.xml` (the default
 * locale) beside `values-<lang>/strings.xml` translations, and a key added
 * to the default file without its locale entries silently falls back to
 * English for that locale's users — no build error, no lint gate. Counted
 * keys are the `<string name="…">` AND `<plurals name="…">` elements (a
 * plurals key counts once, whatever its quantity items); this test walks
 * every module's resource tree and asserts each locale's MISSING-key
 * count never rises above the per-module+locale baseline in
 * [baselineMissingKeys]. Modules may split their strings across several
 * `strings_*.xml` family files per qualifier dir (CMP merges them) — the
 * ratchet reads every `*.xml` in the dir, so a split cannot hide a gap.
 *
 * This is a RATCHET, not full parity enforcement of the raw key sets: the
 * counted set already excludes the conventions below, so the goal is that
 * the numbers only ever go down. When a module reaches 0 for every locale,
 * full parity has arrived there.
 *
 * Counted-set exemptions (a key in these classes is not a missing
 * translation):
 *  - `translatable="false"` keys: brand names, URL/email placeholders and
 *    acronyms that the default file itself marks as deliberately
 *    untranslated (settings_radarr, auth_app_name, subtitle_tester_mode_hdr,
 *    …). The Android resource convention is that these carry no locale
 *    entries; requiring one would be wrong, so they never count.
 *  - keys starting with `diff_`: the recorded convention (CONTEXT.md,
 *    "diff" entries) for intentionally default-locale-only strings. All
 *    historically-defaulted `diff_` keys have since been translated into
 *    every locale, so the prefix is prophylactic — a new `diff_` key is
 *    exempt by convention, anything else must translate or baseline.
 *  - [properNounExemptKeys]: product/service proper nouns that must not be
 *    translated. Belt-and-braces for the day one of them loses its
 *    `translatable="false"` marker.
 *
 * Module discovery mirrors [BareRunCatchingRatchetTest]: repo root located
 * by walking up to `settings.gradle.kts`, then every
 * `composeResources/values/strings.xml` under `shared/` and `apps/` (build
 * output excluded) is a guarded module — a new module with resources is
 * guarded automatically, no hand list to forget.
 *
 * To accept a (temporary) gap: add an entry to [baselineMissingKeys] keyed
 * `"relative/module/path:locale-tag"` with the current missing count. To
 * LOWER a baseline (the point of the ratchet): translate the keys, delete
 * or shrink the entry. Never raise a number except for an accepted gap,
 * and keep its KDoc pointed at the tracking issue.
 */
class I18nParityRatchetTest {

    /**
     * Allowed MISSING-key count per module+locale. Keys are
     * `"module/dir:locale"`, e.g. `"shared/feature/settings:de"`. Absent key
     * means 0 — no missing keys allowed.
     *
     * Zero-tolerance: the core/ui PreferenceEnumNames 15-key translation
     * follow-up landed 2026-10-05, so every entry below reads 0 and the map
     * is kept only as documentation of the accepted-gap mechanism. A new
     * untranslated key must be translated (preferred) or added here as a
     * deliberate baseline entry — never raise an existing number, and keep
     * the entry's KDoc pointed at the tracking issue.
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
    )

    /**
     * Product/service proper nouns that must not be translated. Belt-and-
     * braces: each of these is currently also marked `translatable="false"`
     * in its default file, so the marker alone already exempts them — this
     * list keeps them exempt even if the marker is ever dropped.
     */
    private val properNounExemptKeys = setOf(
        "settings_jellyplay",
        "settings_radarr",
        "settings_sonarr",
        "settings_syncplay",
    )

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        assertTrue(dir != null, "could not locate settings.gradle.kts from ${System.getProperty("user.dir")}")
        return dir!!
    }

    /**
     * Every resource root to guard: directories under `shared/` or `apps/`
     * holding `composeResources/values/strings.xml`, with `build` output
     * directories pruned from the walk.
     */
    private fun discoverResourceRoots(root: File): List<File> {
        val roots = mutableListOf<File>()
        listOf("shared", "apps").forEach { top ->
            val topDir = File(root, top)
            if (!topDir.isDirectory) return@forEach
            topDir.walkTopDown()
                .onEnter { it.name != "build" }
                .filter { it.isFile && it.path.replace('\\', '/').endsWith("composeResources/values/strings.xml") }
                .forEach { roots += it.parentFile }
        }
        return roots
    }

    /**
     * Regexes matching every counted resource entry: `<string name="…">`
     * and `<plurals name="…">`, each captured as (name, attributes). A
     * plurals element counts as one key regardless of how many quantity
     * items it carries — parity is judged on key existence per locale.
     */
    private val keyRegexes = listOf(
        Regex("""<string\s+name="([^"]+)"([^>]*)>"""),
        Regex("""<plurals\s+name="([^"]+)"([^>]*)>"""),
    )

    private fun isExempt(name: String, attributes: String): Boolean =
        name.startsWith("diff_") ||
            attributes.contains("translatable=\"false\"") ||
            name in properNounExemptKeys

    /**
     * Every `*.xml` string file in a qualifier dir. Modules may split their
     * strings across per-family files (the settings module's strings_search /
     * strings_appearance / … siblings of strings.xml, XC-3) — CMP merges all
     * of them per qualifier dir, so parity is judged over the union.
     */
    private fun stringFiles(qualifierDir: File): List<File> =
        qualifierDir.listFiles { f -> f.isFile && f.extension == "xml" }
            .orEmpty()
            .sortedBy { it.name }

    /**
     * The default-locale keys that COUNT for the ratchet: every `<string>`
     * and `<plurals>` key except the exempt classes documented on the class
     * KDoc.
     */
    private fun countedDefaultKeys(valuesDir: File): Set<String> {
        val keys = mutableSetOf<String>()
        for (file in stringFiles(valuesDir)) {
            val text = file.readText(Charsets.UTF_8)
            for (regex in keyRegexes) {
                regex.findAll(text)
                    .map { it.groupValues[1] to it.groupValues[2] }
                    .filter { (name, attrs) -> !isExempt(name, attrs) }
                    .map { it.first }
                    .forEach { keys += it }
            }
        }
        return keys
    }

    private fun localeKeys(localeValuesDir: File): Set<String> {
        val keys = mutableSetOf<String>()
        for (file in stringFiles(localeValuesDir)) {
            val text = file.readText(Charsets.UTF_8)
            for (regex in keyRegexes) {
                regex.findAll(text).mapTo(keys) { it.groupValues[1] }
            }
        }
        return keys
    }

    @Test
    fun `locale missing-key counts never rise above baseline`() {
        val root = repoRoot()
        val resourceRoots = discoverResourceRoots(root)
        assertTrue(
            resourceRoots.size >= 20,
            "i18n ratchet discovered only ${resourceRoots.size} resource roots under shared/ and apps/ — " +
                "discovery is broken or the tree moved; fix discovery, do not lower this bound to fit",
        )

        val failures = mutableListOf<String>()
        for (valuesDir in resourceRoots.sortedBy { it.path }) {
            // <module>/src/commonMain/composeResources/values → <module>
            // (the dir carrying build.gradle.kts), 4 levels up from `values`.
            val module = valuesDir.parentFile!!.parentFile!!.parentFile!!.parentFile!!
            val modulePath = module.relativeTo(root).path.replace('\\', '/')
            val counted = countedDefaultKeys(valuesDir)
            val locales = valuesDir.parentFile!!
                .listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
                .orEmpty()
                .filter { it.resolve("strings.xml").isFile }
                .sortedBy { it.name }
            for (localeDir in locales) {
                val missing = counted - localeKeys(localeDir)
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
