# Architecture context

Orientation for engineers (and coding agents) new to JellyPlay's playback stack.
User-facing feature docs live in `docs/`; this file is about how the code is
shaped. The repo is mid-KMP-migration (docs/kmp-migration-plan.md): every
feature lives in `shared/feature/*` (KMP, commonMain + platform actuals), the
core stack in `shared/core/*`, and the legacy tree is down to the Android-only
remainder — `core:data`, `core:ui` (shim files), `core:notification`,
`core:testing`, and `:app` — plus the `apps/desktop` and `apps/web` shells.
DI is Koin-only repo-wide (Hilt went extinct after the last flip). Player code
lives in two shared modules: `shared/core/player-contract` (the engine-agnostic
`MediaEngine` contract and engine-shared machinery) and
`shared/feature/player-video` (the VOD player screen, ViewModel, and session
collaborators). Paths below are relative to the repo root.

## Engine layer

- **`MediaEngine`** (`shared/core/player-contract/src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/engine/MediaEngine.kt`)
  is the single strategy interface every playback backend implements —
  `ExoPlayerEngine`, `MpvPlayerEngine`, `LibVlcPlayerEngine` and `NoOpEngine`
  (external playback) under `shared/feature/player-video/src/androidMain/.../engine/`.
  It is deliberately one wide contract (load/`PlaybackRequest`, reactive state
  `StateFlow`s, tracks, capabilities via `EngineCapabilities`, config via
  `updateConfig(EngineConfig)`) rather than role interfaces — a previous split
  delivered no decoupling because no consumer ever depended on a narrow role.
- **`PlayerEngineFactory`** (`shared/feature/player-video/src/commonMain/kotlin/.../engine/PlayerEngineFactory.kt`)
  maps a `PlayerType` to a concrete engine. It is a process-wide Koin single
  so the shared Media3 `DefaultBandwidthMeter` (adaptive-bitrate learning)
  survives across streams; `resetBandwidthMeter()` is the test/diagnostics
  escape hatch.
- **`EngineEventCoordinator`** (`shared/feature/player-video/src/commonMain/kotlin/.../EngineEventCoordinator.kt`)
  owns the engine-event *policies*: guarded play/buffering mirrors, the
  FORCE_DIRECT_PLAY → transcode one-shot fallback latch, the 20 s
  initial-buffering watchdog, subtitle toasts, and pass-out protection. Its
  decision model: raw engine flows in, `EngineDecision`s out
  (`ShowError` / `FallbackToTranscode` / `PlaybackEnded` / `PassOutPause` /
  `InformUser`) on a `tryEmit`-only `SharedFlow`. It never writes uiState and
  never commands the engine — every policy is assertable with a
  `FakeMediaEngine` plus an injected clock. Since Stage B of the refactor it is
  constructed, re-armed and executed by `PlaybackSession`; the ViewModel only
  collects its mirror `StateFlow`s.
- **`BasePlayerEngine`** (`shared/feature/player-video/src/androidMain/kotlin/com/raulshma/jellyplay/feature/player/video/engine/BasePlayerEngine.kt`)
  is the shared boilerplate base for the three reloadable adapters. It hoists
  the byte-identical 8 `StateFlow`/`SharedFlow` backing fields, the
  main-thread `engineScope`/`mainHandler` pair, the `updateConfig` dedup guard,
  and the polling/stats-toggle setters. Each adapter still owns its native
  player handle, track/subtitle logic, stats projection, volume/mute contract
  and `positionFlow` wiring — `NoOpEngine` does NOT extend this class.
- **`ReloadablePlayerEngine`** (`shared/feature/player-video/src/androidMain/kotlin/com/raulshma/jellyplay/feature/player/video/engine/ReloadablePlayerEngine.kt`)
  is the second layer for the three reloadable engines (extends `BasePlayerEngine`).
  It hoists `PlaybackSnapshot` / `withPreservedPlayback` (position+speed+isPlaying
  preservation across a rebuild), the `0–1` volume clamp + `0.05f` unmute floor
  + `MediaStreamVolume` sync, the `callbackFlow + EnginePositionTicker` shell for
  `positionFlow`, and the `EngineVideoStats` change-guard. The single
  `snapshotIsPlaying()` hook covers both snapshot and current checks (ExoPlayer
  overrides it to read `player.isPlaying` synchronously; `currentIsPlaying()`
  delegates to it).
- **`EnginePositionTicker`** (`shared/core/player-contract/src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/engine/EnginePositionTicker.kt`)
  is the shared polling-ticker loop used by every `positionFlow`. It lives in
  `:shared:core:player-contract` so both the production adapters
  (`:shared:feature:player-video` via `ReloadablePlayerEngine.positionFlowWithTicker`)
  and the test-double `FakeMediaEngine` (a common-pure twin in player-video's
  `jvmTest` — KMP testFixtures are unsupported on AGP 9) share one
  implementation — the bounded paused-wait (`POSITION_PAUSED_RECHECK_MS = 2_500L`),
  play↔pause edge detection and `delay(pollingIntervalMs)` live in exactly one
  place.
- **`PlayerLifecycleManager`** (`shared/core/data/src/jvmShared/kotlin/com/raulshma/jellyplay/core/data/playback/PlayerLifecycleManager.kt` — moved out of commonMain with the wave-15B jvmShared split)
  is the Activity↔engine lifecycle bridge: the host Activity calls
  `onActivityPause()` / `onActivityResume()`, which delegate straight to the
  `@Volatile activeCallbacks` engine reference (set by `PlayerSessionManager`
  on create/release; no StateFlow hops). Pausing is skipped when
  background-audio is enabled in `PlaybackStore`.

## Playback session (`PlaybackSession`)

**`PlaybackSession`** (`shared/feature/player-video/src/commonMain/kotlin/.../PlaybackSession.kt`)
is the "deep module" extracted from `VideoPlayerViewModel` (Stage B of the
video-player refactor; it now lives in commonMain with the rest of the session
cluster). One instance owns
a playback session's lifecycle:

