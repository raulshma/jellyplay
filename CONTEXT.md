# Architecture context

Orientation for engineers (and coding agents) new to JellyPlay's playback stack.
User-facing feature docs live in `docs/`; this file is about how the code is
shaped. Player code lives in two Gradle modules: `feature/player/core` (the
engine-agnostic `MediaEngine` contract and engine-shared machinery) and
`feature/player/video` (the VOD player screen, ViewModel, and session
collaborators). Paths below are relative to the repo root.

## Engine layer

- **`MediaEngine`** (`feature/player/core/src/main/java/com/raulshma/jellyplay/feature/player/video/engine/MediaEngine.kt`)
  is the single strategy interface every playback backend implements —
  `ExoPlayerEngine`, `MpvPlayerEngine`, `LibVlcPlayerEngine` and `NoOpEngine`
  (external playback) under `feature/player/video/src/main/java/.../engine/`.
  It is deliberately one wide contract (load/`PlaybackRequest`, reactive state
  `StateFlow`s, tracks, capabilities via `EngineCapabilities`, config via
  `updateConfig(EngineConfig)`) rather than role interfaces — a previous split
  delivered no decoupling because no consumer ever depended on a narrow role.
- **`PlayerEngineFactory`** (`feature/player/video/src/main/java/.../engine/PlayerEngineFactory.kt`)
  maps a `PlayerType` to a concrete engine. It is a process-wide `@Singleton`
  so the shared Media3 `DefaultBandwidthMeter` (adaptive-bitrate learning)
  survives across streams; `resetBandwidthMeter()` is the test/diagnostics
  escape hatch.
- **`EngineEventCoordinator`** (`feature/player/video/src/main/java/.../EngineEventCoordinator.kt`)
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
- **`BasePlayerEngine`** (`feature/player/core/src/main/java/com/raulshma/jellyplay/feature/player/video/engine/BasePlayerEngine.kt`)
  is the shared boilerplate base for the three reloadable adapters. It hoists
  the byte-identical 8 `StateFlow`/`SharedFlow` backing fields, the
  main-thread `engineScope`/`mainHandler` pair, the `updateConfig` dedup guard,
  and the polling/stats-toggle setters. Each adapter still owns its native
  player handle, track/subtitle logic, stats projection, volume/mute contract
  and `positionFlow` wiring — `NoOpEngine` does NOT extend this class.
- **`ReloadablePlayerEngine`** (`feature/player/video/src/main/java/com/raulshma/jellyplay/feature/player/video/engine/ReloadablePlayerEngine.kt`)
  is the second layer for the three reloadable engines (extends `BasePlayerEngine`).
  It hoists `PlaybackSnapshot` / `withPreservedPlayback` (position+speed+isPlaying
  preservation across a rebuild), the `0–1` volume clamp + `0.05f` unmute floor
  + `MediaStreamVolume` sync, the `callbackFlow + EnginePositionTicker` shell for
  `positionFlow`, and the `EngineVideoStats` change-guard. The single
  `snapshotIsPlaying()` hook covers both snapshot and current checks (ExoPlayer
  overrides it to read `player.isPlaying` synchronously; `currentIsPlaying()`
  delegates to it).
- **`EnginePositionTicker`** (`feature/player/core/src/main/java/com/raulshma/jellyplay/feature/player/video/engine/EnginePositionTicker.kt`)
  is the shared polling-ticker loop used by every `positionFlow`. It lives in
  `:feature:player:core` so both the production adapters (`:feature:player:video`
  via `ReloadablePlayerEngine.positionFlowWithTicker`) and the test-double
  `FakeMediaEngine` (`:feature:player:core:testFixtures`) share one
  implementation — the bounded paused-wait (`POSITION_PAUSED_RECHECK_MS = 2_500L`),
  play↔pause edge detection and `delay(pollingIntervalMs)` live in exactly one
  place.
- **`PlayerLifecycleManager`** (`core/data/src/main/java/com/raulshma/jellyplay/core/data/playback/PlayerLifecycleManager.kt`)
  is the Activity↔engine lifecycle bridge: the host Activity calls
  `onActivityPause()` / `onActivityResume()`, which delegate straight to the
  `@Volatile activeCallbacks` engine reference (set by `PlayerSessionManager`
  on create/release; no StateFlow hops). Pausing is skipped when
  background-audio is enabled in `PlaybackStore`.

## Playback session (`PlaybackSession`)

**`PlaybackSession`** (`feature/player/video/src/main/java/.../PlaybackSession.kt`)
is the "deep module" extracted from `VideoPlayerViewModel` (Stage B of the
video-player refactor; history in `PlaybackSessionTest.kt`). One instance owns
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
`ControllerOwnershipTest.godStateWiringCount_neverIncreases`): the literals
`getUiState =` / `updateUiState =` / `uiState = _uiState` appear in exactly 3
places across `feature/player/video` src/main — `SettingsProjector`'s pair and
`PlaybackProgressReporter`'s raw handle, both in `VideoPlayerViewModel.kt`.
`PlaybackSession.kt` and the other migrated controllers must stay literally
free of `VideoPlayerUiState` references.

