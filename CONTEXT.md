# Architecture context

Orientation for engineers (and coding agents) new to JellyPlay's codebase.
User-facing feature docs live in `docs/`; this file is about how the code is
shaped. The repo is mid-KMP-migration (docs/kmp-migration-plan.md): every
feature lives in `shared/feature/*` (KMP, commonMain + platform actuals), the
core stack in `shared/core/*`, and the legacy tree is down to the Android-only
remainder — `core:data`, `core:ui` (shim files), `core:notification`,
`core:testing`, and `:app` — plus the `apps/desktop` and `apps/web` shells.
DI is Koin-only repo-wide. Player code lives in two shared modules:
`shared/core/player-contract` (the engine-agnostic `MediaEngine` contract and
engine-shared machinery) and `shared/feature/player-video` (the VOD player
screen, ViewModel, and session collaborators). Paths below are relative to the
repo root.

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
  `FakeMediaEngine` plus an injected clock. It is constructed, re-armed and
  executed by `PlaybackSession`; the ViewModel only collects its mirror
  `StateFlow`s.
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
- **`PlayerLifecycleManager`** (`shared/core/data/src/jvmShared/kotlin/com/raulshma/jellyplay/core/data/playback/PlayerLifecycleManager.kt`)
  is the Activity↔engine lifecycle bridge: the host Activity calls
  `onActivityPause()` / `onActivityResume()`, which delegate straight to the
  `@Volatile activeCallbacks` engine reference (set by `PlayerSessionManager`
  on create/release; no StateFlow hops). Pausing is skipped when
  background-audio is enabled in `PlaybackStore`.

## Playback session (`PlaybackSession`)

**`PlaybackSession`** (`shared/feature/player-video/src/commonMain/kotlin/.../PlaybackSession.kt`)
is the "deep module" behind `VideoPlayerViewModel`. One instance owns
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

**`EpisodeNavigator`** (`shared/feature/player-video/src/commonMain/kotlin/.../EpisodeNavigator.kt`)
owns episode navigation: season/episode browsing writes (through a single
`updateEpisodes` seam into the stored `EpisodeBrowserState` slice),
adjacent-episode discovery, and the previous/next choreography — the #146
single-flight latch (held until the session settles on a different non-null
item, an error, or `NEXT_EPISODE_SETTLE_TIMEOUT_MS`), mark-played-on-advance
(the incognito gate rides the VM's `onAdvanceFrom` lambda), and SyncPlay queue
routing (VM lambdas encapsulate the group-queue check). The VM keeps thin
funnels (`playNextEpisode` / `playPreviousEpisode` / `loadSeasonEpisodes` /
`playEpisode` / `isNextEpisodeLoading`) for the screen, PiP transport and
autoplay; autoplay/close policy on playback end stays VM-side.
`EpisodeNavigatorTest` pins the latch semantics — beware the virtual-time
trap: `advanceUntilIdle` fast-forwards past the settle timeout, use
`runCurrent` between steps.

**God-count rule** (ratcheted by
`ControllerOwnershipTest.godStateWiringCount_neverIncreases`, player-video
`jvmTest`): the literals `getUiState =` / `updateUiState =` /
`uiState = _uiState` appear in exactly 3 places across
`shared/feature/player-video` commonMain+androidMain — `SettingsProjector`'s
pair and `PlaybackProgressReporter`'s raw handle, both in
`VideoPlayerViewModel.kt`. `PlaybackSession.kt` and the other migrated
controllers must stay free of `VideoPlayerUiState` code references (the
ratchet test strips comments before counting, so KDoc prose is exempt —
`EpisodeNavigator`'s doc mentions the type).

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

## Player subtitles & trickplay

- **`SubtitleFormatCatalog`** (`shared/feature/player-video/src/commonMain/kotlin/.../subtitle/SubtitleFormatCatalog.kt`)
  is the one vocabulary for side-loadable subtitle formats: `mapCodecToMime`
  (6 codec groups), `codecForExtension` (canonical-codec fold; alias set
  `subrip`/`webvtt`/`tt`), and `pickerMimeTypes` (the document picker's
  list). Consumers: `SubtitleManager` (local side-load + provider download),
  the screen's picker, the preview repository (via a parseable-subset gate —
  Media3's parser coverage is that consumer's POLICY over the catalog, not a
  second vocabulary), the cast controller, and `ExoPlayerEngine`.
  `core/model`'s `isSideLoadableEmbeddedSubtitle` stays where it is
  (model-level, beside the streams it gates).
- **Trickplay**: `TrickplayInfo` (`shared/core/model/src/commonMain/kotlin/.../TrickplayInfo.kt`)
  is the server's thumbnail manifest descriptor (tile geometry, count,
  interval, bandwidth) carried on `MediaSource.trickplayInfo`. On load, the
  pipeline's `initializeTrickplay` hook runs the VM's three-way selection:
  server info cached into the download dir, a local bundle shipped with the
  download (`OfflineTrickplayHelper`), or a fresh server manifest — the
  chosen info is stored in `uiPrefs.trickplayInfo` and the tile cache
  initialized in `TrickplayManager`
  (`shared/feature/player-video/src/androidMain/.../trickplay/`). The prefs
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
flows are deliberately private. `snapshotIn(scope)` is the shape every
ViewModel wants — `stateIn(scope, WhileSubscribed(5_000), SeerrRequestSnapshot())`
— because `stateIn`-ing inside the holder would pin a never-ending child
coroutine onto the constructing scope (breaks `runTest` scopes). Everything
else on the holder is a command: `requestMedia` (owns the whole
loading → success/error result choreography), `prefetchDetails`,
`loadServiceDetails`, `loadTvSeasons`, `clearRequestResult`. There is no public
per-field state accessor and no `setRequestResult` escape hatch.
`SeerrRequestSnapshot` itself lives in `shared/core/model/.../seerr/` with the
other Seerr state models so core/ui can see it: `SeerrRequestDialog`'s
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
single — each passes its own `scope` to `SeerrRequestDelegate`): `DetailViewModel`
and `SearchViewModel`/`SeerrDetailViewModel` expose `snapshotIn(scope)` as
`seerrSnapshot` (Search/Seerr-detail) or fold it into uiState as a single
`seerrRequest` field (`DetailUiState`); `HomeViewModel` embeds it into
`HomeUiState.seerrRequestState` alongside its `requestItem`. Screens read only
snapshot fields; commands go through the ViewModel wrappers (or the
`viewModel.seerrRequests` seam on the media-detail screen).

