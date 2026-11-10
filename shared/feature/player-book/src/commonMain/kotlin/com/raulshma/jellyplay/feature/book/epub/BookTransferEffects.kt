package com.raulshma.jellyplay.feature.book.epub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import okio.Path

/**
 * The boot-transfer ladder BOTH platform hosts share — the delivery-receipt
 * wiring, the streamed chunk transfer (with the failure fold), and the
 * post-boot appearance push — owned here once, generation-gated exactly as
 * [BookDeliveryTracker] decides. The hosts keep only their genuinely
 * divergent halves, passed in as parameters (declared divergences):
 *  - [pageLoaded]: Android's LATCHED onPageFinished flag vs desktop's LIVE
 *    LoadingState read (a reload transiently reports Loading — see
 *    [BookDeliveryTracker.shouldSend]);
 *  - [eval]: the platform evaluate(Java)Script seam (WebView vs CEF);
 *  - [openTransfer]: the transfer source stays host-side — EpubBookCodec
 *    lives in jvmShared, and the host owns the open's IO hop;
 *  - [onFailBoot]: the failure channel. A failed open or a failed
 *    mid-stream read never resolves into a full transfer, so instead of
 *    leaving the reader on its boot veil forever the boot fails into the
 *    error veil — see [EpubEventListener.failBoot] for why that must ride
 *    the RAW seam.
 *
 * The book itself streams from disk: [openTransfer] hands back a
 * [EpubBookTransfer] that reads one binary chunk at a time and hands each
 * out as standalone base64, so no whole-book bytes or whole-book base64
 * string are ever alive at once — each chunk is dead once its
 * evaluateJavascript completes (the per-chunk form of the old
 * release-after-receipt contract).
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
    openTransfer: suspend (Path) -> EpubBookTransfer,
    onFailBoot: () -> Unit,
): EpubEventListener {
    val receivingEvents = remember(onEvent) {
        onEvent.withStatusReceipt { delivery.confirmDelivery() }
    }
    // Effect-time reads: the lambdas below arrive as fresh instances every
    // recomposition, and the effects must not re-launch (re-send the whole
    // book) just because a capture went stale.
    val openNow = rememberUpdatedState(openTransfer)
    val evalNow = rememberUpdatedState(eval)
    val resumeNow = rememberUpdatedState(resumePercent)
    val failBootNow = rememberUpdatedState(onFailBoot)

    // The book streams over the loadBookBegin/loadBookChunk/loadBookEnd
    // protocol once the page's JS is reachable. Re-runs per page generation
    // until the JS side confirms; loadBookBegin resets reader.js's assembly
    // state, so a resend is idempotent.
    LaunchedEffect(pageLoaded, delivery.pageGeneration, delivery.deliveredGeneration) {
        transferBookIfDue(
            bookFile,
            delivery,
            resumeNow.value,
            appearance,
            pageLoaded,
            evalNow.value,
            openNow.value,
        ) { failBootNow.value() }
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
 * The transfer step, free of Compose so jvmTest can pin the failure fold:
 * gate on [BookDeliveryTracker.shouldSend] (the live page already holding
 * the book is the transfer's idempotency guard), then stream the book
 * through the eval seam. Only non-cancellation failures fold into
 * [onFailBoot] (cancellation rethrows — the effect is leaving composition
 * or re-running for a fresh generation, not failing), and a failure first
 * drops reader.js's partial assembly so no half-book stays pinned in the
 * page awaiting an end frame that will never come.
 */
internal suspend fun transferBookIfDue(
    bookFile: Path,
    delivery: BookDeliveryTracker,
    resumePercent: Double,
    appearance: EpubAppearance,
    pageFinished: Boolean,
    eval: (String) -> Unit,
    openTransfer: suspend (Path) -> EpubBookTransfer,
    onFailBoot: () -> Unit,
) {
    if (!delivery.shouldSend(pageFinished = pageFinished)) return
    runCatchingRethrowingCancellation {
        openTransfer(bookFile).use { transfer ->
            streamBookChunks(transfer, resumePercent, appearance, eval)
        }
    }.onFailure {
        evalAbortScriptBestEffort(eval)
        onFailBoot()
    }
}

/**
 * Best-effort abort-script eval, extracted from [transferBookIfDue]'s suspend
 * body: [eval] is a plain JS-bridge call with no suspension point, so the
 * stdlib `runCatching` here cannot swallow a cancellation — keeping it in a
 * non-suspend fun keeps BareRunCatchingRatchetTest's census honest (the
 * baseline counts only sites that can actually mask structured cancellation).
 */
private fun evalAbortScriptBestEffort(eval: (String) -> Unit) {
    runCatching { eval(buildLoadBookAbortScript()) }
}

/**
 * Fail the boot into the error veil: an ERROR status through the RAW event
 * seam — NEVER the receipt-wrapped listener, because a delivery receipt for
 * a page generation that never received the book would bar the retry's
 * transfer via [BookDeliveryTracker.shouldSend]. The status latches the
 * reader's error UI (ReflowableReaderSession's Status arm); nothing is
 * latched on the host side, so reopening the reader retries the boot from a
 * fresh session.
 */
internal fun EpubEventListener.failBoot() {
    onEvent(EpubEvent.Status(EpubReaderStatus.ERROR))
}
