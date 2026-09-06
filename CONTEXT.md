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
screen, ViewModel, and session collaborators). The two desktop-and-Android shells register their nav sections through one
aggregator module, `shared/feature/shell` (`appSections` + `ShellHostHooks` +
a registration ledger the desktop dead-end guard derives from). Paths below are relative to the
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
  and the polling/stats-toggle setters. It also owns the published-state
  RESET choreography the three `release()` bodies used to re-derive by copy:
  `resetItemScopedPublishedState()` (cues/tracks/buffered/stats, then the
  `onResetItemScopedState()` per-engine hook) and
  `resetPublishedEngineState()` (adds playbackState→IDLE, isPlaying→false) —
  adding a new published flow no longer requires editing three release
  bodies. Each adapter still owns its native
  player handle, track/subtitle logic, stats projection, volume/mute contract
  and `positionFlow` wiring — `NoOpEngine` does NOT extend this class.
- **`ReloadablePlayerEngine`** (`shared/feature/player-video/src/androidMain/kotlin/com/raulshma/jellyplay/feature/player/video/engine/ReloadablePlayerEngine.kt`)
  is the second layer for the three reloadable engines (extends `BasePlayerEngine`).
  It hoists `PlaybackSnapshot` / `withPreservedPlayback` (position+speed+isPlaying
  preservation across a rebuild), the four FINAL volume/mute command templates
  over `PlaybackVolumePolicy` (see that bullet for the adapter seams), the
  `callbackFlow + EnginePositionTicker` shell for
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
- **`PlaybackVolumePolicy` / `AspectRatioMapping` / `EngineDurationFallback`**
  (player-video commonMain `engine/`, the `MpvStyleMapping` shape) are the
  adapters' pure decision halves: volume/mute plans (clamp, remember-unmute,
  `MediaStreamVolume` sync, the `0.05f` floor) with per-engine max boost as
  declared data (`MAX_BOOST_NOMINAL = 1.0f`, `MAX_BOOST_VLC = 2.0f` — VLC
  amplification is a declared divergence, not copy variance), per-engine aspect
  plans (Exo resize-mode selector, mpv panscan + subtitle margins, VLC
  aspect-string override — CROP is a *declared* native-frame reset on VLC:
  libVLC 3.x exposes no engine-drivable zoom), and the duration→server
  fallback ladder. Adapters only apply the returned plans. The VLC unmute
  restores the remembered level (it previously computed the target and jumped
  to 100). `PlaybackVolumePolicyTest` / `AspectRatioMappingTest` /
  `EngineDurationFallbackTest` pin all three engines at once. The volume/mute
  half is now ONE template, not twelve bodies: `ReloadablePlayerEngine` owns
  the four `MediaEngine` commands as `final` templates (plan → remember →
  native write → system-stream mirror) over small adapter seams —
  `applyNativeVolume(normalized)`, `readNativeVolume()` (null aborts the
  delta templates, the old `?: return`s), `volumeBoostCeiling`,
  `nativeVolumeRestore(muted)` (the policy's `NativeVolumeRestore`
  vocabulary: Exo ZERO/FULL, mpv LEAVE_UNCHANGED, VLC
  ZERO/REMEMBERED_LEVEL), `applyNativeMuteFlag` (mpv's real flag),
  `muteTemplateEnabled` (VLC's null-handle abort) and `dispatchVolumeCommand`
  (Exo's player-thread post + null abort; mpv/VLC swallow-all). The remember
  call is unified BEFORE the native write — the former order in Exo/VLC;
  mpv's increase/decrease had drifted to remember-after.
  `PlaybackVolumePolicy` itself is public (not internal) because the
  protected `nativeVolumeRestore` seam returns its nested enum — a protected
  member cannot expose an internal type; it is not a stable API surface.
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

**`PlayerScreenPolicies`** (beside `VideoPlayerScreen`) is the player screen's
Compose-free decision half, the `homeQuickActionEffect` precedent: the seek
STEP targets (`seekBackTargetMs`/`seekForwardTargetMs` — direction-asymmetric
clamp, deliberately a different policy from `GestureSeekMath`'s capped gesture
deltas; the KDoc cross-references why they stay separate), the orientation-lock
fold (`Immediate`/`SettleFirst`; the 400 ms race stays in the effect shell),
the aspect AUTO ladder, the skip-button visibility precedence, the controls
auto-hide predicate + TV double timeout, and the user-font `.ttf`/`.otf` gate.
Six former inline composable pockets; the effects are one-line callers. Pinned
by `PlayerScreenPoliciesTest`. The segment-skip ladder is the newest sibling:
`SegmentSkipPolicy` (beside it) folds `skipIntro`/`skipCredits`/`skipSegment`'s
shared precedence — cinema-intro escape (INTRO kind only) → the outro's
near-end + can-skip-to-next next-episode branch (CREDITS kind only) →
active-segment-of-the-pressed-kind → that kind's end-ticks fallback — into one
pure `segmentSkipTarget(...)` returning a sealed `SegmentSkipTarget`
(`SeekToPosition` in ms / `SkipToNextEpisode` / `AdvanceCinemaIntro` / `None`);
the VM's three funs are a snapshot → policy → one-line effect dispatch, and
`segmentEndSeekTarget` is the shared ticks guard + truncating ticks→ms fold.
Pinned by `SegmentSkipPolicyTest`, which replaced `PlaybackLogicTest`'s
`SkipIntroCreditsTest` placebo (its assertions only re-derived
`introEndTicks / 10_000` integer division and never executed a skip).

**`SubtitlePreviewController`** (beside the other player controllers) owns
the subtitle cue-preview sheet: the EXTERNAL-vs-EMBEDDED source precedence,
exact-id-then-label track→source resolution, the sheet-visible gate on the
engine's `currentCues` pump (inert while closed), the stale-load
cancellation (fast switch wins), the open-time re-sync, and per-item reset
(the VM's `releaseInternalsVmPart` pokes `resetForItem()`). Five-member
interface — `state` (one value: cues + source + visible, re-exposed by the
VM as its own StateFlow; the three former uiState fields are gone),
`setSheetVisible`, `onTrackSelectionChanged` (the VM's `selectSubtitleTrack`
pokes it), `onEngineCues` (the engineFlow collector forwards), `resetForItem`
— constructor-lambda dependencies like `SleepTimerController`. Pinned by
`SubtitlePreviewControllerTest` (15 cases) and the
`ControllerOwnershipTest` ratchet.

**`SeriesPreferenceIntent`** (beside `ItemPlaybackPreferenceWriter`) is the
pure read-side twin for the sheet footers' remember-intents:
`seriesAudioPreferenceIntent(tracks, remember)` → the language to persist,
and `seriesSubtitlePreferenceIntent(...)` → sealed
`SeriesSubtitlePrefIntent` (`Off(disabled)` / `Track(language, forced,
hearingImpaired)` / `Forget`) plus `seriesSubtitlePrefersOffLabel` for the
row label. The audio/subtitle sheet footers in `VideoPlayerScreen` are now
match + writer dispatch — the intent derivation that used to live only in
composable lambdas is jvmTest-pinned by `SeriesPreferenceIntentTest`
(Off-row dispatch, forced+SDH badges, no-selection degradation, forget).

The discrete skip-step path is ONE funnel: `PlayerScreenPolicies.stepSeekTargetMs`
folds onto the existing back/forward target policies, and the VM's
`seekByStep(direction)` runs position+step through it before the usual
SyncPlay → cast → local seek routing. Both entry points funnel through it —
the screen's `doSeekBack`/`doSeekForward` are one-line delegates, and PiP's
SKIP_FORWARD/BACKWARD no longer hand-computes `position ± seekDuration`
(the old inline math floored at 0 only and could seek past media end).
Pinned in `PlayerScreenPoliciesTest` (0-floor, duration cap, no-duration
pass-through via FakeMediaEngine).

**`ItemPlaybackPreferenceWriter`**
(`shared/feature/player-video/src/commonMain/kotlin/.../ItemPlaybackPreferenceWriter.kt`)
is the write side of the per-item/series playback-language preferences — the
command twin of `ItemPlaybackPreferenceResolver` (the read side). Its five
commands (`setSeriesAudioLanguage`, `setSeriesSubtitlePreference`,
`setSeriesSubtitleDisabled`, `setDialogueBoostStrength`, `rememberTrack`)
each internalize the whole write choreography: resolve the write key from
session state per the command's explicit `ScopePolicy` (`SERIES_ONLY` for
language/remembered-track — a standalone movie has nothing to remember onto;
`SERIES_THEN_ITEM` for dialogue boost, whose SERIES→ITEM fallback is
declared, not accidental), write through `ItemPlaybackPreferenceRepository`
where null means FORGET and issues the explicit `clear*` call (save()'s
"null ⇒ preserve" convention must never silently keep the old language),
then fire `onPreferencesChanged` — the resolver refresh the restore ladder
and sheet toggles read. In the VM the writer sits after
`trackSelectionHelper` with a load-bearing explicit type annotation (each
declaration's wiring lambda reads the other). Pinned by
`ItemPlaybackPreferenceWriterTest`.

`TrackSelectionHelper.updateTracksFromEngine`'s twin restore ladders are one
choreography now: a private `TrackRestoreLadder` delta value carries the four
genuine per-type divergences (subtitle's `offline:` id route + target-stream
null-guard vs audio's unconditional `resolveByStreamIndex`; the stored-index
offline fallback positional vs offline-id→positional; the preference ladder
`resolveAudio`/preferAudioDescription vs `resolveSubtitle`/
subtitleDisabled-short-circuit/forcedOnly) while `runRestoreLadder()` runs
the shared pending → held-selection guard → stored index → preference
sequence once. Pinned by `TrackSelectionHelperTest` UNMODIFIED — the dedup
landed only because the untouched suite passes. Two VM residues collapsed
with it: the error-dialog dismissal triple is `clearPlaybackErrorState()`,
and the item-switch uiState rebuild in `releaseInternalsVmPart` is the
declared builder `VideoPlayerUiState.keepAcrossItems()` (the surviving
leaves are its constructor arguments, everything else resets to slice
defaults); the god-count ratchet still counts exactly 3.

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

`SyncPlayManager`'s teardown is one `teardownTo(level)` with a private
`TeardownLevel` enum: `FULL` (leaveGroup/reset — atomics + job cancels +
cores + timeSync + WS disconnect) and `GROUP_LEFT_KEEP_LISTENING` (the
GroupLeft handler — clears session state but keeps jobs and the app-lifetime
websocket alive for rejoin observability; the divergence the three former
hand-copies encoded implicitly). Pinned per level in `SyncPlayManagerTest`.

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

The request-dialog open/close choreography is the holder's too:
`SeerrRequestSnapshot.dialogItem` plus two commands — `openRequestDialog(item)`
sets the item and fires the cascade itself (`loadServiceDetails`, and
`loadTvSeasons` only when mediaType equals "tv" ignoring case), and
`dismissRequestDialog()` clears the item THEN the result, in that order. The
three former per-screen `LaunchedEffect` cascades (media-detail, Seerr detail,
search) render `snapshot.dialogItem` and decide nothing; pinned in
`SeerrRequestStateHolderTest`. Nuance: the dialog's item is frozen at open
time — Seerr-detail used to recompute it from the optimistic PENDING flip;
the in-dialog state change now travels through the result field.

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
`HomeStores` (`HomeStores.kt`) — a construction-time seam,
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

**`HomeDialogSession`** (`HomeDialogSession.kt`) is the open-side twin of
that session: one stateless module (constructed over `onEvent`; dialog
state itself stays in `HomeUiState`) owning the dialog event cascades —
opening the Seerr request dialog fires `LoadSeerrServiceDetails` +
`LoadTvSeasons` (TV-only, case-insensitive), dismissing fires
`SelectSeerrRequestItem(null)` then `ClearRequestResult` in that order, and
the sync sheet's while-open `EnsurePendingItemDetails` mapping lives there
too (empty list included; sheet-visibility gating stays in the screen) —
sequences that were untested `LaunchedEffect` bodies in `HomeScreen`, now
pinned by `HomeDialogSessionTest`. The music fallback-art chain
(AUDIO/MUSIC → parent art → first artist art) and the photo-folder
prefetch narrowing are pure Compose-free functions in `HomeMediaRows.kt`
(`fallbackImageUrls`, `photoFolderPrefetchTargets`), pinned in
`HomeMediaRowsTest`.

The home dock is bundled, not flat: **`HomeDockState`** +
**`HomeDockCallbacks`** (`HomeAppBar.kt`) carry the dock's whole data +
interaction surface, so a dock feature edits the two bundles and the dock
body — not three signatures in lockstep (screen → scrim → dock).
`HomeTopDock` and the scroll-coupled `HomeTopDockScrim` leaf (which owns
the icon-colour lerp, hide-on-scroll, and the query/settings-search leaf
collections) forward the bundles.

The dock's hide-on-scroll is one shared policy with the app-shell floating
nav bar: **`ScrollDirectionVisibility`**
(`shared/core/ui/.../components/ScrollDirectionVisibility.kt`) owns
direction detection, the dead-zone threshold (dock 12dp, nav 15px), the
at-top force (dock ON, nav OFF) and the forced-visible gate; each site
keeps only a thin feed. `HomeTopDockScrim` collects a `snapshotFlow` over
the shared `LazyListState` into `onListScrolled` — offset comparisons are
per-emission, never accumulated, and the first emission (or a `prime` at
effect (re)start) only syncs tracking — while `PhoneContent`'s
`NestedScrollConnection` forwards `available.y` to `onScrollDelta`. The
nav's `LocalFloatingNavVisibility` value is the module's exposed
`visibleState`. Pinned in `ScrollDirectionVisibilityTest` (core/ui
`jvmTest`).

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

## Library & search filters

`LibraryFilters` carries its write algebra beside the value (the
`HomeSectionPrefs` precedent): `withMediaTypeToggled` / `withGenreToggled` /
`withTagToggled` / `withYears` / `withMinRating` / `withSortBy` /
`withPlayedStatus` / `withResumableToggled` / `withDownloadedToggled` /
`cleared`, plus one canonical `hasActiveFilters` fold covering every field
(non-default sort counts as active; tri-state booleans only when `true`).
`LibraryViewModel` and `SearchViewModel` mutators delegate to it (persistence
and public surface unchanged), and both screens' badge/BackHandler guards
read the same fold — fixing library's drift where years/tags/minRating/sort
silently under-reported the active set. Pinned by `LibraryFiltersAlgebraTest`
(core/model commonTest). `RequestsFilterState` (shared/feature/requests) is
the same shape for the admin request queue (`withFilter`/`withSort`/
`withSearchQuery`/`cleared` plus the `withSortDirectionToggled`/
`withMediaType`/`withMyRequestsOnlyToggled` variants, page-1 reset carried by
the `RequestsUiState.withFilterState` fold), and
`RequestsViewModel`'s five mutation commands are one-line delegates onto a
single `runRequestAction` core (the `runBulk` shape; `runBulk`'s own
per-item failure semantics stay separate). Pinned by `RequestsFilterStateTest`.

## Live TV recording & music collections

**`RecordActions`** (`shared/feature/livetv/src/commonMain/kotlin/.../components/RecordActions.kt`)
is the one recording choreography behind every Live TV tab, constructed over
`LiveTvRepository` and the owning ViewModel's scope. Commands
`recordOnce`/`recordSeries`/`cancelTimer`/`cancelSeries` (program-based;
String-id cancels for Schedule/Series) all run the same sequence —
synchronous `RecordOutcome.Requesting`, one repository call, then
`Success`/`Error` carrying the `RecordRequest` identity — surfaced as a
`StateFlow<RecordOutcome>` PLUS a synchronous `onOutcome` callback so the
dialog flip lands in the tap frame. Tabs are adapters mapping outcomes onto
their own refresh and feedback channel (Programs → the shared
`RecordDialogState` + reload; Channel Detail → its `messages` flow +
program-window refresh; EPG → dialog (`Success` carries the program name) +
`loadGuide`; Schedule/Series → sheet-dismiss + reload vs error field).
Absorbs the twelve per-ViewModel mutation blocks and the EPG's duplicate
`RecordDialogState` + private renderer; the single renderer lives in
`RecordManager.kt`. `RecordingsViewModel.deleteRecording` stays put — a
composite choreography (best-effort series-timer cancel before delete, an
`isDeleting` dismissal block), not a RecordActions command. Pinned by
`RecordActionsTest` plus the per-tab suites.

**`SortedPagedCollection`** (`shared/feature/music/src/commonMain/kotlin/.../collection/SortedPagedCollection.kt`)
is the one sorted paged music collection: the `MusicSortOption` `StateFlow`
(the shared enum relocated here from `AlbumsViewModel`), `setSort`, and a
paged `items` flow re-running
`getMediaItemsPaged(LibraryFilters(mediaTypes = listOf(mediaType), sortBy = …))`
per sort change, `cachedIn` the owner's scope. Artists/Albums/Tracks
ViewModels and the browse screen's three pagers are thin adapters exposing
`selectedSort`/`items` under their own names; screens collect `selectedSort`
as state (the Artists/Tracks hand-synced duplicate-state drift is gone).
Pinned by `MusicListViewModelsTest`.

**`InstantMixStateHolder`**
(`shared/core/data/src/commonMain/kotlin/.../playback/InstantMixStateHolder.kt`,
the `SeerrRequestStateHolder` shape) owns the instant-mix choreography that
Album/Artist/Detail ViewModels used to copy three times: the isStartingMix
flag, the first-track one-shot (consumed via `consumeStartedEvent()`), and
the `InstantMixOutcome` → error-message mapping. Constructor takes the
mix-starting seam as a lambda (per-VM adapters normalize their
`AudioQueueOutcome`; the guard veto stays in the adapter), so commonMain
stays pure; `state: StateFlow<InstantMixState>` is the only state surface.
Each VM keeps one delegating fun; the screens collect the single state
(the two former `LaunchedEffect` cascades). Pinned by
`InstantMixStateHolderTest` + `InstantMixOutcomeMessagesTest`.

## Downloads & insights

**`DownloadActions`** (beside `DownloadsViewModel`, the `LibraryFilters`
precedent) is the downloads status/action algebra: `DownloadBulkAction`
(PAUSE/RESUME/CANCEL/RETRY_FAILED/DELETE) over `DownloadActionScope`
(`Item(id)`/`Selected`/`All`), ONE admission table (PAUSE=DOWNLOADING;
RESUME=PAUSED; CANCEL=PENDING/QUEUED/DOWNLOADING/PAUSED; RETRY_FAILED=
FAILED; DELETE=all — derived from the former VM filter lambdas), and the
pure `supports(...)`/`targets(...)` folds. The VM's bulk family is
`applyBulkAction(action)`; the screen's action bar reads `supports()` —
the six former composable predicates are gone, so the screen-enables/VM-
filters drift is structurally impossible. Pinned by `DownloadActionsTest`.

**`HeatmapGridModel`** (beside `WatchProgressHeatmapScreen`) is the
heatmap's Compose-free geometry: week-column grid construction (with the
mid-year `minActivityDate` Sunday backup), quartile level policy,
month-label placement (ISO Mon–Sun week anchoring via
`with(DayOfWeek)` — Sunday-first-of-month labels do NOT hop forward),
`initialFocusedCellIndex`, the no-wrap `clampFocus`, and
`scrollTargetForFocus`. `today` is a parameter, the screen keeps only
dp/px + Canvas drawing. Pinned by `HeatmapGridModelTest` (leap-year
coverage — a 2024 grid is 52 columns and Dec 30–31 fall beyond it —
month-boundary labels, quartile edges, focus clamp).

`DetailPlayPolicies` (details, beside `DetailContentState`) holds the two
pure folds the media-detail callback adapter used to inline six times:
`resolvePlayStreamSelection` (the local-origin subtitle-index policy,
duplicated in onPlayClick/onPlayChapter) and
`requiresMarkPlayedConfirmation` (the series gate, open-coded 4×; season
branches confirm unconditionally via `isSeasonAction`). The adapter
lambdas keep only dispatch. Pinned by `DetailPlayPoliciesTest`.

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
identity from `HomeSession.cacheIdentity()`/`cacheIdentitySnapshot()`. The
get→fetch→put choreography around those reads is **`IdentityCacheFetch`**
(`shared/core/data/src/commonMain/kotlin/.../cache/IdentityCacheFetch.kt`):
`TtlCache.getOrFetch` (plain, plus the `force` freshness lever and the SWR
`onFetched` hook), `getOrFetchGuarded` (epoch captured after the miss; the
write lands only if the epoch is unchanged), and `getOrFetchTyped` (the
`as? V` cast-checked shape over a heterogeneous `TtlCache<Any>` that Seerr's
detail getters need). `MediaRepositoryImpl` and `SeerrRepositoryImpl` go
through it instead of hand-rolling the block; two preserved drifts are
deliberate (studios has no force lever; library folders' force does not
reach its network call). `PlaybackRepositoryImpl`'s segments cache stays
inline — it caches a transformed fallback with conditional caching, a
different shape, and no fourth variant was invented for it.
Below that layer, shared/core/network cannot depend on shared/core/data, so
`LibraryApiClientImpl` keys off the engine's atomic session read directly
(`currentHomeCacheIdentity()`); its favorite-flag cache is an
identity-keyed `TtlCache`, which is why `clearFavoriteCache()` and the
manual call to it from `AuthApiClientImpl.disconnect()` are gone —
disconnect publishes one atomic null session and nothing needs a
hand-rolled cross-module clear.

The **`jellyplay://` grammar** is `DeepLinkGrammar`
(`shared/core/model/src/commonMain/kotlin/.../deeplink/DeepLinkGrammar.kt`):
the scheme + web-host constants, the link builders (media / newsletter / seerr
/ search / settings / downloads / library, plus the GitHub-pages web mirror),
and the `parseCustom`/`parseWeb` → `DeepLinkTarget` fold — pure, no Android
types, round-trip pinned by `DeepLinkGrammarTest`. `DeepLinkHandler` (`:app`)
keeps only the Intent/Uri glue; the four non-app emitters
(`TvWatchNextPublisher`, `NotificationDispatcher`,
`NotificationActionReceiver`, details' share text) call the builders directly,
as do the widget emitters (`ContinueWatchingWidgetService` → `mediaLink`,
`ContinueWatchingWidget` → `continueWatchingLink`, the Library/Seerr
recommendation services → `mediaLink`/`seerrLink`; the shallow
`WidgetDeepLinks` re-encapsulation and its test are deleted) —
the scheme/path vocabulary survives nowhere as a raw literal.

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
(`LrcLibApi`, `LyricsCacheDao`, `NetworkMonitor`) and an injected
`TimeSource` for its clock reads — throttle, cleanup cutoff, `fetchedAt`
stamps (same seam as `MediaRepositoryImpl`). `MediaRepository` does
NOT extend `LyricsRepository`: `AudioLyricsManager` and
`VideoPlayerViewModel` inject the narrow type directly, the app's
`CacheMaintenanceInitializer` injects it instead of the union, and the wasm
`WebMediaRepositoryNarrow` drops the lyrics section (web never served it).
`DataKoinModule` binds `LyricsRepositoryImpl` as its own single;
`DataKoinModulesTest` pins resolution.

**`MediaRepository` union shrink (landed)**: `MediaRepository` no longer
extends `LiveTvRepository` / `SyncPlayRepository` / `NewsletterRepository` /
`PlaylistRepository` — its interface is its own 42 members (the former
86-member union forced every media consumer to learn four unrelated
families). `MediaRepositoryImpl` implements all five interfaces explicitly
and `DataKoinModule` binds the SAME single under each family type (the
`MediaRepositoryCacheInvalidation` same-single-narrow-view pattern), so the
family seams now have two adapters each: the production single and test
doubles. Single-family consumers inject the narrow type (the livetv VMs,
`LiveTvPlayerViewModel`, `NewsletterViewModel`, `SyncPlayViewModel`,
`WatchPartyActions`, `PlaylistTargets`); mixed consumers inject BOTH
`MediaRepository` and `PlaylistRepository` (music browse/playlist VMs,
`AudioPlayerViewModel`, `LibraryLayoutViewModel`, `AudioLibraryBrowser`) —
same instance behind the seam, no body moved. The wasm
`WebMediaRepositoryNarrow` implements `MediaRepository` only (42 overrides,
~196 lines, down from 86/358 — the family throw stubs are gone).

`MediaRepositoryImpl`'s test surface lives beside it in
`shared/core/data/src/jvmTest/.../repository/`: `MediaRepositoryImplTest`
(SWR staleness ceilings, identity-keyed misses, mutation double-evicts),
`MediaRepositoryHomeSectionsCacheTest`, `MediaRepositoryCacheInvalidationTest`,
and `LyricsRepositoryImplTest` (the lyrics chain + eviction throttle on a
fake clock) — ported from the legacy `core/data` suite the KMP move had
stranded non-compiling on the wrong side of the seam (no CI lane compiled
it, which is how the breakage stayed invisible; the legacy files are gone).
The 2026-09-05 dark-lane rescue added the other three sole-coverage suites
to this lane: `PlaybackRepositoryImplTest` (39), 
`UnifiedMediaDetailProviderImplTest` (36) and
`OfflineSyncManagerResyncTest` (20 — the `resyncItem` choreography:
sidecar options, signature rollback, pending-flag retry legs; complementary
to the TTL/baseline `OfflineSyncManagerTest`, kept as a sibling file because
the fixtures conflict). All 95 passed unmodified on their first visible run.
The remaining ~68 legacy test files still run in NO lane — they cover
platform-only playback/cast/worker code and are the deliberate Phase-X husk;
do not port them, and treat a legacy-only assertion as dead when its class
moves to `androidMain`.

The data layer's clock reads go through the injected **`TimeSource`**
(jvmShared `util/TimeSource.kt`, the Koin-single `SystemTimeSource`): every
behaviour-bearing decision — `PlayedStateSyncImpl`'s server-vs-local
reconcile guard, `OfflineSyncManager`'s SYNC_TTL gate,
`OfflineSyncComparator`'s `lastCheckedAt` stamp,
`AdminStatisticsRepositoryImpl`'s 90-day prune — plus the stamp-only writers
(`AuthRepositoryImpl` lastConnected, `ItemPlaybackPreferenceRepositoryImpl`,
`SearchHistoryRepositoryImpl`, `PlaybackOutboxRepositoryImpl`,
`ServerHealthMonitor`, `MoodPlaylistRepository`, `DownloadRepositoryImpl`'s
baseline seed) now take the seam in their constructors;
`PlayedStateSyncImplTest` pins the reconcile ladder on a fake clock.
`SeenMediaRepository` keeps its wall-clock default argument (an interface
default consumed outside the data layer). **`ImageUrlProviderImpl`** moved
to jvmShared beside the `ImageUrlProvider` interface: one memoisation
implementation (perf-mode 300/400 clamp, `p_|b_|c_` key grammar,
put-only-on-non-empty, null-width bypass, 512-entry bound) bound by both the
Android and desktop DI modules — the `android.util.LruCache` and desktop
`LinkedHashMap` twins are gone, pinned by `ImageUrlProviderImplTest`.

The 2026-09-05 review wave deepened four more repository internals (public
interfaces unchanged): **`SeerrRepositoryImpl`** folds its 27 hand-copied
url+credentials guard ladders into one `withSeerrSession` seam with ONE
canonical unconfigured failure (the drifted "Server URL is required" /
"Seerr not configured" pair is gone; the three pre-session url-only ladders
in the login/test-connection members stay — they establish the credentials
the folded ladder resolves). **`ArrRepositoryImpl`** dispatches through an
`ArrServiceClient` seam (the shared Radarr/Sonarr subset; two ~30-line
adapters + `clientFor(server)`) with one `fanOut` helper — the 18
`if (kind == RADARR)` ladders and the per-method semaphore scaffolding are
gone; `postCommand` carries the union of the two clients' signatures
(Radarr movieIds vs Sonarr seriesId/seasonNumber), each adapter forwarding
only its own. **`AdminStatisticsRepositoryImpl`** runs both media scans on
one private `runScan` chassis (paging/progress/cancel/persist/fail; the
per-scan continuation rules live in the fetch closures) and its enhanced
stats through one builder (fallback = `build(null)`); every clock read goes
through the injected `TimeSource.today(zone)` — the four direct
`LocalDate.now()` reads are gone, and month-boundary math is fake-clock
pinned. **`PlaybackRepositoryImpl.getMediaSegments`** rides
`SingleFlightFetcher(segmentsCache, segmentsEpoch)` like the detail cache
(the intro/credit fallback wave is the fetch lambda; a failed API fetch
bumps the epoch to veto the write-back, preserving the empty-vs-failed
caching policy; `invalidateSegmentsCache` removes + bumps). The legacy
sync workers reach the wholesale cache drop through the one-member
`MediaCacheInvalidator` port (bound in `DataKoinModule` to the same
`MediaRepositoryImpl` single) instead of the concrete 1009-line class. In
the legacy `core/data` cast corner, `CastStrategy` gained transport
members (`play`/`pause`/`seekTo`/`setRendererVolume`/`loadMedia` as
interface defaults, overridden by the DLNA and Jellyfin-remote strategies;
the local Google-Cast player rides a manager-owned adapter) —
`CastManager`'s five strategy-name when-chains collapsed to one
`activeTransport` resolver and `cancelJobs()` deduped the teardown
triplication. `updateCastState`/`toggleTicker` stay hand-folded
deliberately (per-branch state writes and predicates that map to no
strategy member); compile-verified only — the legacy Robolectric suite
runs in no CI lane.

## Library client policy (network)

**`LibraryRequestPolicy`** (`shared/core/network/src/commonMain/kotlin/.../library/LibraryRequestPolicy.kt`)
is the one home for the request-level policies the `LibraryApiClient` twins
(`LibraryApiClientImpl`, `KtorWasmLibraryApiClient`) used to ship hand-copied
per source set: the 12-field detail projection (`DETAIL_PROJECTION_FIELDS`),
the jellyfin-web search-suggestions shape, the SEASON/EPISODE exclude-drop,
the empty-library fallback ladder (`EmptyLibraryFallback` + the known-empty
memo probe and `emptyFallbackTotalCount`), and the favorite-flag cache-aside
toggle (`FavoriteFlagCache` over an identity-keyed `TtlCache`, 200 entries /
15 min). Each client resolves the shared wire names against its own
enum/wire dialect and supplies only transport lambdas plus its platform
memo/threading regime (JVM: synchronized access-order LRU probed with
`containsKey`; wasm: lock-free remove+reinsert — a documented divergence,
not a copy). `JellyfinApiEngine.ratingToAge` and
`JellyfinDtoMappers.parseItemSortList` delegate to the canonical commonMain
tables (`parentalRatingAge` / the sort-token parser) instead of carrying
"verbatim" twins, and the wasm lyrics DTO mapping lives in
`LibraryWireMappers` (the SDK-typed jvmShared mapper stays — its input type
is invisible to commonMain). Both clients compile against the single policy
in `:shared:core:network:jvmTest`; the wasm client has no test lane of its
own, which is exactly why the policies must not live there.

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

**Shell section graph** (`shared/feature/shell`, the star-topology
aggregator): `appSections(scope, host: ShellHostHooks)` registers the 20
shared feature sections in ONE canonical order (Android's), with
`MusicHomeScreen`'s 9 nav lambdas wired once (the 7 identical one-liners
inside the module; the 2 audio-source reads supplied by `host`). The shells
keep only their source-set-conditional entries inline (Android:
`livePlayerSection`, `subtitleTesterSection`, `Route.PlayOnCompanion`;
desktop: the bridge-probed `Route.VideoPlayer`). `ShellSectionRegistry` is
a registration ledger: `shellEntryProvider` stamps a sentinel contentKey on
its fallback entry, so `isRegistered(route)` is a derived test — the
desktop dead-end guard is routes-minus-registered, and the hand-kept
"keep in sync" three-route mirror is gone. Admin/logout/homeMode policy
stays per-shell (Android: `MainViewModel`; desktop: inlined) — it was
deliberately NOT absorbed into the hooks: one consumer per policy, and
forcing it through would drag `AuthRepository` into a "shared" module for
one shell's sake.

The shells share the platform-free shell policy in `shared/feature/shell`:
**`AdminRefreshGate`** is the admin-status dedupe (30 s window + in-flight
guard, success-only `onRefreshCompleted` stamping — a failed refresh must
not push the next attempt a full window out) constructed over each shell's
own in-flight flag (read through a lambda) and a wall-clock lambda; Android's
`MainViewModel.refreshAdminStatus` and the desktop scaffold's lambda both
arbitrate through it, and the duplicated `ADMIN_REFRESH_INTERVAL_MS`
constant is gone. The rendered in-flight/admin state stays per-shell as
recorded. In `:app`, `RemoteNavigationRouting.kt` holds the remote-target
decisions as pure functions: `routeForNavigationTarget(target)` (exhaustive
`when` — a new server-emitted target is a compile-time decision, not a
silent `Route.Home` fall-through) and `popPlayerRoutes(backStacks)` (the
Jellyfin-web "Stop" semantics: contiguous player entries popped off the top
of every back stack). Pinned by `AdminRefreshGateTest` (shell jvmTest) and
`RemoteNavigationRoutingTest` (app unit test); `ShellSectionRegistryTest`
pins the ledger mechanics — the sentinel contentKey identity for
unregistered routes, replace-on-re-attach, and a non-null fallback entry.

## Settings search

The settings-search knowledge lives in `shared/feature/settings`, next to the
screens it deep-links into — not in shared/core/ui. Each screen (or screen family)
declares its items in a `*SearchItems.kt` file co-located with the screen
(`PlaybackSettingsSearchItems.kt` beside `PlaybackSettingsScreen.kt` also
hosts the MPV/VLC/ExoPlayer engine, SyncPlay, casting and Live TV & DVR
groups). Every item is a
`SettingsSearchItem(id, titleRes, subtitleRes, categoryRes, keywords, route,
icon, isAdvanced, platforms)` (the `*Res` fields are Compose `StringResource`s —
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

## Settings platform visibility

**`SettingsCapabilities`**
(`shared/feature/settings/src/commonMain/.../SettingsCapabilities.kt`) is the
one visibility surface for platform-gated settings: `settingsCapabilities`
is an expect val with an androidMain and a jvmMain actual, and each flag
answers "can this binary's settings surface offer this row?" — hidden means
structurally absent, never rendered-then-disabled. The ownership rule is the
module's KDoc: capabilities own VISIBILITY; the behavior seams
(`BiometricGate`, `LogCollector`, `PlatformIntents`, `SettingsMessenger`)
own BEHAVIOR (a seam may be refactored to expose its truth for pinning —
never re-behaviored). Flags with a queryable desktop seam
(`supportsSystemNotificationSettings`, `supportsLogSharing`,
`supportsBiometric`) are pinned beside that seam's actual in
`DesktopPlatformActualsTest` — one review home for the platform's truth;
the rest pin the platform fact directly (e.g. `DesktopAppLocaleSetter`'s
no-op has its own test). (`supportsBiometric` is the one with
device-level nuance: Android offers the row for the platform's biometric
APIs, and a device without hardware still nulls the runtime gate — the
screen requires the gate before rendering or counting the row.) Two axes,
never mixed: platform
(ANDROID/DESKTOP/WEB —
compile-time, via `currentPlatform` in core/model) is what capabilities
express; TV vs phone is the runtime `LocalTvMode` composition local and
stays in core/ui.

Engine availability is declared data, not spread: `platformEngineSupport`
(`shared/core/model/.../PlatformEngineSupport.kt`, beside the `PlayerType`
enum) declares which engines a binary ships and the fallback default. Its
actuals MUST mirror which engines the platform's `PlayerEngineFactory`
builds as real, selectable engines — not every `when` branch (desktop
rides Exo/VLC on mpv as a stand-in and no-ops EXTERNAL, but only MPV is
offered). The compiler-forced exhaustive `when` is the source of truth;
the desktop actual is pinned in `DesktopPlatformActualsTest`, and the
Android actual is `PlayerType.entries.toList()`, which auto-syncs with any
entry the `when` is forced to decide. Consumers:
`PlaybackStore.readPreferredPlayer` clamps at its single read choke point
(`normalizePreferredPlayer` — non-destructive, so a cross-platform backup
restore degrades to the platform default instead of reaching an
unregistered factory), the playback settings engine picker filters on it,
and the search catalog tags engine items accordingly.

Search never offers what the surface cannot render:
`SettingsSearchItem.platforms` (defaults to all; the package-level
`ANDROID_ONLY_PLATFORMS` tag lives in feature/settings) is applied at the
`SettingsSearchCatalog.resolved()`/`recentItems()` funnels and the
`SettingsSearchProvider.resolved()` funnel (feature/home's header search
consumes the provider, so it inherits the same filter) through the pure
`filterFor` — `items` itself stays the unfiltered catalog for the integrity
counts. The platform ratchet is `SettingsSearchCatalogPlatformFilterTest`
(desktop-filtered catalog drops every Android-only id; the whole
notifications and Exo/VLC engine-config lists are asserted absent) plus
`SettingsSearchFlowTest`'s funnel pin (the pipeline matches against
`resolved()`, never raw `items`).
Known residue, deliberate: the hand-typed scroll-group id lists
(`PlaybackSettingsScreen.kt`) and the TV / advanced / admin search
dimensions are not yet derived from the catalog declaration; TV-only items
stay tagged ANDROID (they are a runtime-axis problem).

**Drag-to-reorder.** `ReorderState<T>`
(`shared/feature/settings/src/commonMain/kotlin/.../ReorderState.kt`) is
the drag-reorder policy module: the working order, the per-item height
table, and the whole crossing decision live inside `drag(item, deltaY)`
(strict half-height midpoint, neighbour's height charged against the
accumulated offset — which accrues even before the row's height is
measured — end-clamping, unmeasured neighbour borrowing the dragged
height); it returns `true` exactly when the order changed, which is the
composables' mirror-resync + write-on-diff persist signal. `submitOrder`
is the stored-prefs resync, `recordHeight`/`beginDrag`/`endDrag` the rest
of the small interface. The three former inline copies (Appearance home
sections, Appearance newsletter sections, `NavigationCustomizationGroup`)
drive it; `resolveOrder` there is the generalized stored-order-vs-known-
items merge (append-missing). Pinned by `ReorderStateTest` +
`ResolveOrderTest` (jvmTest): sub-threshold drags are no-ops, so persist
stays silent when nothing moved.

**`ReorderableOrderedListState`**
(`shared/feature/settings/src/commonMain/kotlin/.../ReorderableOrderedList.kt`)
is the choreography owner around the `ReorderState<T>` arithmetic: the
observable mirror list, the store-emission resync, the write-on-diff persist
and the drag callbacks live here once — the three former hand copies
(Appearance home sections, Appearance newsletter sections,
`NavigationCustomizationGroup`, whose remember-keys reseed had already
drifted from the guarded-resync majority) are now content:
`rememberReorderableOrderedList(storedOrder, onPersist, knownOrder)` + row
slots. Pinned semantic: store emissions apply only while idle (mid-drag
emissions are ignored, never queued — the drag's final order wins), the diff
base is the last seeded or persisted order, and persistence fires once at
drag end. `ReorderState` itself is unchanged; `ReorderableOrderedListTest`
pins the decisions beside `ReorderStateTest`/`ResolveOrderTest`.

`NewsletterSectionPresentation.kt` (beside `AppearanceSettingsScreen`) is
`NewsletterSectionType`'s presentation vocabulary — exhaustive
`labelRes`/`descriptionRes` + `newsletterSectionIcon()`, Compose resources
resolved at render time, kept in feature/settings because the only renderer
lives here (the `HomeSectionType` precedent's shape, not its letter — that
descriptor hardcodes English strings on the model). The equalizer editor is
`EqualizerEditorSheet.kt` behind one boolean slot — the last per-screen
dialog hierarchy `PickerState` missed (deliberately not a `PickerState`
variant: a multi-slider editor with an Apply step is not a payload picker).
`AdvancedSettingsGate` is the single implementation of the advanced-gate
pair nine settings ViewModels hand-copied verbatim; each VM keeps its
public members and delegates (the settings-root `SettingsViewModel` receives
the store via its constructor + Koin def).

The settings screens hold the datastore seam directly: each per-screen
settings ViewModel (Appearance/Playback/Audio/Storage) exposes one
`edit(transform: suspend (PreferencesEditScope) -> Unit)` command — the
`NotificationSettingsViewModel` shape — instead of a per-field forwarding
stratum (201 one-line renames deleted across the four VMs + Onboarding).
Each VM keeps only the members that decide something: the gate pair, the
category resets, the side-effect-carrying commands (`setAndroidTvWatchNextEnabled`
with its `watchNextRefresher` poke, `setAutoDownloadNewEpisodes` with its sync
poke, `clearAudioCache`, the storage FS walks). The screens read state through
the unchanged composeState projections and issue `viewModel.edit { … }` — the
`HomeStores` construction-seam precedent. `SettingsScreen`'s scaffold is
single-homed too: `settingsSection(key, phoneStep, tvStep)` owns the entrance
+ TV/phone step arithmetic, `openSetting(id, route)` is the one
highlight-then-navigate dispatch (the row path now rides the same
choreography as `onResultClick`), and `dismissSearchAndRefocus()` is the
close triple — composable-identical output.

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

- **Audio playback snapshots**: `AudioPlaybackManager`'s flow members (47
  today, 112 public members total, 14 consumer files) fold into
  now-playing / queue / effects / connection snapshots
  per the `SeerrRequestStateHolder` pattern. Deferred because the consumer
  files across app/widgets/tile rewrite onto it at once and nothing pins
  current behaviour — do it when audio/cast churn resumes, tests first.
  The 2026-09-05 session landed four of the five recorded blockers.
  `WidgetPushSnapshot` + `sameRenderAs` / `sameNonPositionRenderAs` /
  `shouldPushPartialPosition` are pure in `app`'s widget package, pinned by
  `WidgetPushSnapshotTest`, so the partial-vs-full RemoteViews push race
  guard survives the fold. **`NowPlayingTracker`**
  (`shared/core/data` commonMain, beside `AudioPreferencesReducer`) is the
  sole writer of the six now-playing metadata flows: the sequence the
  manager had written 4× (`play`'s detail path, `play`'s local-file
  fallback, `onTrackTransitioned`, `onCrossfadeTransition`) is now three
  publish shapes — `publishDetail` / `publishQueueItem` / `publishLocalFile`
  — plus `clear()` on stop, each recording its deliberate divergence (queue
  transitions leave `artistId` untouched because `AudioQueueItem` carries
  no artist id; the local fallback also leaves `albumArtUrl`; `clear()`
  never resets `artistId`), pinned by `NowPlayingTrackerTest`. The manager
  re-exposes the tracker's flows by reference (same instances), so all 14
  consumers and the widget are unchanged. The manager's test seam is two
  defaulted ctor params (`playbackScope`, `playerFactory`) — production DI
  untouched. The mini-player wiring's 3× paste in `JellyPlayApp` is one
  `AppMiniPlayerHost` (collects the flows once, takes `title` as a
  parameter because TV's hoisted collect also feeds the drawer's Now
  Playing row; per-site modifier/offset slots). `NowPlayingWidgetPolicy`
  (the responsive layout ladder + position/seek/progress math + metadata
  fallbacks, pure, pinned by `NowPlayingWidgetPolicyTest`) completes the
  widget package's tests-first base. Still blocking the fold: the effects
  toggle path applies twice (VM immediate apply + the
  `AudioPreferencesReducer` diff — the reducer tracks only the last store
  slices, not processor state; fixing means a processor-state read-through
  or dropping the immediate apply, a behaviour-timing change deserving a
  listen-pass) and the fold itself (the consumer rewrites onto snapshots).
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
- **Wire-request twin unification**: the wasm↔JVM API-client pairs
  (Library/Seerr/Sonarr/Radarr/Auth/Playback/User/Tmdb, ~1,500 mirrored
  assembly lines) hand-copy endpoint paths, query assembly, bodies and
  error strings request-for-request — maintained by comment discipline
  (the played-status filter bug had to land in both copies; wasm has no
  test lane, so drift is invisible by construction). Design: commonMain
  `WireRequest` spec values + a ~60-line per-platform `WireExecutor`
  (OkHttp vs the wasm Ktor mechanics), error taxonomy as one commonMain
  table; specs become plain-value tests. Deferred: ~7k lines of surface
  across both platforms — land per family (arr first, piggybacking
  `ArrServiceClient`), in a dedicated session.
- **Widget grid skeleton**: the three RemoteViewsFactories share a
  byte-identical skeleton (snapshot read → poster preload → dims refresh →
  deep-link `getViewAt`), the providers share refresh-scope + height
  thresholds + PendingIntent wiring, and `WidgetPersistHelper` carries the
  same persist function twice — the blank-widget version-bump bug was
  fixed in two copies with the same comment. Design: abstract factory +
  provider bases over (snapshotProvider, posterUrlOf, bind, stableIdOf) +
  one generic persist. Deferred: the widget package carries in-flight
  feature work — land first thing after it commits.
- **`DetailViewModel` intent fold**: ~29 public funs force the 160-line
  hand-built `DetailContentCallbacks` adapter in `MediaDetailScreen`
  (keyed on 15 values). Design: sealed `DetailIntent` + `onEvent` (the
  `HomeViewModel` pattern); `DetailPlayPolicies` (landed) already carries
  the load-bearing pure decisions so the fold inherits tested arms.
  Deferred: 1742-line existing suite + screen wiring deserve their own
  session.