## Details feature

**`AddToTargetActions<T>`** (`shared/feature/details/src/commonMain/kotlin/.../AddToTargetActions.kt`)
is the add-to-container concern for ONE generic target type:
`openPicker` / `dismissPicker` / `openCreateDialog` / `dismissCreateDialog` /
`addTo` / `createAndAdd` over `AddToTargetState<T>`, with the eligibility gate
(video/series), the stale-load guard (drop list loads that resolve after an
item switch), the empty-ids guard (BEFORE the create call on every adapter —
load-bearing: the playlist create path once lacked it and could CREATE AN
EMPTY PLAYLIST while reporting success), and the shared series→episode-id
resolver (`resolveTargetItemIds`: sorted-episode snapshot →
`canonicalEpisodeIds` fallback) written once. Two adapters justify the seam:
`PlaylistAddTarget` (editable-filter, mediaType tagging, overview) and
`CollectionAddTarget` (name-only create). `WatchLaterActions` owns the
reserved-bucket quick action (cached-id reuse or create-then-persist) —
deliberately outside the picker module: no picker, no dialog, but the same
resolver + empty-ids policy, and it rides the playlist picker's in-flight
flag + sheet close (its row lives in that sheet). `PlaylistTargets.Factory`
is the DI seam (`AppRuntimeStateStore` stays out of the VM ctor).
`PlaylistTargetsTest` / `CollectionTargetsTest` pin the merged interface.

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
emits ONLINE). `RefreshTrigger` is the folded entry table: the fetch-flavour
triggers (`Manual`, `PullToRefresh`, `PrefsChanged`, `UserDataChanged`), the
going-online kick and the identity triggers (`refreshForUserSwitch`-,
`onSignedOut`-, `fetchDiscover`-shaped) are enum values routed through
`request`; the online→offline content drop (`dropOnlineContent`) stays a
private method the offline-mode observer calls directly. The refresher
reacts to ALL offline-mode emissions (app-start and external/auto flips
included) and runs the drain+fetch handshake on EVERY offline→online
transition — a `GoingOnline` request additionally raises the busy flag the
Go Online spinners render from; external flips (the nav ⋮ toggle, which
writes the preference straight from `MainViewModel`) and auto-detect
reconnects run the same handshake without it. It is the SOLE writer of
`sections`: the VM's optimistic played/unplayed container forwards through
`patchItems`, which maps the patch over every section (the same item can
appear in several, and every visible card must flip together).

