package com.raulshma.jellyplay.feature.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationColor
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationSpec
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationStyle
import com.raulshma.jellyplay.feature.book.epub.EpubEvent
import com.raulshma.jellyplay.feature.book.epub.EpubEventListener
import com.raulshma.jellyplay.feature.book.epub.EpubReaderHandle
import com.raulshma.jellyplay.feature.book.epub.EpubReaderStatus
import com.raulshma.jellyplay.feature.book.epub.EpubSearchResult
import com.raulshma.jellyplay.feature.book.epub.EpubTapZone
import com.raulshma.jellyplay.feature.book.epub.EpubTocItem

/**
 * The reflowable reader's Compose-free behaviour half — the modules
 * [ReflowableReaderContent] used to inline as untestable composable state
 * (the tap/zone DECISION half lives in ReaderInput.kt's `readerNavDecision`,
 * beside the input surface that funnels through it). [ReflowableReaderSession]
 * owns the choreography over the three controllers below it; each controller
 * takes its host late (`() -> Handle?`), because the composition creates the
 * host after the session (and the session after the event listener).
 */

/**
 * The ViewModel's narrow window onto the screen-owned reflowable session —
 * the seam that replaced the one-shot `ReaderHostCommand` channel. The
 * speech chapter continuation used to ping-pong controller → VM flow →
 * screen collector → host → JS → event → VM → controller (eight hops,
 * existing only because the host was screen-owned while the loop was
 * VM-owned); with [host] in hand the speech controller runs the continuation
 * DIRECTLY, and the two halves that are choreography rather than raw host
 * calls (the paragraph-follow paint, the sleep timer's auto-scroll stop)
 * stay plain synchronous methods on this seam. Implemented by
 * [ReflowableReaderSession]; attached through
 * [BookReaderViewModel.attachReaderSession] when the reader composes.
 */
internal interface ReaderSessionPort {

    /** The session's late-bound host handle, or null before/after the reader composition. */
    val host: EpubReaderHandle?

    /** Follow the spoken paragraph: precedence-guarded ephemeral paint + `goToCfi`. */
    fun followSpeech(cfi: String)

    /** The sleep timer fired: the VM stopped read-aloud, the scroller stops too. */
    fun sleepTimerFired()
}

/**
 * The reflowable reader's screen-side session — the Compose-free half of
 * [ReflowableReaderContent], which becomes its render shell. Owns: the
 * late-bound host handle ([attachHost]), the three screen-side controllers'
 * creation and wiring ([annotationSync], [autoScroll], [searchSession] — all
 * late-bound to the ONE handle), the exact-resume latch ([resumeJump]), the
 * JS-tap routing input ([onJsTap]), and the search / speech-follow /
 * auto-scroll choreography the composable used to inline. The composable
 * attaches the platform host, keys the sync effects and dispatches sheet
 * callbacks into the session's methods — everything that DECIDES lives
 * here, constructible in a jvmTest through constructor lambdas (the module's
 * controller convention — cf. [ReaderAnnotationSync]).
 *
 * The ViewModel holds this session through [ReaderSessionPort] (attached
 * when the reader composes; the session nulls its own host on dispose, so a
 * detached screen degrades every port call to a no-op — exactly the old
 * channel-without-collector semantics).
 */
