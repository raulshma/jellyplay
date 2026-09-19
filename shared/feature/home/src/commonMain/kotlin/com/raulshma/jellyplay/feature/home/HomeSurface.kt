package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeMode

/**
 * WHICH surface the home renders, folded ONCE from [HomeUiState] + the
 * [OfflineHomeContent] aggregate by [homeSurface] — the pure successor of the
 * three differently-derived branch conditions that used to open
 * `MainHomeContent`'s render `when` (error+sections+renderSource vs
 * offlineMode+offlineSections vs homeMode+offlineMode).
 *
 * Every predicate reads [HomeUiState.renderSource] — never the
 * [HomeUiState.offlineMode] mirror, which lags a hop behind and re-derived
 * per branch was the source of the banner-flash bug class. The equivalence
 * the fold relies on (pinned both directions by `HomeRenderSourceTest`):
 * `offlineMode != ONLINE` ⟺ `renderSource == Offline.Explicit`, because
 * [computeHomeRenderSource] resolves any non-online mode to Explicit before
 * any other branch can win.
 *
 * Precedence is fixed: HardError → NoDownloads → Music → Content.
 */
internal sealed interface HomeSurface {
    /**
     * The online fetch failed with nothing to fall back on (failed fetch over
     * a CONFIRMED-empty offline library — renderSource folded back to
     * [HomeRenderSource.Online]). Retry is the only affordance. Never fires
     * while [HomeRenderSource.FallbackPending]: downloads may yet exist, and
     * that window renders [Content] with the loading feed.
     */
    data object HardError : HomeSurface

    /**
     * An explicit offline mode is active and nothing is downloaded for this
     * home mode — the go-online empty state. Only Explicit: the implicit
     * fallback never shows it (the gate proved downloads exist, or
     * renderSource folded back to Online/HardError).
     */
    data class NoDownloads(val isGoingOnline: Boolean) : HomeSurface

    /**
     * Online MUSIC mode delegates to the host's music-home slot. Fires for
     * every render source except [HomeRenderSource.Offline.Explicit] — the
     * music module keeps the slot during the implicit fallback; explicit
     * offline renders the (music-filtered) offline content list instead.
     */
    data object Music : HomeSurface

    /**
     * The content list. The feed (server or offline, with the offline-only
     * surfaces suppressed and the FallbackPending window folded to a loading
     * offline feed) plus the winning render source carried WHOLE — the
     * screen's hero, banner and quick-action facts are `is Offline` /
     * `== Explicit` / `== Implicit` reads of this one value, so a new
     * offline flavour adds no field here. [bookProgressFractions] is the
     * same fold for the CR progress bars: whichever map belongs to the
     * carried feed (the refresher's online decodes for an online feed, the
     * gate's offline decodes for an offline one — FallbackPending included,
     * its feed is offline), so no render site re-derives the pick.
     */
    data class Content(
        val feed: HomeFeed,
        val renderSource: HomeRenderSource,
        val bookProgressFractions: Map<String, Float>,
    ) : HomeSurface
}

/**
 * THE fold — pure over [HomeUiState] + the offline aggregate, so the whole
 * branch policy is assertable JVM-side ([HomeSurfaceTest]) without the VM's
 * collaborators. The screen's `when` is exhaustive over the result and owns
 * rendering only.
 */
internal fun homeSurface(
    state: HomeUiState,
    offlineContent: OfflineHomeContent,
): HomeSurface = when {
    state.error != null && state.sections.isEmpty() &&
        state.renderSource == HomeRenderSource.Online -> HomeSurface.HardError

    state.renderSource == HomeRenderSource.Offline.Explicit &&
        offlineContent.sections.isEmpty() -> HomeSurface.NoDownloads(state.isGoingOnline)

    state.homeMode == HomeMode.MUSIC &&
        state.renderSource != HomeRenderSource.Offline.Explicit -> HomeSurface.Music

    else -> {
        val feed = when (state.renderSource) {
            HomeRenderSource.Online -> HomeFeed.Online(
                sections = state.sections,
                isLoading = state.isLoading,
                partialLoadError = state.partialLoadError,
                newsletterBannerVisible = state.newsletterBannerVisible,
            )
            HomeRenderSource.FallbackPending -> HomeFeed.Offline(offlineContent, isLoading = true)
            is HomeRenderSource.Offline -> HomeFeed.Offline(offlineContent)
        }
        HomeSurface.Content(
            feed = feed,
            renderSource = state.renderSource,
            // The fractions map keyed on the feed, not the mode: the
            // FallbackPending window renders the offline feed, so it reads
            // the gate's decodes even while the offline-mode mirror still
            // says ONLINE.
            bookProgressFractions = if (feed is HomeFeed.Offline) {
                state.offlineBookProgressFractions
            } else {
                state.bookProgressFractions
            },
        )
    }
}