`HomeRefresherFactory` (`@Inject`) is the construction seam: it owns the nine
pure-DI collaborators; `create()` takes only VM-owned runtime inputs (scope,
the sync holder's drain gate, the preference-mirror providers) plus
`offlineModeManager` (a DI bean the VM itself uses for ToggleOfflineMode, so
it stays on `create()` rather than the factory). `SyncStatusStateHolderFactory`
(core/data) is the same move for the sync holder. `HomeRefresherTest` still
constructs the refresher directly — the factory delegates, it adds no
behavioural seam.

**`HomeViewModel`** (`shared/feature/home/src/commonMain/kotlin/com/raulshma/jellyplay/feature/home/HomeViewModel.kt`)
is a flows + `onEvent` facade. Its public surface is StateFlows
(`uiState`, `activeDownloadCount`, the `SyncStatusStateHolder`
re-exposures, `searchQuery`, `searchHistory`, `undoActions`,
`currentServerUsers`), sync getters
(`getImageUrl`/`getBackdropUrl`, the scroll-position pair, and the
per-item `photoFolderChildUrlsFor(itemId)` photo-folder slice each
photo card leaf-collects), `onStart`/`onStop`/`onCleared`, and one command
funnel: `onEvent(HomeUiEvent)`. Every user intent — quick actions (mark
played/unplayed, delete download, inline download via `DownloadItem`, the
series delete-episodes sheet), search-history edits, settings-result
clicks, section-config sheet writes, user switching and the offline
toggle — arrives as a `HomeUiEvent` (`HomeUiEvent.kt`) and is routed once:
pure-forwarding events go straight to their holder in the `when` (the
series download/delete sheets, search query/history edits, sync — no
one-line delegate stratum survives), anything with VM-side logic keeps a
private handler; there is no per-action command method to keep in sync
with the screen. The VM's remaining orchestration is folding
`HomeRefresher.state` and `OfflineHomeGate.state` into `HomeUiState` (the
refresher fold covers nine fields including `sections`, `isGoingOnline`
and `offlineMode` — single writer, VM only folds), the preference mirrors
the refresher re-reads through read-only providers (`sectionPrefs`,
`seerrPreferences`, `discoverEnabled`, `directArrEnabled`,
`androidTvWatchNextEnabled` — ALL private mirrors owned by the prefs
collectors; uiState's render fields are never a refresher input), and the
scroll reset on manual refresh and identity changes (pure VM state the
refresher cannot see). The four datastore stores (`homeDiscovery` /
`appearance` / `experimental` / `playback`) arrive bundled as
`HomePrefsProviders` (`HomePrefsProviders.kt`) — a construction-time seam,
same move as `HomeRefresherFactory`, not a read-only narrowing (the VM
still writes via `HomeDiscoveryStore` commands).

`HomeUiState` embeds two value slices rather than mirroring fields:
`appearance: AppearanceUiState` (the theme quintet — dynamicTheming, oled,
colorStyle, swatch, performanceMode; one `AppearanceSlice` emission,
written as one assignment) and `sectionConfig: SectionConfigState` (the
inline section-config sheet's three pref mirrors). Same precedent as
`SeerrRequestState`'s embedded snapshot: no per-field hand-sync.

**Render pipeline.** `HomeRenderSource`
(`shared/feature/home/src/commonMain/kotlin/.../HomeRenderSource.kt`) is the
home screen's single offline-render predicate: `Online` / `Offline` /
`FallbackPending`, folded ONCE per gate emission by the pure
`computeHomeRenderSource` and carried as `HomeUiState.renderSource`. The
screen's content/error/loading branches, the implicit-offline banner, and
the VM's downloads-rendering gate (`isRenderingDownloads`, read by the
series smart-play funnel) all branch on that one value — no site re-derives
the predicate from `offlineMode` + error/sections, and every predicate reads
`renderSource`, never the offline-mode mirror.

**`OfflineHomeGate`** (`shared/feature/home/src/commonMain/kotlin/.../OfflineHomeGate.kt`)
owns "when does the home render downloads?": the offline collection gate,
BOTH gated collectors (library + episodes — their emissions stay
independent so large episode batches don't delay the library's
pending→loaded transition), and the render-source fold, behind one
`state: StateFlow<OfflineHomeState>` (render source + both offline lists).
Inputs: `offlineModeManager.offlineMode` and the refresher's `fetchFailed`
(error != null — sections on screen do NOT disqualify the fallback; they
are the stale pre-failure snapshot the offline rows must replace). The fold
keys on the SAME gate emission that opened the collection (the gate value
is paired into every library emission inside `flatMapLatest`), so a
mutable-mirror lag race is structurally impossible. Semantics worth
remembering: a failed fetch over a CONFIRMED-empty offline library is
`Online` (the hard-error screen) — only unprobed-or-populated downloads
make the implicit fallback render.

**`HomeSurface`** (`shared/feature/home/src/commonMain/kotlin/.../HomeSurface.kt`)
is the render-branch fold: ONE pure `homeSurface(state, offlineContent)`
computation producing a sealed surface — fixed precedence `HardError` →
`NoDownloads` → `Music` → `Content` — where `Content` carries the
pre-folded `HomeFeed` plus the winning render source carried whole (the
screen's hero/banner/quick-action facts are single reads of it, not
re-encoded booleans). The fold relies on the equivalence
`offlineMode != ONLINE` ⟺ `renderSource == Offline.Explicit` (both
directions pinned by `HomeRenderSourceTest`); the screen's `when` is
exhaustive over the result and decides nothing; `computeHomeRenderSource`
stays the VM-side emission fold. The remove-download quick action stays
Explicit-offline-only (pinned as-is).

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
cannot disagree. The row titles are localized strings, so the aggregate is
built at the call site (next to `rememberOfflineHomeSectionTitles`) rather
than in the VM; the UiState mirrors (`offlineLibrary` / `offlineEpisodes` /
`offlineSectionPrefs`) stay raw repository/prefs emissions with the VM as
their single writer. The hero backdrop resolver keys on id+path triples
(stable across download-progress ticks, so the hero controller never resets
rotation) but reads the lookup through a `rememberUpdatedState` wrapper, so
its content is always the aggregate's fresh `itemsById`.

**`HeroController`** (`HomeHeroController.kt`) owns all hero policy as
Compose-free, synchronously testable methods: `rotationDelayMs(isScrolling,
lifecycleResumed)` is the whole rotation-cadence decision (`null` = no
scheduling when candidates are empty, rotation off, focus outside the hero,
or lifecycle below RESUMED; 2s re-check wait while scrolling; 8s idle tick
decision), `shouldTickNow()` is the post-delay re-check (state flipped
mid-delay — e.g. "Surprise Me" disabling rotation — suppresses the tick),
and `onFocusEffect(focused, isTv)` absorbs the TV snap-to-top policy (the
first invocation ever only settles the skip flag, so a freshly recomposed
Home doesn't snap before per-row focus restoration; later invocations
return whether to `scrollToItem(0, 0)` — focused+TV only).
`rememberHeroController` is a dumb collector shell: the composition-time
candidate sync (the deliberate snapshot write + its no-derivation
invariant, pinned by a repeated-identical-inputs test), the surprise-launch
arm, the `snapshotFlow(isScrollInProgress)`/`collectLatest` cadence
collector (keys `featuredCandidates`/`listState`/`autoRotateEnabled`
preserved; RESUMED passed as an argument), and the focus-keyed snap effect.

**Section ordering.** The pure `HomeSectionsAssembler`
(`shared/core/network/src/commonMain/kotlin/.../library/HomeSectionsAssembler.kt`)
backs BOTH production paths — the wasm client and
`LibraryApiClientImpl.getHomeSections` (both fetch through
`HomeSectionsFetcher`, which supplies `HomeSectionsAssemblyInputs`). The
section-ordering policy (CW → Next Up → per-folder Latest →
Recently-Added-insert-after-last-latest → Recommendations/suggestions →
pinned) is pinned ONCE for both paths by `HomeSectionsAssemblerTest`.

**`HomeSectionsFetcher`**
(`shared/core/network/src/commonMain/kotlin/.../library/HomeSectionsFetcher.kt`)
is the fetch half of the same split: ONE commonMain orchestrator owning the
sub-call schedule, the semaphore bounds (4 for the latest/pinned fan-outs, 3
for similar-items), the recommendations chain and the two
`NETWORK_SUBCALL_TTL_MS` TTL sub-caches — it decides what/when is fetched,
while the assembler decides what the fetched data becomes. Its
`HomeSectionSources` port (the ten client sub-calls; parameter defaults
omitted because Kotlin forbids duplicate defaults across super-interfaces)
is satisfied by `LibraryApiClientImpl` and `KtorWasmLibraryApiClient` for
free via their common `LibraryApiClient` supertype. The fetcher's
suggestions pre-fetch condition (recommendations succeeded but empty) is the
SAME predicate the assembler's fallback branch renders on — the two are
pinned together by `HomeSectionsFetcherTest`. Both platforms now memoise
under `CacheIdentity.UNKNOWN` pre-login (the wasm twin previously skipped
caching there), and the wasm-only `WasmTtlCache` was deleted — the
favorite-flag cache migrated to the shared commonMain `TtlCache`
(access-order LRU eviction, vs the old twin's insertion order).

**`HomeSectionPrefs`** (`shared/core/model/src/commonMain/kotlin/.../HomeSectionPrefs.kt`,
beside `HomeSectionType`) is the section-prefs write algebra: the prefs
snapshot type plus `withSectionVisible` / `withSectionMoved` /
`withLibrarySectionVisible` — the single policy behind every section
toggle/move. The sanctioned write path is `HomeDiscoveryStore`'s command
methods (`setSectionVisible`, `moveSection`, `setLibrarySectionVisible`):
read-modify-write over the current persisted state with order
re-normalization. Home's inline sheet, Settings → Configure Libraries and
the Appearance toggle all issue commands. Bulk restore paths (preset apply,
onboarding) keep the raw setters — they write whole lists, not toggles — and
so does the Appearance drag-to-reorder (it persists the dragged final order
in one write, not a replay of per-swap moves).

**`HomeSearchSession`**
(`shared/feature/home/src/commonMain/kotlin/com/raulshma/jellyplay/feature/home/HomeSearchSession.kt`)
is the search bar's SESSION half: it owns the expanded flag (snapshot state)
and the close ordering — collapse the surface → `ClearSearch` → drop
keyboard focus — as one method (`close(clearFocus)`), with `closeThen` as
the result-click shape; that triple is written once instead of hand-copied
into every close site (BackHandler, result-click lambdas, the dock's
clear/back/dpad paths). `HomeTopDock` only FORWARDS
(`onBack = onSearchExpanded(false)`, `onClear = onClearSearch`) and holds no
FocusManager. The data half (query, results, history, undo) stays on the
VM's `HomeSearchStateHolder`; `isSearchFocused` folds
`state.isSearchActive || session.isExpanded`.

The home dock is bundled, not flat: **`HomeDockState`** +
**`HomeDockCallbacks`** (`HomeAppBar.kt`) carry the dock's whole data +
interaction surface, so a dock feature edits the two bundles and the dock
body — not three signatures in lockstep (screen → scrim → dock).
`HomeTopDock` and the scroll-coupled `HomeTopDockScrim` leaf (which owns
the icon-colour lerp, hide-on-scroll, and the query/settings-search leaf
collections) forward the bundles.

The home rows share one chassis: `HomeItemRow<T>` (`HomeMediaRows.kt`) owns
the TV (`TvFocusableItemRow`) / touch (`HorizontalMediaScroller`) branch
with the card as a `(item, modifier)` slot, and `HomeRowTitle` is the
module's one row header (long-press configure, See-All pill, `topPadding`
for the standalone discover and *arr headers). Row chrome is shared too:
`HomeRowMetrics` + `homeRowMetrics(widthScale)` derive the card
width/pad/spacing triple, `HomeStatusBannerRow` is the content list's one
notice row (optional Retry), and `HomeResultTile` is the search result
row's one lead tile (the extracted subtitle builders stay as the
Jellyfin/Seerr adapter mapping).

The chassis decision itself is data too: `HomeRowChassis` + the pure
`homeRowChassis(section, hasOfflineContent)` (`HomeRowChassis.kt`) replace
HomeContentList's former ~130-line if/else — four variants (`OfflinePoster`,
`OfflineWide`, `OnlineWide`, `OnlinePoster`, each carrying only the
section), with the offline-mirror predicate (DOWNLOADED wins outright, then
the offline feed claims every non-wide section) pinned by
`HomeRowChassisTest` instead of living only in the render site, whose `when`
is now exhaustive and decides nothing. `sectionHasSeeAll` is the one See-All
gate (RECENTLY_ADDED / LATEST_MEDIA) for both the online and mirrored rows,
and `cwRowClick` is the one CW/NEXT_UP click routing the online and offline
wide rows share end-to-end — sites differ only in the item mapper, and the
ASK branch maps before the sink, so the Resume-vs-Details dialog wiring
cannot drift. On the discover side, `DiscoverRowSlot` +
`discoverPatternFor`/`discoverItemWidth` (`HomeDiscoverSection.kt`) write
the 12-arg `SeerrDiscoverRow` invocation once: the nine shared arguments
ride a `DiscoverRowSlotArgs` bundle built at composable scope, and the
discover and *arr rows pass only items, pattern-derived target size, and row
width, with the lazy keys/contentTypes staying at the call sites.

