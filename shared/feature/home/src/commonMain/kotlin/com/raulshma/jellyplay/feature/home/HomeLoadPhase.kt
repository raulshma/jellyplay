package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable

/**
 * The home screen's load/refresh lifecycle as ONE mutually-exclusive phase —
 * the sealed migration of the former `HomeRefreshState` flag soup
 * (`isLoading: Boolean` + `isRefreshing: Boolean` + `error: String?`), which
 * allowed the impossible combinations this type now makes UNREPRESENTABLE
 * (convention §1/§3: impossible combinations must be unrepresentable; the
 * exemplar shape is `DetailUiLoadState`).
 *
 * What it replaced, verbatim: the three fields above (folded into
 * [HomeUiState] by the VM's refresher collector) and their SEVEN write sites,
 * all inside [HomeRefresher]:
 *
 *  1. `fetchOnce`'s success arm (`error = null`) → the [Ready] leg of
 *     [HomeLoadPhaseTransitions.succeeded].
 *  2. `fetchOnce`'s failure arm (`error = UserErrorMessages.resolve(...)`)
 *     → [HomeLoadPhaseTransitions.failed].
 *  3. `fetchOnce`'s guarded `finally` (`isLoading = false, isRefreshing =
 *     false` — only when the fetch was NOT cancelled) → the demotion leg of
 *     [HomeLoadPhaseTransitions.settled].
 *  4. `request(PullToRefresh)`'s preamble (`isRefreshing = true, error =
 *     null`) → [InlineRefreshing].
 *  5. `showFullScreenLoader` (`isLoading = true`, the going-online
 *     handshake's raise) → [Handshaking].
 *  6. `clearFullScreenLoader` (`isLoading = false`, the handshake's
 *     `finally` clear) → the demotion leg of
 *     [HomeLoadPhaseTransitions.settled].
 *  7. `stop()` (`isLoading = false, isRefreshing = false`) → the demotion
 *     leg of [HomeLoadPhaseTransitions.settled].
 *
 * plus the content-drop preambles ([Manual][RefreshTrigger.Manual],
 * [SignedOut][RefreshTrigger.SignedOut], the user-switch SWR paint, the
 * offline content drop) that moved `isLoading`/`error` through
 * `withContentDropped` — now a single [ColdLoading]/[Ready] (or untouched)
 * phase parameter on that helper.
 *
 * The invariant the sealed shape makes structural: the former cancelled-fetch
 * `finally` had to GUARD its spinner clear against the cold-launch
 * "No Content Available" flash — a cancelled fetch clearing
 * `isLoading/isRefreshing` would land between an identity transition's paint
 * and its replacement fetch, exposing `sections=empty + isLoading=false +
 * error=null`, which renders the empty state until the replacement fetch
 * lands. With the phase the same rule is expressed by NOT WRITING at all:
 * whoever cancelled the fetch owns the phase, and `Ready` + empty sections —
 * the state the flash used to manufacture — is now reachable only by an
 * explicit settling write ([HomeLoadPhaseTransitions.settled] from the
 * guarded `finally` or `stop()`), never by a cancelled one.
 *
 * Declared windows — the two places where ONE phase value forced a
 * transition-shape decision (the old machine held the spinner and the
 * failure as separate fields, so its render fold already resolved their
 * conflict; each decision below is behavior-preserving and pinned by
 * [HomeLoadPhaseTransitionsTest]):
 *
 *  1. A failure REPLACES the active spinner phase
 *     ([HomeLoadPhaseTransitions.failed]) — the old
 *     `error != null` + `isLoading/isRefreshing == true` combination already
 *     rendered as the failure (the HardError surface beat the invisible
 *     full-screen loader; the pull indicator outlived the failure record by
 *     only the fetch fan-out's tail awaits). The one visible trace of this:
 *     a pull-to-refresh indicator now drops when the failure lands instead
 *     of when the whole fetch fan-out settles.
 *  2. A success RECOVERS a [Failed] phase to [Ready]
 *     ([HomeLoadPhaseTransitions.succeeded]) while leaving live spinner
 *     phases to the guarded `finally` — the former `error = null` write
 *     cleared only the failure. When a handshake's SECOND fetch recovers a
 *     first-fetch failure (the slow-sync path), the phase settles at the
 *     success write rather than at the handshake's `finally` — a sub-second
 *     window between two writes that rendered identically (rows over painted
 *     content) except when the recovery result is empty, where the empty
 *     state replaces the (delayed) loader marginally earlier.
 *
 * Deliberately NOT phases (orthogonal axes that legitimately coexist with
 * every variant, kept as [HomeRefreshState] fields): the per-row edge-pull
 * spinners ([HomeRefreshState.refreshingSectionIds]), the dice-roll spinners
 * ([HomeRefreshState.rollingDiscoverRowIds]), the offline-mode mirror, and
 * the partial-load notice ([HomeRefreshState.partialLoadError] — a
 * non-blocking banner over a SUCCESSFUL fetch, so it belongs to no single
 * phase).
 *
 * Rendering: the render folds consume the phase exhaustively —
 * [computeHomeRenderSource] routes the implicit-offline gate on
 * [Failed], and the screen's full-screen-loader verdict
 * ([HomeUiState.isLoading]) is a `when` over the spinner variants. `sections`
 * stays a field beside the phase: [Failed] + empty sections is today's
 * hard-error screen ([HomeUiState.isHardError]), [Failed] + sections on
 * screen is the stale-content render — the sections-presence distinction the
 * old conditional-error semantics carried, mirrored exactly.
 */
