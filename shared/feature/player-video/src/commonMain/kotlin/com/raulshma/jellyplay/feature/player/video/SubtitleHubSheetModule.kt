package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.feature.player.video.components.RememberPreferenceToggle
import com.raulshma.jellyplay.feature.player.video.components.SubtitleHubDelayTab
import com.raulshma.jellyplay.feature.player.video.components.SubtitleHubGetTab
import com.raulshma.jellyplay.feature.player.video.components.SubtitleHubSheet
import com.raulshma.jellyplay.feature.player.video.components.SubtitleHubStyleTab
import com.raulshma.jellyplay.feature.player.video.components.SubtitleHubTracksTab
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleOwnership
import com.raulshma.jellyplay.feature.player.video.state.ProviderSubtitleSearchState
import com.raulshma.jellyplay.feature.player.video.state.RemoteSubtitleSearchState
import com.raulshma.jellyplay.feature.player.video.state.SubtitleState
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_remember_subtitle_language
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_remember_subtitles_off

/**
 * The unified subtitle hub module (the sheet-module wave 2, after the Hdr /
 * Render pilots): the four-tab hub — Tracks / Style / Get / Delay — behind one
 * [PlayerSheetModule]. The router branch's ~137-line forwarding body moved
 * verbatim: the single hub-open load trigger, the four sheet-local slice
 * collections, and the per-tab lambdas, which now ride the hub's per-tab
 * input bundles ([SubtitleHubTracksTab] / [SubtitleHubStyleTab] /
 * [SubtitleHubGetTab] / [SubtitleHubDelayTab]) instead of threading 41 flat
 * parameters.
 *
 * The open intent (reset-first flag + landing tab) still rides
 * [SheetRouterInputs] — the same per-sheet-intent channel the Render sheet's
 * "save for this series" toggle uses; the module reads both through its scope.
 */
internal object SubtitleHubSheetModule : PlayerSheetModule {

    override val sheet: PlayerSheet = PlayerSheet.SubtitleHub