**`HomeQuickActionEffect`** (`HomeQuickActions.kt`) is the quick-action
routing table as data: the pure `homeQuickActionEffect(item, action,
onOpenDetail)` decides series-vs-movie (Download → series sheet vs inline
`DownloadItem`; Remove-download → delete-episodes sheet vs confirm dialog),
and the screen's execute lambda is a mechanical effect dispatch. Pinned by
`HomeQuickActionsTest`; the `resolveActions` gate keys on `explicitOffline`
— a read of the render source carried by `HomeSurface.Content`, not a
`.value` snapshot of any VM singleton.

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
feature): `HomeRefresherTest` pins cadence, throttles, the offline
transitions, the going-online sequence and its timeout, and `patchItems`;
`HomeViewModelTest` (no Robolectric; the refresher's and sync holder's
collaborators are folded into the two injected factories, so those
sub-module dependencies no longer surface on the VM) pins the UiState folds,
the event funnel and the identity routing through a real `HomeSession` —
every test runs through a `vmTest` helper whose `finally` stops the periodic
loop INSIDE the coroutine (an `@After` is too late: runTest's completion
never returns while its scheduler drives the infinite loop);
`OfflineHomeGateTest` drives the gate module through its interface with one
mocked repository (no VM); `OfflineHomeContentTest` pins the one-pass
aggregate; `OfflineShelfTest` (shared/core/model `commonTest`) pins the
shared partition/query/threshold rules; `HomeRenderSourceTest` pins the
render-source fold's corners and the render-source/offline-mode equivalence
in BOTH directions; `HomeSurfaceTest` pins the render-branch fold's
precedence (and that `Content` carries the winning render source);
`HomeSectionPrefsTest` (core/model, beside the algebra) pins the three
section-config write policies directly; `HomeDiscoveryStoreTest` pins the
store commands' read-modify-write + normalization; `HomeQuickActionsTest`
pins the quick-action routing table; `HomeSearchSessionTest` pins the close
ordering; `HomeSectionConfigSheetTest` pins the production
`sectionConfigCapabilities` derivation; `HomeSearchOverlayTest` and
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
playback/cast/syncplay/network managers still own private scopes;
identity-path code must not.

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

