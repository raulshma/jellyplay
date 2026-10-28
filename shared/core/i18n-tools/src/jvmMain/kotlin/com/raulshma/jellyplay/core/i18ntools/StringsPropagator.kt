package com.raulshma.jellyplay.core.i18ntools

import java.io.File

/**
 * Propagates a module's default-locale strings into its locale tables — the
 * write-side counterpart of this module's I18nParityRatchetTest. The
 * default-locale `values` XML family is the DECLARATION; every
 * `values-<lang>` sibling is an artifact that must carry each counted key. A
 * key added to the default tables without its locale entries silently falls
 * back to English for that locale's users (no build error — the ratchet
 * caught the drift only after it accumulated, and commit 4f00fdee4 had to
 * translate 259 accumulated keys × 8 locales in one pass), so this tool
 * inserts the missing keys the moment they appear.
 *
 * Gradle wiring lives in the ROOT build — repo-wide `propagateStrings` /
 * `checkStrings` JavaExec tasks (the settings module's old per-module task
 * pair is gone; settings is covered by the repo-wide discovery), running
 * this main with `--discover <repo-root>` so [I18nResourceDiscovery]
 * enumerates every module that ships `composeResources/values/strings.xml`.
 * Run `./gradlew propagateStrings` after adding keys; `checkStrings` fails
 * while any gap remains, and `StringsPropagatorTest` pins the settings tree
 * at zero in the IDE test run too.
 *
 * Rules:
 *  - NEVER overwrites an existing locale entry — only keys absent from the
 *    whole `values-<lang>/` family are propagated;
 *  - propagated entries are copied VERBATIM from the default locale and
 *    marked pending (the [CountedKeyGrammar.PENDING_MARKER] block at the end
 *    of the locale's `strings.xml`) — the ratchet counts PENDING-marked
 *    entries as still-missing (see the grammar's KDoc), so propagation
 *    cannot fake parity — translate each and fold it into the file's
 *    comment groups;
 *  - the grammar's exempt classes are not gaps and are never propagated:
 *    `translatable="false"` keys (brand names, URL placeholders), the
 *    `diff_` prefix (the recorded convention for default-locale-only reset
 *    strings) and the grammar's proper-noun list (those keys are also
 *    marked `translatable="false"`, so the marker alone already covers
 *    them — the list is belt-and-braces for the day one loses its marker).
 */
object StringsPropagator {

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
        val defaults = CountedKeyGrammar.defaultEntries(valuesDir)
        return localeDirs(resourcesDir).mapNotNull { (locale, dir) ->
            val present = CountedKeyGrammar.presentKeys(dir)
            val missing = defaults.keys.filter { it !in present }
            if (missing.isEmpty()) null else Gap(locale, dir, missing)
        }
    }

    /**
     * Writes every gap: the missing entries are appended — verbatim from the
     * default locale, under the [CountedKeyGrammar.PENDING_MARKER] heading —
     * before the closing `</resources>` of the locale's `strings.xml`
     * (created if the family has no such file yet). Returns the per-locale
     * propagated key counts (summed per locale across modules — repo-wide
     * runs cover one locale tag per module). Idempotent: an entry already
     * present in the family (any file) is skipped, and a block already
     * carrying the marker is left untouched except for keys still missing
     * from it.
     */
    fun propagate(gaps: List<Gap>): Map<String, Int> = buildMap {
        gaps.forEach { gap ->
            val defaults = CountedKeyGrammar.defaultEntries(File(gap.valuesDir.parentFile, "values"))
            val file = File(gap.valuesDir, "strings.xml")
            val text = if (file.isFile) {
                file.readText(Charsets.UTF_8)
            } else {
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n"
            }
            val present = CountedKeyGrammar.presentKeys(gap.valuesDir)
            val toAdd = gap.missingKeys.filter { it !in present }
            if (toAdd.isEmpty()) {
                put(gap.locale, (get(gap.locale) ?: 0))
            } else {
                val block = buildString {
                    append("    <!-- ${CountedKeyGrammar.PENDING_MARKER}: copied verbatim from the default locale by")
                    append(" StringsPropagator — translate each entry and fold it into the groups above. -->\n")
                    toAdd.forEach { key ->
                        append(rawElement(File(gap.valuesDir.parentFile, "values"), key)?.trimEnd()?.prependIndent("    "))
                        append('\n')
                    }
                }
                val closing = text.lastIndexOf("</resources>")
                check(closing >= 0) { "strings propagator: ${file.path} has no </resources> closing tag" }
                file.writeText(text.substring(0, closing) + block + text.substring(closing))
                put(gap.locale, (get(gap.locale) ?: 0) + toAdd.size)
            }
        }
    }

    /**
     * The raw source text of one key's element from the default family —
     * copied verbatim so escapes (`\u2026`, `%1$s`, `\'`) survive exactly as
     * declared. Null when the key vanished mid-run.
     */
    private fun rawElement(valuesDir: File, key: String): String? =
        CountedKeyGrammar.stringFiles(valuesDir).firstNotNullOfOrNull { file ->
            val text = file.readText(Charsets.UTF_8)
            CountedKeyGrammar.elementMatches(text)
                .firstOrNull { it.groupValues[2] == key }
                ?.value
        }

    /** The locale dirs of [resourcesDir], sorted: (tag, dir) pairs. */
    private fun localeDirs(resourcesDir: File): List<Pair<String, File>> =
        resourcesDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
            .orEmpty()
            .filter { it.isDirectory }
            .sortedBy { it.name }
            .map { it.name.removePrefix("values-") to it }
}

fun main(args: Array<String>) {
    val usage = "usage: StringsPropagator [--check] (--discover <repo-root> | <composeResources-dir>...)"
    val checkMode = args.contains("--check")
    val rest = args.filterNot { it == "--check" }
    val resourcesDirs: List<File> = when (rest.firstOrNull()) {
        // Repo-wide mode (the root build's tasks): enumerate every guarded
        // resource root under shared/ and apps/ — the same walk the ratchet
        // does, one task pair covering all resource-carrying modules.
        "--discover" -> {
            check(rest.size == 2) { usage }
            val root = File(rest[1])
            check(root.isDirectory) { "strings propagator: ${rest[1]} is not a directory — $usage" }
            I18nResourceDiscovery.discoverResourceRoots(root)
        }
        // Ad-hoc mode: one or more explicit composeResources dirs.
        null -> error(usage)
        else -> rest.map { File(it) }
    }
    check(resourcesDirs.isNotEmpty()) {
        "strings propagator: no composeResources/values roots found — $usage"
    }

    val gaps = resourcesDirs.flatMap { dir -> StringsPropagator.findGaps(dir) }
    if (gaps.isEmpty()) {
        println(
            "strings propagator: ${resourcesDirs.size} resource root(s) — " +
                "every locale carries every counted key",
        )
        return
    }
    if (checkMode) {
        error(
            "strings propagator: ${gaps.size} locale table(s) missing keys — run " +
                "./gradlew propagateStrings and translate the " +
                "${CountedKeyGrammar.PENDING_MARKER} entries:\n" +
                gaps.joinToString("\n") { gap ->
                    "  ${gap.valuesDir.parentFile!!.parentFile!!.name}/values-${gap.locale}: " +
                        "${gap.missingKeys.size} missing: ${gap.missingKeys.joinToString(", ")}"
                },
        )
    }
    val propagated = StringsPropagator.propagate(gaps)
    println(
        "strings propagator: propagated " + propagated.values.sum() + " keys — " +
            propagated.entries.joinToString(", ") { (locale, count) -> "$locale: $count" },
    )
}