- **initialize** — the ordered load entry: latch resets, remote "Play On"
  routing early-return, same-item short-circuit, single-flight `loadJob`
  tracking, mini-player reclaim, and WHEN the `SessionLoadPipeline` starts.
- **retry / reload / decisions** — `retryWithEngine`, `retryPlayback`,
  `reloadForMode`, `reloadForStreamChange`, plus the
  `EngineEventCoordinator` lifecycle and its decision fan-out.
- **reporting + release** — stop reports (deduped via a per-session latch so
  two release paths never double-report), `getReportPositionMs` seek latches,
  and the release split: the session-owned teardown half runs first, then the
  ViewModel half runs back-to-back from the same synchronous call chain
  (no dispatch hop — an interleaved recomposition could flash a stale title).
- **position persistence** — process-death resume position behind the
  `SessionPositionStore` seam (production impl `SavedStateHandlePositionStore`
  wraps 4 `SavedStateHandle` keys; positions older than 1 h are stale and
  ignored).
- **cinema intro** (`beginCinemaMode` / `advanceCinemaIntro`) and the
  **mini-player reclaim body** (`loadReclaimedEngine`).

The session **never touches `VideoPlayerUiState`** — every uiState read/write
it needs is a constructor lambda or a `SessionLifecycleHooks` hook, and
outcomes surface as `SessionEvent`s (`ShowError`, `InformUser`,
`PlaybackEnded`, `ClosePlayerRequested`, `PassOutPause`) on an `events`
`SharedFlow`. The ViewModel is the single forwarder: one init collector maps
each event into its existing sinks (error fields, message bus,
`_closePlayer`, `_passOutEvents`); autoplay/close policy stays VM-side.
`SessionLifecycleHooks` is the VM's synchronous prologue (transport re-arm,
new-item resets, routing gates, the VM teardown half, trickplay clear,
SyncPlay reattach). `sessionState` / `engineFlow` are direct aliases of
`PlayerSessionManager`'s flows — same instance, no re-publish, so dispatch
ordering is unchanged.

**God-count rule** (ratcheted by
`ControllerOwnershipTest.godStateWiringCount_neverIncreases`, player-video
`jvmTest`): the literals `getUiState =` / `updateUiState =` /
`uiState = _uiState` appear in exactly 3 places across
`shared/feature/player-video` commonMain+androidMain — `SettingsProjector`'s
pair and `PlaybackProgressReporter`'s raw handle, both in
`VideoPlayerViewModel.kt` (commonMain since the 8C split). `PlaybackSession.kt`
and the other migrated controllers must stay literally free of
`VideoPlayerUiState` references.

## Playback source resolution

- **`SessionLoadPipeline`** (`shared/feature/player-video/src/commonMain/kotlin/.../SessionLoadPipeline.kt`)
  owns the *order* of load stages: SyncPlay queue reconcile → prefs projection
  → remembered-muted restore → cinema gate (early return) → offline-resume
  resolution → playhead seed → `loadMedia` → per-item hydration → stream
  URL / media session / duration seed → veil lift → trickplay → start report
  and tracking → segments/episodes; a `finally` guarantees the loading veil
  always lifts. It writes uiState only through the VM-implemented
  `SessionLoadOutputs` and calls VM bodies through `SessionLoadHooks` — stage
  order is pinned by `SessionLoadPipelineTest`.
- **`PlayerSessionManager`** (`shared/feature/player-video/src/commonMain/kotlin/.../PlayerSessionManager.kt`)
  owns *what a load means*: how a Jellyfin item becomes a playable source.
  `loadMedia` resolves a `PlaybackSource` (Auto/Offline/Online) against the
  downloads DB via `PlaybackSourceResolver`. Online: fetch `MediaDetail`,
  pick the media source, ask `PlaybackRepository.resolvePlayback` (the
  Jellyfin PlaybackInfo endpoint, given the user's `PlaybackMode`, preferred
  engine and `AdaptiveBitrateManager` max bitrate) and fall back to a static
  `getStreamUrl` when the server cannot resolve a method. It then builds the
  `PlaybackRequest` (auth header, side-loaded subtitles chosen per play
  method) and creates/reuses the engine via `PlayerEngineFactory`. Offline:
  local-file URL, container sniffing for legacy downloads, extracted runtime.
  Reloads (`reloadPlayback`, `reloadForStreamChange`, `reloadWithEngine`)
  re-resolve and swap engines at the current position. It publishes
  `PlayerSessionState` (item, detail, source, streams, play method,
  transcode reasons, play-session id, stream URL, offline flag) — this, not
  uiState, is the session's source of truth.

## Direct Play ↔ Transcode

`PlaybackMode` (`shared/core/model/src/commonMain/kotlin/.../PreferenceModels.kt`) is
`AUTO` / `FORCE_DIRECT_PLAY` / `FORCE_TRANSCODE`. With **AUTO** the server
decides via PlaybackInfo against the device profile and the effective max
bitrate resolved by **`AdaptiveBitrateManager`**
(`shared/core/data/src/jvmShared/kotlin/.../playback/AdaptiveBitrateManager.kt`): quality
tiers (360p–4K), a 2.5 Mbps cap on metered networks, data-saver clamping, and
a manual cap; with adaptive bitrate disabled the cap is `null` and the server
direct-plays anything decodable. **FORCE_DIRECT_PLAY** requests the
"direct play all" profile — the server hands back a static URL even for
codecs the device cannot decode; when that fails at runtime, the
coordinator's one-shot fallback latch emits
`EngineDecision.FallbackToTranscode`, and the session flips the mode to
FORCE_TRANSCODE (persisted via `PlaybackStore`), stop-reports the old
session and reloads. **FORCE_TRANSCODE** makes the server re-encode;
`reloadForMode` surfaces a "Switched to transcoded stream" notice. When the
server transcodes, `PlayerSessionManager`'s `TranscodeReasonsRefresher`
fetches the live session's `TranscodingInfo` reasons; they land in
`PlayerSessionState.transcodeReasons` and are mirrored into
`uiState.media.transcodeReasons` by the ViewModel's sessionState collector
(`isDirectPlayForced` mirrors the mode the same way).

