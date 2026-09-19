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
 * decodeAndOpen — i.e. after loadBookEnd assembled OUR bytes — so any status
 * event routed through `withStatusReceipt` is a delivery receipt:
 * [confirmDelivery] latches [deliveredGeneration] to the current generation,
 * which both releases the pinned payload and opens the appearance push. A
 * page that finishes loading without a receipt (recreation before/after the
 * send) re-encodes and re-sends — no fire-once latch that a reload can
 * strand.
 *
 * The state is snapshot-backed so hosts key LaunchedEffects on the
 * generations; [confirmDelivery] may fire off the main thread (desktop's
 * event poll), which snapshot writes tolerate. The encode itself
 * (EpubBookCodec on IO) stays host-side — it lives in jvmShared.
 */
internal class BookDeliveryTracker {

    /** 0 until the first page load; one per finished page load after that. */
    var pageGeneration: Int by mutableStateOf(0)
        private set

    /** [Int.MIN_VALUE] until the first receipt; then the generation it vouches for. */
    var deliveredGeneration: Int by mutableStateOf(Int.MIN_VALUE)
        private set

    /** The encoded payload between the encode and send gates; `null` when unpinned. */
    var bookBase64: String? by mutableStateOf(null)
        private set

    /** A finished page load begins (or re-runs) the ladder for a fresh generation. */
    fun onPageLoadFinished() {
        pageGeneration++
    }

    /** The delivery receipt: the CURRENT page generation now holds the book. */
    fun confirmDelivery() {
        deliveredGeneration = pageGeneration
    }

    /** Pin the freshly encoded payload for the send gate. */
    fun storeEncodedBook(base64: String) {
        bookBase64 = base64
    }

    /** Drop the payload so the composition stops pinning the whole base64 string. */
    fun releaseEncodedBook() {
        bookBase64 = null
    }

    /**
     * Encode only with nothing pinned and only for a page that does not
     * already hold the book — the live page's delivery is the encoder's
     * idempotency guard.
     */
    fun shouldEncode(): Boolean = bookBase64 == null && pageGeneration > deliveredGeneration

    /**
     * Send only a pinned payload, to a page whose JS is reachable
     * ([pageFinished]: Android's latched onPageFinished flag, desktop's LIVE
     * LoadingState — a reload transiently reports Loading), for a generation
     * the JS side has not confirmed yet.
     */
    fun shouldSend(pageFinished: Boolean): Boolean =
        bookBase64 != null && pageFinished && pageGeneration > deliveredGeneration

    /**
     * Generations count from 0 and the sentinel is negative, so this is
     * exactly "a receipt exists" — not merely "the sentinel moved".
     */
    fun shouldReleaseEncodedBook(): Boolean = deliveredGeneration >= 0

    /**
     * Push only into a page that actually holds the book: a push before
     * loadBookBegin is a silent no-op (reader.js defaults would win), and the
     * boot bundle already carried the saved appearance.
     */
    fun shouldPushAppearance(): Boolean = deliveredGeneration >= pageGeneration
}
