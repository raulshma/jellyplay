package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The structured "What's New" view over the GitHub release notes — ONE
 * content artifact per release (the release body), carrying an optional
 * `## What's New` GFM table whose rows become the guided entry cards; the
 * rest of the body stays renderable markdown. One [WhatsNewRelease] per
 * version.
 *
 * **Sources & precedence:** the release body lives in ONE place — the GitHub
 * release. The app consumes it through the GitHub Releases API (fetched at
 * runtime; per-version fetch results override the cached copy, so editing a
 * release body on GitHub reaches existing installs) and persists the last
 * fetch as the offline cache. Decoding lives here so the network client and
 * the DataStore cache parse through ONE definition.
 *
 * **Authoring format** (the release body, rendered on the GitHub release
 * page AND parsed into cards):
 *
 * ```markdown
 * ## What's New
 *
 * | Title | Category | Summary | Where | Icon | Link |
 * |---|---|---|---|---|---|
 * | Discover Rows | new | Pin custom rows to Home. | Settings → Home Screen | rows | settings.discoverRows |
 * ```
 *
 * Column names are prefix-matched case-insensitively (`Title` required;
 * `Category`, `Summary`, `Where to find it`, `Icon`, `Link`,
 * `Highlight` optional; unknown columns ignored). Categories: `new`,
 * `improvement`, `fix` (unknown falls back to IMPROVEMENT). Cells are plain
 * text — surrounding `**`/`` ` `` wrappers are stripped; an escaped pipe
 * (`\|`) stays in its cell and unescapes to a literal `|`. A body without
 * the section/table yields no entries and renders as plain markdown (the
 * fallback).
 *
 * Content is English-only by design (matches the release notes); the UI chrome
 * around it is localized through composeResources.
 *
 * **Forward compatibility:** unknown fields are ignored; a release with a
 * blank version, or with neither entries nor a body, is dropped; an entry
 * without a title is dropped; `target` ids are resolved against a compiled-in
 * route map at display time — an unknown id simply renders no "take me there"
 * action. A newer release body on an older app therefore degrades cleanly.
 */
@Immutable
@Serializable
data class WhatsNewFeed(
    val releases: List<WhatsNewRelease> = emptyList(),
)

@Immutable
@Serializable
data class WhatsNewRelease(
    /** Dotted version string exactly as tagged, minus any leading `v`. */
    val version: String,
    /** Release date as authored (`YYYY-MM-DD`); display-only, never parsed. */
    val date: String? = null,
    /** Optional one-line headline for the release (shown under the version). */
    val title: String? = null,
    /**
     * The full GitHub release-notes markdown this release was authored with.
     * Kept alongside the derived [entries] so surfaces without cards (the
     * Settings archive, an entry-less release) can render the prose.
     */
    val body: String? = null,
    val entries: List<WhatsNewEntry> = emptyList(),
) {
    companion object {
        /** Normalizes a tag (`v0.11.2` → `0.11.2`) for version-key comparisons. */
        fun normalizeVersion(tag: String): String = tag.trim().removePrefix("v")
    }
}

/**
 * One user-visible change. [category] drives the chip/icon; [target] +
 * [highlightSettingId] drive the optional deep link ("Take me there").
 */
@Immutable
@Serializable
data class WhatsNewEntry(
    /** Stable id, unique within its release (also the Compose list key). */
    val id: String,
    val category: WhatsNewCategory = WhatsNewCategory.IMPROVEMENT,
    val title: String,
    val summary: String,
    /** Where to find it / how to use it, in user terms. */
    val howTo: String? = null,
    /** Icon name from the compiled-in icon vocabulary; unknown names render the category icon. */
    val icon: String? = null,
    /** Deep-link target id resolved by the app's compiled-in route map. */
    val target: String? = null,
    /** Settings row to scroll to / focus once the target settings screen opens. */
    val highlightSettingId: String? = null,
)

@Immutable
@Serializable
enum class WhatsNewCategory {
    NEW,
    IMPROVEMENT,
    FIX,
}

// Wire DTOs: every field nullable/optional so a hand-edited document can be
// decoded leniently and validated per-item instead of failing whole-document.
// Category arrives as a plain string so unknown values can be dropped per-entry
// (a kotlinx enum decode would throw on an unrecognized name).
@Serializable
private data class WireEntry(
    val id: String? = null,
    val category: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val howTo: String? = null,
    val icon: String? = null,
    val target: String? = null,
    val highlightSettingId: String? = null,
)

@Serializable
private data class WireRelease(
    val version: String? = null,
    val date: String? = null,
    val title: String? = null,
    val body: String? = null,
    val entries: List<WireEntry> = emptyList(),
)

@Serializable
private data class WireFeed(val releases: List<WireRelease> = emptyList())

/**
 * Decodes + sanitizes a feed document (the DataStore cache round-trips through
 * this; the bundled snapshot and GitHub releases map via
 * [whatsNewReleaseFromNotes] instead). Lenient by policy (ignoreUnknownKeys,
 * isLenient): the cached document is written by a prior app version, so an
 * unknown field must degrade, not crash. Releases with a blank version, or
 * with neither surviving entries nor a body, are dropped individually; an
 * empty/blank document yields the empty feed (callers treat that as "no
 * content"). A release carrying only a body has its entries derived from the
 * `## What's New` table. Returns null only when the document is structurally
 * unparseable (not JSON at all, or not the expected shape).
 */
