package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * One table-of-contents entry of a book, format-agnostic. This is the shape
 * the reader caches locally (see
 * `BookTocCacheRepository`) and the media-detail screen renders as the
 * book's "Contents" row — the detail screen never parses a book file itself
 * for it.
 *
 * Exactly one of [href] / [page] is meaningful, mirroring the two reader
 * content kinds:
 *  - EPUB (reflowable): [href] carries the destination the EPUB host jumps
 *    to with its `goTo` (spine href, optionally with a fragment). Opaque —
 *    like the CFI anchors of ADR 0003, the native side only ever stores and
 *    hands it back.
 *  - Paged (PDF): [page] is the 0-based destination page index
 *    (`PdfOutlineParser` semantics). CBZ/CBR archives have no TOC story, so
 *    they never produce entries.
 *
 * [level] is the nesting depth (0 = top level) so a nested outline renders
 * indented. [Serializable] because the cache row stores the entry list as a
 * JSON document.
 */
@Serializable
@Immutable
data class BookTocEntry(
    val label: String,
    /** EPUB destination (spine href, optional fragment). Null for paged entries. */
    val href: String? = null,
    /** 0-based PDF destination page. Null for reflowable entries. */
    val page: Int? = null,
    /** Nesting depth (0 = top level) for indented rendering. */
    val level: Int = 0,
)