internal class ReflowableReaderSession(
    /** Reading direction, read at event time (a captured param value would go stale mid-session). */
    private val direction: () -> ReadingDirection,
    /** The sheet stack's `open` fold — a tap under a sheet is noise ([readerNavDecision]'s guard). */
    private val sheetOpen: () -> Boolean,
    /** A live text selection is active — a selection must not page (the belt behind reader.js's own gesture skip). */
    private val selectionActive: () -> Boolean,
    /** Persisted auto-scroll speed (px/s), read at command time from the live preference snapshot. */
    private val autoScrollSpeed: () -> Int,
    /** The CENTER tap decision's action (chrome toggle — a VM call, not a host call). */
    private val onToggleControls: () -> Unit,
    /** The VM-bound event kinds ride [BookReaderViewModel.onEpubEvent]'s funnel. */
    private val forward: (EpubEvent) -> Unit,
) : ReaderSessionPort {

    /**
     * Boot status veil state (fed by `reader.js` through [onEvent]).
     * `mutableStateOf`-backed like [ReaderSheetStack]'s flags so the render
     * shell recomposes on change while staying constructible without Compose.
     */
    var status by mutableStateOf(EpubReaderStatus.LOADING)
        private set

    /** The TOC mirror the sheet + tick rail render (the VM caches its own copy through [forward]). */
    var tocItems by mutableStateOf<List<EpubTocItem>>(emptyList())
        private set

    /**
     * The late-bound host handle — null until the composition attaches the
     * platform host ([attachHost]) and nulled again on dispose. Taps and
     * commands can only arrive after the page loads, so it is never null in
     * practice; the null window exists so a disposed screen's port calls
     * degrade to no-ops instead of touching a dead WebView.
     */
    override var host: EpubReaderHandle? = null
        private set

    /** The three screen-side controllers, all late-bound to [host]. */
    val annotationSync = ReaderAnnotationSync { host }
    val autoScroll = ReaderAutoScroll(host = { host }, speed = autoScrollSpeed)
    val searchSession = ReaderSearchSession()

    /** The exact-resume latch: the READY jump happens exactly once per session. */
    private var resumed = false

    /**
     * One seam, one dispatch: the hosts invoke this listener once per parsed
     * event; screen-owned kinds (boot status, the TOC mirror, search, taps,
     * swipes, auto-scroll stops) fold here and everything else rides the
     * [forward] funnel — the same split the old in-composable listener had,
     * testable without Compose. State writes are thread-safe (the bridge
     * threads invoke the seam off the main thread).
     */
    val onEvent: EpubEventListener = EpubEventListener { event ->
        when (event) {
            is EpubEvent.Percent -> forward(event)
            is EpubEvent.Status -> status = event.status
            is EpubEvent.Direction -> forward(event)
            is EpubEvent.Toc -> {
                tocItems = event.items
                forward(event)
            }
            is EpubEvent.Relocated -> forward(event)
            is EpubEvent.Tap -> onJsTap(event.zone)
            is EpubEvent.Swipe ->
                // Physical swipe → the zone a tap on that side would produce
                // (swipe left ≡ tap right), so navigation rides the exact
                // same direction-aware mapping as taps.
                onJsTap(if (event.toLeft) EpubTapZone.RIGHT else EpubTapZone.LEFT)
            is EpubEvent.Selected -> forward(event)
            EpubEvent.SelectionCleared -> forward(event)
            is EpubEvent.SearchResults -> searchSession.onResults(event.token, event.results)
            is EpubEvent.SpeechContext -> forward(event)
            EpubEvent.AutoScrollStopped -> autoScroll.onHostStopped()
            is EpubEvent.DisplayError -> Unit
        }
    }

    /** The composition binds the platform host here (and unbinds it on dispose). */
    fun attachHost(handle: EpubReaderHandle?) {
        host = handle
    }

    /**
     * JS-reported taps (and the synthesized swipe zones) drive touch
     * navigation with the same direction-aware mapping as the keys
     * (LEFT pages forward under RTL). The decision itself is ReaderInput.kt's
     * [readerNavDecision] with the guards folded in front; this is its input
     * seam: sheet-open and live-selection taps are pure noise, page turns
     * drop the search flash first, CENTER toggles the chrome.
     */
    fun onJsTap(zone: EpubTapZone) {
        when (readerNavDecision(zone, direction(), sheetOpen(), selectionActive())) {
            ReaderNavDecision.FORWARD -> {
                annotationSync.clearEphemeral(EphemeralHighlight.SEARCH)
                host?.next()
            }
            ReaderNavDecision.BACKWARD -> {
                annotationSync.clearEphemeral(EphemeralHighlight.SEARCH)
                host?.prev()
            }
            ReaderNavDecision.TOGGLE_CONTROLS -> onToggleControls()
            ReaderNavDecision.IGNORE -> Unit
        }
    }

    /**
     * Exact resume (ADR 0003 point 4): once READY, jump to the locally
     * stored CFI — exactly once. A deep-link destination (detail "Contents"
     * tap) outranks the resume anchor; a null pair consumes the latch with
     * no jump (the percent resume stands). A failed display degrades
     * in-place (reader.js keeps the current page and reports displayError).
     * The render shell keys its effect on (status, cfi, href); the latch and
     * the status read are the session's own.
     */
    fun resumeJump(resumeCfi: String?, jumpHref: String?) {
        if (status != EpubReaderStatus.READY || resumed) return
        resumed = true
        when {
            jumpHref != null -> host?.goTo(jumpHref)
            else -> resumeCfi?.let { host?.goToCfi(it) }
        }
    }

    /**
     * Persisted marks → host paint (full apply on READY entry, incremental
     * diffs after — [ReaderAnnotationSync]). The render shell keys its
     * effect on (status, annotations); this reads the session's own status.
     */
    fun syncAnnotations(persisted: List<ReaderAnnotation>) {
        annotationSync.sync(status, persisted)
    }

    /**
     * Scroll-mode flip: reader.js's setFlow rebuilds the rendition and
     * re-displays the same position (no-ops when the flow already matches).
     * Leaving scrolled flow kills auto-scroll (its rAF scroller is gone);
     * the controller mirrors the state so the chrome toggle resets with it.
     */
    fun onFlowChanged(scrolled: Boolean) {
        host?.setFlow(scrolled)
        if (!scrolled) autoScroll.stopForFlowExit()
    }

    /** Session end (stop/finish/engine loss) drops the painted speech highlight. */
    fun onSpeechActiveChanged(active: Boolean) {
        if (!active) annotationSync.clearEphemeral(EphemeralHighlight.SPEECH)
    }

    /**
     * A query that survived the sheet's debounce: leave the previous search
     * context, then stamp the token (late results drop — [ReaderSearchSession])
     * and hand the scan to the host.
     */
    fun search(query: String) {
        searchSession.launchSearch(query) { q, token ->
            annotationSync.clearEphemeral(EphemeralHighlight.SEARCH)
            host?.search(q, token)
        }
    }

    /**
     * A search-result tap: drop the previous flash, jump, and paint the
     * ephemeral flash (the controller skips it when a persisted mark already
     * paints this CFI — the precedence invariant).
     */
    fun openSearchResult(result: EpubSearchResult) {
        annotationSync.clearEphemeral(EphemeralHighlight.SEARCH)
        host?.goToCfi(result.cfi)
        annotationSync.paintEphemeral(EphemeralHighlight.SEARCH, result.cfi)
    }

    /** Leaving (or re-opening) the search sheet: drop the flash, reset the session. */
    fun resetSearch() {
        annotationSync.clearEphemeral(EphemeralHighlight.SEARCH)
        searchSession.reset()
    }

    /** [ReaderSessionPort.followSpeech] — the speech loop's paragraph follow. */
    override fun followSpeech(cfi: String) {
        annotationSync.followSpeech(cfi)
    }

    /** [ReaderSessionPort.sleepTimerFired] — the VM stopped speech; the scroller stops too. */
    override fun sleepTimerFired() {
        autoScroll.stopForSleepTimer()
    }
}

