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