    /**
     * The four slices (track, subtitle-workflow, style presets, engine) are
     * collected HERE (sheet-local) exactly where the router branch collected
     * them — only this hub consumes them while the sheet is open, so the
     * router root never ticks with their cadence (the placement rule). The
     * hub-open load trigger stays a [LaunchedEffect] of this content: both
     * entry clicks (Tracks tab / overflow) only route here, so an open costs
     * one remote-subtitles request.
     */
    @Composable
    override fun Content(scope: PlayerSheetScope) {
        val sheets = scope.sheets
        // The sheet's SINGLE hub-open load trigger ([SubtitleManager.openSubtitleHub]):
        // both entry clicks (Tracks tab / overflow) only route here, so an
        // open costs one remote-subtitles request — the former hand-copied
        // click cascades double-fetched (click loads, then this effect
        // cancelled and re-fetched). Timing change: the fetch
        // starts at sheet composition rather than at click (the hub's
        // loading spinner already covers the in-flight window). The reset
        // intent is per-entry: the overflow entry opens with a cleared
        // search/cultures slice (stale results must not leak across
        // items), the Tracks tab deliberately keeps prior state — the
        // flag rides from the click and is consumed single-shot. A
        // config-change sheet restore re-enters with the flag reset to
        // false, i.e. the same no-reset re-load this effect always did.
        LaunchedEffect(Unit) {
            sheets.openSubtitleHub(resetFirst = scope.inputs.subtitleHubResetFirst)
            scope.inputs.onSubtitleHubResetConsumed()
        }
        // Track + subtitle-workflow slices: collected inside the
        // branch — only this hub consumes them while the sheet is open.
        val trackState by sheets.trackState.collectAsStateWithLifecycle()
        val subtitleState by sheets.subtitleState.collectAsStateWithLifecycle()
        // Display-filter prefs: the picker list may hide tracks outside the
        // preferred languages (presentation only — auto-selection and the
        // series-off label below always see the full list). The slice rides
        // the sheets slice (the GLOSSARY "Session slice" seam).
        val subtitlePrefs by sheets.subtitlePreferences.collectAsStateWithLifecycle()
        // named style presets: the hub's Style tab is the apply/save/
        // delete surface. Presets are pure data over the style-controller
        // slice behind the sheets slice; the list rides the slice.
        val userStylePresets by sheets.userStylePresets
            .collectAsStateWithLifecycle(initialValue = emptyList())
        // Custom-mpv-config ownership: which sub-* styling keys the engine
        // yields to Advanced MPV Configuration / mpv.conf this session.
        // The Style tab renders its case-by-case notice (owned keys, ASS
        // force requirement, quoting trap) from this snapshot.
        val sessionEngine by sheets.engineFlow.collectAsStateWithLifecycle()
        SubtitleHubSheet(
            initialTab = scope.inputs.subtitleHubInitialTab,
            onDismiss = scope.inputs.dismissSheet,
            tracks = SubtitleHubTracksTab(
                subtitleTracks = visibleSubtitleTracks(
                    tracks = trackState.subtitleTracks,
                    showOnlyPreferred = subtitlePrefs.showOnlyPreferredSubtitles,
                    preferredLanguage = subtitlePrefs.preferredSubtitleLanguage,
                    orderedLanguages = subtitlePrefs.languageRules.subtitleLanguages,
                ),
                onSelectSubtitleTrack = { scope.viewModel.onEvent(VideoPlayerUiEvent.SelectSubtitleTrack(it)) },
                onResetSubtitleTrack = if (trackState.hasSubtitleOverride) {
                    { scope.viewModel.onEvent(VideoPlayerUiEvent.ResetSubtitleTrack) }
                } else null,
                tracksFooter = if (scope.uiState.media.seriesId != null) {
                    {
                        // Per-series subtitle preference toggle. With a real track
                        // selected it saves that track's language + role so every
                        // episode restores the right same-language track; with the
                        // "Off" row selected it saves a "subtitles off" intent so
                        // every episode loads with subs off. Toggling off forgets
                        // whichever intent was saved. The intent derivation lives
                        // in [seriesSubtitlePreferenceIntent].
                        val label = if (seriesSubtitlePrefersOffLabel(trackState.subtitleTracks, trackState.hasSeriesSubtitleOffPref)) {
                            stringResource(Res.string.player_video_remember_subtitles_off)
                        } else {
                            stringResource(Res.string.player_video_remember_subtitle_language)
                        }
                        RememberPreferenceToggle(
                            label = label,
                            checked = trackState.hasSeriesSubtitlePref,
                            onToggle = { remember ->
                                when (val intent = seriesSubtitlePreferenceIntent(trackState.subtitleTracks, remember)) {
                                    is SeriesSubtitlePrefIntent.Off ->
                                        scope.viewModel.onEvent(VideoPlayerUiEvent.SetSeriesSubtitleDisabled(intent.disabled))
                                    is SeriesSubtitlePrefIntent.Track ->
                                        scope.viewModel.onEvent(
                                            VideoPlayerUiEvent.SetSeriesSubtitlePreference(
                                                language = intent.language,
                                                forced = intent.forced,
                                                hearingImpaired = intent.hearingImpaired,
                                            )
                                        )
                                    SeriesSubtitlePrefIntent.Forget ->
                                        scope.viewModel.onEvent(VideoPlayerUiEvent.SetSeriesSubtitlePreference(language = null))
                                }
                            },
                        )
                    }
                } else null,
            ),
            style = SubtitleHubStyleTab(
                subtitleStyle = scope.uiState.subtitleStyle,
                capabilities = scope.uiState.engineCapabilities,
                subtitleOwnership = sessionEngine?.subtitleStyleOwnership
                    ?: MpvSubtitleOwnership.NONE,
                userStylePresets = userStylePresets,
                onStyleChange = { scope.viewModel.onEvent(VideoPlayerUiEvent.SetSubtitleStyle(it)) },
                onSubtitleDelayChange = { scope.viewModel.onEvent(VideoPlayerUiEvent.SetSubtitleDelay(it)) },
                onPickFont = scope.inputs.onPickFont,
                onOpenTester = scope.inputs.onOpenSubtitleTester,
                onSaveStylePreset = { name ->
                    sheets.saveStylePreset(name, scope.uiState.subtitleStyle)
                },
                onDeleteStylePreset = { name ->
                    sheets.deleteStylePreset(name)
                },
            ),
            get = SubtitleHubGetTab(
                downloadSubtitles = subtitleState.remoteSubtitles,
                isDownloading = subtitleState.isLoadingRemoteSubtitles,
                remoteSubtitlesError = subtitleState.remoteSubtitlesError,
                onDownload = { sheets.downloadSubtitle(it) },
                onLoadLocalFile = scope.inputs.onLoadLocalSubtitle,
                // The Search tab renders a FOLD of the two independent search
                // machines (legacy Jellyfin / multi-provider) — see the
                // `searchTab*` helpers below this class. Folding
                // "either machine Searching" is the spinner-drop fix: the
                // legacy machine's completion can no longer drop the sheet's
                // spinner while provider results are still streaming in.
                searchResults = subtitleState.searchTabLegacyResults,
                isSearching = subtitleState.searchTabIsSearching,
                hasSearched = subtitleState.searchTabHasSearched,
                searchError = subtitleState.searchTabLegacyError,
                cultures = subtitleState.subtitleCultures,
                defaultLanguage = subtitleState.defaultSearchLanguage,
                onSearch = { sheets.searchRemoteSubtitles(it) },
                onDownloadSearched = { sheets.downloadSubtitle(it) },
                providerSearchResults = subtitleState.searchTabProviderResults,
                providerSearchErrors = subtitleState.searchTabProviderErrors,
                configuredProviders = subtitleState.configuredSubtitleProviders,
                onSearchAllProviders = { sheets.searchAllProviders(it) },
                onDownloadProviderSubtitle = { sheets.downloadProviderSubtitle(it) },
                downloadingSubtitles = subtitleState.downloadingSubtitles,
                // "Use" activates the downloaded subtitle as the current track
                // (resolved from the manager's ready hints); on success the hub
                // shows the Tracks tab with the new selection, otherwise it
                // stays on Get while the pending selection auto-applies.
                onUseSubtitle = { rowKey -> scope.viewModel.useDownloadedSubtitle(rowKey) },
                onSaveSubtitleToServer = { rowKey ->
                    sheets.retryUploadSubtitle(rowKey)
                },
                isUploading = subtitleState.isUploadingSubtitle,
                onUpload = { uriStr, fileName, language, isForced, isHearingImpaired ->
                    // KMP seam: the sheets hand the picked SAF
                    // document as its string form; SubtitleManager consumes it.
                    sheets.uploadSubtitle(
                        uriStr,
                        fileName,
                        language,
                        isForced,
                        isHearingImpaired,
                    )
                    scope.inputs.onSheetChange(PlayerSheet.None)
                },
            ),
            delay = SubtitleHubDelayTab(
                currentDelayMs = scope.uiState.subtitleStyle.offsetMs,
                onOpenDelayOverlay = scope.inputs.onOpenSubtitleDelayOverlay,
            ),
        )
    }
}