## Trickplay

`TrickplayInfo` (`shared/core/model/src/commonMain/kotlin/.../TrickplayInfo.kt`) is the
server's thumbnail manifest descriptor (tile geometry, count, interval,
bandwidth) carried on `MediaSource.trickplayInfo`. On load, the pipeline's
`initializeTrickplay` hook runs the VM's three-way selection: server info
cached into the download dir, a local bundle shipped with the download
(`OfflineTrickplayHelper`), or a fresh server manifest — the chosen info is
stored in `uiPrefs.trickplayInfo` and the tile cache initialized in
`TrickplayManager` (`shared/feature/player-video/src/androidMain/.../trickplay/`). The prefs
`trickplayEnabled` and `trickplayOnSeekGesture` live in the `uiPrefs` slice;
when gesture previews are on, the seek overlay calls
`VideoPlayerViewModel.getTrickplayThumbnail(positionMs)` to render
thumbnails while scrubbing.

## SyncPlay

**`SyncPlayBridge`** (`shared/feature/player-video/src/commonMain/kotlin/.../SyncPlayBridge.kt`)
bridges the process-wide `SyncPlayManager` singleton to the local session:
it forwards group playback commands to the engine, reports local state back,
and owns the group-display slice `SyncPlayUiState` (group name, participants,
sync status, repeat/shuffle) as its own `StateFlow` — it does not hold the
uiState handle. `isInSyncPlaySession`'s single home is the bridge's state; the
ViewModel mirrors it one-way into flat `uiState.isInSyncPlaySession` because
the segment-overlay projection reads it. Reattach on reload:
`PlaybackSession.initialize` reads `hooks.wasInSyncPlay()` before teardown
and calls `hooks.reattachSyncPlay()` (which restarts the bridge) after it, so
loading a new item inside an active group keeps the session; the pipeline's
first stage additionally reconciles the group queue.

## State slices (`VideoPlayerUiState`)

`VideoPlayerUiState` (`shared/feature/player-video/src/commonMain/kotlin/.../VideoPlayerUiState.kt`)
is seven stored slices — `gestures` (`GesturePrefsState`), `segmentState`
(`SegmentState`), `media` (`MediaContentState`), `autoplay`
(`AutoplayState`), `videoFx` (`VideoFxState`), `episodes`
(`EpisodeBrowserState`), `uiPrefs` (`PlayerUiPrefsState`, written by
`SettingsProjector`) — plus a small, deliberately flat remainder whose
rationale the class KDoc documents: identity/transport/engine plumbing
(`title`, `preferredPlayerType`, `engineCapabilities`, `isPlaying`, …), the
subtitle-style trio + dialogue boost (one `EngineConfigBuilder` input group),
error/session fields (the session's events land here), and high-frequency
residuals (`currentPosition` / `duration` / `bufferedPosition` /
`videoStats`) that exist only as seeds for on-state segment math. The live
4 Hz values live in dedicated ViewModel `StateFlow`s (`currentPositionMs`,
`durationMs`, `bufferedPositionMs`, `videoStats`) and are read only inside
the composables that render them; the segment overlay combines position with
a `distinctUntilChanged` projection of uiState, so position ticks do not
recompose the player chrome. Controller-owned concerns (sleep timer, track
selection, subtitles, audio effects, SyncPlay display) are not in uiState at
all — each controller exposes its own `StateFlow`.

## Seerr request state

**`SeerrRequestStateHolder`** (`shared/core/data/src/commonMain/kotlin/.../seerr/SeerrRequestStateHolder.kt`)
is the deep module for the Seerr request lifecycle. Its ONLY state interface is
`snapshot: Flow<SeerrRequestSnapshot>` (a cold combine of its six internal
`MutableStateFlow`s — request result, radarr/sonarr service lists, services
loading, TV seasons, anime flag — plus `distinctUntilChanged`); the individual
flows are deliberately private. Consumers hand-synced six mirror fields out of
them once, which every new holder field forced them to re-write; the snapshot
makes that a single fold. `snapshotIn(scope)` is the shape every ViewModel
wants — `stateIn(scope, WhileSubscribed(5_000), SeerrRequestSnapshot())` —
because `stateIn`-ing inside the holder would pin a never-ending child
coroutine onto the constructing scope (breaks `runTest` scopes). Everything
else on the holder is a command: `requestMedia` (owns the whole
loading → success/error result choreography), `prefetchDetails`,
`loadServiceDetails`, `loadTvSeasons`, `clearRequestResult`. There is no public
per-field state accessor and no `setRequestResult` escape hatch.
`SeerrRequestSnapshot` itself lives in `shared/core/model/.../seerr/` with the other
Seerr state models, so core/ui can see it: `SeerrRequestDialog`'s
snapshot-taking overload is the ONE fold of snapshot → dialog fields (screens
pass `snapshot =` instead of re-mapping eight fields per screen).

`requestMedia` takes an optional `onSuccess: ((SeerrMediaRequest) -> Unit)?`
hook that fires after the success result is set (never on failure) — the seam
post-request side effects ride instead of a consumer re-implementing the
choreography around a direct `SeerrRequestDelegate` call.

The optimistic **PENDING flip** lives at the model level
(`shared/core/model/src/commonMain/kotlin/.../seerr/SeerrModels.kt`):
`SeerrMovieDetails.withPendingRequest(item)` / `SeerrTvDetails` counterpart
match the detail's own `id == item.id` (not `mediaInfo.tmdbId` — Overseerr
omits `mediaInfo` entirely from `/movie/{id}` and `/tv/{id}` for never-requested
media, so a tmdbId match would never fire and the button would stay on
"Request"), synthesize a minimal `SeerrMediaInfo(tmdbId = item.id)` when absent,
set `status = SeerrMediaStatus.PENDING`, and leave non-matching details
untouched. Pure and unit-tested; no feature-code imports.

