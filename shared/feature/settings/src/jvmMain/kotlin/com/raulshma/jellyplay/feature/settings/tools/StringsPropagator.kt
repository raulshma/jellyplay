package com.raulshma.jellyplay.feature.settings.tools

import java.io.File

/**
 * Propagates the settings feature's default-locale strings into its locale
 * tables — the write-side counterpart of the concurrency module's
 * I18nParityRatchetTest. The default-locale `values` XML family is the
 * DECLARATION; every `values-<lang>` sibling is an artifact that must carry
 * each counted key. A key added to the default tables without its locale
 * entries silently falls back to English for that locale's users (no build
 * error — the ratchet caught the drift only after it accumulated, and commit
 * 4f00fdee4 had to translate 259 accumulated keys × 8 locales in one pass),
 * so this tool inserts the missing keys the moment they appear.
 *
 * Mirrors the `SettingsCatalogGenerator` precedent (jvmMain tools package,
 * `--check` mode wired into `check`, a JVM test pinning the committed state):
 * run `./gradlew :shared:feature:settings:propagateStrings` after adding
 * keys; `checkStrings` (wired into `check`) fails while any gap remains, and
 * `StringsPropagatorTest` pins the tree at zero in the IDE test run too.
 *
 * Rules:
 *  - NEVER overwrites an existing locale entry — only keys absent from the
 *    whole `values-<lang>/` family are propagated;
 *  - propagated entries are copied VERBATIM from the default locale and
 *    marked pending (the `PENDING TRANSLATION` block at the end of the
 *    locale's `strings.xml`) — translate each and fold it into the file's
 *    comment groups;
 *  - the ratchet's exempt classes are not gaps and are never propagated:
 *    `translatable="false"` keys (brand names, URL placeholders) and the
 *    `diff_` prefix (the recorded convention for default-locale-only reset
 *    strings). The I18nParityRatchetTest's proper-noun belt-and-braces list
 *    needs no mirror here — those keys are also marked
 *    `translatable="false"`, which the exemption below already covers.
 */
object StringsPropagator {

    /** The marker heading every propagated block in a locale file. */
    const val PENDING_MARKER = "PENDING TRANSLATION"

    /** One locale's gap, before writing. */
    data class Gap(val locale: String, val valuesDir: File, val missingKeys: List<String>)

    /**
     * Walks [resourcesDir] (`.../composeResources`): `values/` is the default
     * declaration, every `values-<tag>/` sibling a locale artifact. Returns
     * one [Gap] per locale whose family misses counted keys, in stable
     * (sorted) order. An absent locale dir is not a gap — a module ships
     * only the locales it translates.
     */
    fun findGaps(resourcesDir: File): List<Gap> {
        val valuesDir = File(resourcesDir, "values")
        check(valuesDir.isDirectory) { "strings propagator: no values/ under $resourcesDir" }
        val defaults = defaultEntries(valuesDir)
        return localeDirs(resourcesDir).mapNotNull { (locale, dir) ->
            val present = localeKeys(dir)
            val missing = defaults.keys.filter { it !in present }
            if (missing.isEmpty()) null else Gap(locale, dir, missing)
        }
    }

    /**
     * Writes every gap: the missing entries are appended — verbatim from the
     * default locale, under the [PENDING_MARKER] heading — before the
     * closing `</resources>` of the locale's `strings.xml` (created if the
     * family has no such file yet). Returns the per-locale propagated key
     * counts. Idempotent: an entry already present in the family (any file)
     * is skipped, and a block already carrying the marker is left untouched
     * except for keys still missing from it.
     */
    fun propagate(gaps: List<Gap>): Map<String, Int> = buildMap {
        gaps.forEach { gap ->
            val defaults = defaultEntries(File(gap.valuesDir.parentFile, "values"))
            val file = File(gap.valuesDir, "strings.xml")
            val text = if (file.isFile) {
                file.readText(Charsets.UTF_8)
            } else {
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n"
            }
            val present = localeKeys(gap.valuesDir)
            val toAdd = gap.missingKeys.filter { it !in present }
            if (toAdd.isEmpty()) {
                put(gap.locale, 0)
            } else {
                val block = buildString {
                    append("    <!-- $PENDING_MARKER: copied verbatim from the default locale by")
                    append(" StringsPropagator — translate each entry and fold it into the groups above. -->\n")
                    toAdd.forEach { key ->
                        append(rawElement(File(gap.valuesDir.parentFile, "values"), key)?.trimEnd()?.prependIndent("    "))
                        append('\n')
                    }
                }
                val closing = text.lastIndexOf("</resources>")
                check(closing >= 0) { "strings propagator: ${file.path} has no </resources> closing tag" }
                file.writeText(text.substring(0, closing) + block + text.substring(closing))
                put(gap.locale, toAdd.size)
            }
        }
    }

