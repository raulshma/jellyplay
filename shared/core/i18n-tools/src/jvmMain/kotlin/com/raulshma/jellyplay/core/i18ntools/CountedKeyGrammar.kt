package com.raulshma.jellyplay.core.i18ntools

import java.io.File

/**
 * THE one home of the counted-key grammar for the repo's locale string
 * tables — the exact rules both sides of the i18n parity story share (the
 * write-side [StringsPropagator] and the read-side I18nParityRatchetTest,
 * this module's jvmTest):
 *
 *  - COUNTED keys are the `<string name="…">` and `<plurals name="…">`
 *    elements of a qualifier dir; a plurals element is ONE key whatever its
 *    quantity items;
 *  - parity is judged over the UNION of every `*.xml` in the dir — modules
 *    split their strings across per-family files (`strings_search.xml`, …)
 *    and CMP merges them, so a split cannot hide a gap;
 *  - EXEMPT classes (never counted as gaps, never propagated): keys marked
 *    `translatable="false"` (brand names, URL/email placeholders), keys
 *    starting with `diff_` (the recorded convention for default-locale-only
 *    reset strings) and [properNounExemptKeys] (product/service proper
 *    nouns — belt-and-braces for the day one of them loses its marker).
 *
 * The regex implementation is the UNION of the two shapes the tooling
 * duplicated before it moved here: the paired form
 * (`<string name="…">…</string>`, needed by the propagator to lift a whole
 * raw element — escapes and all — for verbatim copying) plus the
 * self-closing form (`<string name="x"/>` — an empty entry), whose negative
 * lookbehind keeps a self-closing element from pairing with a LATER
 * element's closing tag. Together they match a strict superset of the
 * open-tag-only regexes the read-side ratchet once used; on
 * convention-conformant tables (XML-escaped values, no literal `<`) the
 * extracted key and attribute sets are identical either way.
 *
 * PENDING rule (the load-bearing honesty rule that keeps repo-wide
 * propagation from destroying the ratchet's metric): a key the propagator
 * inserts is copied VERBATIM from the default locale under a
 * `<!-- PENDING TRANSLATION: … -->` heading appended at the end of the
 * locale's `strings.xml`. [presentKeys] — the writer's read — counts it (a
 * real entry; the propagator must not insert it twice), but
 * [translatedKeys] — the read the ratchet consumes — EXCLUDES every key
 * whose occurrences all sit inside a pending block, so a propagated key
 * still reads missing and the ratchet's "numbers only ever go down; 0 =
 * parity arrived" metric survives propagation. Translate the pending
 * entries, fold them into the file's comment groups and delete the marker
 * comment; the key then re-enters [translatedKeys].
 */
object CountedKeyGrammar {

    /** The marker heading every propagated block in a locale file. */
    const val PENDING_MARKER = "PENDING TRANSLATION"

    /**
     * The exact comment opener the propagator writes before every pending
     * block; everything from this opener to the end of its file counts as
     * pending. The propagator always appends blocks just before the closing
     * `</resources>`, so later blocks stack after earlier ones and one
     * opener per file is the correct region start; a translator deletes the
     * marker (and its block) when the entries are folded up translated.
     */
    private const val pendingCommentOpener = "<!-- $PENDING_MARKER:"

    /**
     * Product/service proper nouns that must not be translated
     * (settings_jellyplay, settings_radarr, settings_sonarr,
     * settings_syncplay). Belt-and-braces: each is currently also marked
     * `translatable="false"` in its default file, so the marker alone
     * already exempts them — this list keeps them exempt even if the marker
     * is ever dropped, on both sides of the grammar at once.
     */
    private val properNounExemptKeys = setOf(
        "settings_jellyplay",
        "settings_radarr",
        "settings_sonarr",
        "settings_syncplay",
    )

    /**
     * Matches every counted resource element with its attributes: `<string
     * name="…">…</string>` and `<plurals name="…">…</plurals>`. A plurals
     * element is one key whatever its quantity items (the counting rule).
     * Element values are XML-escaped by convention (no literal `<` inside),
     * so the lazy scan to the closing tag is exact for these tables; the
     * negative lookbehind keeps self-closing elements (`name="x"/>` — an
     * empty entry) from pairing with a LATER element's closing tag.
     * Groups: 1 = element kind, 2 = name, 3 = attributes.
     */
    val pairedElementRegex = Regex(
        """<(string|plurals)\s+name="([^"]+)"([^>]*(?<!/))>.*?</\1>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** The self-closing form (`<string name="x"/>` — an empty entry). */
    val selfClosingElementRegex = Regex(
        """<(string|plurals)\s+name="([^"]+)"([^>]*)/>""",
    )

    /** The exemption rule — one home, both consumers. */
    fun isExempt(name: String, attributes: String): Boolean =
        name.startsWith("diff_") ||
            attributes.contains("translatable=\"false\"") ||
            name in properNounExemptKeys

    /**
     * Every `*.xml` string file in a qualifier dir — modules split their
     * strings across per-family files and CMP merges them, so parity is
     * judged over the union.
     */
    fun stringFiles(qualifierDir: File): List<File> =
        qualifierDir.listFiles { f -> f.isFile && f.extension == "xml" }
            .orEmpty()
            .sortedBy { it.name }

    /** Paired + self-closing element matches over one file's text. */
    fun elementMatches(text: String): Sequence<MatchResult> =
        pairedElementRegex.findAll(text) + selfClosingElementRegex.findAll(text)

    /**
     * The default locale's counted entries: key → raw element text. The
     * exempt classes are excluded — they are not gaps and must never
     * propagate. Last occurrence wins per key (a duplicated name in one
     * family is already a bug this table shape cannot fix).
     */
    fun defaultEntries(valuesDir: File): Map<String, String> = buildMap {
        stringFiles(valuesDir).forEach { file ->
            elementMatches(file.readText(Charsets.UTF_8)).forEach { match ->
                if (!isExempt(match.groupValues[2], match.groupValues[3])) {
                    put(match.groupValues[2], match.value)
                }
            }
        }
    }

    /**
     * The default locale's counted keys — the set the ratchet judges parity
     * against.
     */
    fun countedKeys(valuesDir: File): Set<String> = defaultEntries(valuesDir).keys

    /**
     * Every key a locale family carries, PENDING-marked entries included —
     * the writer's read: an entry still awaiting translation is a real
     * entry, so the propagator must not insert it twice.
     */
    fun presentKeys(valuesDir: File): Set<String> =
        stringFiles(valuesDir)
            .flatMap { file -> elementMatches(file.readText(Charsets.UTF_8)) }
            .mapTo(mutableSetOf()) { it.groupValues[2] }

    /**
     * The keys a locale family carries TRANSLATED — [presentKeys] minus
     * every key whose occurrences all sit inside a PENDING block (the
     * region from [pendingCommentOpener] to its file's end). The ratchet's
     * read: a verbatim untranslated copy is still a missing translation.
     */
    fun translatedKeys(valuesDir: File): Set<String> =
        stringFiles(valuesDir)
            .flatMap { file ->
                val text = file.readText(Charsets.UTF_8)
                val pendingStart = text.indexOf(pendingCommentOpener)
                elementMatches(if (pendingStart < 0) text else text.substring(0, pendingStart))
            }
            .mapTo(mutableSetOf()) { it.groupValues[2] }
}