## Core data repositories

**`LyricsRepositoryImpl`** (`shared/core/data/src/jvmShared/kotlin/.../repository/LyricsRepositoryImpl.kt`)
owns the whole LRC/LRCLIB fetch-parse-cache chain (cache read → Jellyfin
endpoint → LRCLIB best-match, skipped on Local networks → negative-result
caching, plus the hour-throttled eviction) with its own private deps
(`LrcLibApi`, `LyricsCacheDao`, `NetworkMonitor`). `MediaRepository` does
NOT extend `LyricsRepository`: `AudioLyricsManager` and
`VideoPlayerViewModel` inject the narrow type directly, the app's
`CacheMaintenanceInitializer` injects it instead of the union, and the wasm
`WebMediaRepositoryNarrow` drops the lyrics section (web never served it).
`DataKoinModule` binds `LyricsRepositoryImpl` as its own single;
`DataKoinModulesTest` pins resolution.

## Navigation destinations

The **`NavDestination` registry** (core/ui `NavKey.kt`) is the single home
for top-level destination facts — persisted customization key, icon, rail
label, rail group — as `NAV_DESTINATIONS` (+ `NAV_DESTINATION_BY_ROUTE`
lookup and `Route.navIcon`). `NAV_KEYS_BY_ROUTE` is DERIVED from it, so the
persisted vocabulary cannot drift from the registry; the shells carry no
per-route icon tables — the desktop `DESKTOP_RAIL_ITEMS` is a display-ORDER
list resolved through the registry (ordering is per-shell policy; facts are
not). The video vs music bottom-bar maps stay separate: their labels are
context-specific ("Browse" on the music bar vs "Music" on the rail) and
their ORDER is per-mode. `NavDestinationRegistryTest` (core/ui `jvmTest`)
pins coverage, key/navKey agreement, uniqueness and per-route icon
resolution.