// ── The Search tab's fold of the two independent search machines ────────────
// [SubtitleState] carries ONE state per search machine
// ([RemoteSubtitleSearchState] / [ProviderSubtitleSearchState]); the tab's
// rendering surface ([SubtitleManagerSection]) still speaks the old flat
// vocabulary (isSearching / hasSearched / results / error), so the module
// folds the two machines into it. Pure rules, pinned in SubtitleManagerTest.
//
// The bug the fold fixes: both machines used to write ONE isSearchingSubtitles
// / hasSearchedSubtitles pair, so the legacy machine's completion dropped the
// sheet's spinner while provider results were still streaming in (and a slow
// legacy search flipped the pair under a settled provider flow). Folding
// "either machine Searching" keeps the spinner up until BOTH machines settle.

/**
 * The sheet's spinner: up while EITHER machine is in flight. The
 * spinner-drop fix — the legacy machine's completion can no longer clear the
 * flag under a still-streaming provider search, and vice versa.
 */
internal val SubtitleState.searchTabIsSearching: Boolean
    get() = remoteSubtitleSearch is RemoteSubtitleSearchState.Searching ||
        providerSubtitleSearch is ProviderSubtitleSearchState.Searching

/**
 * "A search has settled" for the tab's no-results branch. Mirrors the old
 * shared flag's writes exactly: a machine counts as searched when it completed
 * with results ([RemoteSubtitleSearchState.Done] /
 * [ProviderSubtitleSearchState.Results]) — a legacy FAILURE does not (the old
 * code cleared `hasSearchedSubtitles` on failure and rendered only the error
 * block), and neither does a streaming [ProviderSubtitleSearchState.Searching].
 */
internal val SubtitleState.searchTabHasSearched: Boolean
    get() = remoteSubtitleSearch is RemoteSubtitleSearchState.Done ||
        providerSubtitleSearch is ProviderSubtitleSearchState.Results

/** The legacy machine's settled results (empty unless [RemoteSubtitleSearchState.Done]). */
internal val SubtitleState.searchTabLegacyResults: List<RemoteSubtitleInfo>
    get() = (remoteSubtitleSearch as? RemoteSubtitleSearchState.Done)?.results ?: emptyList()

/**
 * The legacy machine's failure message — the tab's "search failed" retry
 * invitation ([SubtitleFetchErrorBlock] in [SubtitleManagerSection]). The
 * former `subtitleSearchError` field, surface unchanged.
 */
internal val SubtitleState.searchTabLegacyError: String?
    get() = (remoteSubtitleSearch as? RemoteSubtitleSearchState.Failed)?.message

/** The provider machine's results (the streaming partials while still searching). */
internal val SubtitleState.searchTabProviderResults: List<SubtitleSearchResult>
    get() = when (val search = providerSubtitleSearch) {
        is ProviderSubtitleSearchState.Searching -> search.partialResults
        is ProviderSubtitleSearchState.Results -> search.results
        ProviderSubtitleSearchState.Idle -> emptyList()
    }

/** The provider machine's per-provider error chips (the streaming partials while still searching). */
internal val SubtitleState.searchTabProviderErrors: Map<SubtitleProviderKind, String>
    get() = when (val search = providerSubtitleSearch) {
        is ProviderSubtitleSearchState.Searching -> search.partialErrors
        is ProviderSubtitleSearchState.Results -> search.errors
        ProviderSubtitleSearchState.Idle -> emptyMap()
    }