@Immutable
sealed interface HomeLoadPhase {

    /**
     * Full-screen spinner over no content — the screen's very first state
     * (`HomeRefreshState`'s former `isLoading = true` default), and every
     * transition that deliberately drops the content for it: the
     * [Manual][RefreshTrigger.Manual] refresh preamble, the
     * [SignedOut][RefreshTrigger.SignedOut] identity reset, and a user switch
     * with nothing cached to paint. A [ColdLoading] whose SWR snapshot paint
     * has already re-filled `sections` renders the rows behind the (visually
     * idle) spinner — exactly the former `isLoading = true` + non-empty
     * sections combination.
     */
    data object ColdLoading : HomeLoadPhase

    /**
     * Content view, nothing spinning: a settled fetch, the user-switch SWR
     * paint (cached snapshot visible while its revalidation fetch runs
     * silently), and — deliberately — `stop()`'s settling of a backgrounded
     * cold start (empty sections + no spinner, which renders the
     * "No Content Available" empty state exactly as the former
     * `isLoading = false` clear did).
     */
    data object Ready : HomeLoadPhase

    /**
     * The pull-to-refresh spinner over live content — content kept, no
     * full-screen loader (the former `isRefreshing = true` with
     * `isLoading` untouched). Raised only by the
     * [PullToRefresh][RefreshTrigger.PullToRefresh] preamble.
     */
    data object InlineRefreshing : HomeLoadPhase

    /**
     * The going-online handshake's full-screen loader (raised by
     * [HomeRefresher.showFullScreenLoader] when the ONLINE emission lands) —
     * live content may appear underneath it mid-handshake when the capped
     * fetch's SWR snapshot paint re-fills `sections`, exactly as the former
     * `isLoading = true` behaved.
     */
    data object Handshaking : HomeLoadPhase

    /**
     * A fetch failed. `sections` presence carries the former
     * conditional-error semantics exactly: empty sections → the hard-error
     * screen ([HomeUiState.isHardError]); sections on screen → the stale
     * snapshot keeps rendering (the implicit-offline gate opens —
     * [computeHomeRenderSource] keys on this variant) with the failure only
     * surfacing in the header chip. [message] is the resolved user-facing
     * string; never rendered by the home (the header chip consumes the
     * variant alone) but carried for diagnostics and pins.
     */
    data class Failed(val message: String?) : HomeLoadPhase
}

/**
 * The phase transition rules as a PURE decision object (convention §3) —
 * the same shape as `AppUpdateDecision`/`LiveFallbackPhase`: every rule the
 * refresher applies to [HomeLoadPhase], unit-testable without the
 * refresher's scopes, mutex, or repositories ([HomeLoadPhaseTransitionsTest]
 * pins them directly; the refresher suites pin them end-to-end).
 */