Four ViewModels construct a per-VM instance (deliberately not a shared Koin
single — each passes its own `scope` to `SeerrRequestDelegate`): `DetailViewModel` and
`SearchViewModel`/`SeerrDetailViewModel` expose `snapshotIn(scope)` as
`seerrSnapshot` (Search/Seerr-detail) or fold it into uiState as a single
`seerrRequest` field (`DetailUiState`); `HomeViewModel` embeds it into
`HomeUiState.seerrRequestState` alongside its `requestItem`. Screens read only
snapshot fields; commands go through the ViewModel wrappers (or the
`viewModel.seerrRequests` seam on the media-detail screen).

## Home feature

**`HomeRefresher`** (`shared/feature/home/src/commonMain/kotlin/com/raulshma/jellyplay/feature/home/HomeRefresher.kt`)
is the Home feed's deep module. Its public interface is five members —
`state`, `request(RefreshTrigger)`, `start`, `stop`, `patchItems` — plus the
mutex-protected `fetchOnce(force)` suspend core that the internal identity
transitions and going-online handshake call directly (their fetch must
survive a mid-flight `stop`). It owns WHAT and WHEN of the home screen:
exclusive mutex ownership, the job choreography (`refreshJob`,
`transitionJob`, `discoverJob`, each with its own replacement/cancellation
policy), the foreground/background-jittered cadence loop, the discover TTL
gate, the user-data-push debounce/throttle/deferral chain, and every
offline-shaped field of `HomeRefreshState` — the offline-mode mirror, the
online→offline content drop, and the user-initiated going-online handshake
(busy flag, full-screen loader, playback-outbox drain through the injected
`awaitOutboxDrained` seam, 30 s-capped fetch; the timeout `finally`
force-clears both flags so a hung fetch can never park the Go Online
spinners — plus a same-cap watchdog in `request(GoingOnline)` that clears
the busy flag if the preference write is lost and the mode flow never
emits ONLINE). `RefreshTrigger` is the folded entry table: the former public
members (`refreshForUserSwitch`, `onSignedOut`, `fetchDiscover`), the
going-online kick and the fetch-flavour triggers (`Manual`, `PullToRefresh`,
`PrefsChanged`, `UserDataChanged`) are enum values routed through `request`;
the online→offline content drop (`dropOnlineContent`) stays a private method
the offline-mode observer calls directly. The refresher reacts to ALL
offline-mode emissions (app-start
and external/auto flips included) and runs the drain+fetch handshake on EVERY
offline→online transition — a `GoingOnline` request additionally raises the
busy flag the Go Online spinners render from; external flips (the nav ⋮
toggle, which writes the preference straight from `MainViewModel`) and
auto-detect reconnects run the same handshake without it. It is the SOLE
writer of `sections`: the VM's
optimistic played/unplayed container forwards through `patchItems`, which
maps the patch over every section (the same item can appear in several, and
every visible card must flip together).

**`HomeViewModel`** (`shared/feature/home/src/commonMain/kotlin/com/raulshma/jellyplay/feature/home/HomeViewModel.kt`)
is a flows + `onEvent` facade. Its public surface is StateFlows
(`uiState`, `activeDownloadCount`, the `SyncStatusStateHolder`
re-exposures, `searchQuery`, `searchHistory`, `undoActions`,
`currentServerUsers`), sync getters
(`getImageUrl`/`getBackdropUrl`, the scroll-position pair, and the
per-item `photoFolderChildUrlsFor(itemId)` photo-folder slice each
photo card leaf-collects), `onStart`/
`onStop`/`onCleared`, and one command funnel:
`onEvent(HomeUiEvent)`. Every user intent — including the quick actions
(mark played/unplayed, delete download, inline download via `DownloadItem`,
the series delete-episodes sheet), search-history edits, settings-result
clicks, section-config sheet writes, user switching and the offline
toggle — arrives as a `HomeUiEvent` (`HomeUiEvent.kt`, 35 cases) and is
routed once to a private handler; there is no per-action command method to
keep in sync with the screen. The VM's remaining orchestration is folding
`HomeRefresher.state` and `OfflineHomeGate.state` into `HomeUiState` (the
refresher fold covers nine fields including `sections`, `isGoingOnline`
and `offlineMode` — single writer, VM only folds), the preference mirrors
the refresher re-reads through read-only providers (`sectionPrefs`,
`seerrPreferences`, `discoverEnabled`, `directArrEnabled`,
`androidTvWatchNextEnabled` — ALL private mirrors owned by the prefs
collectors; uiState's render fields are never a refresher input), and the
scroll reset on manual refresh and identity changes (pure VM state the
refresher cannot see).

`HomeUiState` embeds two value slices rather than mirroring fields:
`appearance: AppearanceUiState` (the theme quintet — dynamicTheming, oled,
colorStyle, swatch, performanceMode; one `AppearanceSlice` emission,
written as one assignment) and `sectionConfig: SectionConfigState` (the
inline section-config sheet's three pref mirrors). Same precedent as
`SeerrRequestState`'s embedded snapshot: no per-field hand-sync.

**`HomeSearchSession`**
(`shared/feature/home/src/commonMain/kotlin/com/raulshma/jellyplay/feature/home/HomeSearchSession.kt`)
is the search bar's SESSION half: it owns the expanded flag (snapshot
state) and the close ordering — collapse the surface → `ClearSearch` →
drop keyboard focus — as one method (`close(clearFocus)`), with
`closeThen` as the result-click shape. That triple used to be hand-copied
at seven sites (BackHandler, three result-click lambdas, the dock's
clear/back/dpad paths); `HomeTopDock` now only FORWARDS
(`onBack = onSearchExpanded(false)`, `onClear = onClearSearch`) and holds
no FocusManager. The data half (query, results, history, undo) stays on
the VM's `HomeSearchStateHolder`; `isSearchFocused` folds
`state.isSearchActive || session.isExpanded`.

The home dock is bundled, not flat: **`HomeDockState`** +
**`HomeDockCallbacks`** (`HomeAppBar.kt`) carry the dock's whole data +
interaction surface, so a dock feature edits the two bundles and the dock
body — not three signatures in lockstep (screen → scrim → dock).
`HomeTopDock` and the scroll-coupled `HomeTopDockScrim` leaf (which still
owns the icon-colour lerp, hide-on-scroll, and the query/settings-search
leaf collections) forward the bundles; the former dead
`activeDownloadCount` parameter is gone.

**`HomeQuickActionEffect`** (`HomeQuickActions.kt`) is the quick-action
routing table as data: the pure `homeQuickActionEffect(item, action,
onOpenDetail)` decides series-vs-movie (Download → series sheet vs inline
`DownloadItem`; Remove-download → delete-episodes sheet vs confirm
dialog), and the screen's execute lambda is a mechanical effect dispatch.
Pinned by `HomeQuickActionsTest`; the `resolveActions` gate keys on
`explicitOffline` — a read of the render source carried by
`HomeSurface.Content`, not a `.value` snapshot of any VM singleton.

**`HomeRenderSource`** (`shared/feature/home/src/commonMain/kotlin/.../HomeRenderSource.kt`)
is the home screen's single offline-render predicate: `Online` / `Offline` /
`FallbackPending`, folded ONCE per gate emission by the pure
`computeHomeRenderSource` and carried as `HomeUiState.renderSource`. The
screen branches (content vs hard-error vs loading), the implicit-offline
banner, and the VM's downloads-rendering gate (`isRenderingDownloads`, read
by the series smart-play funnel) all branch on that one value — no site
re-derives the predicate from `offlineMode` + error/sections.