## Playback source resolution

- **`SessionLoadPipeline`** (`feature/player/video/src/main/java/.../SessionLoadPipeline.kt`)
  owns the *order* of load stages: SyncPlay queue reconcile → prefs projection
  → remembered-muted restore → cinema gate (early return) → offline-resume
  resolution → playhead seed → `loadMedia` → per-item hydration → stream
  URL / media session / duration seed → veil lift → trickplay → start report
  and tracking → segments/episodes; a `finally` guarantees the loading veil
  always lifts. It writes uiState only through the VM-implemented
  `SessionLoadOutputs` and calls VM bodies through `SessionLoadHooks` — stage
  order is pinned by `SessionLoadPipelineTest`.
- **`PlayerSessionManager`** (`feature/player/video/src/main/java/.../PlayerSessionManager.kt`)
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

`PlaybackMode` (`core/model/src/main/java/.../PreferenceModels.kt`) is
`AUTO` / `FORCE_DIRECT_PLAY` / `FORCE_TRANSCODE`. With **AUTO** the server
decides via PlaybackInfo against the device profile and the effective max
bitrate resolved by **`AdaptiveBitrateManager`**
(`core/data/src/main/java/.../playback/AdaptiveBitrateManager.kt`): quality
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

`TrickplayInfo` (`core/model/src/main/java/.../TrickplayInfo.kt`) is the
server's thumbnail manifest descriptor (tile geometry, count, interval,
bandwidth) carried on `MediaSource.trickplayInfo`. On load, the pipeline's
`initializeTrickplay` hook runs the VM's three-way selection: server info
cached into the download dir, a local bundle shipped with the download
(`OfflineTrickplayHelper`), or a fresh server manifest — the chosen info is
stored in `uiPrefs.trickplayInfo` and the tile cache initialized in
`TrickplayManager` (`feature/player/video/.../trickplay/`). The prefs
`trickplayEnabled` and `trickplayOnSeekGesture` live in the `uiPrefs` slice;
when gesture previews are on, the seek overlay calls
`VideoPlayerViewModel.getTrickplayThumbnail(positionMs)` to render
thumbnails while scrubbing.

## SyncPlay

**`SyncPlayBridge`** (`feature/player/video/src/main/java/.../SyncPlayBridge.kt`)
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

`VideoPlayerUiState` (`feature/player/video/src/main/java/.../VideoPlayerUiState.kt`)
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

**`SeerrRequestStateHolder`** (`core/data/src/main/java/.../seerr/SeerrRequestStateHolder.kt`)
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
`SeerrRequestSnapshot` itself lives in `core/model/.../seerr/` with the other
Seerr state models, so core/ui can see it: `SeerrRequestDialog`'s
snapshot-taking overload is the ONE fold of snapshot → dialog fields (screens
pass `snapshot =` instead of re-mapping eight fields per screen).

`requestMedia` takes an optional `onSuccess: ((SeerrMediaRequest) -> Unit)?`
hook that fires after the success result is set (never on failure) — the seam
post-request side effects ride instead of a consumer re-implementing the
choreography around a direct `SeerrRequestDelegate` call.

The optimistic **PENDING flip** lives at the model level
(`core/model/src/main/java/.../seerr/SeerrModels.kt`):
`SeerrMovieDetails.withPendingRequest(item)` / `SeerrTvDetails` counterpart
match the detail's own `id == item.id` (not `mediaInfo.tmdbId` — Overseerr
omits `mediaInfo` entirely from `/movie/{id}` and `/tv/{id}` for never-requested
media, so a tmdbId match would never fire and the button would stay on
"Request"), synthesize a minimal `SeerrMediaInfo(tmdbId = item.id)` when absent,
set `status = SeerrMediaStatus.PENDING`, and leave non-matching details
untouched. Pure and unit-tested; no feature-code imports.

Four ViewModels construct a per-VM instance (deliberately not Hilt — each
passes its own `scope` to `SeerrRequestDelegate`): `DetailViewModel` and
`SearchViewModel`/`SeerrDetailViewModel` expose `snapshotIn(scope)` as
`seerrSnapshot` (Search/Seerr-detail) or fold it into uiState as a single
`seerrRequest` field (`DetailUiState`); `HomeViewModel` embeds it into
`HomeUiState.seerrRequestState` alongside its `requestItem`. Screens read only
snapshot fields; commands go through the ViewModel wrappers (or the
`viewModel.seerrRequests` seam on the media-detail screen).

## Home feature

