package com.raulshma.jellyplay.feature.book.epub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import okio.Path

/**
 * The boot-transfer ladder BOTH platform hosts used to hand-copy — the
 * delivery-receipt wiring, the encode effect, the chunked send, the payload
 * release and the post-boot appearance push — owned here once, generation-
 * gated exactly as [BookDeliveryTracker] decides. The hosts keep only their
 * genuinely divergent halves, passed in as parameters (declared
 * divergences):
 *  - [pageLoaded]: Android's LATCHED onPageFinished flag vs desktop's LIVE
 *    LoadingState read (a reload transiently reports Loading — see
 *    [BookDeliveryTracker.shouldSend]);
 *  - [eval]: the platform evaluate(Java)Script seam (WebView vs CEF);
 *  - [encodeBook]: the encode stays host-side — EpubBookCodec lives in
 *    jvmShared, and the host owns the IO hop;
 *  - [onFailBoot]: the failure channel. An encode failure never resolves
 *    into a payload, so instead of waiting on `bookBase64` forever (or, as
 *    Android's pre-fold code did, crashing the composition effect on an
 *    uncaught IOException) the boot fails into the error veil — see
 *    [EpubEventListener.failBoot] for why that must ride the RAW seam.
 *
 * Returns the receipt-wrapped listener ([EpubEventListener.withStatusReceipt])
 * the host's event receiver feeds through: any status event is a delivery
 * receipt for the current page generation; every other event rides the wrap
 * untouched.
 */
@Composable
internal fun BookTransferEffects(
    bookFile: Path,
    resumePercent: Double,
    appearance: EpubAppearance,
    onEvent: EpubEventListener,
    delivery: BookDeliveryTracker,
    pageLoaded: Boolean,
    eval: (String) -> Unit,
    encodeBook: suspend (Path) -> String,
    onFailBoot: () -> Unit,
): EpubEventListener {
    val receivingEvents = remember(onEvent) {
        onEvent.withStatusReceipt { delivery.confirmDelivery() }
    }
    // Effect-time reads: the lambdas below arrive as fresh instances every
    // recomposition, and the effects must not re-launch (re-send the whole
    // book) just because a capture went stale.
    val encodeNow = rememberUpdatedState(encodeBook)
    val evalNow = rememberUpdatedState(eval)
    val resumeNow = rememberUpdatedState(resumePercent)
    val failBootNow = rememberUpdatedState(onFailBoot)

    LaunchedEffect(bookFile, delivery.pageGeneration, delivery.deliveredGeneration) {
        encodeBookIfDue(bookFile, delivery, encodeNow.value) { failBootNow.value() }
    }

    // The book rides the chunked loadBookBegin/loadBookChunk/loadBookEnd
    // protocol once the page's JS is reachable (see BOOK_CHUNK_CHARS). Re-runs
    // per page generation until the JS side confirms; loadBookBegin resets
    // reader.js's assembly state, so a resend is idempotent.
    LaunchedEffect(pageLoaded, delivery.pageGeneration, delivery.bookBase64) {
        sendBookIfDue(delivery, resumeNow.value, appearance, pageLoaded, evalNow.value)
    }

    // The transfer confirmed — the composition no longer needs to pin the
    // whole base64 payload (a later page reload re-encodes via the effect
    // above).
    LaunchedEffect(delivery.deliveredGeneration) {
        if (delivery.shouldReleaseEncodedBook()) delivery.releaseEncodedBook()
    }

    // Appearance push for CHANGES after load — see pushAppearanceScripts.
    // Gated on the CURRENT page generation actually holding the book: a push
    // before loadBookBegin would be a silent no-op (reader.js defaults would
    // win), and the boot bundle already carries the saved appearance.
    LaunchedEffect(delivery.pageGeneration, delivery.deliveredGeneration, appearance) {
        if (!delivery.shouldPushAppearance()) return@LaunchedEffect
        pushAppearanceScripts(appearance, evalNow.value)
    }

    return receivingEvents
}

/**
 * The encode step, free of Compose so jvmTest can pin the failure fold: gate
 * on [BookDeliveryTracker.shouldEncode] (the live page already holding the
 * book is the encoder's idempotency guard), then either pin the payload or
 * fail the boot — a failed encode never resolves into a payload. Only
 * non-cancellation failures fold into [onFailBoot] (cancellation rethrows —
 * the effect is leaving composition, not failing).
 */
internal suspend fun encodeBookIfDue(
    bookFile: Path,
    delivery: BookDeliveryTracker,
    encodeBook: suspend (Path) -> String,
    onFailBoot: () -> Unit,
) {
    if (!delivery.shouldEncode()) return
    runCatchingRethrowingCancellation { encodeBook(bookFile) }
        .onSuccess { delivery.storeEncodedBook(it) }
        .onFailure { onFailBoot() }
}

/**
 * The send step, free of Compose for the same reason: nothing leaves for a
 * page whose JS is not reachable ([pageFinished]) or a generation the JS side
 * has already confirmed — otherwise the pinned payload goes out through the
 * chunked protocol.
 */
internal suspend fun sendBookIfDue(
    delivery: BookDeliveryTracker,
    resumePercent: Double,
    appearance: EpubAppearance,
    pageFinished: Boolean,
    eval: (String) -> Unit,
) {
    val base64 = delivery.bookBase64 ?: return
    if (!delivery.shouldSend(pageFinished = pageFinished)) return
    sendBookChunks(base64, resumePercent, appearance, eval)
}

/**
 * Fail the boot into the error veil: an ERROR status through the RAW event
 * seam — NEVER the receipt-wrapped listener, because a delivery receipt for
 * a page generation that never received the book would bar the retry's
 * encode via [BookDeliveryTracker.shouldEncode]. The status latches the
 * reader's error UI (ReflowableReaderSession's Status arm); nothing is
 * latched on the host side, so reopening the reader retries the boot from a
 * fresh session.
 */
internal fun EpubEventListener.failBoot() {
    onEvent(EpubEvent.Status(EpubReaderStatus.ERROR))
}
