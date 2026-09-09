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
  `EngineEventCoordinator` lifecycle and its decision fan-out. Every
  engine-adopting path outside `SessionLoadPipeline` rebinds through one
  funnel, `rebindSessionTracking(itemId, trackProgress = true)` (reload ×4,
  retry, reclaim); cinema pre-roll intros call it with
  `trackProgress = false` (not part of library history) — the former
  `afterEngineReload…` helper's KDoc claimed a consolidation its own
  reclaim/cinema bodies contradicted; now true, and pinned (reclaim trio +
  cinema suppression in the session suites).
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

The VM's three playback-pref setters (`setPlaybackMode`,
`setStreamingQuality`, `setAdaptiveBitrateEnabled`) funnel through one
private command, `applyPlaybackPrefChange(mode, quality, persist)` —
guard/coordinator/mirror-write stay per-setter (different fields, same
order), the command owns launch → persist → reload with EXPLICIT
post-change values; `reloadPlaybackForMode(mode, quality)` no longer reads
the ui-prefs mirror back (previously correct only because each setter
wrote the mirror first — undocumented and untested; now pinned at the
session seam in `PlaybackSessionReportingTest`).

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

**`DetailUiState.clearedForReload(keepDetail)`** (beside the data class,
the `VideoPlayerUiState.keepAcrossItems` twin) is the details feature's
navigation/refresh reset declared once: survivors are its constructor
arguments — `detail` on refresh, the three connection-level Seerr leaves
(`isSeerrConnected`, `isSeerrRecommendationsEnabled`, `seerrRequest` —
re-folded by the VM's outer combine anyway) — `loadState` flips
Loading/Refreshing per flavour, and every content slice resets
structurally, `sortedEpisodes` included (the former 34-line inline copy
cleared seasons/episodes/fetchedSeasonIds but leaked the sorted mirror
across navigation into smart-play resolution — the drift the builder
kills). Pinned in `DetailViewModelTest`.

**`DetailContentCallbacks`** (beside `DetailContentState`) is bundled,
not flat: nine `@Immutable` per-concern bundles — `ArtworkCallbacks` (3
URL getters), `PlaybackCallbacks` (10: play dispatches incl. chapters/
extras/album tracks, stream + local-subtitle selection, instant mix,
watch party), `DownloadCallbacks` (11: picker, per-series download,
delete family, resync + download-details entry points), `SeasonsCallbacks`
(6: season select/pin, ordering + compact toggles, season-level
mark-played), `UserDataCallbacks` (9: favorite + played toggles, the
hide/show next-up + continue-watching + detail-up-next trio), plus
`SeerrCallbacks`, `AddToCallbacks`, `NavigationCallbacks`,
`ScreenCallbacks` — each defaulting to its own no-op instance, so a
section capability edits its bundle + its section instead of the former
5-file keystroke (DetailUiState → DetailContentState → screen wiring →
callbacks → section). The grouping rule is "the section it serves":
season-level mark-played rides `SeasonsCallbacks` while the item-level
toggle rides `UserDataCallbacks` (the shared dispatch is not the
grouping unit), and the download-lifecycle deletes ride
`DownloadCallbacks` even though the seasons section consumes them.
`MediaDetailScreen`'s container `remember` keys on the nine bundles
(was 17 flat keys whose wiring had already drifted once at 22); each
bundle's own keys are exactly the locals its lambdas capture, so no
bundle can go stale now that the others are stable. Four dead lambdas
died with the fold (`getSeerrPosterUrl` — Seerr cards read
`seerrItem.posterUrl` — plus `onResync`/`onRedownloadMedia`/
`onClearResync`, whose sheet calls `viewModel.resync.*` directly), and
`DetailViewModel.getSeerrPosterUrl` with them. The ~35-field
`DetailContentState` projection stays flat deliberately (Compose
skippability, not behaviour-hiding).

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
(full-screen loader, playback-outbox drain through the injected
`awaitOutboxDrained` seam, 30 s-capped fetch; the timeout `finally`
force-clears the loader so a hung fetch can never park the handshake —
the Go Online spinner cannot hang the same way, the flag clears at the
ONLINE emission before any fetch starts). The going-online BUSY flag itself is NOT the refresher's — its
one owner is `OfflineModeManager.goingOnline: StateFlow<Boolean>`
(`GoingOnlineFlag`, core/data `offline/`): `toggleManualOffline()` arms it
on the store-snapshot direction (an OFFLINE_AUTO toggle goes further
offline and never arms), the flag's own collector clears on any ONLINE
emission (before any fetch starts, so a hung fetch structurally cannot
park it), and a 30 s watchdog force-clears any flag still up at the
deadline — the production case being a lost preference write whose ONLINE
emission never lands, and the deadline being unconditional is what lets
`arm` raise without a mode-value refusal (the managers derive the
persisted mode asynchronously over an ONLINE-initialized flow, so a
refusal keyed on the live value would misread that cold-start default as
"already online" and silently skip the Go Online spinner on the very
first toggle) — covering the nav-⋮ toggle even when Home's
refresher is not alive. `HomeViewModel` folds the manager's flow into
`HomeUiState.isGoingOnline`; the refresher's `request(GoingOnline)` is the
manager toggle plus the refresher's emission-driven handshake (the
drain+fetch choreography itself runs in the offline-mode observer on the
ONLINE emission, not inside `request`).
`RefreshTrigger` is the folded entry table: the fetch-flavour
triggers (`Manual`, `PullToRefresh`, `PrefsChanged`, `UserDataChanged`), the
going-online kick and the identity triggers (`refreshForUserSwitch`-,
`onSignedOut`-, `fetchDiscover`-shaped) are enum values routed through
`request`; the online→offline content drop (`dropOnlineContent`) stays a
private method the offline-mode observer calls directly. The refresher
reacts to ALL offline-mode emissions (app-start and external/auto flips
included) and runs the drain+fetch handshake on EVERY offline→online
transition — every user-initiated toggle (Home's Go Online button, the
nav ⋮ toggle, which route through the same
`OfflineModeManager.toggleManualOffline`) additionally raises the busy
flag the Go Online spinners render from, via the manager's snapshot-gated
arm; only auto/external transitions (network restoration, auto-detect
reconnects) run the handshake without it. It is the SOLE writer of
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
series download/delete sheets), search-history edits, settings-result
clicks, section-config sheet writes, user switching and the offline
toggle — arrives as a `HomeUiEvent` (`HomeUiEvent.kt`) and is routed once:
pure-forwarding events go straight to their holder in the `when` (the
series download/delete sheets, search query/history edits, sync — no
one-line delegate stratum survives), anything with VM-side logic keeps a
private handler; there is no per-action command method to keep in sync
with the screen. The VM's remaining orchestration is folding
`HomeRefresher.state`, `OfflineHomeGate.state` and the manager's
`goingOnline` flow into `HomeUiState` (the refresher fold covers
`sections` and `offlineMode` — single writer, VM only folds), the preference mirrors
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

**`LibraryItemsQuerySpec`** (commonMain `library/`, 2026-09-10) is the
request-SHAPE half of the twin convergence: the five non-trivial read
endpoints of the library client pair (`getMediaItems`, `getSearchHints`,
`getFavorites`, `getItemsByGenre`, `getItemsByStudio`) build ONE pure spec
(include/exclude kinds, filters, sort tokens + descending flag,
paging, fields — the path stays adapter-side: both clients hit `/Items`
with their own per-client defaults) via `build*QuerySpec` beside the
assembler/fetcher — the
JVM client resolves spec → Jellyfin SDK typed args
(`LibraryItemsQueryResolvers`, jvmShared) and the wasm client renders
spec → raw query strings, so a filter decision (played-status,
resumable, sortOrder normalization, `libraryExcludeKinds` pruning,
empty-gating) is written once and pinned once by
`LibraryItemsQuerySpecTest` (commonTest — runs on both lanes' JVM
runner; the wasmJs node lane never executes in CI, mirror-contract +
spec pins cover it). Trivial fixed-path endpoints deliberately keep
their per-client one-liners.

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
The enrichment half is one private `enrichEach` core now (distinct-id fan-out,
`Semaphore(4)`, per-item failure swallowed) whose map merge is atomic:
every completion folds through `updateState { }` —
`Snapshot.withMutableSnapshot` over the Compose-snapshot `_state` — so two
enrich coroutines completing concurrently can no longer each copy the same
stale base map and silently drop one result (the lost-update the old
`_state.value = _state.value.copy(...)` pairs allowed); `removeQueueItem`'s
two-key eviction rides the same fold. Pinned by
`RequestsViewModelEnrichMergeTest` (interleaved completions, dedupe,
failure swallow).

`AlphabetRailGeometry` (beside `LibraryScreen`, the `HeatmapGridModel`
precedent) is the alphabet rail's Compose-free math — `indexAt(y)`,
`fisheyeScaleAt` (with its peak/sigma constants), the tap/drag jump-target
letter fold, rail-end clamps — extracted from private functions inside the
~1900-line screen file; the composable keeps dp/px conversion, drawing and
pointer wiring. `groupByLabel` stays in the screen deliberately (it resolves
`stringResource` display labels). Pinned by `AlphabetRailGeometryTest`
(fisheye edges, `#`-bucket targets, clamps, degenerate rail height).

## Live TV recording & music collections

**`LiveTvTimeFormat`** (`shared/feature/livetv/.../LiveTvTimeFormat.kt`)
is the Live-TV feature's one timestamp/airing vocabulary:
`toInstantOrNull` (the EPG's loose ISO_DATE_TIME + bare-LocalDateTime→UTC
ladder, kept verbatim — the canonical parse), `formatLiveTvTime` /
`formatLiveTvDateLabel` over one shared offset-then-naive-local core (the
string-munging fallback lives once, byte-identical), the `isAiringAt`
predicate (half-open `[start, end)`, null bound = unconstrained), and
`liveProgressFraction` (epochSecond math, clamps, `Float?`). Declared fix
(2026-09-07): channel detail's five strict `Instant.parse` sites now parse
leniently, so offset-less server timestamps no longer make the
ended-filter, airing check and live progress bar disagree (an ended
program with an offset-less endDate is dropped instead of lingering).
Pinned by `LiveTvTimeFormatTest`; `EpgGridLayout` keeps only its
`startInstant`/`endInstant` program folds.

The livetv VMs read the clock through the injected **`TimeSource`**
(2026-09-07, the `HomeRefresher` pattern — the Koin single the feature
already resolves): `EpgViewModel`, `ProgramsViewModel` and
`ChannelDetailViewModel` take it in their constructors; the EPG guide
window is ONE file-private pure `guideWindow(now)` used by both the
boot defaults and every fetch pass (the init/fetch formula drift is
dead), and the jellyfin-web-derived 5-minute staleness constant is ONE
`LIVE_TV_STALENESS_INTERVAL_MS` in `LiveTvTimeFormat.kt` shared by the
EPG refresh loop and the Programs full-render throttle — fake-clock
pinned (`EpgViewModelTest` asserts exact window bounds now, replacing
its real-clock tolerance workaround). `ChannelDetailContent`'s
`rememberLiveProgress` keeps its wall-clock read deliberately (a
render-clock composition, documented). **`ImageUrlProvider.getImageUrlOrNull`**
(interface default beside `getImageUrl`) owns the "no image tag means
no image" policy the five livetv VMs used to hand-copy; the policy
itself is pinned in `ImageUrlProviderImplTest`.

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