internal object HomeLoadPhaseTransitions {

    /**
     * The fetch-settling rule — the ONE demotion shared by three former
     * write sites (`fetchOnce`'s guarded `finally`, `clearFullScreenLoader`,
     * and `stop()`): a spinner phase settles to [HomeLoadPhase.Ready]; a
     * [HomeLoadPhase.Failed] phase is LEFT ALONE, because the former flag
     * writes cleared only `isLoading`/`isRefreshing` and never touched
     * `error` — the failure must outlive the spinner that was running when
     * it landed (the handshake's loader clearing over a failed fetch still
     * shows the hard error; `stop()` over a failed fetch keeps it for the
     * next foreground).
     */
    fun settled(phase: HomeLoadPhase): HomeLoadPhase = when (phase) {
        HomeLoadPhase.ColdLoading,
        HomeLoadPhase.Handshaking,
        HomeLoadPhase.InlineRefreshing,
        -> HomeLoadPhase.Ready
        is HomeLoadPhase.Failed -> phase
        HomeLoadPhase.Ready -> phase
    }

    /**
     * The success rule — the former success arm's `error = null`: the ONLY
     * thing it did was clear a recorded failure (the spinner clear belonged
     * to the guarded `finally`, rule [settled]). A [HomeLoadPhase.Failed]
     * phase recovers to [HomeLoadPhase.Ready] the moment a fetch succeeds
     * over it; every other phase keeps spinning or stays settled — the
     * success arm must not demote a handshake's loader early (the guarded
     * `finally` owns that, as it always did).
     */
    fun succeeded(phase: HomeLoadPhase): HomeLoadPhase = when (phase) {
        is HomeLoadPhase.Failed -> HomeLoadPhase.Ready
        HomeLoadPhase.ColdLoading,
        HomeLoadPhase.Ready,
        HomeLoadPhase.InlineRefreshing,
        HomeLoadPhase.Handshaking,
        -> phase
    }

    /**
     * The failure rule — the former failure arm's `error =
     * UserErrorMessages.resolve(...)`: the failure REPLACES whatever phase
     * is active, spinner included. A failure landing mid-[HomeLoadPhase.Handshaking]
     * therefore drops the handshake's loader into the failure state — the
     * former `error != null` + `isLoading == true` combination rendered the
     * hard-error screen already (the surface fold's HardError arm wins, the
     * loader was invisible beneath it), so this is behavior-preserving, not
     * a change. Declared decision forced by the phase shape: one value
     * cannot hold "spinner up" AND "failure recorded"; the old machine held
     * them as separate fields and the render fold already resolved the
     * conflict in the failure's favor.
     */
    fun failed(message: String?): HomeLoadPhase = HomeLoadPhase.Failed(message)

    /**
     * The pull-to-refresh preamble rule — the R3 fix from the 2026-11-17
     * regression analysis: a PTR landing while a FULL-SCREEN loader is up
     * ([HomeLoadPhase.ColdLoading] / [HomeLoadPhase.Handshaking]) must KEEP
     * the loader. The phase is a single value, so the former unconditional
     * `loadPhase = InlineRefreshing` write DEMOTED the loader mid-flight —
     * `HomeUiState.isLoading` flipped false while the refresh was still
     * running, despite the write site's comment claiming "the full-screen
     * loader untouched". Reachable whenever PTR fires over a
     * SWR-painted loading screen; the inline spinner's contract (content
     * kept, no full-screen loader — see the phase's KDoc) applies only to
     * the content-visible phases. [HomeLoadPhase.Failed] still demotes: a
     * user-initiated refresh clears the recorded failure, as the former
     * unconditional write did.
     */
    fun onPullToRefresh(phase: HomeLoadPhase): HomeLoadPhase = when (phase) {
        HomeLoadPhase.ColdLoading,
        HomeLoadPhase.Handshaking,
        -> phase
        HomeLoadPhase.Ready,
        HomeLoadPhase.InlineRefreshing,
        is HomeLoadPhase.Failed,
        -> HomeLoadPhase.InlineRefreshing
    }
}