## Settings search

The settings-search knowledge lives in `shared/feature/settings`, next to the
screens it deep-links into — not in shared/core/ui. Each screen (or screen family)
declares its items in a `*SearchItems.kt` file co-located with the screen
(`PlaybackSettingsSearchItems.kt` beside `PlaybackSettingsScreen.kt` also
hosts the MPV/VLC/ExoPlayer engine, SyncPlay, casting and Live TV & DVR
groups). Every item is a
`SettingsSearchItem(id, titleRes, subtitleRes, categoryRes, keywords, route,
icon, isAdvanced)` (the `*Res` fields are Compose `StringResource`s —
locale resolves lazily at render/match time); `SettingsSearchCatalog`
aggregates the per-screen lists in one curated flat order (257 items — the
matcher's stable sort uses that order as the tiebreaker, so keep additions
deliberate). The `ss_<id>_title`/`ss_<id>_subtitle` strings live in
feature/settings' Compose resources; the 14 `ss_cat_*` category strings stay
in shared/core/ui because both feature modules render them.

`SettingsSearchProvider` (`shared/core/ui/.../settingssearch/SettingsSearchProvider.kt`)
is the seam: a one-property interface defined in shared/core/ui so feature/home
depends only on core/ui (Gradle star topology intact), while the Koin
binding — settingsModule in shared/feature/settings providing
`SettingsSearchCatalog` itself as the `SettingsSearchProvider` single —
resolves at app level. `HomeViewModel` injects the provider and re-exposes
the core/ui `settingsSearchResults(queries, provider)` pipeline as a
VM function; `HomeScreen`'s `HomeTopDockScrim` leaf collects it behind the
"settings in home search" appearance gate. The in-settings search
(`SettingsScreen`) skips DI and reads `SettingsSearchCatalog.items` directly
(same module), sharing `SettingsSearchMatcher` and `resolve` from core/ui.