**`downloadedSeasonSlices`** (beside `DeleteDownloadedEpisodesSheet`,
core/ui, the `MultiEpisodeSelection` placement precedent) derives the
delete sheet's seasons/episodes pair once: drop the episode map's empty
seasons, keep only season rows still keyed in it, order preserved. The
only-downloaded rule stays the caller's half — no map↔list intersection
(select-all's selectable set reads the map's keys). The detail screen and
home's `SeriesDeleteStateHolder` call it (holder sizes still derive from
offline items); pinned by `DownloadedSeasonSlicesTest` (core/ui jvmTest).

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
`media-identity-clear` action: `DetailCacheGroup.invalidateAll()` — the detail
cache's epoch bump + similarCache companion, which a plain registry drop
can't express; routing through `invalidateCaches()` would clear every
cache twice and double-bump the catalogue's epoch — + the previous
identity's SWR room rows), `episode-catalogue` (an action —
`invalidateAll()` also bumps the in-flight epoch, which a bare cache clear
wouldn't), `playback` (the media-segments cache) and `seerr` (the detail
cache). Reactions run in registration order.

`media`'s detail-side caches are one file-private **`DetailCacheGroup`**
(beside `MediaRepositoryImpl`, the `EpisodeCatalogue` shape in miniature):
the detail/similar/tracks/themes quartet, the shared epoch (guarded
`getOrFetchGuarded` + the SingleFlight detail fetch), and the item-scoped
KEY GRAMMAR — similar's get key is derived from its evict-all-limits
prefix (`similar_$id` + `_$limit`), so the historical get-key/evict-prefix
drift (a no-op invalidation pinning every limit variant for the TTL) is
structurally impossible; themes/tracks are unshaped. The group's
`invalidateItem` is the old `invalidateDetailCache` body (epoch bump +
detail remove + similar prefix + themes prefix), `invalidateUserData`
returns the pre-eviction detail for the repo's series resolution, and the
group contributes `[albumTracksCache]` to the registry's cache list (the
one member a plain clear fully invalidates — the detail/similar/themes
trio rides the `media-identity-clear` action's epoch bump instead). Adding
an item-scoped cache is now a group-internal edit, not the five-site
ritual. Pinned by `MediaRepositoryDetailCacheGroupTest` (limit-variant
co-eviction, the force lever, wholesale theme clear). Session- and identity-path
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
No legacy unit-test file runs lane-less anymore: kmp-build.yml's android-app
job executes :core:data:testDebugUnitTest (the 69 Robolectric files — cast,
worker and playback platform code included),
:core:notification:testDebugUnitTest (8) and :core:ui:testDebugUnitTest (14,
incl. RoutePredicatesTest and TvDrawerFocusWiringTest) — the 2026-09-08
dark-lane rescue opened the last of them, so the former "~68 files in no
lane" husk is closed (keep the Phase-X rule itself: treat a legacy-only
assertion as dead when its class moves to `androidMain`). Still dark is
execution, not compilation: the instrumented androidTest sources are
compile-gated only — :core:ui via :core:ui:assembleDebugAndroidTest, :app
via :app:assemblePhoneDebugAndroidTest, no emulator lane — and apps/web's six
wasmJsTest files are compile-gated (:apps:web:compileTestKotlinWasmJs in the
shared-targets matrix) but never executed in CI; the runnable
wasmJsBrowserTest lane (karma/webpack + headless-Chrome npm graph) stays
local.

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
pinned. Its two pages share one `buildUserStatistics` (the detail page's
inline copy — drifted completion-rate math, omitted `isCurrentlyActive` —
is gone; the detail path deliberately takes `isActive = false`: no session
source there and nothing on it renders the flag) and one `whenPlugin` fold
for the six plugin-gated list fetches (caller-captured `_pluginStatus`
read — a page's gates see one status even if an admin refresh lands
mid-load; the enhanced wave's group gate stays open-coded because its
non-null deferred bundle IS the downstream gate in
`buildEnhancedStatistics`). **`PlaybackRepositoryImpl.getMediaSegments`** rides
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
strategy member); behaviour-pinned by the legacy Robolectric suites
(CastManagerTest/JellyfinRemotePlayCastStrategyTest/DlnaCastStrategyTest),
which run in CI via the :core:data:testDebugUnitTest lane.

The 2026-09-07 review wave deepened the auth establishment path and the
server-address vocabulary. **`AuthRepositoryImpl`** folds the
`switchServer`/`switchUser` session-establishment choreography into one
private `adoptPersistedSession(serverEntity, userEntity, caller,
persistStamps)` — disconnect → `setServer` → failover → `setUser` →
stored-token-401 guard (caller-named log line) → `setActiveSession` → one
`withTransaction` of caller-supplied `lastConnected` stamps; a null
resolved user still runs the chain head. The best-effort failover body is
`selectReachableAddressDefensively()`, shared by `restoreSession`'s
bounded stages; `restoreSession` itself stays hand-rolled (timeout staging,
`clearSession` teardown — the divergence is documented in the helper
KDoc). **`normalizeServerAddress`** (core/model `ServerAddress.kt`, pure,
pinned by `ServerAddressTest` in commonTest) is the one server-address
typing policy — trim, trailing-slash strip, `https://` defaulting; the
eight copies across core:data / core:network (jvm + wasm failover probing)
/ auth's TLS-trust prompt / settings' trust toggle now call it, and the two
private twins are gone. Trim-only sites (`switchServerAddress`,
`NetworkOfflineStore`, `ServerAddressRouter`, `SocketUrl`) are a different
policy and stay local.

The 2026-09-07 second wave deepened the paged reads and the telemetry
capture side. **`JellyfinPagingSource`** (`shared/core/data` commonMain
`paging/`) is the ONE paged source: the refresh-key and next/prev page math
that `MediaPagingSource`/`FavoritesPagingSource`/`SearchPagingSource` used to
hand-copy three times now lives behind one `fetch(startIndex, limit)` lambda,
with `pagedMediaPager` owning the single `Pager`/`PagingConfig`
(`PAGE_SIZE = 50`, `PREFETCH_DISTANCE = 20`) and the blank-query guard
surviving as the `MediaRepository.searchPagingSource` extension (pinned at
that exact seam). The three classes and their three suites are deleted —
`JellyfinPagingSourceTest` pins the boundary math once, parameterized across
all three flavours. **`PlaybackRepositoryImpl`'s three report methods** run
one private `reportOrStage(stage, send)`: offline → stage, online failure →
stage, success → send — each report declares its outbox payload exactly once
(the enqueue argument list previously appeared twice per method and could
desync offline staging from failure staging); STOP's
`deletePlaybackTelemetryForItem` fires only on a delivered stop.
`SQLITE_HOST_VARIABLE_CHUNK_SIZE` (`util/SqliteLimits.kt`) replaces the three
local 900s (`SeenMediaRepositoryImpl`, `OfflineSyncManager`,
`OfflineRepositoryImpl`).

## Deferred user-data freshness

**`DeferredUserDataRefresher`** (`shared/core/ui/.../viewmodel/DeferredUserDataRefresher.kt`)
is the read-side twin of the silent flip contract: a user-data change landing
while a screen is NOT shown only MARKS it stale (boolean `pendingRefresh` — a
WS burst collapses to one refresh), and the single regeneration fires on the
next `onScreenActiveChanged(true)`. A change landing while the screen IS
active arms the flag but never regenerates mid-scroll — the pager-generation
swap the contract exists to prevent only ever happens on re-entry, when the
grid rebuilds from the top anyway (a trigger bump restarts the
key-combined `flatMapLatest` pager from its INITIAL key; `getRefreshKey`
anchors within one generation, it does not carry the anchor across). It is
itself a `DeferredRefreshHost` (`DeferredRefreshEffect(viewModel.deferredRefresher)`
is the whole screen wiring), with a secondary constructor taking the pager's
`StateFlowHandle<Int>` trigger and owning the bump lambda. Main-thread
confinement: the collector and the `LifecycleResumeEffect` caller both run
main-immediate, so the flag needs no synchronization. **Silent-failure
re-arming**: a host whose regeneration failed WITHOUT surfacing an error
(the stale-while-revalidate paths) calls `rearm()` so the next re-entry
retries — a consumed flag after a failed silent fetch would otherwise pin
the pre-change data until the next WS event. Cancellation never re-arms
(the cancelling loud load is the regeneration).

The host choreography (collection/person/album detail) is one shape, owned
by **`DeferredFetchCoordinator<K, T>`**
(`shared/core/ui/.../viewmodel/DeferredFetchCoordinator.kt`) — a
state-container owner, not just a job-slot owner. The host hands ONE
aggregate-returning fetch — `suspend (id: K, force: Boolean) -> T`, the
whole content pair/value, throwing on failure — and the coordinator owns
`state: StateFlow<DeferredFetchState<T>>` (`value`/`isLoading`/`error`)
plus the entire loud/silent publish policy: loud accepted publishes Loading
synchronously — synchronously on the COORDINATOR's state; the hosts'
screen-facing state is a projection that trails one hop (an eager
`stateIn` fold on collection/person, a collector-mirrored Compose-state
trio on album — each host's KDoc declares it), so a uiState read in the
same dispatch as `load` still sees the previous frame, loud failure
publishes Error while KEEPING the last value
(all-or-nothing on the loud path — a half-failed album load no longer
publishes one fresh half), silent success serves stale-while-revalidate
without ever touching `isLoading` (the pull-to-refresh spinner keys off
it), and silent success over a failed loud load HEALS the error with no
Loading flash. `FetchMode`, the old `Boolean` protocol, and the
`onLoudStart`/`onLoudError`/`onSilentHeal` hooks are gone from the
interface — the album host's old shared load/mix error field became a
projection (`mixError ?: loadError`), not a constructor hook. `load(id,
force = false)` is the loud entry; the coordinator tracks what that id's
last completed fetch did, which IS the back-stack re-entry guard the hosts
used to hand-roll (`currentXId` + `state is Success` checks): a loud load
for the id already showing whose last fetch succeeded is a no-op, a
previously failed one re-arms so re-entry reloads. `updateValue(transform)`
is the host-side optimistic-patch seam (the `UserDataContainer` flips); it
patches whatever value is kept — including one behind an in-flight loud
reload's spinner, where the completing load's aggregate overwrites it (the
next fetch reconciles server truth either way).
`force` (pull-to-refresh, retry) bypasses the guard and reaches the fetch
body as the repository cache-bypass flag, exactly as the silent
regeneration's always-forced reads do. The single-flight/re-arm table is
unchanged: one fetch-job slot for loud AND silent loads — a loud load
cancels an in-flight silent one (re-arming first), the deferred refresh
skips itself while any load is active (the skip re-arms), any fetch
reporting failure re-arms (a loud failure with nothing pending over-arms
one quiet refetch), and cancellation never re-arms. The fetch invocation
is wrapped in `runCatchingRethrowingCancellation` (core/ui depends on
:shared:core:concurrency for it), so the old "fetch bodies must rethrow
`CancellationException`" rule is enforced at the module boundary — a
cancelled loud load cannot mask as a fetch failure and strand its spinner.
Throwables that are not `Exception`s (an `Error`) are not fetch failures
either: `runFetch` rethrows them and they surface through the scope as they
always did.
`DeferredFetchCoordinatorTest` (core/ui) pins the
single-flight/re-arm/identity table AND the publish policy (serve-stale,
loud-error-keeps-value, heal-without-flash, synchronous loud start); the
migrated hosts' suites pin only their projection surfaces (pair publish,
spinner clears, re-entry reload-after-failure, same-id no-op). The
whole-screen hosts' Loading → Error → content precedence is one shared
fold, `DeferredFetchState.wholeScreenPhase` — each host supplies only its
own uiState constructors.
**`MusicHomeViewModel` deliberately does NOT ride it** — its loud fetch
publishes PARTIAL sections (sub-fetch failures drop rows while the load
still reports failure for the re-arm), needs quiet failures (offline gate,
swallowed partials), and clears its error only past the offline gate: all
three need the mode inside the fetch body, which the state-container
interface deliberately removed. It stays on
**`LegacyDeferredFetchCoordinator`** (same package, frozen copy — bodies
verbatim, KDocs rewritten —
with `FetchMode`, single-consumer KDoc, its own 21-test unit owner; its
deletion condition — music home's partial/quiet semantics dissolving into
the generic container — is stated in that KDoc).