**`HomeRefresher`** (`feature/home/src/main/java/com/raulshma/jellyplay/feature/home/HomeRefresher.kt`)
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

**`HomeViewModel`** (`feature/home/src/main/java/com/raulshma/jellyplay/feature/home/HomeViewModel.kt`)
is a flows + `onEvent` facade. Its public surface is StateFlows
(`uiState`, `activeDownloadCount`, the `SyncStatusStateHolder`
re-exposures, `searchQuery`, `searchHistory`, `undoActions`,
`photoFolderChildUrls`, `currentServerUsers`), sync getters
(`getImageUrl`/`getBackdropUrl`, the scroll-position pair), `onStart`/
`onStop`/`onCleared`, and one command funnel:
`onEvent(HomeUiEvent)`. Every user intent — including the quick actions
(mark played/unplayed, delete download, the series delete-episodes sheet),
search-history edits, settings-result clicks, section-config sheet writes,
user switching and the offline toggle — arrives as a
`HomeUiEvent` (`HomeUiEvent.kt`, 30 cases) and is routed once to a private
handler; there is no per-action command method to keep in sync with the
screen. The VM's remaining orchestration is folding `HomeRefresher.state`
into `HomeUiState` (nine fields including `sections`, `isGoingOnline` and
`offlineMode` — single writer, VM only folds), the preference mirrors the
refresher re-reads through read-only providers, and the scroll reset on
manual refresh and identity changes (pure VM state the refresher cannot
see).