/** Which ephemeral (non-persisted) highlight a paint belongs to. */
internal enum class EphemeralHighlight { SPEECH, SEARCH }

/**
 * The incremental annotation-sync engine AND the one owner of the
 * ephemeral-highlight precedence rule: an ephemeral highlight is never
 * painted over a persisted mark — `removeAnnotation` is CFI-keyed, so a
 * later mark removal would wipe the real mark's paint. The rule was written
 * three times in the composable (speech follow, search flash, the sync
 * loop); it lives exactly once here.
 *
 * Sync semantics: the first READY applies the full persisted set (replace is
 * safe pre-interaction); every later list change syncs INCREMENTALLY
 * (add/remove per CFI) so the ephemeral search flash and any selection in
 * progress survive unrelated edits. Leaving READY resets the full-apply
 * latch, so a re-entry re-applies the whole set.
 */
internal class ReaderAnnotationSync(
    private val host: () -> EpubReaderHandle?,
) {

    private var syncedReady = false
    private var applied: Map<String, EpubAnnotationSpec> = emptyMap()
    private val ephemeral = mutableMapOf<EphemeralHighlight, String>()

    /** Drives the full/incremental sync from the host status + marks flow. */
    fun sync(status: EpubReaderStatus, persisted: List<ReaderAnnotation>) {
        if (status != EpubReaderStatus.READY) {
            syncedReady = false
            return
        }
        val handle = host() ?: return
        val target = persisted.associate { it.cfi to it.toEpubSpec() }
        if (!syncedReady) {
            handle.applyAnnotations(target.values.toList())
            syncedReady = true
        } else {
            if (target == applied) return
            (applied.keys - target.keys).forEach(handle::removeAnnotation)
            target.forEach { (cfi, spec) ->
                if (applied[cfi] != spec) handle.addAnnotation(spec)
            }
        }
        applied = target
    }

    /**
     * Paints an ephemeral [kind] highlight at [cfi] — skipped when a
     * persisted mark occupies the CFI (the precedence invariant). Replaces
     * the same kind's previous paint; returns whether anything was painted.
     */
    fun paintEphemeral(kind: EphemeralHighlight, cfi: String): Boolean {
        if (applied.containsKey(cfi)) return false
        val handle = host() ?: return false
        ephemeral[kind]?.takeIf { it != cfi }?.let(handle::removeAnnotation)
        handle.addAnnotation(EpubAnnotationSpec(cfi, EpubAnnotationStyle.HIGHLIGHT, EpubAnnotationColor.YELLOW))
        ephemeral[kind] = cfi
        return true
    }

    /** Drops the ephemeral paint for [kind] (leaving search / speech end). */
    fun clearEphemeral(kind: EphemeralHighlight) {
        val cfi = ephemeral.remove(kind) ?: return
        host()?.removeAnnotation(cfi)
    }

    /**
     * The speech follow: repaint the ephemeral paragraph highlight (under
     * the precedence invariant) and goToCfi (epub.js no-ops when already
     * visible).
     */
    fun followSpeech(cfi: String) {
        val handle = host() ?: return
        clearEphemeral(EphemeralHighlight.SPEECH)
        paintEphemeral(EphemeralHighlight.SPEECH, cfi)
        handle.goToCfi(cfi)
    }
}

