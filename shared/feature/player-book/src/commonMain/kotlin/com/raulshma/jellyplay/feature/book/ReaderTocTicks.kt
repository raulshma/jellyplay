package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.feature.book.epub.EpubTocItem

/**
 * One jump target on the reader's TOC tick rail: the visible-tick window
 * (±2 around the current entry), the drag-scrub preview set, and the jump
 * command's payload. Exactly one of [href]/[page] is meaningful — EPUB
 * entries carry the spine href, PDF entries the 0-based page (same split as
 * the core `BookTocEntry` model, kept separate because the rail runs on the
 * reader's LIVE per-format state, not the details screen's cached copy).
 */
internal data class ReaderTocTick(
    val label: String,
    val href: String? = null,
    val page: Int? = null,
)

/** The live `toc` event's flattened EPUB items → rail ticks, 1:1. */
internal fun epubTocTicks(items: List<EpubTocItem>): List<ReaderTocTick> =
    items.map { ReaderTocTick(label = it.label, href = it.href) }

/**
 * Index in [items] of the chapter [chapterHref] currently points at — the
 * tick rail's "you are here". Matching mirrors reader.js `chapterLabelFor`
 * (fragments stripped, exact then `/`-boundary suffix in either direction),
 * with one refinement: an entry whose destination carries the SAME fragment
 * outranks the fragment-stripped match, so a sub-section TOC entry beats its
 * parent chapter. First match wins per pass (books do repeat destinations);
 * `null` = no href yet or nothing matches — the rail stays hidden rather
 * than guessing.
 */
internal fun epubCurrentTocIndex(items: List<EpubTocItem>, chapterHref: String?): Int? {
    val raw = chapterHref.orEmpty()
    if (raw.isEmpty()) return null
    items.forEachIndexed { index, item ->
        if (item.href == raw) return index
    }
    val clean = raw.substringBefore('#')
    items.forEachIndexed { index, item ->
        if (item.href.substringBefore('#') == clean) return index
    }
    items.forEachIndexed { index, item ->
        if (endsWithAtBoundary(clean, item.href.substringBefore('#'))) return index
    }
    return null
}

private fun endsWithAtBoundary(a: String, b: String): Boolean =
    (a.endsWith("/$b") && a.length > b.length + 1) || (b.endsWith("/$a") && b.length > a.length + 1)

/**
 * The PDF outline tree → depth-first rail ticks. Nodes without a page
 * destination are dropped: a tick the jump cannot land on has no business on
 * a scrub rail (they stay in the TOC sheet, which renders them read-only).
 */
internal fun pdfTocTicks(outline: List<PdfOutlineNode>): List<ReaderTocTick> =
    buildList {
        fun walk(nodes: List<PdfOutlineNode>) {
            for (node in nodes) {
                node.pageIndex?.let { add(ReaderTocTick(label = node.title, page = it)) }
                walk(node.children)
            }
        }
        walk(outline)
    }

/**
 * Tick index the [currentPage] sits in — the LAST tick whose page is at or
 * before it (outline targets are page starts; the chapter runs until the next
 * target). `null` before the first outline target (cover pages) — nothing to
 * anchor the rail to yet.
 */
internal fun pdfCurrentTickIndex(ticks: List<ReaderTocTick>, currentPage: Int): Int? {
    var found: Int? = null
    ticks.forEachIndexed { index, tick ->
        val page = tick.page ?: return@forEachIndexed
        if (page <= currentPage) found = index
    }
    return found
}

/**
 * The rail's visible tick indices: [before] entries before [currentIndex] and
 * [after] after it, clamped at both ends (a TOC with ≤ before+1+after entries
 * shows all of them; near either end the window shrinks rather than slides
 * past the boundary). Empty when [currentIndex] is out of range — the caller
 * hides the rail.
 */
internal fun tocTickWindow(
    tickCount: Int,
    currentIndex: Int?,
    before: Int = TOC_RAIL_CONTEXT_TICKS,
    after: Int = TOC_RAIL_CONTEXT_TICKS,
): List<Int> {
    if (tickCount <= 0) return emptyList()
    val current = currentIndex ?: return emptyList()
    if (current < 0 || current >= tickCount) return emptyList()
    val first = (current - before).coerceAtLeast(0)
    val last = (current + after).coerceAtMost(tickCount - 1)
    return (first..last).toList()
}

/** How many neighbors the tick rail shows on each side of the current entry. */
internal const val TOC_RAIL_CONTEXT_TICKS = 2