`SettingsNavActions` (`SettingsScreen.kt`) is the settings navigation
facade: four fields — `onNavigate: (Route) -> Unit` plus
`onLogout`/`onSetupWizard`/`onCheckForUpdates` — instead of a per-callback
field. Rows and search results navigate with the highlight id baked into
the route (`onNavigate(Route.AppearanceSettings(lastClickedSettingId))`,
`item.route.withHighlightSettingId(item.id)`); the only bespoke branches
are the sign-out dialogs (`ACTION_ONLY_IDS`), the on-screen screensaver
group (`Route.Settings` targets) and the host-indirected setup wizard.
`AppearanceSettingsScreen`'s drill-ins go through the same facade.

Adding a settings screen touches: the route (NavKey.kt in shared/core/ui —
unchanged persistence contract), the screen itself, and its items in the
co-located `*SearchItems.kt` (+ the new strings in feature/settings' Compose
resources, + one line in `SettingsSearchCatalog`). No core/ui edit, no new
callback field. `SettingsSearchCatalogTest` (feature/settings `jvmTest`,
kotlin.test — resource resolvability is compile-time-guaranteed by the
generated `StringResource` accessors, so the suite pins id uniqueness,
resource/category cardinality, keywords and the 257-item aggregation);
`SettingsSearchMatcherTest` (shared/core/ui `jvmTest`) is synthetic and pins
matching only.

The settings **icon prewarmer** derives its workload from the same catalog:
the 257 catalog rows × 3 resource slots (title/subtitle/category
`StringResource` accessors) = 771 reads over 528 distinct resources — the
dedup happens at the generated-accessor level, so the prewarmer warms the
528 distinct entries and the count is pinned by the catalog test.

## Theme variants