/**
 * The scrolled-flow auto-scroll session: one active flag + the host command,
 * with the stop reasons named (flow exit, sleep timer, host-reported manual
 * stop) instead of three inline branches. reader.js stops on wheel/touch and
 * reports [onHostStopped], so the toggle re-arms cleanly after every manual
 * interruption.
 */
internal class ReaderAutoScroll(
    private val host: () -> EpubReaderHandle?,
    private val speed: () -> Int,
) {

    var active: Boolean by mutableStateOf(false)
        private set

    fun toggle() {
        active = !active
        host()?.setAutoScroll(active, speed())
    }

    /** Live re-target after a speed commit: an active rAF loop picks it up. */
    fun retarget() {
        if (active) host()?.setAutoScroll(true, speed())
    }

    /** Leaving scrolled flow kills the scroller (its rAF loop is gone). */
    fun stopForFlowExit() {
        if (!active) return
        active = false
        host()?.setAutoScroll(false, speed())
    }

    /** The sleep timer fired — the VM stopped read-aloud, the scroll stops too. */
    fun stopForSleepTimer() = stopForFlowExit()

    /** reader.js reported a manual stop (wheel/touchstart). */
    fun onHostStopped() {
        active = false
    }
}

/**
 * The in-book search SESSION — one owner of the late-result-drop token and
 * the result state machine. The token previously lived in three places kept
 * in step by hand across a composable boundary (the sheet's local counter,
 * the parent's `searchToken` mirror, reader.js's own guard): two Kotlin
 * counters that could drift independently of the host. Now the sheet debounces
 * and reports the surviving query; THIS class stamps the token, flips to
 * Searching, and drops any result whose token isn't the newest (the
 * host-side guard in reader.js stays as a second belt).
 */
internal class ReaderSearchSession {
    var state by mutableStateOf<ReaderSearchState>(ReaderSearchState.Idle)
        private set

    private var token = 0

    /**
     * A query that survived the sheet's debounce: stamp a fresh token, flip
     * to [ReaderSearchState.Searching], and hand the scan to [scan] with the
     * stamped token (`host.search(query, token)`).
     */
    fun launchSearch(query: String, scan: (query: String, token: Int) -> Unit) {
        token += 1
        state = ReaderSearchState.Searching
        scan(query, token)
    }

    /** Host results arrive with their scan's token; older tokens drop. */
    fun onResults(arrivedToken: Int, rows: List<EpubSearchResult>) {
        if (arrivedToken == token) state = ReaderSearchState.Results(rows)
    }

    /** Back to idle (dismiss, or re-open from the TOC's search entry). */
    fun reset() {
        state = ReaderSearchState.Idle
    }
}