**`OfflineHomeGate`**
(`shared/feature/home/src/commonMain/kotlin/com/raulshma/jellyplay/feature/home/OfflineHomeGate.kt`)
owns "when does the home render downloads?": the offline collection gate,
BOTH gated collectors (library + episodes — their emissions stay
independent so large episode batches don't delay the library's
pending→loaded transition), and the render-source fold, behind one
`state: StateFlow<OfflineHomeState>` (render source + both offline lists).
Inputs: `offlineModeManager.offlineMode` and the refresher's
`fetchFailedEmpty`. The fold keys on the SAME gate emission that opened
the collection (the gate value is paired into every library emission
inside `flatMapLatest`), so the old mutable-mirror lag race is
structurally impossible. The VM only folds `OfflineHomeState` into
uiState. Semantics worth remembering: a failed fetch over a
CONFIRMED-empty offline library is `Online` (the hard-error screen) — only
unprobed-or-populated downloads make the implicit fallback render.

**`OfflineHomeContent`** (`shared/feature/home/src/commonMain/kotlin/.../OfflineHomeSections.kt`)
is the offline home's render model, derived in ONE pass by
`buildOfflineHomeContent` (filtered library + episodes, the derived sections,
and the id→item lookup built once per emission). The screen remembers one
aggregate and passes it down as a single value — `HomeContentState` carries
it inside the sealed **`HomeFeed`** (`Online(sections, isLoading,
partialLoadError, newsletterBannerVisible)` / `Offline(content,
isLoading)`), built ONCE at the construction site from `renderSource`;
each branch's constructor IS the former offline-short-circuit mask, so the
online-only surfaces cannot exist on the offline feed and the two halves
cannot disagree. The row titles are localized
strings, so the aggregate is built at the call site (next to
`rememberOfflineHomeSectionTitles`) rather than in the VM; the UiState
mirrors (`offlineLibrary` / `offlineEpisodes` / `offlineSectionPrefs`) stay
raw repository/prefs emissions with the VM as their single writer. The hero
backdrop resolver keys on id+path triples (stable across download-progress
ticks, so the hero controller never resets rotation) but reads the lookup
through a `rememberUpdatedState` wrapper, so its content is always the
aggregate's fresh `itemsById`.

The home rows share one chassis: `HomeItemRow<T>`
(`HomeMediaRows.kt`) owns the TV (`TvFocusableItemRow`) / touch
(`HorizontalMediaScroller`) branch with the card as a
`(item, modifier)` slot, and `HomeRowTitle` is the module's one row
header (long-press configure, See-All pill, `topPadding` for the
standalone discover and *arr headers) — the three rows used to duplicate
the branch six times and there were three title implementations.

The offline partition facts live in `core/model/.../OfflineShelf.kt`:
`OfflineMediaTypeGroup` (VIDEO/MUSIC — the one type partition, shared by the
home's mode filter and the downloads screen's filter chips; the DAO's SQL
literals stay the storage contract and may legitimately differ),
`matchesOfflineQuery` (the name/series/season field set, agreeing with the
repository's SQL `searchOffline`), and `isFinishedOffline`
(`OFFLINE_WATCHED_THRESHOLD` on the stored 0–100 percent scale — note
`playedPercentage` is percent, while `toMediaItem` normalizes via the tick
ratio).

Test surfaces (all kotlin.test on the module's `jvmTest`, ported with the
feature): `HomeRefresherTest` (constructs the refresher directly with a fake
`awaitOutboxDrained`) pins
cadence, throttles, the offline transitions, the going-online sequence and
its timeout, and `patchItems`; `HomeViewModelTest` (kotlin.test on the
shared module's `jvmTest` — no Robolectric; the refresher's and sync
holder's collaborators are folded into the two injected factories below, so
those sub-module dependencies no longer surface on the VM) pins the UiState
folds, the event funnel and the identity routing through a real
`HomeSession` — every test runs through a `vmTest` helper whose `finally`
stops the periodic loop INSIDE the coroutine (an `@After` is too late:
runTest's completion never returns while its scheduler drives the infinite
loop); `OfflineHomeGateTest` drives the gate module through its interface
with one mocked repository (no VM); `OfflineHomeContentTest` pins the
one-pass aggregate; `OfflineShelfTest` (shared/core/model `commonTest`) pins
the shared partition/query/threshold rules; `HomeRenderSourceTest` pins the
render-source fold's corners and the render-source/offline-mode equivalence
in BOTH directions; `HomeSectionPrefsTest` (core/model, beside the algebra)
pins the three section-config write policies directly (formerly reachable
only through the VM harness), and `HomeDiscoveryStoreTest` pins the store
commands' read-modify-write + normalization; `HomeSurfaceTest` pins the
render-branch fold's precedence (and that `Content` carries the winning
render source); `HomeQuickActionsTest` pins the quick-action routing table;
`HomeSearchSessionTest` pins the close ordering; `HomeSectionConfigSheetTest`
pins the production `sectionConfigCapabilities` derivation (the old suite
asserted a local copy with a DIFFERENT rule); `HomeSearchOverlayTest` and
`HomeBackgroundPipelineTest` assert the extracted production
subtitle/target-colour functions, not local copies; `HomeUiStateTest` pins
only the state-class defaults.