**`ThemeVariant`** (`shared/core/designsystem/src/commonMain/kotlin/com/raulshma/jellyplay/core/designsystem/theme/ThemeVariant.kt`)
is the single registry for the theme fleet: `STANDARD`, `SYNTHWAVE`,
`SOOTHING`, `MONOCHROME`, `VIVID`, `AURORA`, `SAKURA`, `VECTOR_POP`. The
enum carries the derived facts every consumer would otherwise re-derive —
`isDarkLocked` (SYNTHWAVE, AURORA paint dark-only gradients so the
light/dark picker goes inert) and `allowsOled` (the OLED pure-black surface
treatment; suppressed for the dark-locked gradients and for
Soothing/Monochrome) — plus two extension surfaces that replace every
per-variant `if` chain: `accentOptions()` returning the variant's
`VariantAccent(id, label, lightColor, darkColor)` swatch list (null = no
accent picker: Standard uses the global accent, Monochrome is fixed;
Synthwave/Soothing keep their historical palettes, the others own theirs in
their theme files) and `backgroundBrush()` returning the full-bleed
vertical gradient (Synthwave + Aurora) or null, which is what the app
shell's `LocalThemeVariant.current.backgroundBrush()` background switch and
the `JellyPlayScreenScaffold`'s remembered-background transparency check
both read. Per-variant schemes live beside the registry (`AuroraTheme.kt`,
`SakuraTheme.kt`, `VectorPopTheme.kt`, `VividTheme.kt`);
`AppearanceSettingsScreen` renders one generalized
`VariantAccentPicker(variant)` (in shared/core/ui's `AccentColorPicker.kt`).

## TV drawer and focus wiring

`TvNavigationDrawer` (app/.../navigation/TvNavigationDrawer.kt) filters its
folder rows through `isExcludedTvDrawerFolder`: the `EXCLUDED_DRAWER_TYPES`
collection types (including `livetv`, whose UserView duplicates the drawer's
primary Live TV item) plus the DVR recordings library Jellyfin injects with
no collection type and the exact name "Recordings" (its content lives in the
Live TV screen's Recordings tab). Screen content opens the drawer through
`LocalTvDrawerOpener` (shared/core/ui `tv/TvMode.kt`), provided by the
scaffold around its content slot with a no-op default (including phone):
D-pad Left at a content left edge calls it instead of relying on geometric
focus search into the rail, which fails when the selected rail entry is
recycled out of the lazy column.

`LibraryScreen` (shared/feature/library) applies the same philosophy to its
stacked TV header rows: geometric D-pad search between them is unreliable
(chip-row focus bounds overlap; the alphabet rail interleaves on the right
edge), so each row intercepts its own vertical hops (`onDpadKey`) and
redirects them to a leaf `FocusRequester` on the neighbouring row's first
chip. Interception is per-row (not at the screen root with shared "which
row holds focus" state), so the routing is static and stale tracking can
never send a hop to the wrong row; the wrappable active-tags row keeps
Up/Down geometric, and each header row plus the content area carries
`openDrawerOnLeftExit` (the `LocalTvDrawerOpener` exit hook).

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

## Rejected designs

Recorded with evidence so future reviews don't re-suggest them.

- **Track controllers reading `PlayerSessionState.mediaStreams` instead of
  the uiState mirror**: NOT a pure render mirror. Offline (and any
  null-currentMediaSource session), `refreshMediaDetail` deliberately keeps
  the session's `mediaStreams` EMPTY (`fallbackToFirst = false`; the
  track-restore ladder depends on it) while the VM's
  `applyMediaDetailAndSourceState` writes the mirror from
  `matchedMediaSource(detail, fallbackToFirst = TRUE)` — the offline
  picker's server rows come FROM that mirror difference. Swapping the read
  source would regress offline track rows. Do not re-suggest without a
  design for the offline ladder first.
- **`PlayerTransportRouter`**: the screen already concentrates the three-way
  dispatch — `doPlay`/`doPause`/`doSeekTo` + the `isPlaying`/`duration`
  merges are one adjacent block in `VideoPlayerScreen`; the
  CompanionDashboard consumes those same lambdas via parameters (its
  cast-specific callbacks exist because it IS the cast companion);
  `PlayerControls`' `isInSyncPlaySession` branches are VISIBILITY gates
  (SyncPlay indicator/button), not transport routing; and the PiP transport
  is deliberately engine-direct (it bypasses the MediaSession by design). A
  router module would relocate ~25 lines without concentrating anything.

## Deferred designs

Designed but deliberately not landed — recorded so future work neither
re-derives the designs nor lands them casually.

- **Audio playback snapshots**: `AudioPlaybackManager`'s 52 StateFlow
  members fold into now-playing / queue / effects / connection snapshots
  per the `SeerrRequestStateHolder` pattern. Deferred because ten consumer
  files across app/widgets/tile rewrite onto it at once and nothing pins
  current behaviour — do it when audio/cast churn resumes, tests first.
- **Settings category-merge module** (`SettingsCategoryMerge`): one
  `merge(category, incoming, current)` interface in core/datastore with
  legacy v0/v1 and factory-reset as adapters, folding
  `ImportPreviewViewModel.mergeForCategory` (~250 lines) and the duplicate
  snapshot builders. Deferred because backup/restore is the destructive
  path and deserves a dedicated session with the diff UI in the loop.
- **`SeerrDetailPresentation` fold**: the movie/tv union is coalesced at
  ~17 sites through 3 nesting levels of `SeerrDetailScreen` (~600 deletable
  lines incl. 4 near-verbatim chassis copies from the media-detail side:
  backdrop `DetailBackdrop`, trailer dialog, `VideosSection`, Seerr row).
  Design: one pure presentation fold beside the `withPendingRequest`
  precedent, sections take a single value; route the chassis copies through
  the shared modules. Deferred: ~15 composable-signature changes through a
  2213-line file whose regressions are visual-only — deserves a session
  with screenshot verification.
- **`MediaRepository` union shrink**: the interface still extends
  LiveTv/SyncPlay/Newsletter/Playlist repositories (91 members, ~50
  one-line passthroughs). Design: drop the supertypes, consumers inject the
  existing family interfaces (the impl already delegates — DI splits,
  bodies don't move). Deferred for its own compiler-guided sweep (every
  consumer of a dropped member re-types its injected dependency across
  features + Koin + test harnesses).
- **`SideloadedTrackIdRegistry`**: the side-load id grammar
  (`external:`/`offline:`/`provider:`/`local:`) is constructed in
  `PlayerSessionManager`/`SubtitleManager`, matched in
  `TrackSelectionPolicy`, and "keep the caller id alive across the engine's
  track republish" is implemented three times (mpv label-keyed registry,
  VLC spu-diff + queue, Exo config-id scheme). Design: one registry in
  commonMain (`register`/`resolve`), adapters supply their native key.
  Deferred: the three implementations live in androidMain where no unit
  test can reach them — land it together with an engine-harness seam, tests
  first.
- **Shared `appSections` nav graph**: the ordered section list (~22
  builders) is restated per shell (`JellyPlayApp.kt` vs `DesktopAppRoot.kt`;
  `MusicHomeScreen` wired with 10 identical lambdas at both sites; the
  desktop dead-end guard is a hand-maintained "keep in sync" mirror;
  `MainViewModel`'s admin-refresh/logout duties are inlined verbatim on
  desktop). Design: one `appSections(navigator, host: ShellHostHooks)`
  module in a NEW shell-aggregator Gradle module (it must depend on every
  feature — the star topology has no existing home), dead-end list derived
  as routes-minus-registered. Deferred: new-module + two-shell rewiring
  deserves an un-rushed session.
