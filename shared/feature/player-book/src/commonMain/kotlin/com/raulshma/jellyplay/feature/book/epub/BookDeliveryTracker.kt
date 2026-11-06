package com.raulshma.jellyplay.feature.book.epub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The boot-transfer ladder both EPUB hosts climb, reduced to pure gate
 * decisions — the hosts keep only what genuinely diverges (the eval
 * receiver, the page-load signal, the event channel).
 *
 * [pageGeneration] counts finished page loads: the engine view can be
 * recreated or reloaded (configuration change, AndroidView rebuild), which
 * wipes reader.js and the book with it, so the book must be delivered to the
 * CURRENT generation. reader.js posts its first status only from
 * decodeAndOpen — i.e. after loadBookEnd assembled OUR bytes (and verified
 * the received count against the begin frame's totalBytes header) — so any
 * status event routed through `withStatusReceipt` is a delivery receipt:
 * [confirmDelivery] latches [deliveredGeneration] to the current generation,
 * which bars a re-transfer and opens the appearance push. A page that
 * finishes loading without a receipt (recreation before/after the send)
 * re-streams the book from the file — no fire-once latch that a reload can
 * strand.
 *
 * There is no pinned payload to release: the transfer streams the book from
 * disk in per-chunk reads (EpubBookCodec), so nothing whole-book-sized is
 * ever held across the boot — the old encode-then-send pin is gone. The
 * state is snapshot-backed so hosts key LaunchedEffects on the generations;
 * [confirmDelivery] may fire off the main thread (desktop's event poll),
 * which snapshot writes tolerate.
 */
internal class BookDeliveryTracker {

    /** 0 until the first page load; one per finished page load after that. */
    var pageGeneration: Int by mutableStateOf(0)
        private set

    /** [Int.MIN_VALUE] until the first receipt; then the generation it vouches for. */
    var deliveredGeneration: Int by mutableStateOf(Int.MIN_VALUE)
        private set

    /** A finished page load begins (or re-runs) the ladder for a fresh generation. */
    fun onPageLoadFinished() {
        pageGeneration++
    }

    /** The delivery receipt: the CURRENT page generation now holds the book. */
    fun confirmDelivery() {
        deliveredGeneration = pageGeneration
    }

    /**
     * Send only to a page whose JS is reachable ([pageFinished]: Android's
     * latched onPageFinished flag, desktop's LIVE LoadingState — a reload
     * transiently reports Loading) for a generation the JS side has not
     * confirmed yet. Generation 0 is the pre-boot generation — no page load
     * has ever finished, so nothing may be sent even if a host's page flag
     * were somehow latched early.
     */
    fun shouldSend(pageFinished: Boolean): Boolean =
        pageFinished && pageGeneration > 0 && pageGeneration > deliveredGeneration

    /**
     * Push only into a page that actually holds the book: a push before
     * loadBookBegin is a silent no-op (reader.js defaults would win), and the
     * boot bundle already carries the saved appearance.
     */
    fun shouldPushAppearance(): Boolean = deliveredGeneration >= pageGeneration
}