## Session identity

**`HomeSession`** (`shared/core/data/src/jvmShared/kotlin/com/raulshma/jellyplay/core/data/session/HomeSession.kt`)
is the identity module. The atomic session source is the network engine:
`JellyfinApiEngine.session` publishes `ActiveSession?` — one server plus
its authenticated user as ONE value, updated inside the engine's critical
sections — so observers never see the synthetic `(newServer, oldUser)`
intermediate that combining the separate `currentServer`/`currentUser`
StateFlows produces during a two-step publish. That rule has one derived
corollary: never re-derive "is there a session" by combining those two
flows. `AuthRepositoryImpl.isAuthenticated` is
`apiClient.session.map { it != null }` (`WhileSubscribed(5_000)`, initial
`false`) for exactly this reason. HomeSession classifies consecutive
identities from the session flow into `HomeSessionTransition`s — `SignedIn`,
`UserSwitched`, `ServerSwitched`, `SignedOut`, each carrying
`previousIdentity` (null only on `SignedIn`) — collapsed by
`distinctUntilChanged`, and exposes the sanctioned identity reads for cache
keying: `cacheIdentity()` (suspend, reads the SOURCE flow) and
`cacheIdentitySnapshot()` (synchronous mirror read, for best-effort
evictions where staleness is benign). `HomeViewModel` subscribes to
`transitions` directly for its scroll-reset/refresh choreography — that
stays.