Data side: the #157 lazy-staleness rule lives in **`StaleReadGroup`** +
**`StaleReadGroups`** (`shared/core/data` jvmShared `concurrency/`, beside
`SingleFlightFetcher`; they absorbed and deleted the old
`AnnouncedStaleness` marker type). One `StaleReadGroup` owns the whole
ladder for one read group: arm on announce → `staleAwareRead(force) {
effectiveForce -> … }` consumes the marker as a one-shot force (forced
reads consume it too — such a read is at least as fresh as the announce,
so leaving the marker armed would only buy one redundant forced read
later) and re-arms on BOTH failure shapes (returned `Result.failure` and
thrown, cancellation included — the two fixed bug classes: a consumed
marker dying with a failed read, 1ba22d962; a consumed marker not
propagating its force into the network layer's nested sub-call caches,
53b90d228). `MediaRepositoryImpl` holds a registry of three —
`homeSectionsStale`, `albumTracksStale`, `collectionItemsStale` — declared
at construction via `register(ridesUserDataWrite = …)`; the reads are
`group.staleAwareRead(force) { … }` one-liners. Arming fan-out is
registry-owned, not copy-pasted: `announceConfirmedWrite()` (every
confirmed own-write path — flips, delivered STOPs, the outbox drain, via
the synthetic `notifyUserDataChanged`) arms ALL THREE;
`announceUserDataWrite()` (the USER-DATA callers of the composite
eviction — `withUserDataMutationCacheInvalidation` and the
`invalidateForUserDataChange` seam the STOP path uses) arms only the two
gap groups (registered `ridesUserDataWrite = true`) — NOT inside
`invalidateUserDataCaches` itself, because that eviction also serves
`invalidateFor(ALBUM)` from every FORCED album-detail read, and a
pull-to-refresh must not arm markers (one redundant forced read of
whichever album/collection is read next, healing nothing). That
gap-group arming is deliberately UNCONDITIONAL — it rides the eviction,
not the write's confirmation: the flip wrapper arms before the mutation
runs, in step with its pre-eviction (which drops the item's own keys
even when the write then fails), and the STOP path's pre-send purge
arms even when the STOP then stages offline (the local mirror already
reflects the staged position, so the group IS stale as locally visible;
the outbox drain's later announce is the confirmed half). Both
divergences cost at most one redundant forced read — the coarse-marker
budget the group already accepts. A TRACK flip
evicts `tracks_<trackId>` and never the album's `tracks_<albumId>` key, a
collection MEMBER flip evicts
`detail_<itemId>` and never the collection's page keys, so the album's track
rows and the collection's pages now heal on their next non-forced read
without a caller-side force (the markers are coarse — one per group, so a
flip costs at most one forced read of whichever album/collection is read
next; identity switch resets all via `resetAll()` in the
`media-identity-clear` action). `getMediaDetail` deliberately has NO
group: its announce path already eagerly evicts the item's detail entry
(`DetailCacheGroup.invalidateUserData` → `invalidateItem`), so the next
read is a guaranteed fresh miss — a marker would only double-evict.
Adding a fourth group is one `register` call.
`StaleReadGroupTest` pins the ladder directly (announce-forces-fetch,
failed/thrown/cancelled reads re-arm, success consumes, force
bypasses-but-consumes, both fan-out channels). `PlaybackRepositoryImpl`'s
segments cache follows the same registry doctrine as `DetailCacheGroup`:
its identity reaction is the registry's plain wholesale clear PLUS a
registered action that bumps `segmentsEpoch`, so an in-flight
previous-identity fetch cannot write back into the just-cleared cache.
Server WS pushes arm none of these — `HomeRefresher` serves those live.

## Concurrency (`shared/core/concurrency`)

**`:shared:core:concurrency`** (commonMain, zero-dependency leaf below
core:network) is the repo's one cancellation-safety seam.
**`runCatchingRethrowingCancellation`** is THE sanctioned wrapper for any
best-effort `runCatching` around suspend calls: stdlib `runCatching` captures
`CancellationException` and masks structured cancellation — the recurring
bug class (masked worker retries, half-applied offline flips, orphaned
observers) that pre-2026-09-07 commits kept re-fixing one file per commit.
The wrapper is born commonMain so the wasm stack rides the same
implementation; both engines' `apiResult` (JVM `JellyfinApiEngine` + wasm
`WasmApiSupport`) are the helper plus their own typed-exception mapping —
declared parity, no per-platform twin. Non-suspend bodies (JSON/enum parses
in mappers) keep stdlib `runCatching`. **`BareRunCatchingRatchetTest`**
(module `jvmTest`) is the source ratchet: bare `runCatching` inside
`suspend fun` bodies never increases — the guard is repo-complete since
the 2026-09-08 third wave, and its guarded-root set is DISCOVERED, not
hand-listed (since 2026-09-09): the test parses every `include(...)` in
`settings.gradle.kts` and walks `shared/` + `apps/` for
`build.gradle.kts` dirs (pruning build output), unioning both — a new
module is guarded the moment it exists, and a canary assertion checks
discovery ⊇ the retired 40-path hand list (the single widening found,
`baselineprofile`, contained no `runCatching`; baseline 22 unchanged).
Two known deliberate baseline entries are named in the
test's KDoc (HomeDiscoveryStore's best-effort migration swallow,
PluginConfigViewModel's asset read); `AddToTargetActions
.resolveTargetItemIds` — the one live hazard the widened sweep found — is
converted (a cancelled canonicalEpisodeIds fetch used to settle as the
couldn't-add message path). The heuristic can't see bare `runCatching`
inside suspend LAMBDAS; the wave's review pass converted the two found
that way (AdminDashboardViewModel's and LogsViewModel's `AdminLoad` fetch
variants — recorded in the test KDoc too). Lower the baseline when another site
converts, never raise it; prefer extracting a legitimate parse out of
the suspend body over raising it.
**`Semaphore.mapConcurrent` / `mapConcurrentCatching`**
(`MapConcurrent.kt`, same module) is the one bounded-parallel-map surface:
order-preserving `items.map { async { withPermit { … } } }.awaitAll()` written
once (plus the `Catching` variant for the drop-failed-item policy, which rides
`runCatchingRethrowingCancellation` — a failing item is dropped, a cancelling
one still cancels the caller). Every site keeps its own concurrency constant
and post-processing; only the permit ladder is shared. Adopters:
`HomeSectionsFetcher`'s three fan-outs, `MediaInfoApiClientImpl`,
`PhotoFolderPrefetcher`, `AdminStatisticsRepositoryImpl`,
`OfflineSyncManager`, `EpisodeCatalogueImpl` and `ArrRepositoryImpl` (whose
private `fanOut` is deleted). Pinned by `MapConcurrentTest` (order under
randomized delays, permit bound, cancellation propagation both variants).
**`TaskBundle`** is the cancel-and-replace slot choreography (named keys
over a caller-owned scope; deliberately NOT thread-safe — the same
dispatcher-confinement contract as the plain `Job?` vars it replaces).
Adopters: `EngineEventCoordinator`'s per-engine policy slot,
`TrickplayManager`'s preload, `ServerHealthMonitor`'s monitor loop,
`PlaybackSession`'s load / seek-progress / decision slots. `EngineActivityRecorder`
stays a deliberate hand-rolled one-off — its observation choreography IS
its evidence. The bundle owns slots, never scopes: scope recreation
(TrickplayManager's `clear()`, the health monitor's test dispatcher swap)
stays site-side and rebinds the bundle.

## Library client policy (network)

**`LibraryRequestPolicy`** (`shared/core/network/src/commonMain/kotlin/.../library/LibraryRequestPolicy.kt`)
is the one home for the request-level policies the `LibraryApiClient` twins
(`LibraryApiClientImpl`, `KtorWasmLibraryApiClient`) used to ship hand-copied
per source set: the 12-field detail projection (`DETAIL_PROJECTION_FIELDS`),
the list projection (`LIST_PROJECTION_FIELDS` — the two-field
"Overview"+"PrimaryImageAspectRatio" set every list-shaped query attaches;
the genre and playlists variants compose on top; the JVM client resolves
it through the wire-name ladder, the wasm client's private `LIST_FIELDS`
twin is gone; pinned by `LibraryRequestPolicyTest` in commonTest),
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
**`WasmMirrorContractTest`** (network `jvmTest`, the
`SettingsCatalogScreenContractTest` pattern) is the mirror's source
contract: it reads `KtorWasmLibraryApiClient` and `LibraryApiClientImpl`
at test runtime, extracts per endpoint the verb, path template and
query-parameter names (the JVM side resolved through an explicit
`sdkEndpoints` table verified against the jellyfin-api 1.8.12 sources),
and asserts per-method parity — method-set parity both directions plus
per-method wire-shape equality, with declared-divergence exception slots
(currently empty; one documented placeholder alias: wasm's `entryId`
names the SDK's `{itemId}` segment in `movePlaylistItem`). The same
machinery covers the user-client pair (`KtorWasmUserApiClient` ↔
`UserApiClientImpl`); the auth and playback wasm clients are noted
follow-ups, and the ARR/Seerr/Tmdb wasm mirrors use a different
URL-builder idiom on both sides so they would need different extraction.
Its first run caught a real drift — the wasm `emptyLibraryFallback`'s
latest-media probe omitted the SDK's always-sent `groupItems=true`.
`JellyfinApiEngine.requireUserId()` / `currentUserId()` (internal, beside
`requireApi()`) are the named user-id contract replacing the 15+
hand-rolled `currentUser.value?.id` guards across the jvmShared clients —
both read the ATOMIC `session` value (a user without a server is no
identity; the separate `currentUser` flow must not be re-combined for
this), message-aligned with wasm's `requireCurrentUser()` and pinned in
`JellyfinApiEngineSessionTest`.

**`JellyfinRawRequester`** (jvmShared, beside the clients, internal —
the 2026-09-07 fold) is the ONE seam for the hand-built raw-OkHttp
requests the plugin catalogue, newsletter/playback-reporting plugin
endpoints and intro/credit probes used to copy per endpoint (~28 sites
across Plugin/MediaInfo/Playback clients, three incompatible private
guard adapters): session guard → `X-Emby-Token` →
`newCall().execute().use` → status check with the per-endpoint failure
text, over `getJson`/`postStatusOnly`/`deleteStatusOnly`/`getBodyText`
members mirroring the wasm `WasmApiSupport` shapes (this is the JVM
twin of those helpers, NOT the deferred cross-platform WireRequest
unification — nothing crosses source sets). The load-bearing rule:
every member derives the base from `engine.activeServerAddress` (the
router's active endpoint, failover-correct) — the pre-fold Plugin and
MediaInfo sites built URLs from `currentServer.value?.address` (the
primary, stale after failover) and were rescued only by the failover
interceptor's absolute-URL promise. Pinned by
`JellyfinRawRequesterTest` (MockWebServer, the `SeerrApiClientTest`
setup) plus the first-ever `PluginApiClientImplTest` through the seam.
The wave also landed the small folds around it:
`ItemCountsDto`→`toItemCounts()` lives in `JellyfinDtoMappers` (the
byte-identical Admin/MediaInfo pair is gone), the parental
filter+map tail is the top-level
`List<BaseItemDto>.toFilteredMediaItems(maxParentalRating)` over the
canonical commonMain `filterByParentalRating` (the engine's member
twins and the 21 `engine.run { … }` scope-borrows are deleted),
`LIST_ITEM_FIELDS` / `LIST_ITEM_FIELDS_WITH_GENRES` (jvmShared
`LibraryItemFields.kt`) resolve the shared `LIST_PROJECTION_FIELDS`
policy against the SDK enum for every consumer (the six hand copies in
MediaInfo/LiveTv are gone), `MetadataApiClientImpl`'s 12 UUID + 3
ImageType ladders are two private helpers, and `TtlCache.getOrPut`
(core/model, pinned beside `TtlCacheTest`) folds the get→fetch→put
contortion in the Admin/MediaInfo cache-aside sites.

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
"keep in sync" three-route mirror is gone.

**`ShellSessionController`** (landed 2026-09-07; the old per-shell ruling
for this wiring is reversed — see
`docs/adr/0001-shell-session-controller.md`) is the session-policy wiring
both shells share: admin status + `AdminRefreshGate` arbitration
(`refreshAdminStatusNow`'s gate early-out → refresh → success-only stamp
→ finally-reset), `homeMode` set with optimistic write, and
`logout(revoke)`, over pure injected flows/suspend lambdas (the gate's
construction pattern; no Koin in this module — each shell constructs it
with its own scope, Android's `MainViewModel` delegates, desktop's
`DesktopAppRoot` collects it where the inlined "MainViewModel duties"
block used to be). `updateCheckMessage(Result<AppUpdateInfo>)` is the
one shared update fact (a pure companion fold → `UpdateAvailable` /
`UpToDate` / `Failed`); the update SURFACES stay per-shell (Android's
`UpdateCoordinator` is structurally richer; the desktop inert sentinel
per `docs/adr/desktop-auto-update.md` untouched). Android's rendered
homeMode stays `MainPreferences`-derived (the ADR's "other duties stay"),
so Android passes `homeModeChanges = null` while desktop feeds the store
flow for its optimistic rail switch. Platform-conditional blocks (rail,
media keys, surface probe, saved-state config) stay per-shell. Pinned by
`ShellSessionControllerTest` (11 tests).

**`UserMessageHost`** (`shared/feature/shell`,
`UserMessageHost.kt`) is the message-presentation seam behind every shell —
the fix for the two `:app` collectors that hand-copied the
severity→duration policy and the TV-Toast/phone-Snackbar fork, and for
desktop/web never collecting the shared `UserMessageBus` at all (shared-feature
error feedback was silently dropped on non-Android shells). Interface:
`UserMessageHost(resolveText, present)` + `host(vararg sources)` for shared
`UserMessage` payloads, `hostAdapted(sources, severityOf, resolveText)` for
shell-owned payloads (the legacy `core:ui` bus), pure `durationFor(severity)`
(Error→Long, Info→Short), and the commonMain `resolveUiText` helper. The
choreography inside: one collector over `merge(sources)`, serial presentation
(a mid-presentation message queues, never drops), exactly-once, per-source
order. Each shell supplies only the `present` adapter — Android's owns the
entire TV-vs-phone fork (`remember(isTv)`; both `LaunchedEffect`s keep the
`(bus, isTv)` keys), desktop's maps onto `SnackbarHostState` and now feeds the
shared bus alongside its music relay (the relay's messages deliberately
normalize to Error/Long with a dismiss action — the presentation Android's
own music-bus bridge always gave the same messages; desktop's old
no-dismiss/Short snackbars were the drift). `apps/web` has no message surface
(inline Text only) and adopts nothing. Pinned by `UserMessageHostTest`
(severity table, merge exactly-once/order, queue-not-drop, and the
desktop-receives-shared-bus regression).

`apps/web`'s browser-history integration is split at its natural seam:
**`WebBackStackMirror`** (wasmJsMain, internal, 2026-09-07) is the pure
reconcile core — hash↔index parsing, `trimToDepth`, the dispatch-first
pop with root-refuse, reload normalization and the forward-onto-pruned
walk-back, returning sealed `WebHistoryCommand`s (Push/Rewrite/
NavigateBack/GoTo/None) — and `WebAppRoot` keeps only the thin JS
adapter that applies commands to `window.history` (pushState/back/go,
one opt-in site). The ~70 KDoc'd model rules moved with the logic;
pinned by `WebBackStackMirrorTest` (15 cases: root refusal,
consumed-press, stale/boot-deep/foreign reload hashes, trim depths,
walk-back deltas).

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

`SettingsScreen`'s row summaries are pure policy, not composition:
`appearanceSummaryParts` / `experimentalSummaryParts` emit a
`List<SettingsSummaryPart>` (sealed `Literal`/`Token`/`Formatted`/`Plural`)
joined by the pure `joinSummaryTokens`; the two `@Composable` subtitle
helpers resolve each part at composition (`resolveAtComposition()`), so
locale changes still recompose while the which-prefs/order/casing
decisions are jvmTest-pinned by `SettingsSummariesTest` (the
resolver-lambda shape was unimplementable — this Compose compiler
rejects `stringResource` inside non-inline lambdas).

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
Known residue, deliberate: the TV / advanced / admin search
dimensions are not yet derived from the catalog declaration; TV-only items
stay tagged ANDROID (they are a runtime-axis problem). The scroll-group
side is DONE — the 2026-09-07 wave finished the catalog-derivation
migration the playback screen started: the `language` group is split
`language.general`/`language.subtitles` and `system` into
`system.core`/`system.screensaver` at the aggregation (the LiveTv
prefix-split precedent; language splits on the leading-trio size,
pinned by name), the Language/Settings screens derive scroll targets,
expand sets and row totals from `SettingsScreenGroups` (`dreamTotal`,
`insightsCount`, the account count), the Audio screen's 22-line
row-count oracle is the pure `audioScreenRowTotal(showAdvanced,
preferences)` counting the `audio` group through a per-id visibility
predicate, and `SettingsScreen` consumes core/ui's shared
`settingsSearchResults` pipeline instead of its inline copy (the
blank-query short-circuit now applies there too — the copy resolved
all 258 items on every blank query). `ACTION_ONLY_IDS` stays a hand
set: "action" is dialog semantics, not structure. The `edit(transform)`
migration is complete too — Security/Language VMs lost their pure
forwarding strata (screens issue `viewModel.edit { … }`; the deciding
members like `setAppLanguage`'s locale side effect stay), and the
stateless `AdvancedSettingsGate` is ONE Koin single injected into all
nine VMs (public only because their constructors are — not a stable
API). The search-result click dispatch is the internal pure
`settingsResultClickAction` (+ sealed `SettingsSearchResultAction`)
beside the screen, the `playbackAdjustForAdvanced` precedent; the
retired-list ratchet in `SettingsCatalogScreenContractTest` now covers
every derivation.

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

## App widgets (`:app`)

The **widget grid skeleton** (landed `aec4c138b`, app-local) owns the
byte-identical factory chassis — snapshot read → poster preload → dims
refresh → deep-link `getViewAt` — plus refresh-scope, height thresholds
and the grid PendingIntent wiring (`WidgetGridFactory`, provider bases,
one generic persist; the blank-widget version-bump bug class is fixed in
one copy). The 2026-09-07 review completed the straggler family:
`widgetIdsFor` / `notifyProviderDataChanged` / `updateAllProviderWidgets`
(`WidgetProviderSkeleton.kt`, internal) own the `getInstance` →
`ComponentName` → `getAppWidgetIds` triple the package hand-copied 12× —
the ContinueWatching/Library/Seerr refresh-broadcast tails, the persist
helper's Library/Seerr notify twins (deleted), the Now Playing updater's
start/presence reads, and the work scheduler's bound-widget gates all call
them; `NowPlayingWidget.viewVisibility` is deleted for the skeleton's
`toViewVisibility`. No helper test — trivial Android pass-throughs
(`WidgetPersistHelperTest` already pins the empty-id branch). The provider
LIFECYCLE half joined too: **`GridWidgetProvider`** (between
`WidgetProviderSkeleton` and the two recommendation providers) owns the
`onUpdate` (loop + `triggerInitialRefresh`), `onAppWidgetOptionsChanged`,
`onEnabled`/`onDisabled` (scope cancel) and `ACTION_REFRESH`-on-`onReceive`
choreography the Library/Seerr twins hand-copied; subclasses keep only
`gridViewId`/`refreshAction` plus the `updateWidget`/`refreshNow`/`onUpdateWidgets`
seams (request codes stay in each subclass's PendingIntent wiring, values
unchanged). `WidgetWorkScheduler`'s `refreshLibraryNow`/`refreshSeerrNow`
twins and double `enqueuePeriodic` build collapsed onto one private
`Flavour` table; public member names unchanged and
`WidgetWorkSchedulerTest` passes unmodified.

The 2026-09-07 architecture wave completed three more deepenings. The
**widget version/updatedAt protocol is deleted**: `persistItems` is
id-dedup + write + notify-on-changed-ids only, and `WidgetDataStore`
lost the `*WidgetVersion`/`*UpdatedAtMs`/`widgetLastRefreshMs` flows,
keys and setter params — none had a production reader (the factory's
version-match early-return was already gone; only tests fed the
protocol). **`WidgetPosterIdentity`** (beside the workers, pure,
`WidgetPosterIdentityTest`) is the single home of the poster-identity
policy — the CW and Library/Seerr image-id resolution (`seriesId ?:
itemId`) and the per-flavour maxWidth constants (CW 300, Library 400 —
the Library worker's hand copy with the wrong width is gone; Seerr has
no rule, its posters are TMDB CDN urls). **`NowPlayingWidgetRenderer`**
is the one render pipeline behind every full push: the provider's
`onUpdate`/`onAppWidgetOptionsChanged`, the updater's push and the
config save all read the manager ONCE (`readPushSnapshot`) and bind
through `renderFullPush`, with the pure `nowPlayingConfigFold` (beside
`NowPlayingWidgetPolicy`, pinned) applying the artwork/progress config
toggles on EVERY full push AFTER the responsive ladder — the former
`updateAllWidgets` path skipped the fold entirely and the options
path re-showed disabled rows at wide rungs; the empty player state
keeps the backdrop hidden regardless of the artwork toggle (the
fold's `isEmptyState` gate — the top-level backdrop never renders
behind the empty-state text). The options-changed path keeps only its
goAsync/Handler threading shell.

## App shell (`:app`)

**`PinGateController`** (beside `AppLockState`) is the app-lock state machine
that used to live inline in `MainActivity`'s `setContent`: one command
`submit(pin)` → `Unlocked` / `Incorrect` / `LockedOut(remainingMs)`, owning
the click-time lockout re-check (a lockout may land between composition and
tap), the record-failure-then-re-read-limiter ordering, and the success
ordering (unlock → clear error via `submit`'s `onUnlocked` hook → reset
limiter) over constructor-injected `PinRateLimiter`/`AppLockState`/`verifyPin`
seam/clock. The pure
`lockoutMessage(remainingMs)` fold sits on its companion; the Activity is a
thin render adapter. Pinned by `PinGateControllerTest` (lockout race,
failure accounting, boundaries).

**`BackExitConfirmation`** (`shared/core/ui/components`, the
`ScrollDirectionVisibility` precedent) is the double-back-to-exit policy both
shells shared as a hand-copied `ExitConfirmationTimeoutMs = 2000L` pair:
`onBack(nowMs, lastAtMs, atExitPoint)` → `Pop` / `Prompt(nowMs)` / `Exit`,
exit resets the window. `JellyPlayApp` and `TvNavigationDrawer` keep only the
  `moveTaskToBack` + Toast effects. Pinned by `BackExitConfirmationTest`
  (1999/2000 ms boundaries, window reset).

## The 2026-09-07 evening wave (13 deepenings)

Landed autonomously with per-module pinning tests; consolidated gradle pass
green across every touched module.

- **Enum parse seam**: `String?.toEnumOrNull()` (core/datastore
  `EnumPreferenceParsing.kt`, public reified inline, never-throws) is the
  repo-wide seam for persisted-string → enum. ~50 former `valueOf` /
  try-catch / `runCatching{}.getOrDefault` / name→enum-map sites across all
  14 stores and the core/data repositories now flow through it with explicit
  defaults. Declared delta: a corrupt persisted enum falls back to the
  documented default instead of throwing during the read projection (a
  throw tripped the store-level `.catch { emptyPreferences() }`, wiping ALL
  preferences); corrupt outbox `eventType` drains as `START` (the one replay
  path that sends nothing) instead of poisoning `drain()`. Pinned by
  garbage-write tests per module (`EnumPreferenceParsingTest`,
  store tests, `ItemPlaybackPreferenceRepositoryImplTest`,
  `PlaybackOutboxRepositoryImplTest`).
- **`OfflineDeletionCore`** (core/data, internal collaborator) is the ONE
  deletion choreography behind `deleteOfflineItem/Series/Season` and
  `DownloadRepositoryImpl.cleanupDownloadFiles` (former 4 hand-copies):
  artifacts-before-DB delete → capture-before-transaction → 4-table cascade
  → memo evict → cast prune → orphan prune; series-artwork cleanup is a
  series-scope-only hook. The download-cleanup caller carries the wave's one
  declared deletion delta: its former inline body pruned orphans inside its
  single transaction and never pruned cast images; through the core it
  adopts the majority post-transaction prune and the reference-scanned cast
  prune (the old skip leaked orphaned cast image files — the scan only
  deletes images no surviving row references; see the method's KDoc).
  `OfflineRepositoryImpl` now injects `TimeSource`
  for the `lastPlayedDate` stamps (two inline `OffsetDateTime.now()` gone).
  Pinned by `OfflineRepositoryDeletionTest` (three scopes, shared cast image
  retention, zero orphans) + fake-clock `applyPlayedState` tests.
- **`ArrClientSupport(okHttp, json, serviceName)`** (core/network jvmShared)
  folds the Radarr/Sonarr client preambles (~180 lines); all failure texts
  byte-identical, pinned via MockWebServer. Seerr is deliberately NOT folded
  (structurally different sentence shapes). `MediaInfoApiClientImpl` lost
  its decode twins (`fetchBreakdownReport`/`parseBreakdownReport`,
  `toStaleMediaItem`, `isPlaybackReportingUserIdToken` — fixture-pinned).
- **Arr redownload ladder**: `redownloadMovie`/`redownloadEpisode` are one
  `redownloadLadder(client, kind, ref)` over five new `ArrServiceClient`
  ops (the seam and its redownload types are module-internal; the adapters
  expose `serviceName` so the ladder's user-visible strings don't re-derive
  it from the kind); the Radarr/Sonarr verify-FAILED divergence
  (best-effort continue vs hard gate) is a kind-gated ladder rule, both
  step tables pinned.
  `deleteQueueItem`/`deleteBlocklistItem` share `withServer`
  (bulk `deleteQueueItems` keeps its single end-refresh deliberately).
  `arrBaseUrl(externalUrl, useSsl, hostname, port, baseUrl)` (core/model
  seerr) is the single base-URL grammar behind both `getFullUrl()` bodies
  and ArrRepositoryImpl. Note: `getFullUrl` with a blank hostname used to
  return `"http://:7878"` garbage; via `arrBaseUrl` it now yields `""`
  (nothing pinned the garbage — deliberate).
- **`StatisticsMath`** (core/data, pure) owns watch-time breakdown +
  viewing-streak math out of `AdminStatisticsRepositoryImpl` (14
  deterministic tests); the watched scan rides `AdminStatisticsLabelProvider`
  (its strings were already identical to the seam members). The
  `ByteFormatter` twins were KEPT on purpose: repo `formatSize` ("" for ≤0,
  integer KB band, locale-sensitive decimals) feeds strings persisted to
  Room and `ArrQueueScreen.toReadableBytes` rounds the KB band — swapping
  either changes persisted/UI text and needs a deliberate decision.
- **Settings**: the dead import twin is deleted — `PendingImport`/
  `parsePendingImport`/`confirmImport` gone from `SettingsViewModel`
  (stage→navigate→`ImportPreviewViewModel`/`BackupParser` is the only
  pipeline; desktop `DesktopNativeDialogHarness` migrated onto it; ~660
  lines of dead-twin tests removed). Declared delta: `importSettings` no
  longer reads the file at stage time, so an unopenable/corrupt backup
  surfaces its error on the ImportPreview screen (pinned by
  `ImportPreviewViewModelTest`) instead of as an inline settings-screen
  status. `SETTINGS_ENTRANCE_SECTIONS` +
  `settingsEntranceStep(key)` derive the 19 entrance steps (pinned equal to
  the old literal phone/tv pairs); `SettingsSearchPanelState` owns the
  search panel machine with named focus delays; the five `setDream*` funs
  and `setShowAdvancedSettings` are gone (screen uses the house
  `viewModel.edit {}` shape).
- **`PipLifecyclePolicy`** (beside `PlayerActivity`, pure, internal) owns
  the PiP ordering machine: `pipExited(phase,…)/onResume/onStop/
  onUserLeaveHint/onTopResumedChanged` → `Decision(action, justExitedPip)`
  plus `clampAspectRatio`/`isValidSourceRect`; the OEM-ordering comments are
  its KDoc; 19-test callback-sequence table (confirmed PiP entry is
  execution-only — no policy event). The two auto-enter predicates
  are modelled SEPARATELY (`userLeaveAutoEnter` has no `isPlaying` term;
  `topResumedLossAutoEnter` adds it; the pre-arm skips `controlsLocked`) —
  no single existing predicate used all four terms, so they were not
  unified.
- **Shell pure folds**: `externalPlayerPositionTicks` (extras
  "position"/"positionMs" alias, Number coercion, ≥0 gate, ×10_000) and
  `visibleTopLevelRoutes` (homeMode set + offline LiveTv hide + nav
  customization) leave `JellyPlayApp` with tests;
  `OnboardingGate.onboardingGateRoute(authenticated, completed, isTv)`
  (shared/feature/shell) is the one gate behind both shells (desktop's
  `DesktopOnboardingGate` is a thin `isTv = false` wrapper; Android's TV
  auto-mark stays a call-site effect). `TilePlaybackState.policy` is the
  tile's truth-table fold.
- **`WidgetPushGate`** owns `lastItemId`/`lastArtwork`/`lastPushedRender`
  with `decideOnMetadata` (full push, post-artwork-load re-read is what
  gets recorded) / `decideOnPositionTick` (partial via
  `shouldPushPartialPosition`) / `reset()`. Beside it,
  `WidgetWorkScheduler.claimRefreshSlot` is a CAS loop — declared fix: the
  former get-then-set let two triggers inside the 5 s window BOTH enqueue
  (its own KDoc already claimed they didn't); the CAS loser is now
  suppressed without restamping (pinned: no window extension, 8-thread race
  single winner). The skeleton's `runWithPendingResult` +
  `launchFinishingOnMain` absorb both
  former goAsync shapes in `NowPlayingWidget`.
- **Web**: `WebConnectFailurePolicy` (internal, beside `WebConnectFlow`)
  makes the CORS/transport taxonomy + 401 sign-in mapping wasmJs-test-pinned
  (20 tests) before the real-server browser pass; `WebSideEffectScope`
  (`launchDegrading`: swallow `Exception`, rethrow `CancellationException`)
  replaces the two hand-rolled controller scopes. The AuthRepository
  promotion stays deferred. Note: ktor 3.5.2's
  `HttpRequestTimeoutException` extends `IOException`, so that cause check
  is redundant-but-harmless (left, pinned).
- **Details**: `DetailPlayPolicies.resolveDetailPlayDispatch` +
  `DetailPlayPolicies.dispatchMarkPlayedAction`
  (table: SERIES → confirm; MOVIE/EPISODE/SEASON/null → direct; any season
  action → confirm) replace the duplicated play/chapter fold and the 4×
  mark-played gate chain in `MediaDetailScreen`.
  `MediaItem.progressFraction(positionTicks)` (core/model
  `MediaItemProgress.kt`) is the single resume-fraction home — the core:ui
  twin extension is deleted, 11 importers re-pointed,
  `rememberProgressFraction` delegates.
- **Hygiene**: the stale repo-root `feature/` tree (57k files of pre-KMP
  Hilt-era build output; 0 tracked files, 0 sources) is deleted.
- Deferred-list items were NOT landed (per their recorded blockers):
  settings category-merge, SeerrDetailPresentation fold,
  SideloadedTrackIdRegistry, wire-request twins, web AuthRepository
  promotion, the load-ladder fold (fresh census: 22 canonical + 7
  variant-form VMs across 8-11 modules, vs the recorded "~23 across 10" —
  effectively unchanged),
  DetailViewModel intent fold, PlayerControls callback bundles,
  MediaDetailScreen dialog coordinator, desktop window placement policy,
  signed-out auth shell, factory-reset field enumeration, settings row-twin
  rendering, `ss_*`/`settings_*` string merge.

## The 2026-09-08 wave (11 deepenings)

Landed autonomously via parallel workstreams, each with pinned tests; the
exploration pass that selected them is summarized in the run's temp report.
The dominant theme: multi-copy choreography folding onto one home, and the
sync drain leaving the CI-dark legacy lane.

- **`PlaybackOutboxDrainer`** (shared/core/data jvmShared,
  `worker/PlaybackOutboxDrainer.kt`) is the ONE drain choreography behind
  `suspend drainOnce(attempt: Int): DrainResult`: offline gate, staged-intent
  collection, derived-watched flips, the retry-budget ladder
  (`MAX_RETRIES = 3` / `MAX_INTENT_RETRIES = 10`), dead-letter policy,
  superseded-telemetry skip, bounded reconcile batch (cap 50, via
  `Semaphore.mapConcurrent`), and the cache-invalidate +
  `notifyUserDataChanged` + `enqueueNow` tail. `PlaybackSyncWorker`
  (legacy `core:data`) shrinks to a thin adapter implementing the drainer's
  `Notifier` (foreground promotion / mid-drain update / dismissal) and
  mapping `DrainResult.retriesPending` → `Result.retry()`; `attempt` is the
  only seam the old `runAttemptCount` coupling needed. Deliberate deltas:
  desktop's `DesktopPlaybackSyncScheduler` is now REAL (startup,
  Offline→Online transitions via `DesktopNetworkMonitor.networkStatus`,
  manual-sync `enqueueNow`; Mutex-serialized; `attempt = 0` fresh budget per
  pass; no periodic backstop — a row staged while continuously online waits
  for the next transition/restart), so the no-op-binding "staged rows sit"
  era is over. The 33-test worker suite moved from the legacy
  `core/data/src/test` lane — which NO CI job ran, i.e. dead assertions per
  this file's own rule — into `:shared:core:data:jvmTest` (CI-gated), plus
  7 new drainer/Notifier-protocol tests; the Android lane keeps the trimmed
  WorkManager-specific resilience cases and
  `OfflineWatchSyncContractTest`, and `kmp-build.yml` now runs
  `:core:data:testDebugUnitTest` beside the `:core:notification` precedent.
  Pinned by `PlaybackOutboxDrainerTest` (33) +
  `PlaybackOutboxDrainerResilienceTest`.
- **`LiveTvLoad`** (livetv commonMain, `internal object` beside
  `LiveTvTimeFormat`) owns the load ladder (start → fetch → dispatch to
  exactly one arm; returned `Result` is the continuation gate) folded across
  Channels/Series/Recordings/ChannelDetail/Programs ViewModels. This is the
  load-ladder fold's FIRST module slice per the recorded landing condition.
  Site-specific drift stays at the call sites as declared arms: Recordings'
  legacy unconditional `getOrDefault(emptyList())` settle is preserved
  verbatim (failure clears the list — commented), Programs' `fullRender`
  variant rides the `start` closure (no flavour parameter needed),
  ChannelDetail leg-gates on the returned Result, and ScheduleViewModel is
  deliberately NOT folded (two independent fetches with per-call
  `getOrDefault` settle — a single-Result dispatch would lose the surviving
  half on partial failure). Pinned by `LiveTvLoadTest` (6) with the six
  per-VM suites unmodified.
- **`SelectionState<T>`** (core/model commonMain, beside
  `LibraryFilters`) is the one list-selection algebra: `ids` + derived
  `active`, pure `toggled`/`cleared`/`selectAll` — the
  `selectionMode = next.isNotEmpty()` derivation lives once. ArrQueue
  (`String`), Downloads (`String`) and Requests (`Int`) ViewModels store one
  `SelectionState` in their uiState with `selectedIds`/`selectionMode` as
  derived properties, so screens are untouched. Declared delta:
  `selectAll(empty)` now stays inactive (the old VMs flipped
  `selectionMode = true`, showing a 0-count bar with every action disabled).
  The three screens' `SelectionActionBar` composables remain deliberately
  separate (their visual drift is a product decision; recorded below).
  Pinned by `SelectionStateTest` (9, commonTest algebra style).
- **`PlayerKeyPolicy`** (beside `PlayerScreenPolicies`) lifts the media-key
  decision table out of the `VideoPlayerScreen` composable:
  `mediaKeyAction(keyCode, controlsVisible): PlayerKeyAction?` (sealed arms
  TogglePlayPause…HideControls/Exit, media-key aliases, both ESC arms,
  unknown → null); the screen keeps a one-line effect shell, both delivery
  paths (focused-chain `.onKeyEvent`, desktop key sink) now provably
  identical. The TV D-pad handler stays out (stateful by design). Pinned by
  `PlayerKeyPolicyTest` (10).
- **`SubtitleManager.openSubtitleHub(resetFirst)`** is the one hub-open
  command (optional reset → `loadRemoteSubtitles` →
  `loadSubtitleCultures` → `loadConfiguredProviders`, historical order).
  The screen's three hand-copied cascades are gone: the click sites route
  only (overflow sets `resetFirst = true` via a screen-local single-shot
  pending flag), the sheet router's `LaunchedEffect` is the SINGLE trigger.
  Declared deltas: the Tracks-tab double-fetch is gone (one remote-subtitle
  request per open, was two), and sheet-open loading starts at composition
  rather than at click (sub-frame; the hub spinner covers it). Pinned by 4
  new `SubtitleManagerTest` cases (28 pre-existing untouched).
- **Player VM drive-by folds**: `PlayerScreenPolicies.resumeSkipTargetMs`
  (skip ≤ 0 → unchanged; else 0-floor) is the one resume-skip-back math,
  with the `isPlaying` guard divergence DECLARED — `onRegain` applies it
  unguarded (focus regain follows a transient loss), `resumePlayback` keeps
  the guard (the play toggle must not scrub a playing stream) — and the two
  byte-identical cinema-advance-else-close end-of-media bodies are one
  private `onEndedWithNoNext()`. Pinned in
  `PlayerScreenPoliciesTest` (`ResumeSkipTargetTest`, 4).
- **MainActivity adopts `JellyPlayPreferenceTheme`** — the wrapper's own
  KDoc said it was extracted FROM MainActivity, which had never adopted it
  (PlayerActivity and desktop had). The byte-identical ~98-line hand copy
  (17 theme args, motion/performance locals, filter chain) is deleted;
  divergence check found none beyond the wrapper's existing `isTv` param.
- **Auto-lock joins the lock family**: `AppLockRedirect.shouldRelock(gate,
  timerMs, backgroundedAtMs, nowMs)` (the `backgroundedAt > 0` "never
  backgrounded" arm preserved) + `AppLockState.onBackgrounded/onResumed`
  move the timer decision out of MainActivity's lifecycle callbacks — the
  third lock decision, previously the only untested one beside
  `PinGateController`/`AppLockRedirect`. Truth-table pinned (11 new tests
  incl. exact-equal and one-ms-short boundary arms).
- **`Semaphore.mapConcurrent` adoptions**: NewMediaCheckWorker's folder
  fan-out, FavoritesViewModel photo-folder prefetch, MusicHomeViewModel
  album tracks, DreamImageProvider (wrapped in `withContext(Dispatchers.IO)`
  — mapConcurrent inherits the caller context), and (inside the drainer
  move) `reconcileBatch`. Four explorer-nominated sites were verified NOT
  ladders and left alone: UpcomingCalendar/Requests enrichment and
  AlbumDetail downloads are fire-and-forget `launch`-per-item with
  incremental merges (no awaited list — mapConcurrent would block the
  collector and change failure propagation), and ArrSettings'
  `testAllServers` keeps its permit INSIDE the shared `probeServer`
  (a 1:1 conversion double-acquires → deadlock; removing it moves a
  pre-permit status flip — the "Testing" UI timing — across the gate).
- **Watch-state vocabulary**: `OfflineMediaItem.isWatchedOffline`
  (`isPlayed || isFinishedOffline` — the derived-flip predicate, now
  greppable) and `OfflineMediaItem.hasPlaybackPosition` join
  `OfflineShelf.kt`; the `hasWatchProgress` hand copies in
  `MediaDetailSeasons` (×4) and `EpisodePickerSheet` re-point onto the
  core/model extensions, as does OfflineHomeSections' private
  `hasResumePosition`. Declined as different predicates, not drift:
  `DownloadInfoCard`'s `isPlayed || positionTicks > 0` means "has any watch
  activity" (hasWatchProgress requires `!isPlayed`), and
  `MediaDetailBody`'s position read is `target.startPositionTicks` (a
  resume/chapter-start field, not the saved playback position).
- **Logs pagination guard fix (defect)**: `LogsScreen` fed the
  infinite-list guard a hardcoded `isLoadingMore = false`, so
  `loadMoreActivity` could fire re-entrantly with the same
  `startIndex = currentSize` and double-append a server page. Now:
  `LogsState.isLoadingMoreActivity` (live guard + footer spinner),
  synchronous early-return in `loadMoreActivity`, flag cleared on both
  settle arms. The regression test (two rapid calls → exactly one fetch,
  one appended page) was verified to fail against the old semantics.

## The 2026-09-08 second wave (8 deepenings)

Landed autonomously via parallel workstreams (one repair pass after a
usage-limit kill mid-batch), each pinned; the exploration pass that
selected them rendered its candidates in the run's temp HTML report.
The theme: shell choreography getting homes, the quick-action intake
crossing modules, and the last dark test lanes opening.

- **`NavRequestCollector`** (`:app` navigation, beside
  `RemoteNavigationRouting`) is the shell's one home for the five
  collect-then-dispatch loops `MainContent` hand-rolled composable-inline:
  pendingRoute (tab-vs-nested fork + consume-once), remote navigation
  (ClosePlayer multi-stack pop / target routing — reuses the
  `RemoteNavigationRouting` folds), the remote-control now-playing
  snackbar (title fallback + template), the dual user-message-bus
  adaptation (severity projection; presentation POLICY stays in
  `UserMessageHost`), and SyncPlay auto-open (player-open-anywhere
  guard). Constructor-lambda controller + pure companion folds
  (`pendingRouteDispatch` / `syncPlayAutoOpenRoute` /
  `nowPlayingSnackbarMessage`); the composables keep one-line effects.
  The external-player launch branch is untouched (the deferred
  `ExternalPlayerHost`). Pinned by `NavRequestCollectorTest` (20).
- **`QuickActionIntake`** (shared/core/ui `components/QuickActionIntake.kt`,
  the `HomeQuickActions` shape generalized): the pure
  `quickActionEffect(item, action)` fold over sealed `QuickActionEffect`
  (Play / MarkPlayed / Download / RemoveDownload / OpenDetail /
  ToggleFavorite) plus `rememberQuickActionIntake` +
  `QuickActionAdapter` + `QuickActionIntakeHost` owning the sheet
  controller, TV focus key and remove-download confirm — the ~45-line
  intake block eight screens hand-copied (Library/Favorites/Studio,
  Search, Media/Collection/Person detail, OfflineLibrary) is deleted
  (screens net −164 lines; adapters are navigation lambdas only).
  Declared delta: `ADD_TO_PLAYLIST` folds onto `OpenDetail` — its only
  offering host (the library grid) always routed it there; now the table
  says so once. Home's own fold stays home-shaped and untouched. The
  default `isDownloaded` resolver is one top-level `notDownloaded`
  instance, not a per-recomposition lambda (a fresh default churned the
  remember keys and closed an open sheet). Pinned by
  `QuickActionIntakeTest` (9, incl. an exhaustiveness guard).
- **Play On one home**: `PlayOnViewModel` IS the controller now —
  `JellyfinRemotePlayCastStrategy` is private (the public `val strategy`
  is gone), the transport surface is the narrow documented set, and the
  shell resolves the VM exactly ONCE in `MainContent` (hoisted above the
  TV/phone/full-screen fork, so the companion survives a runtime TV-mode
  flip) and threads it as an explicit `playOn` parameter through all
  three hosts — the companion screen's `koinViewModel()`
  identity-by-convention second resolution is deleted.
  `flingIfConnected(itemId, startPositionMs)` moved in from the inline
  Home redirect adapter. Declared deltas: the `canFling` field is
  DELETED (a `WhileSubscribed` stateIn nothing ever collected —
  permanently false since it landed; the old test pinned it as a
  "likely bug", the new suite pins the fling gate the redirect actually
  reads); the Home Play-On redirect is now armed on every host (was
  phone-layout-only; observable only with a live remote session, which
  only the phone sheet initiates); the 5 s status poll is pinned for
  the first time (seed-in-launch-frame, cadence, silence after
  disconnect). `PlayOnViewModelTest` grows 10→11 tests, both flavors.
- **CI lanes — the last dark ones open**: the android-app job now runs
  `:core:ui:testDebugUnitTest` + `:core:ui:assembleDebugAndroidTest`
  (compile-gate for instrumented sources, the
  `:app:assemblePhoneDebugAndroidTest` precedent), and the shared-targets
  matrix compiles `:apps:web:compileTestKotlinWasmJs` — the sole pins of
  `Route` classification, TV focus wiring, `WebBackStackMirror` and
  `WebConnectFailurePolicy` are dead assertions no longer. Dead
  androidTest triage in the legacy `core/ui`: `ConfirmDialogTest` and
  `SeerrRequestDialogDefaultsTest` DELETED (both referenced `internal`
  panels that moved to shared/core/ui — uncompilable for a while;
  Seerr's assertions were PORTED, see below; ConfirmDialog's surviving
  value was Compose-render semantics the shared jvm lane has no
  framework for, and `ConfirmStateTest` already pins the logic);
  `PlayerModalBottomSheetTest` + `ScreenStateContainerTest` KEPT (public
  types only — clean compiles) and now compile-gated. Local evidence:
  the full `:apps:web:wasmJsBrowserTest` runs 60/60 green; CI stays
  compile-gate-only (karma/Chrome bootstrap ×3 OS is not cheap — a
  dedicated single-OS web-test lane is the recorded next step if
  wanted).
- **`SeerrRequestDefaults`** (shared/core/ui, beside the dialog): the
  request sheet's preselect decision table — default server index
  (radarr/sonarr media-type-named, JVM erasure), profile + root-folder
  defaults (root folders matched by `path`; the "second folder" arm the
  dark test existed for), anime defaults with regular fallback, default
  tags (empty anime tags treated as absent), the tags
  arrival-order APPLICATION KEY (manual tag edits survive list churn,
  reset only on a (server, anime) transition — the stateful half
  modelled as an explicit input), and select-all-seasons — extracted
  pure (`internal object`, no Compose types); `SeerrRequestDialog`
  shrinks 794→771 and calls the policy instead of inline closures.
  Verbatim extraction, no deltas. The instrumented pin is ported to
  `SeerrRequestDefaultsTest` (shared/core/ui `jvmTest`, 11 tests) and
  the legacy androidTest deleted.
- **`MediaCleanupScanStateHolder` + `MediaCleanupScreenScaffold`**
  (`shared/feature/admin/mediacleanup/`): the stale-media /
  watched-cleanup twins' whole scan lifecycle — startScan → detect →
  observeScanProgress → COMPLETED → results-JSON decode → selection →
  confirm → delete, plus the 3-tab scaffold, sort dropdown, select-all
  row and delete sheet — single-homed over a constructor-lambda
  repository seam (repository interfaces untouched); the screens shrink
  to config forms + item cards (production 1903→1692 lines). Declared
  deltas: the twins' progress re-collect race is FIXED (each scan leaked
  its progress collector; the chassis cancels single-flight — a late
  COMPLETED from an abandoned scan can no longer clobber the live one,
  pinned), `StaleMediaState.selectedTabIndex` dropped (no reader), the
  delete-button treatment unified (stale's press-scale wins; watched's
  variant was copy drift), select-all stays an all↔none TOGGLE
  (not `SelectionState.selectAll`'s unconditional select — documented),
  and the chassis is fully localized — the twins' hardcoded English
  permission banner and the sort enum's English label literals are now
  `Res.string.admin_no_delete_permission` + `Res.string.admin_sort_*`
  (9 locales; the enum carries no label, the scaffold maps entries to
  resources).
  Tests 21→21: chassis 15 (lifecycle, decode failure, re-collect race,
  sort, selection, delete arms) + 3+3 slim adapter arms.
- **`EditableItemMetadataForm`** (shared/feature/editor): the editor's
  ~30 metadata fields were enumerated three times (inbound load map,
  outbound save map, order-sensitive dirty hash) with no compile-time
  link — a missed save-map entry silently dropped an edit, a missed
  hash entry killed the dirty flag. Now ONE form value
  (`runtimeMinutes` the declared string-edit twin of outbound
  `runtimeTicks`) plus `MetadataEditSession(value, original)`;
  `isDirty` is structural equality and `computeDirtyHash` is DELETED;
  `EditorUiState` embeds the form as one slice; `updateField` is
  form-typed (MetadataTab call-site syntax unchanged). The drift-class
  pins the old shape could not express: reflection-enumerated tests
  (the `ResetCoverageGuard` precedent, no kotlin-reflect) over the
  form's OWN properties, so a NEW field auto-fails until wired —
  fromDetail→toEditable round-trips every field, mutating every field
  individually trips dirty, untouched stays clean. Declared deltas
  (KDoc'd on `MetadataEditSession`, none UI-reachable): person
  id/primaryImageTag edits now trip dirty; providerIds compared
  order-insensitively; pre-load edits no longer dirty.
- **`DesktopWindowPlacementController`** (apps/desktop): the
  undecorated-window maximize dance (AWT `MAXIMIZED_BOTH` is broken on
  `WS_POPUP` frames) — work-area on maximize, saved bounds on restore,
  replay-once on windowOpened, skip persist in fullscreen, restore-bounds
  preference — over a two-member `DesktopWindowPlacementHost` seam
  (`bounds` + `workAreaOrNull()`; the AWT adapter and the test fake are
  the two adapters that justify it). `DesktopWindowStateStore` is NOT
  re-absorbed (persistence stays pinned by its own suite); `Main.kt` is
  wiring-only. All five rules pinned (12 tests) in the existing
  `:apps:desktop:test` lane, incl. a replay→restore→persist session
  sequence.

- **`SubtitleHttp`** (core/network jvmShared `subtitle/`, beside
  `SubtitleRateLimiter`) is the one HTTP chassis behind both subtitle
  providers: `execute`/`executeForString`/`wrapNetwork` over an options
  record (`redactSecrets`, `captureResponseBody`,
  `rewordSerializationErrors`). The two providers' formerly hand-copied
  execute chassis (OkHttp use, bounded body log, Retry-After-aware
  `ApiException`) and friendly ladder are parameterised divergences now:
  only OpenSubtitles rewords SerializationException (the JSON-token leak
  guard), only Wyzie redacts secrets and captures the body (its
  400-empty detection reads it). Pinned by `SubtitleHttpTest` (MockWebServer,
  ladder/redaction/retryability table) on top of both provider suites.
- **`EmptyLibraryFallbackTest`** (core/network commonTest, the
  `LibraryRequestPolicyTest` neighbour): the fallback ladder both platform
  clients ride is pinned for the first time — memo short-circuit (zero
  transport), the three bypasses, `limit <= 0 → 50` coercion,
  remember-only-genuinely-empty (a FAILED fetch degrades to empty and IS
  remembered), cancellation propagation, and the
  `emptyFallbackTotalCount` table. Declared semantics the test now makes
  explicit: a BLANK search term is an unfiltered browse (the gate is
  `isNullOrBlank`) and pays the fallback.
- **`SubtitleProviderRepositoryImpl`** folds its two fan-outs' shared
  block (no-credentials skip log → isolation
  `runCatchingRethrowingCancellation` → per-arm outcome log) into one
  private `externalOutcomeFor`; `search` keeps awaitAll, streaming keeps
  launch+emitPartial. Declared log-timing delta (KDoc'd): in `search`,
  the per-arm log now fires at each job's completion instead of after the
  barrier, and in `searchAllStreaming` ahead of the launch site's mutex
  store + `emitPartial` (within-job reordering only). Contract still
  pinned by the Robolectric lane.
- **`RecommendationWorkerSkeleton`** (app widget/skeleton) owns the
  recommendation workers' guard → fetch → empty-keep → cap/map/persist →
  retry-fold chassis over `skipFetch`/`fetchItems`/`mapItem`/`persist`/
  `logFailure` seams (per-site logging preserved: Library logs every
  failure, Seerr only permanent). `WidgetGridFactory` gained
  `bindGridCellTail`/`gridCellLoadingView` — the poster-or-fallback +
  responsive-text bind-tail the Library/Seerr services re-copied
  (ContinueWatching keeps its declared diverged tail).
  `BaseWidgetConfigActivity` owns the config save tail
  (getInstance → update seam → notify(gridViewId?) → refreshNow seam →
  finish) — the four `saveAndFinish` hand-copies are gone. The recorded
  deferred trio (worker chassis, bind-tail, config tails) is closed;
  behaviour pinned by the existing worker/factory suites.
- **`PipActionSet`** (app, beside `PipLifecyclePolicy`): the PiP action
  apparatus's pure halves — `actionSpecs(isPlaying, hasNext)` (the ordered
  skip-back → play/pause icon+title fork → skip-forward → gated-next
  fold, resource ids only) and the `idFor`/`actionForId` wire codec plus
  the protocol constants, moved verbatim (wire-stable across app
  updates). `PlayerActivity` keeps only RemoteAction/PendingIntent
  wiring. Pinned by `PipActionSetTest` (14: fork cells, round-trip,
  unknown-id nulls, protocol strings verbatim).
- **`ExternalPlayerHost`** (app navigation/playbackhost, the recorded
  design landed): the six-step launch protocol (resolve → report-start →
  stash → chooser → failure clears stash + error; result consumes once →
  ticks fold → report-stop) over constructor lambdas; the shell keeps
  the remember construction, the ActivityResult wiring, and one-call
  sites. The launcher arrives per-call (the host must exist before the
  launcher's callback can reference it — KDoc'd). Ordering pinned by
  `ExternalPlayerHostTest` (Robolectric, fake-lambda choreography) —
  the pieces were tested before, the ORDERING was not. Its chooser arm
  rides `runCatchingRethrowingCancellation`.
- **Desktop `NowPlayingTracker` adoption**: `DesktopAudioQueueManager`
  constructs the tracker and re-exposes its six metadata flows by
  reference (the Android manager's pattern — public property names
  unchanged, tray/title-bar/shared screens untouched); the three
  hand-write sites are `publishDetail`/`publishQueueItem`/`clear`.
  Declared delta: `stopAndRelease` no longer resets `artistId` (the
  tracker's clear() deliberately keeps it — the Android side already
  behaved that way). Pinned by 3 new `DesktopAudioQueueManagerTest`
  cases; the publish contract is now single-pinned
  (`NowPlayingTrackerTest`) across both platforms.
- **`CueAccumulator` sharing**: `mergeAccumulatedCues` is public
  (desktop-engine adapter surface, the `PlaybackVolumePolicy` precedent
  language); `MpvDesktopEngine`'s ~30-line private mirror is deleted —
  the desktop keeps only its `sub-start` read divergence at the call
  site. Merge rules single-pinned by the existing `CueAccumulatorTest`
  for both platforms.
- **`MpvErrorTaxonomy`** (player-video commonMain engine/): one
  errorCode→`EngineError` table (−13 LOADING_FAILED → Network; −14…−19
  init/format family → Decoder; else Unknown) plus the Android string-code
  normalization (`fromCodeString`). The two engines' private tables are
  deleted. Declared divergence parameter: the Unknown arm's diagnostic
  (`unknownDetail`) — desktop passes `mpv_error_string(code)`, Android
  the raw handed-over string, each preserving its former behaviour.
  Pinned by `MpvErrorTaxonomyTest` (9).
- **`PicoConfigHtml`** (admin commonMain plugins/, beside
  `PluginBridgeScript`): the pico WebView builders (`colorToHex`,
  `buildPicoOverrides`, `buildWrappedHtml`) moved out of androidMain
  where no CI lane could reach them; androidMain keeps WebView wiring.
  Pinned by `PicoConfigHtmlTest` (11).
- **Storage-byte vocabulary** (core/model `ByteFormatter`): a
  `Long.toStorageBytesValue()` band table now backs `formatBytes`; the
  four drifted UI copies (Logs, PhotoViewer, DetailDownloadDialog,
  ArrQueue) migrated onto it. Declared deltas: ÷1000 sites (PhotoViewer,
  DetailDownload) join the ÷1024 house convention; Logs' integer-KB and
  ArrQueue's `%.0f KB` collapse to the one-decimal band table, as does
  the ÷1000 pair's sub-KB funnel (500 B showed `0.5 KB`, now `500 B`);
  Logs gains the GB band. DetailDownload keeps localization via a per-unit
  `localizedStorageSize` wrapper. `AdminStatisticsRepositoryImpl
  .formatSize` stays untouched (Room-persisted text — the recorded
  blocker). Known residue: core/ui's `FormatFileSize.kt` (SI, own test)
  and PlaybackInfoOverlay's private copy remain — different surface,
  opportunistic. Pinned by `FormatStorageBytesTest` + `ByteFormatterTest`.
- **`AdminLoad`** (admin commonMain, the `LiveTvLoad` shape): the admin
  slice of the load-ladder fold — 10 VMs (Dashboard, Devices, Logs,
  Plugin Detail, Plugins, Stats, Stats Detail, Scheduled Tasks, Users,
  androidMain Plugin Config), both ladder shapes. The helper owns
  start → single suspend fetch → exactly-one-arm dispatch; settles stay
  per-VM as declared variants (final-update, flavour starts, Dashboard's
  persisted-error try/catch expressed as a `runCatching{getOrThrow}`
  fetch, Logs' parallel pair under one catch). Declared timing
  unification: Plugins/ScheduledTasks' legacy fire-and-forget inner
  launch now awaits — `isLoading` covers the fetch (per-VM suites assert
  settle only after `advanceUntilIdle`, unmodified). Pinned by
  `AdminLoadTest` (8, the `LiveTvLoadTest` pattern).
- **Cancellation ratchet repo-complete**: `BareRunCatchingRatchetTest`
  guards every shared/core + shared/feature root, legacy core/data +
  core/ui, core/notification, both shells and web — baseline 22 (down
  from 27 on the smaller surface). The widened sweep's one live hazard —
  `AddToTargetActions.resolveTargetItemIds` — is converted (details now
  depends on core/concurrency); the two deliberate sites are named in the
  test KDoc. See the Concurrency section.

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
- **Widget grid skeleton**: LANDED (`aec4c138b`, plus the 2026-09-07
  id-resolution/notify straggler helpers) — see "App widgets" above.
- **Web session via shared `AuthRepository`**: the web shell's
  `WebConnectController` re-implements the AuthRepository establishment
  choreography by hand over raw `AuthApiClient` (its KDocs say "call
  order mirrors `AuthRepositoryImpl`", "same shape as
  `revokeServerSession`"), including its own `web_last_server_url`
  persistence. ADR-0001's revisit trigger has FIRED (a third shell
  gaining session state). Design: promote the `AuthRepository`
  interface into core:data commonMain with the Room/identity edges as
  injected seams (or a thin wasmImpl over `AtomicSessionState` + the
  `user_prefs` DataStore — core:data has had a wasm target since wave
  15B); `WebConnectController` deletes down to construction plus its
  capability-note flow. Deferred: cross-module persistence-edge design
  deserves the grilling loop, not an autonomous batch.
- **Feature-VM load-ladder fold**: the `isLoading = true, error = null`
  suspend-guard ladder is hand-copied across requests,
  calendar, editor, music, syncplay (the livetv slice
  LANDED 2026-09-08 as `LiveTvLoad` and the admin slice — 10 VMs, both
  ladder shapes — as `AdminLoad` in the same day's third wave; see those
  waves). Settle arms are
  drifted per copy (final-update vs per-arm vs getOrDefault — a missed arm
  leaves a stuck spinner). Design: one `loadInto`-shaped helper in core:ui
  next to `JellyPlayViewModel` (or a per-module helper like `LiveTvLoad`),
  VMs map Success payloads into their own state. Deferred: each conversion
  is a per-VM behaviour decision (settle timing); land module-by-module
  with pinned tests, not as one mechanical sweep.
- **`DetailViewModel` intent fold**: ~29 public funs force the 160-line
  hand-built `DetailContentCallbacks` adapter in `MediaDetailScreen`
  (keyed on 15 values). Design: sealed `DetailIntent` + `onEvent` (the
  `HomeViewModel` pattern); `DetailPlayPolicies` (landed) already carries
  the load-bearing pure decisions so the fold inherits tested arms.
  Deferred: 1742-line existing suite + screen wiring deserve their own
  session.
- **`TrickplayPreviewSource`** (player-video): "fetch a trickplay thumbnail
  for this position?" is a 3-way split — seek-lane gate, gesture-lane gate
  (info-null check inside the collect body), and the VM prefs double-check —
  with the 4-step fetch choreography hand-copied in two `snapshotFlow`
  collectors. Design: a constructor-lambda controller owning ONE gate
  predicate, the fetch, and per-lane clear/linger (seek clears immediate,
  gesture lingers 1 s — keep the lanes as declared variants over one core).
  Deferred: overlay-timing regressions are visual-only; needs device eyes.
- **`SubtitleStyleController`** (player-video): the subtitle style/delay
  write path is ~7 pockets inside the unconstructable 2704-line
  `VideoPlayerViewModel`, protecting the load-bearing invariant "the
  in-memory offsetMs is the per-item resolved delay and must never persist
  into the global store" by KDoc discipline across three write paths.
  Design: a `SubtitlePreviewController`-style controller (style flow,
  `setStyle`/`setDelay`/`installFont`/`resolveForItem`, constructor
  lambdas so the god-count ratchet stays at 3). Deferred: the engineFlow
  collector seeds `uiState.subtitleStyle` with the resolved style on every
  engine bind and the write must stay ordered before the first
  `updateConfigWithUiState` — land in two steps, mirror write VM-side
  first.
- **`ExternalPlayerHost`** (`app`): LANDED — see
  `navigation/playbackhost/ExternalPlayerHost.kt`; the shell-churn trigger
  had fired (two waves since the record).
- **`PendingConfirmation<T>`**: the confirm-dialog pending-item machine
  (hold item → dismiss = null-write → confirm clears + runs + reloads)
  was recorded hand-copied in 6 places (Devices/Users/Recordings/
  ManageSeries/ArrQueue VMs + two `remember`-state machines inside
  `MediaDetailScreen`); the 2026-09-08 third-wave census counted ~14
  (new copies: Downloads' two, ImportPreview, PrivacyData, FactoryReset,
  SyncPlay's join pair, AdminDashboard's stop-session pair — itself the
  in-flight-flag variant vocabulary this record warned about — editor's
  image delete). Drift-shaped variance: only Recordings has the
  dismiss-during-in-flight guard; only ArrQueue generalized to a sealed
  action. Deferred: universalizing the guard changes dismiss behaviour at
  ~13 sites — the decision deserves to be made explicitly, and the
  leverage grows with every copy.
- **Settings/Library section hosts**: `SettingsScreen`'s root composable
  holds ~1150 lines (every section inline; the leaves are already
  extracted); `LibraryScreen` similar (~1270-line body). Design: a
  section-list seam matching `SETTINGS_ENTRANCE_SECTIONS`, one private
  composable per section. Deferred: composition-shape only, zero
  behaviour — do settings + library together, never bundled with
  behaviour changes.
- **`PageAppender`**: three append-page ladders with drifted re-entrancy
  vocabularies (Requests guards on `isLoading`, StatsDetail on
  `!isLoadingMore && hasMoreItems`; the Logs defect is FIXED —
  2026-09-08 wave). Design: one small appender owning the in-flight guard,
  `hasMore`, and index math. Deferred: each site's interleaving semantics
  deserve their own pinned interleaved-completion tests.
- **SelectionActionBar unification**: three per-screen bars
  (arrqueue/downloads/requests) share anatomy but have drifted visually
  (corner radius, container color, icon-vs-text buttons, approve/decline
  placement); downloads adds `has*` enable flags computed in-screen.
  Deferred: pixel changes are a product call — screenshots first.
- **Pull-to-refresh spinner policy**: `PullToRefreshBox` is shared
  (good) but "when does the spinner show" is re-decided per call site —
  livetv Series spins on cold load (full-screen blank + spinner), music
  Albums and calendar guard with "have content". Deferred: pixel-visible
  product decision (the livetv cold-load spinner is probably wrong, but
  that's a call, not a fold).
- **Widget worker refresh chassis / grid bind-tail**: LANDED (2026-09-08
  third wave) — `RecommendationWorkerSkeleton` + the `WidgetGridFactory`
  bind-tail; see that wave.
- **PiP action apparatus** (`:app` `PlayerActivity.kt` ~661–747): LANDED as `PipActionSet` beside `PipLifecyclePolicy` —
  the PiP-churn trigger had fired (`PipLifecyclePolicy` itself landed in
  `541cdabee`).
- **Mood/Smart generated-playlist state idiom** (`shared/feature/music`):
  `MoodPlaylistsViewModel` + `SmartPlaylistsViewModel` hand-sync four
  loose compose states through every generate call, duplicate the
  `"custom-" + UUID` id convention + delete guard, and use raw compose
  state fields where the rest of the module uses one `UiState` flow —
  two state idioms in one module. Design: one `GeneratedPlaylistState`
  snapshot holder (pipeline + id convention + playAll), per-kind
  filter/sort functions stay pure adapters. Deferred: no churn pressure;
  land with the next music-feature change.
- **`DetailContentBody` section admission** (`shared/feature/details`
  `MediaDetailBody.kt` ~202–1136): which sections render in what order
  is an inline mediaType × origin × capabilities decision table inside a
  ~935-line composable — the last untested decision surface in the
  details screen tree (`SeasonsSection`'s 24-parameter interface is the
  same hand-splicing `DetailContentState` was built to avoid). Design: a
  pure `DetailSectionPolicy` + the state bundle threaded whole.
  Deferred: sequenced deliberately BEHIND the `DetailViewModel` intent
  fold — do not race them.
- **Widget-config save tails** (`:app` `widget/config/
  WidgetConfigActivity.kt` ~121–181): LANDED with
  the refresh chassis it was recorded to fold together with —
  `BaseWidgetConfigActivity` owns the save tail.
- **User-feedback conveyor completion** (top
  declined candidate, recorded so the next run designs it instead of
  re-discovering it): the one-shot message stratum re-derives the shared
  `UserMessageBus`/`UserMessageHost` pair per feature — 7 expect/actual
  Messenger trios (library, livetv, settings, calendar, downloads,
  arrqueue, admin), 10 per-feature message seals (6 with identical
  `asText()` collapses), 2 private buses (music's, player-video's), plus
  per-screen SnackbarHostState sites and two direct legacy-bus leaks
  (PluginConfigScreen, LivePlayerScreen). The shared bus's
  `UiText.Resource(args)` already covers every seal's shape. The deletion
  test passes harder than anything in the third wave, BUT presentation is
  the blocker: web has no message surface (several trios' wasm actuals
  already no-op), several screens own their SnackbarHostState (SyncPlay,
  Newsletter, UserDetail, ManageSeries, both players), and moving VM
  posts onto the shared bus changes what non-Android shells render.
  Design: land per feature (VM posts `UserMessage` with resolved
  `UiText`; the trio/seal/screen-collapse deletes), starting with a
  feature whose screen already defers to `UserMessageHost`; needs a
  per-shell presentation mapping decision first. Deferred: deserves the
  grilling loop, not an autonomous batch.