    /**
     * The default locale's counted entries: key → (raw element text). The
     * exempt classes (translatable="false", `diff_` prefix) are excluded —
     * they are not gaps and must never propagate.
     */
    private fun defaultEntries(valuesDir: File): Map<String, String> {
        val raw = mutableMapOf<String, String>()
        stringFiles(valuesDir).forEach { file ->
            val text = file.readText(Charsets.UTF_8)
            pairedElementRegex.findAll(text).forEach { match ->
                if (!isExempt(match.groupValues[2], match.groupValues[3])) raw[match.groupValues[2]] = match.value
            }
            selfClosingElementRegex.findAll(text).forEach { match ->
                if (!isExempt(match.groupValues[2], match.groupValues[3])) raw[match.groupValues[2]] = match.value
            }
        }
        return raw
    }

    /** The key set a locale family already carries (exemptions irrelevant). */
    private fun localeKeys(valuesDir: File): Set<String> =
        stringFiles(valuesDir)
            .flatMap { file ->
                val text = file.readText(Charsets.UTF_8)
                pairedElementRegex.findAll(text) + selfClosingElementRegex.findAll(text)
            }
            .mapTo(mutableSetOf()) { it.groupValues[2] }

    /**
     * The raw source text of one key's element from the default family —
     * copied verbatim so escapes (`\u2026`, `%1$s`, `\'`) survive exactly as
     * declared. Null when the key vanished mid-run.
     */
    private fun rawElement(valuesDir: File, key: String): String? =
        stringFiles(valuesDir).firstNotNullOfOrNull { file ->
            val text = file.readText(Charsets.UTF_8)
            (pairedElementRegex.findAll(text) + selfClosingElementRegex.findAll(text))
                .firstOrNull { it.groupValues[2] == key }
                ?.value
        }

    /**
     * Matches every counted resource element with its attributes: `<string
     * name="…">…</string>` and `<plurals name="…">…</plurals>`. A plurals
     * element is one key whatever its quantity items (the ratchet's counting
     * rule). Element values are XML-escaped by convention (no literal `<`
     * inside), so the lazy scan to the closing tag is exact for these tables;
     * the negative lookbehind keeps self-closing elements (`name="x"/>` — an
     * empty entry) from pairing with a LATER element's closing tag.
     */
    private val pairedElementRegex = Regex(
        """<(string|plurals)\s+name="([^"]+)"([^>]*(?<!/))>.*?</\1>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** The self-closing form (`<string name="x"/>` — an empty entry). */
    private val selfClosingElementRegex = Regex(
        """<(string|plurals)\s+name="([^"]+)"([^>]*)/>""",
    )

    /** The locale dirs of [resourcesDir], sorted: (tag, dir) pairs. */
    private fun localeDirs(resourcesDir: File): List<Pair<String, File>> =
        resourcesDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
            .orEmpty()
            .filter { it.isDirectory }
            .sortedBy { it.name }
            .map { it.name.removePrefix("values-") to it }

    /**
     * Every `*.xml` string file in a qualifier dir — modules split their
     * strings across per-family files (`strings_search.xml`, …) and CMP
     * merges them, so parity is judged over the union.
     */
    private fun stringFiles(valuesDir: File): List<File> =
        valuesDir.listFiles { f -> f.isFile && f.extension == "xml" }
            .orEmpty()
            .sortedBy { it.name }

    private fun isExempt(name: String, attributes: String): Boolean =
        name.startsWith("diff_") || attributes.contains("translatable=\"false\"")
}

fun main(args: Array<String>) {
    val usage = "usage: StringsPropagator [--check] <composeResources-dir>"
    val checkMode = args.contains("--check")
    val dir = args.filterNot { it == "--check" }.singleOrNull()
        ?: error(usage)
    val resourcesDir = File(dir)
    check(resourcesDir.isDirectory) { "strings propagator: $dir is not a directory — $usage" }

    val gaps = StringsPropagator.findGaps(resourcesDir)
    if (gaps.isEmpty()) {
        println("strings propagator: ${resourcesDir.path} — every locale carries every counted key")
        return
    }
    if (checkMode) {
        error(
            "strings propagator: ${gaps.size} locale(s) missing keys — run " +
                "./gradlew :shared:feature:settings:propagateStrings and translate the " +
                "PENDING TRANSLATION entries:\n" +
                gaps.joinToString("\n") { (locale, _, missing) ->
                    "  values-$locale: ${missing.size} missing: ${missing.joinToString(", ")}"
                },
        )
    }
    val propagated = StringsPropagator.propagate(gaps)
    println(
        "strings propagator: propagated " + propagated.values.sum() + " keys — " +
            propagated.entries.joinToString(", ") { (locale, count) -> "$locale: $count" },
    )
}