**`SessionCacheRegistry`** (`shared/core/data/src/commonMain/kotlin/com/raulshma/jellyplay/core/data/session/SessionCacheRegistry.kt`)
is the single home for identity reactions. It owns the ONE collector on
`HomeSession.transitions`; anything that must react to an identity change
registers instead of writing a bespoke collector:
`registerCaches(owner, caches...)` for plain `TtlCache`s whose wholesale
clear is the whole reaction, `registerAction(owner, action)` when the
reaction is more (the action receives the transition, so it can read
`previousIdentity` — Media's persisted home-section SWR clear needs it).
`SignedIn` never triggers; every other transition clears the registered
caches then runs the actions in registration order, each per-owner failure
caught and logged so one bad owner cannot kill the stream, and registration
is idempotent per owner (re-registering replaces). Registered owners today:
`media` (all of `MediaRepositoryImpl`'s TtlCaches plus its
`media-identity-clear` action: `invalidateDetailCache()` — the detail
cache's epoch bump + similarCache companion, which a plain registry drop
can't express; routing through `invalidateCaches()` would clear every
cache twice and double-bump the catalogue's epoch — + the previous
identity's SWR room rows), `episode-catalogue` (an action —
`invalidateAll()` also bumps the in-flight epoch, which a bare cache clear
wouldn't), `playback` (the media-segments cache) and `seerr` (the detail
cache). Reactions run in registration order. Session- and identity-path
collectors in shared/core/data (`HomeSession`, `SessionCacheRegistry`, the
repositories' registrations) inject the application-scope `CoroutineScope`
(`named("applicationScope")` Koin single, owned by shared/core:datastore's
Koin modules) instead of hand-rolling
`CoroutineScope(SupervisorJob() + …)` — HomeSession included; its two-arg
constructor doubles as the cross-module test seam. The longer-lived
playback/cast/syncplay/network managers still own
private scopes; identity-path code must not.

The identity-keyed-cache policy: an in-memory cache holding user-scoped
data uses the `TtlCache` identity overloads (`get`/`put`/`remove(identity,
key)` with a `CacheIdentity`) so a wrong identity is a guaranteed miss by
construction — no parallel invalidation channel. core:data caches get the
identity from `HomeSession.cacheIdentity()`/`cacheIdentitySnapshot()`.
Below that layer, shared/core/network cannot depend on shared/core/data, so
`LibraryApiClientImpl` keys off the engine's atomic session read directly
(`currentHomeCacheIdentity()`); its favorite-flag cache is an
identity-keyed `TtlCache`, which is why `clearFavoriteCache()` and the
manual call to it from `AuthApiClientImpl.disconnect()` are gone —
disconnect publishes one atomic null session and nothing needs a
hand-rolled cross-module clear.

`MediaSearchEngine` intentionally still keys search history on
`ServerIdentityStore.activeUserId` (the persisted session), not on
HomeSession: history reads run on cold start before `restoreSession()` has
established the engine session (and from the widget worker), where the
persisted store has the user but the runtime session does not yet — the
persisted identity is the stable source there, and logout clears both.


## Settings search

The settings-search knowledge lives in `shared/feature/settings`, next to the
screens it deep-links into — not in shared/core/ui. Each screen (or screen family)
declares its items in a `*SearchItems.kt` file co-located with the screen
(`PlaybackSettingsSearchItems.kt` beside `PlaybackSettingsScreen.kt` also
hosts the MPV/VLC/ExoPlayer engine, SyncPlay, casting and Live TV & DVR
groups). Every item is a
`SettingsSearchItem(id, titleRes, subtitleRes, categoryRes, keywords, route,
icon, isAdvanced)` (the `*Res` fields are Compose `StringResource`s since the
KMP move — locale resolves lazily at render/match time); `SettingsSearchCatalog`
aggregates the per-screen lists
in one curated flat order (257 items — the matcher's stable sort uses that
order as the tiebreaker, so keep additions deliberate). The `ss_<id>_title`
/`ss_<id>_subtitle` strings live in feature/settings' Compose resources; the 14
`ss_cat_*` category strings stay in shared/core/ui because both feature modules
render them.

`SettingsSearchProvider` (`shared/core/ui/.../settingssearch/SettingsSearchProvider.kt`)
is the seam: a one-property interface defined in shared/core/ui so feature/home
depends only on core/ui (Gradle star topology intact), while the Koin
binding — settingsModule in shared/feature/settings providing
`SettingsSearchCatalog` itself as the `SettingsSearchProvider` single —
resolves at app level.
`HomeViewModel` injects the provider and re-exposes the core/ui
`settingsSearchResults(queries, provider)` pipeline as a
VM function; `HomeScreen`'s `HomeTopDockScrim` leaf collects it behind the
"settings in home search" appearance gate. The in-settings search
(`SettingsScreen`) skips DI and reads `SettingsSearchCatalog.items` directly
(same module), sharing `SettingsSearchMatcher` and `resolve` from core/ui.

`SettingsNavActions` (`SettingsScreen.kt`) is the settings navigation
facade: four fields — `onNavigate: (Route) -> Unit` plus
`onLogout`/`onSetupWizard`/`onCheckForUpdates` — replacing the former
28-lambda `SettingsCallbacks`. Rows and search results navigate with the
highlight id baked into the route
(`onNavigate(Route.AppearanceSettings(lastClickedSettingId))`,
`item.route.withHighlightSettingId(item.id)`); the only bespoke branches
left are the sign-out dialogs (`ACTION_ONLY_IDS`), the on-screen screensaver
group (`Route.Settings` targets) and the host-indirected setup wizard.
`AppearanceSettingsScreen`'s drill-ins go through the same facade.

Adding a settings screen now touches: the route (NavKey.kt in
shared/core/ui — unchanged persistence contract), the screen itself, and its
items in the co-located
`*SearchItems.kt` (+ the new strings in feature/settings' Compose resources, +
one line in `SettingsSearchCatalog`). No core/ui edit, no new callback
field. `SettingsSearchCatalogTest` (feature/settings `jvmTest`, kotlin.test —
resource resolvability is compile-time-guaranteed by the generated
`StringResource` accessors, so the suite pins id uniqueness, resource/category
cardinality, keywords and the 257-item aggregation);
`SettingsSearchMatcherTest` (shared/core/ui `jvmTest`) is synthetic and pins matching only.

The settings **icon prewarmer** derives its workload from the same catalog:
the 257 catalog rows × 3 resource slots (title/subtitle/category `StringResource`
accessors) = 771 reads over 528 distinct resources — the dedup happens at the
generated-accessor level, so the prewarmer warms the 528 distinct entries and
the count is pinned by the catalog test.

## Theme variants (v0.10.6)

**`ThemeVariant`** (`shared/core/designsystem/src/commonMain/kotlin/com/raulshma/jellyplay/core/designsystem/theme/ThemeVariant.kt`)
is the single registry for the theme fleet: `STANDARD`, `SYNTHWAVE`,
`SOOTHING`, `MONOCHROME` (pre-existing) plus the four v0.10.6 arrivals
`VIVID`, `AURORA`, `SAKURA`, `VECTOR_POP`. The enum carries the derived
facts every consumer used to re-derive — `isDarkLocked` (SYNTHWAVE, AURORA
paint dark-only gradients so the light/dark picker goes inert) and
`allowsOled` (the OLED pure-black surface treatment; suppressed for the
dark-locked gradients and for Soothing/Monochrome) — plus two extension
surfaces that replaced every per-variant `if` chain: `accentOptions()`
returning the variant's `VariantAccent(id, label, lightColor, darkColor)`
swatch list (null = no accent picker: Standard uses the global accent,
Monochrome is fixed; Synthwave/Soothing keep their historical palettes, the
four new variants own theirs in their theme files) and
`backgroundBrush()` returning the full-bleed vertical gradient (Synthwave +
Aurora) or null, which is what the app shell's
`LocalThemeVariant.current.backgroundBrush()` background switch and the
`JellyPlayScreenScaffold`'s remembered-background transparency check both
read. The old `LocalIsSynthwave` / `synthwaveBackgroundBrush` pairs are
gone. Per-variant schemes live beside the registry
(`AuroraTheme.kt`, `SakuraTheme.kt`, `VectorPopTheme.kt`, `VividTheme.kt`);
`AppearanceSettingsScreen` renders one generalized
`VariantAccentPicker(variant)` (in shared/core/ui's `AccentColorPicker.kt`)
instead of the former hardcoded Synthwave/Soothing swatch rows.

## Decided deepenings (2026-08-30 architecture review)

**`HomeSurface`** (IMPLEMENTED, `shared/feature/home/src/commonMain/.../HomeSurface.kt`) is the
home screen's render-branch fold: ONE pure `homeSurface(state, offlineContent)`
computation producing a sealed surface — fixed precedence `HardError` →
`NoDownloads` → `Music` → `Content` — where `Content` carries the
pre-folded `HomeFeed` plus the winning render source carried whole (the
screen's hero/banner/quick-action facts are single reads of it, not
re-encoded booleans). The fold
relies on the equivalence `offlineMode != ONLINE` ⟺
`renderSource == Offline.Explicit` (both directions pinned by
`HomeRenderSourceTest`); every
predicate reads `renderSource`, never the offline-mode mirror (the raw
mirror read in the quick-action resolver died with it). The screen's
`when` is exhaustive over the result and decides nothing;
`computeHomeRenderSource` stays the VM-side emission fold. Rejected
alternative: a configurable flavour/policy engine — one production caller,
so it buys width, not depth. The remove-download quick action stays
Explicit-offline-only (pinned as-is); revisit if the implicit fallback
should offer it.

**`HomeRefresherFactory`** (IMPLEMENTED, `shared/feature/home`) is the refresher's
construction seam: an `@Inject` factory owning the nine pure-DI
collaborators, `create()` taking only VM-owned runtime inputs (scope, the
sync holder's drain gate, the preference-mirror providers) — plus
`offlineModeManager`, a DI bean the VM itself uses for ToggleOfflineMode,
so it stays on `create()` rather than the factory.
`SyncStatusStateHolderFactory` (core/data) is the same move for the sync
holder. `HomeViewModel`'s constructor dropped 33 → 23 parameters;
`HomeRefresherTest` still constructs the refresher directly — the factory
delegates, it adds no behavioural seam.

**`HomeSectionPrefs`** (IMPLEMENTED, core/model beside `HomeSectionType`)
is the section-prefs write algebra: the prefs snapshot type plus
`withSectionVisible` / `withSectionMoved` / `withLibrarySectionVisible` —
the single policy behind every section toggle/move. The sanctioned write
path is `HomeDiscoveryStore`'s command methods (`setSectionVisible`,
`moveSection`, `setLibrarySectionVisible`): read-modify-write over the
current persisted state with order re-normalization. Home's inline sheet,
Settings → Configure Libraries and the Appearance toggle all issue
commands; the two verbatim policy copies and the inline composable toggle
are gone. Bulk restore paths (preset apply, onboarding) keep the raw
setters — they write whole lists, not toggles. The Appearance
drag-to-reorder also keeps the raw whole-list setter: it persists the
dragged final order in one write, not a replay of per-swap moves.

**Row chrome consolidation** (IMPLEMENTED): `HomeRowMetrics` +
`homeRowMetrics(widthScale)` replace the four hand-derived
cardWidth/pad/spacing preambles; `HomeStatusBannerRow` is the content
list's one notice row (was twin verbatim blocks, optional Retry);
`HomeResultTile` is the search result row's one lead tile (was duplicated
per result kind — the extracted subtitle builders stay as the
Jellyfin/Seerr adapter mapping).

**Dead surface deleted** (IMPLEMENTED): `HomeUiEvent.ExcludeSeriesFromNextUp`
(zero senders) and five never-read `HomeCallbacks` fields (incl. the
`onPlayOnClick` plumbing chain through `homeSection`/`MainNavDisplay`).
The three continuation-carrying events stay — `HomeSurface` +
`homeQuickActionEffect` already shrink their when-maps to terminal
adapters.

**DEFERRED — audio playback snapshots**: `AudioPlaybackManager`'s 52
StateFlow members fold into now-playing / queue / effects / connection
snapshots per the `SeerrRequestStateHolder` pattern. Deferred because ten
consumer files across app/widgets/tile rewrite onto it at once and nothing
pins current behaviour — do it when audio/cast churn resumes, tests first.

**DEFERRED — settings category-merge module** (`SettingsCategoryMerge`):
one `merge(category, incoming, current)` interface in core/datastore with
legacy v0/v1 and factory-reset as adapters, folding
`ImportPreviewViewModel.mergeForCategory` (~250 lines) and the duplicate
snapshot builders. Deferred because backup/restore is the destructive path
and deserves a dedicated session with the diff UI in the loop.

## TV drawer and focus wiring

`TvNavigationDrawer` (app/.../navigation/TvNavigationDrawer.kt) filters its
folder rows through `isExcludedTvDrawerFolder`: the `EXCLUDED_DRAWER_TYPES`
collection types (now including `livetv`, whose UserView duplicates the
drawer's primary Live TV item) plus the DVR recordings library Jellyfin
injects with no collection type and the exact name "Recordings" (its
content lives in the Live TV screen's Recordings tab). Screen content
opens the drawer through `LocalTvDrawerOpener` (shared/core/ui `tv/TvMode.kt`),
provided by the scaffold around its content slot with a no-op default
(including phone): D-pad Left at a content left edge calls it instead of
relying on geometric focus search into the rail, which fails when the
selected rail entry is recycled out of the lazy column.

`LibraryScreen` (shared/feature/library) applies the same philosophy to its stacked
TV header rows: geometric D-pad search between them is unreliable (chip-row
focus bounds overlap; the alphabet rail interleaves on the right edge), so
each row intercepts its own vertical hops (`onDpadKey`) and redirects them to
a leaf `FocusRequester` on the neighbouring row's first chip. Interception is
per-row (not at the screen root with shared "which row holds focus" state),
so the routing is static and stale tracking can never send a hop to the wrong
row; the wrappable active-tags row keeps Up/Down geometric, and each header
row plus the content area carries `openDrawerOnLeftExit` (the
`LocalTvDrawerOpener` exit hook).

Focus-restorer contract (`shared/core/ui` `tv/FocusRestorer.kt`): focus
properties attach to the next INNER focus target, so `tvFocusRestorer`
must be placed BEFORE the focus group it manages
(`tvFocusRestorer(fallback).focusGroup()`), and because `onEnter`/`onExit`
are single-slot properties with outermost-wins aggregation, a restorer
must never wrap a whole screen slot — it would clobber the enter/exit
hooks of every focus group inside. `TvNavigationDrawer`'s content slot
therefore carries no restorer; `TvFocusableGrid`/`TvFocusableColumn` own
theirs. `TvDrawerFolderFilterTest` (app) pins the folder filter;
`TvDrawerFocusWiringTest` (core/ui) pins the modifier order.