fun parseWhatsNewFeed(raw: String?): WhatsNewFeed? {
    if (raw.isNullOrBlank()) return WhatsNewFeed()
    return runCatching {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val wire = json.decodeFromString<WireFeed>(raw)
        WhatsNewFeed(
            releases = wire.releases.mapNotNull { release ->
                val version = WhatsNewRelease.normalizeVersion(release.version.orEmpty())
                val body = release.body?.trim().orEmpty()
                val entries = release.entries.mapNotNull { entry ->
                    val category = categoryOf(entry.category)
                    if (entry.title.isNullOrBlank()) return@mapNotNull null
                    WhatsNewEntry(
                        id = entry.id.orEmpty().ifBlank { entry.title },
                        category = category,
                        title = entry.title,
                        summary = entry.summary.orEmpty(),
                        howTo = entry.howTo?.takeIf { it.isNotBlank() },
                        icon = entry.icon?.takeIf { it.isNotBlank() },
                        target = entry.target?.takeIf { it.isNotBlank() },
                        highlightSettingId = entry.highlightSettingId?.takeIf { it.isNotBlank() },
                    )
                }.ifEmpty { parseWhatsNewEntries(body.ifBlank { null }) }
                if (version.isBlank() || (entries.isEmpty() && body.isBlank())) null
                else WhatsNewRelease(version = version, date = release.date, title = release.title, body = body.ifBlank { null }, entries = entries)
            },
        )
    }.getOrNull()
}

/**
 * Maps one GitHub release (tag, published date, release name, notes body)
 * onto a [WhatsNewRelease]. Returns null when the version or body is blank —
 * the single validation seam shared by the bundled snapshot and the fetched
 * releases list.
 */
fun whatsNewReleaseFromNotes(
    version: String?,
    date: String?,
    title: String?,
    body: String?,
): WhatsNewRelease? {
    val normalized = WhatsNewRelease.normalizeVersion(version.orEmpty())
    val notes = body?.trim().orEmpty()
    if (normalized.isBlank() || notes.isBlank()) return null
    return WhatsNewRelease(
        version = normalized,
        date = date?.takeIf { it.isNotBlank() },
        title = title?.takeIf { it.isNotBlank() },
        body = notes,
        entries = parseWhatsNewEntries(notes),
    )
}

// ── The `## What's New` body-table parser ─────────────────────────────────
//
// Hand-rolled on purpose: the section is authored once per release in the
// repo's own release-notes markdown, so the grammar is exactly GFM's
// pipe-table subset — heading, header row, delimiter row, data rows — with
// nothing else to support.

/** A `## What's New` heading (h2–h6, straight or typographic apostrophe). */
private val whatsNewHeadingRegex = Regex("^#{2,6}\\s*what[’']s\\s+new\\s*$", RegexOption.IGNORE_CASE)

/** A GFM delimiter-row cell: `---`, `:---`, `---:`, `:---:`. */
private val delimiterCellRegex = Regex("^:?-+:?$")

/** Header names are prefix-matched after this normalization. */
private fun canonicalHeaderName(raw: String): String =
    raw.lowercase().replace("’", "'").trim()

private fun categoryOf(raw: String?): WhatsNewCategory =
    raw?.trim()
        ?.let { value -> WhatsNewCategory.entries.find { c -> c.name.equals(value, ignoreCase = true) } }
        ?: WhatsNewCategory.IMPROVEMENT

/**
 * Derives the entry cards from a release body's `## What's New` table.
 * Empty when the body is blank, has no such section, or the section carries
 * no valid table — callers fall back to rendering the body as markdown.
 */
fun parseWhatsNewEntries(body: String?): List<WhatsNewEntry> {
    if (body.isNullOrBlank()) return emptyList()
    val lines = body.lines()
    val headingIndex = lines.indexOfFirst { whatsNewHeadingRegex.matches(it.trim()) }
    if (headingIndex == -1) return emptyList()

    // The section ends at the next heading of ANY depth.
    val tableHeaderIndex = (headingIndex + 1 until lines.size)
        .takeWhile { !lines[it].trimStart().startsWith("#") }
        .firstOrNull { index ->
            isTableRow(lines[index]) &&
                index + 1 < lines.size &&
                isDelimiterRow(lines[index + 1])
        }
        ?: return emptyList()

    val columns = mapHeaderCells(tableCells(lines[tableHeaderIndex])) ?: return emptyList()

    val entries = mutableListOf<WhatsNewEntry>()
    var index = tableHeaderIndex + 2
    while (index < lines.size && isTableRow(lines[index])) {
        val cells = tableCells(lines[index])
        fun cell(column: Int?): String =
            column?.let { cells.getOrNull(it) }?.cleanCell().orEmpty()

        val title = cell(columns.title)
        if (title.isNotBlank()) {
            entries += WhatsNewEntry(
                id = title.toEntryId(),
                category = categoryOf(cell(columns.category)),
                title = title,
                summary = cell(columns.summary),
                howTo = cell(columns.howTo).ifBlank { null },
                icon = cell(columns.icon).ifBlank { null },
                target = cell(columns.target).ifBlank { null },
                highlightSettingId = cell(columns.highlight).ifBlank { null },
            )
        }
        index++
    }
    return entries
}