**`HomeRenderSource`** (`feature/home/src/main/java/.../HomeRenderSource.kt`)
is the home screen's single offline-render predicate: `Online` / `Offline` /
`FallbackPending`, folded ONCE per gate emission by the pure
`computeHomeRenderSource` and carried as `HomeUiState.renderSource`. The
screen branches (content vs hard-error vs loading), the implicit-offline
banner, and the VM's downloads-rendering gate (`isRenderingDownloads`, read
by the series smart-play funnel) all branch on that one value — no site
re-derives the predicate from `offlineMode` + error/sections. The gate itself
is ONE flow in the VM (`offlineGate`: `offlineModeManager.offlineMode` +
the refresher's `fetchFailedEmpty`), shared by the offline-library and
offline-episodes collectors (their emissions stay independent so large
episode batches don't delay the library's pending→loaded transition); the
same gate emission computes the render source, so the predicate can never
disagree with what the collectors are doing. Semantics worth remembering: a
failed fetch over a CONFIRMED-empty offline library is `Online` (the
hard-error screen) — only unprobed-or-populated downloads make the implicit
fallback render.

**`OfflineHomeContent`** (`feature/home/src/main/java/.../OfflineHomeSections.kt`)
is the offline home's render model, derived in ONE pass by
`buildOfflineHomeContent` (filtered library + episodes, the derived sections,
and the id→item lookup built once per emission). The screen remembers one
aggregate and passes it down as a single value — `HomeContentState` carries
`offlineContent: OfflineHomeContent?`, null while online so download-progress
ticks never invalidate the content list. The row titles are localized
strings, so the aggregate is built at the call site (next to
`rememberOfflineHomeSectionTitles`) rather than in the VM; the UiState
mirrors (`offlineLibrary` / `offlineEpisodes` / `offlineSectionPrefs`) stay
raw repository/prefs emissions with the VM as their single writer. The hero
backdrop resolver keys on id+path triples (stable across download-progress
ticks, so the hero controller never resets rotation) but reads the lookup
through a `rememberUpdatedState` wrapper, so its content is always the
aggregate's fresh `itemsById`.

Test surfaces: `HomeRefresherTest` (plain JUnit + `MainDispatcherRule`,
constructs the refresher directly with a fake `awaitOutboxDrained`) pins
cadence, throttles, the offline transitions, the going-online sequence and
its timeout, and `patchItems`; `HomeViewModelTest` (Robolectric, all 30
constructor collaborators) pins the UiState folds, the event funnel and the
identity routing through a real `HomeSession`; `OfflineHomeContentTest` pins the one-pass aggregate (sections/lookup consistency, music-mode filter, empty inputs); `HomeRenderSourceTest` pins
the render-source fold's five corners; `HomeUiStateTest` pins the
state-class defaults.

## Session identity

**`HomeSession`** (`core/data/src/main/java/com/raulshma/jellyplay/core/data/session/HomeSession.kt`)
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

**`SessionCacheRegistry`** (`core/data/src/main/java/com/raulshma/jellyplay/core/data/session/SessionCacheRegistry.kt`)
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
collectors in core:data (`HomeSession`, `SessionCacheRegistry`, the
repositories' registrations) inject the `@ApplicationScope CoroutineScope`
bound in core:datastore's `CoroutineScopeModule` instead of hand-rolling
`CoroutineScope(SupervisorJob() + …)` (HomeSession's `@Inject` constructor
included; its two-arg primary constructor remains the cross-module test
seam). The longer-lived playback/cast/syncplay/network managers still own
private scopes; identity-path code must not.

The identity-keyed-cache policy: an in-memory cache holding user-scoped
data uses the `TtlCache` identity overloads (`get`/`put`/`remove(identity,
key)` with a `CacheIdentity`) so a wrong identity is a guaranteed miss by
construction — no parallel invalidation channel. core:data caches get the
identity from `HomeSession.cacheIdentity()`/`cacheIdentitySnapshot()`.
Below that layer, core:network cannot depend on core:data, so
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

The settings-search knowledge lives in `feature/settings`, next to the
screens it deep-links into — not in core/ui. Each screen (or screen family)
declares its items in a `*SearchItems.kt` file co-located with the screen
(`PlaybackSettingsSearchItems.kt` beside `PlaybackSettingsScreen.kt` also
hosts the MPV/VLC/ExoPlayer engine, SyncPlay, casting and Live TV & DVR
groups). Every item is a
`SettingsSearchItem(id, titleRes, subtitleRes, categoryRes, keywords, route,
icon, isAdvanced)`; `SettingsSearchCatalog` aggregates the per-screen lists
in one curated flat order (256 items — the matcher's stable sort uses that
order as the tiebreaker, so keep additions deliberate). The `ss_<id>_title`
/`ss_<id>_subtitle` strings live in feature/settings' `strings.xml`; the 14
`ss_cat_*` category strings stay in core/ui because both feature modules
render them.

`SettingsSearchProvider` (`core/ui/.../settingssearch/SettingsSearchProvider.kt`)
is the seam: a one-property interface defined in core/ui so feature/home
depends only on core/ui (Gradle star topology intact), while the Hilt
binding — `SettingsSearchModule` in feature/settings providing
`SettingsSearchCatalog` as a `@Singleton` — resolves at app level.
`HomeViewModel` injects the provider and re-exposes the core/ui
`settingsSearchResults(queries, context, provider)` pipeline as a
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

Adding a settings screen now touches: the route (NavKey.kt — unchanged
persistence contract), the screen itself, and its items in the co-located
`*SearchItems.kt` (+ the new strings in feature/settings' `strings.xml`, +
one line in `SettingsSearchCatalog`). No core/ui edit, no new callback
field. `SettingsSearchCatalogTest` (feature/settings, JVM-only — parses
both modules' `strings.xml` from disk like the old matcher test did) pins
id uniqueness, string resolvability and the 256-item aggregation;
`SettingsSearchMatcherTest` (core/ui) is synthetic and pins matching only.

## TV drawer and focus wiring

`TvNavigationDrawer` (app/.../navigation/TvNavigationDrawer.kt) filters its
folder rows through `isExcludedTvDrawerFolder`: the `EXCLUDED_DRAWER_TYPES`
collection types (now including `livetv`, whose UserView duplicates the
drawer's primary Live TV item) plus the DVR recordings library Jellyfin
injects with no collection type and the exact name "Recordings" (its
content lives in the Live TV screen's Recordings tab). Screen content
opens the drawer through `LocalTvDrawerOpener` (core/ui `tv/TvMode.kt`),
provided by the scaffold around its content slot with a no-op default
(including phone): D-pad Left at a content left edge calls it instead of
relying on geometric focus search into the rail, which fails when the
selected rail entry is recycled out of the lazy column.

`LibraryScreen` (feature/library) applies the same philosophy to its stacked
TV header rows: geometric D-pad search between them is unreliable (chip-row
focus bounds overlap; the alphabet rail interleaves on the right edge), so
each row intercepts its own vertical hops (`onDpadKey`) and redirects them to
a leaf `FocusRequester` on the neighbouring row's first chip. Interception is
per-row (not at the screen root with shared "which row holds focus" state),
so the routing is static and stale tracking can never send a hop to the wrong
row; the wrappable active-tags row keeps Up/Down geometric, and each header
row plus the content area carries `openDrawerOnLeftExit` (the
`LocalTvDrawerOpener` exit hook).

Focus-restorer contract (`core/ui` `tv/FocusRestorer.kt`): focus
properties attach to the next INNER focus target, so `tvFocusRestorer`
must be placed BEFORE the focus group it manages
(`tvFocusRestorer(fallback).focusGroup()`), and because `onEnter`/`onExit`
are single-slot properties with outermost-wins aggregation, a restorer
must never wrap a whole screen slot — it would clobber the enter/exit
hooks of every focus group inside. `TvNavigationDrawer`'s content slot
therefore carries no restorer; `TvFocusableGrid`/`TvFocusableColumn` own
theirs. `TvDrawerFolderFilterTest` (app) pins the folder filter;
`TvDrawerFocusWiringTest` (core/ui) pins the modifier order.