/** Mapped column indexes for the entry fields; null when `Title` is missing. */
private class TableColumns(
    val title: Int,
    val category: Int?,
    val summary: Int?,
    val howTo: Int?,
    val icon: Int?,
    val target: Int?,
    val highlight: Int?,
)

private fun mapHeaderCells(headerCells: List<String>): TableColumns? {
    var title: Int? = null
    var category: Int? = null
    var summary: Int? = null
    var howTo: Int? = null
    var icon: Int? = null
    var target: Int? = null
    var highlight: Int? = null
    headerCells.forEachIndexed { index, raw ->
        // First prefix match wins; later duplicate columns are ignored.
        val name = canonicalHeaderName(raw.cleanCell())
        when {
            title == null && name.startsWith("title") -> title = index
            category == null && name.startsWith("categor") -> category = index
            summary == null && name.startsWith("summar") -> summary = index
            howTo == null && name.startsWith("where") -> howTo = index
            icon == null && name.startsWith("icon") -> icon = index
            target == null && name.startsWith("link") -> target = index
            highlight == null && name.startsWith("highlight") -> highlight = index
        }
    }
    return title?.let {
        TableColumns(it, category, summary, howTo, icon, target, highlight)
    }
}

private fun isTableRow(line: String): Boolean = line.contains("|")

private fun isDelimiterRow(line: String): Boolean {
    val cells = tableCells(line)
    return cells.isNotEmpty() && cells.all { it.matches(delimiterCellRegex) }
}

/**
 * Splits a pipe-table row into trimmed cells (leading/trailing pipes dropped).
 * An escaped pipe (`\|`) is part of its cell, not a delimiter — GFM's way of
 * authoring a literal pipe inside a cell; the backslash is dropped by
 * [cleanCell], never shown.
 */
private fun tableCells(line: String): List<String> {
    var row = line.trim()
    if (row.startsWith("|")) row = row.substring(1)
    val cells = splitOnUnescapedPipes(row).toMutableList()
    // A row-closing pipe yields a trailing empty cell; drop it (an escaped
    // closing pipe stays — it is cell content, not the closer).
    if (cells.isNotEmpty() && cells.last().isBlank() && row.endsWith("|")) {
        cells.removeAt(cells.lastIndex)
    }
    return cells.map { it.trim() }
}

/** Splits on `|` except when escaped as `\|` (a `\\` pair escapes the backslash). */
private fun splitOnUnescapedPipes(row: String): List<String> {
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var index = 0
    while (index < row.length) {
        val char = row[index]
        if (char == '\\' && index + 1 < row.length) {
            cell.append(char).append(row[index + 1])
            index += 2
        } else if (char == '|') {
            cells += cell.toString()
            cell.setLength(0)
            index++
        } else {
            cell.append(char)
            index++
        }
    }
    cells += cell.toString()
    return cells
}

/** Cell text as authored: stray bold/code markers stripped, `\|`/`\\` unescaped (plain text cells). */
private fun String.cleanCell(): String =
    trim().replace("**", "").replace("`", "").replace("\\|", "|").replace("\\\\", "\\").trim()

/** Stable id derived from the title (`Steadier Home loading` → `steadier-home-loading`). */
private fun String.toEntryId(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

/**
 * The releases of [this] feed, newest version first, using the app's canonical
 * dotted-version comparison (pre-release aware) rather than document order —
 * a hand-edited file may append out of order.
 */
fun WhatsNewFeed.sortedByNewest(): WhatsNewFeed =
    copy(releases = releases.sortedWith { a, b -> compareVersions(b.version, a.version) })

/**
 * The release describing [version], if the feed carries one (exact version
 * match after tag normalization).
 */
fun WhatsNewFeed.releaseFor(version: String): WhatsNewRelease? {
    val normalized = WhatsNewRelease.normalizeVersion(version)
    return releases.firstOrNull { it.version == normalized }
}

/**
 * Merges two feeds (typically bundled + fetched): [newer] wins per exact
 * version, unioned with [older]'s versions, newest first. Body and entry
 * lists are taken wholesale from the winning release — no per-entry merge
 * (the fetched release body is the correction mechanism for a bundled
 * release's whole content).
 */
fun mergeWhatsNewFeeds(older: WhatsNewFeed, newer: WhatsNewFeed): WhatsNewFeed {
    val byVersion = older.releases.associateBy { it.version } + newer.releases.associateBy { it.version }
    return WhatsNewFeed(
        releases = byVersion.values.sortedWith { a, b -> compareVersions(b.version, a.version) },
    )
}
