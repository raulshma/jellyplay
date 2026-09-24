# Architecture context

Orientation for engineers (and coding agents) new to JellyPlay's codebase.
User-facing feature docs live in `docs/`; this file is about how the code is
shaped. The repo's KMP migration (docs/kmp-migration-plan.md) is COMPLETE
(2026-09-13): every feature lives in `shared/feature/*` (KMP,
commonMain + platform actuals), the core stack in `shared/core/*` — including
the former legacy modules' Android halves, now `androidMain` source sets of
`:shared:core:ui` / `:shared:core:data` (identical packages; Robolectric
suites in their `androidHostTest` lanes) — and the only Android-only module
left is the `:app` shell, beside `apps/desktop`. The
persistence layer is Room 3 (`androidx.room3`) on android/jvm.
DI is Koin-only repo-wide. Player code lives in two shared modules:
`shared/core/player-contract` (the engine-agnostic `MediaEngine` contract and
engine-shared machinery) and `shared/feature/player-video` (the VOD player
screen, ViewModel, and session collaborators). The two desktop-and-Android shells register their nav sections through one
aggregator module, `shared/feature/shell` (`appSections` + `ShellHostHooks` +
a registration ledger the desktop dead-end guard derives from). Paths below
are relative to the repo root.

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
- **`EngineEventCoordinator`** (`shared/core/player-contract/src/commonMain/kotlin/.../engine/EngineEventCoordinator.kt`,
  moved from player-video in the C4 dedup so BOTH players consume one policy
  core) owns the engine-event *policies*: guarded play/buffering mirrors, the
  transcode-fallback policy, the 20 s buffering watchdog, subtitle toasts,
  and pass-out protection. Its decision model: raw engine flows in — one
  `EngineEventSource` per engine instance carrying the minimal
  `isPlaying` / `playbackState` / `errors` / `subtitleEvents` /
  `currentPositionMs` slice (`MediaEngine` maps via `toEngineEventSource()`;
  the live tuner engine feeds its own instance, mapping `LiveEngineState`
  onto `EnginePlaybackState`) — and `EngineDecision`s out (`ShowError` /
  `FallbackToTranscode` / `PlaybackEnded` / `PassOutPause` / `InformUser`) on
  a `tryEmit`-only `SharedFlow`. It never writes uiState and never commands
  the engine — every policy is assertable with flow fixtures plus an
  injected clock (suite in player-contract's `commonTest`). The two hosts pin
  their historically-different behavior via `Config` knobs instead of copies:
  the VOD `PlaybackSession` constructs, re-arms and executes the
  coordinator's defaults (`FallbackPolicy.FORCE_DIRECT_PLAY_ONE_SHOT` — the
  one-shot latch re-armed by `onNewItem`/`onPlaybackModeChanged` — and
  `WatchdogScope.INITIAL_BUFFER_ONLY`), and its ViewModel only collects the
  mirror `StateFlow`s; `LiveTvPlayerViewModel` passes
  `WatchdogScope.EVERY_BUFFERING_EPISODE` (a stalled tuner can stall
  MID-playback without an exception, unlike a VOD rebuffer) and
  `FallbackPolicy.EXTERNAL_REQUEST_ONLY` (the tuner engine's per-load phase
  machine decides WHEN a direct/direct-stream failure falls back and drives
  `onTranscodeFallbackRequested()` — unlatched; the engine owns the one-shot
  counting) and executes the resulting decisions itself.
- **`EngineSessionShell`** (player-contract commonMain `engine/`) is the
  session-structural plumbing both hosts construct instead of hand-wiring
  the coordinator's lifecycle/event machinery: coordinator construction
  over the host's hot engine-source stream, `dispose`/`reArm` and the
  decision fan-out TaskBundle slot. Hosts: `PlaybackSession` (VOD) and
  `LiveTvPlayerViewModel` (live). Pinned by `EngineSessionShellTest`.
- **`PlayerChromePolicies`** (player-contract commonMain `engine/`) are the
  pure chrome-timing policies BOTH player screens cite — one home instead of
  byte-identical copies: `controlsAutoHideTimeoutMs` (the TV-doubling fold
  over each screen's preference-sourced base timeout) and
  `liveWindowRefreshLoop` + `LIVE_WINDOW_REFRESH_TICK_MS` (the live player's
  DVR-window refresh poll — required because the live engine contract is
  media3's PULL model: position/duration/live-edge only republish when
  `refreshLiveWindow()` is called, so a poll cadence is unavoidable; the
  loop is gated on the auto-hiding chrome being visible, since its consumers
  all render inside it).
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
  bodies — and the Activity pause/resume template the three engines carried
  byte-identical (`onActivityPause()`/`onActivityResume()`: remember
  isPlaying → pause → conditional restore; the `onPausedNative()`/
  `onResumingNative()` hooks absorb the per-engine native quirks). Each
  adapter still owns its native
  player handle, native track/subtitle application (the common fold rides
  `MpvTrackCatalog`/`MpvSubtitleSideLoadPlan`), stats projection
  and `positionFlow` wiring — `NoOpEngine` does NOT extend this class.
- **`ReloadablePlayerEngine`** (`shared/feature/player-video/src/androidMain/kotlin/com/raulshma/jellyplay/feature/player/video/engine/ReloadablePlayerEngine.kt`)
  is the second layer for the three reloadable engines (extends `BasePlayerEngine`).
  It hoists `PlaybackSnapshot` / `withPreservedPlayback` (position+speed+isPlaying
  preservation across a rebuild), the volume/mute commands — delegated to
  commonMain `VolumeCommandTemplates` over one `NativeVolumeSurface` seam
  (see that bullet), the
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
  half is now ONE commonMain template, not twelve bodies:
  `VolumeCommandTemplates` (player-video commonMain `engine/`) owns the four
  `MediaEngine` commands as `final` templates (plan → remember → capture →
  native write → system-stream mirror) over the `NativeVolumeSurface` seam —
  `readNativeVolume()` (null aborts the delta templates, the old
  `?: return`s), `applyNativeVolume`, `volumeBoostCeiling`,
  `nativeVolumeRestore(muted)` (the policy's `NativeVolumeRestore`
  vocabulary: Exo ZERO/FULL, mpv LEAVE_UNCHANGED, VLC
  ZERO/REMEMBERED_LEVEL), `applyNativeMuteFlag` (mpv's real flag),
  `muteTemplateEnabled` (VLC's null-handle abort), the Android-only
  system-stream sync and the desktop-only user-change capture. BOTH the
  Android `ReloadablePlayerEngine` finals and the desktop `MpvDesktopEngine`
  delegate here (the desktop formerly hand-rolled the ordering in
  parallel); the per-engine `dispatchVolumeCommand` shell stays adapter-side
  (Exo's player-thread post + null abort; mpv/VLC swallow-all). The remember
  call is unified BEFORE the native write — the former order in Exo/VLC;
  mpv's increase/decrease had drifted to remember-after. Pinned by
  `VolumeCommandTemplatesTest`.
  `PlaybackVolumePolicy` itself is public (not internal) because the
  protected `nativeVolumeRestore` seam returns its nested enum — a protected
  member cannot expose an internal type; it is not a stable API surface.
- **`PlayerLifecycleManager`** (`shared/core/data/src/jvmShared/kotlin/com/raulshma/jellyplay/core/data/playback/PlayerLifecycleManager.kt`)
  is the Activity↔engine lifecycle bridge: the host Activity calls
  `onActivityPause()` / `onActivityResume()`, which delegate straight to the
  `@Volatile activeCallbacks` engine reference (set by `PlayerSessionManager`
  on create/release; no StateFlow hops). Pausing is skipped when
  background-audio is enabled in `PlaybackStore`.
- **`MpvErrorTaxonomy`** (player-video commonMain `engine/`) is one
  errorCode→`EngineError` table (−13 LOADING_FAILED → Network; −14…−19
  init/format family → Decoder; else Unknown) plus the Android string-code
  normalization (`fromCodeString`); the two engines' private tables are
  deleted. Declared divergence parameter: the Unknown arm's diagnostic —
  desktop passes `mpv_error_string(code)`, Android the raw handed-over
  string. Pinned by `MpvErrorTaxonomyTest`.
- **`MpvEventFold`** (player-contract commonMain `engine/`) is the one
  raw-mpv-event → engine-state fold both mpv engines apply: the
  `MpvPlaybackLatches` latch set (isPlaying is fileLoaded/eof-gated — the
  Android engine formerly wrote it unguarded from the pause observer; the
  desktop ran a thinner set) and the decision enums for the fold-out
  effects — cue clears (the desktop's clear-on-`sid`-switch arm), track
  re-enumeration, the live-subtitle mirror, the END_FILE error signal.
  Pinned by `MpvEventFoldTest`.
- **`MpvTrackCatalog` + `MpvSubtitleSideLoadPlan`** (same `engine/`, next)
  are the shared track-republish half: the pure track-list → `MediaTrack`
  catalog both mpv engines funnel `buildTracks` through (the desktop
  formerly re-parsed bare, so offline-restore ids never resolved there) and
  the `sub-add` side-load plan whose registry stamps the caller's
  `SubtitleSource.id` onto the republished tracks — the side-load id
  contract pinned per platform by
  `MpvTrackCatalogTrackSelectionContractTest`.
- **`CueAccumulator`** merge rules are shared: `mergeAccumulatedCues` is
  public (desktop-engine adapter surface); `MpvDesktopEngine`'s private
  mirror is deleted — the desktop keeps only its `sub-start` read
  divergence at the call site. Single-pinned by `CueAccumulatorTest` for
  both platforms.
- **The desktop mpv engine rides the same commonMain policies** (both
  precedents set by `mergeAccumulatedCues`): `MpvStyleMapping` is now a
  PUBLIC object because `MpvDesktopEngine` applies the same
  `SubtitleStyle` → mpv `sub-*` table to its JNA handle (its former
  private `argbCss`/edge-table mirror is deleted) — subtitle styling is
  one mapping for both platforms. Its volume/mute choreography is
  `PlaybackVolumePolicy` the same way (`planLevel`/`planMute`/
  `planUnmute` over the `NativeVolumeRestore` vocabulary): the desktop
  previously hand-rolled `coerceIn(0f,1f) * 100` + a bare mute flag, so
  unmuting never restored the remembered level — the `REMEMBERED_LEVEL`
  unmute plan now writes the restore into the mpv `volume` property
  (desktop has no system music stream, so the property IS the audible
  surface; Android's mpv adapter instead pairs `LEAVE_UNCHANGED` with
  the system-stream sync).
- **The media3 narrowing is a typed capability, not a cast**:
  `RemotePlayableEngine.underlyingPlayer: Any?` is RETIRED (the
  player-contract interface keeps only control members).
  androidMain's `Media3PlayerHost` capability interface
  (`media3Player: Player?`, implemented by `ExoPlayerEngine`) plus
  `MediaEngine.asMedia3Player()` is the SINGLE media3 narrowing site —
  the former per-callsite `engine.underlyingPlayer as? Player` pattern
  is gone (the `ZoomSafeSubtitleStrategy` precedent of declaring a
  capability on the seam instead of type-testing concrete adapters).
  `MediaSessionController.createForPlayer` takes the typed
  `MediaEngine?` (no-op when the engine hosts no media3 player) and
  `createForBackgroundCast(sessionId)` resolves the cast receiver's
  player behind an androidMain-wired provider, replacing the `Any?`
  cast path; player-video's `CastManager` seam dropped
  `castPlayerForSession` accordingly (the legacy core:data member feeds
  `VideoMediaSessionFactory`'s provider wiring directly).
- **`EnginePositionTicker` first tick**: `startPositionTracking` primes
  one synchronous `tickBody()` read after launch — without it the ticker
  delayed before its FIRST tick where the former hand-rolled loops
  published position/duration immediately (the stop-report
  `_duration.value * 10_000` fallback read 0 and advance stop reports
  were suppressed; the regression surfaced when DesktopAudioQueueManager
  adopted the ticker).

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

**`PlayerStores`** (`shared/feature/player-video/src/commonMain/kotlin/.../PlayerStores.kt`)
is the player's construction-time store bundle — the home `HomeStores` move
applied to `VideoPlayerViewModel`'s constructor (44 → 33 parameters at the
move): the TWELVE datastore stores the player reads and writes (`aggregate`
`VideoPlayerAggregateStore`, `engine` `PlayerEngineStore`, `subtitleLanguage`
`SubtitleLanguageStore`, `playback` `PlaybackStore`, `audio` `AudioStore`,
`audioEffects` `AudioEffectsStore`, `videoPlayer` `VideoPlayerStore`,
`security` `SecurityStore`, `syncPlayCast` `SyncPlayCastStore`, `downloads`
`DownloadsStore`, `appearance` `AppearanceStore`, `networkOffline`
`NetworkOfflineStore`) arrive as ONE `stores: PlayerStores` aggregate, so a
new store dependency widens the bundle + the two platform Koin definitions —
`androidPlayerVideoModule` and `desktopPlayerVideoModule` each construct
`PlayerStores(...)` inline at the viewModel call site, `homeModule`-style —
not the VM interface and every call site with it. NOT a read-only narrowing:
the VM keeps every store command write (subtitle style/delay, playback
mode/quality/frame-rate persistence, equalizer, mute/autoplay mirrors, PIN
verify, smart-download gate, haptics), and members still flow through to the
internally-built modules under their ORIGINAL receiving parameter names
(`PlayerSessionManager`'s `aggregateStore`, `PlaybackSession`'s
`playbackStore`, `SessionLoadPipeline`'s `aggregateStore`+
`networkOfflineStore`, `TrackSelectionHelper`'s `engineStore`/`subtitleStore`,
`SleepTimerController`'s `audioStore`, `VideoEffectsController`'s trio, the
cast controller's `syncPlayCastStore`) — the deep modules' own signatures are
untouched. The rest of the constructor stays explicit on purpose: a
`HomeRefresherFactory`-style construction factory was evaluated and rejected —
every remaining parameter is either a runtime input (`platform`,
`savedStateHandle`), a collaborator the VM body touches directly, or a
pass-through to exactly ONE internally-built module whose construction wiring
the VM deliberately shows in one place (and `ControllerOwnershipTest` pins
that declaration order), so a factory would move the same width without
hiding a runtime input. `PlayerStores` is PUBLIC (unlike home's internal
`HomeStores`) because `VideoPlayerViewModel` itself is public — a private-val
constructor parameter cannot expose an internal type.

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

**`RenderControls`** (player-video commonMain) is the render-slice carrier
beside `PlayerScreenPolicies`: the Rendering-sheet + gear-menu deinterlace
write choreography extracted from `VideoPlayerViewModel` over
`SessionRenderState` (the VM's `sessionRender` alias kept; the engine
re-apply stays VM-side via the dirty-config callback). Pinned by
`RenderControlsTest`. **`PlayerWindowSessionEffects`**
(`PlayerWindowSession.kt`) is the window-effect session: the eight
host-window/lifecycle effects moved verbatim into ONE composable called at
the same composition position, so effect dispatch order is preserved (the
race-documented guards ride along unchanged).

`VideoPlayerScreen`'s main composable is nine private siblings now
(`CastCompanionDashboardBranch`, `playerBoxKeyInputModifier`,
`Modifier.playerTapAndZoomGestures`, `PlayerGestureOverlayTier`,
`PlayerCenterOverlayTier`, `PlayerLockOverlayTier`,
`PlayerStatusOverlayTier`, `PlayerSubtitleDelayOverlay`,
`PlayerSeekScrubTrickplayOverlay`); the controls' arg-prep block stays
INLINE deliberately — its lambdas are remember-memoized delegates for
skippability, and extraction would freeze them. The screen's
`MediaContentProjector` absorbed the VM's `sessionState` collector
residue via narrow seams (`setTitleSubtitle`,
`onStoredSelectionChanged`, `getStoredSelection`,
`refreshPlaybackPreferences`, `onSessionItemChanged`, `launchAsync`), the
`lastItemId`/`lastSeriesId` fold state is projector-private, and the VM
collector is one delegation — the `godStateWirings` ratchet still 3, six
new projector pins.

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

**`PlayerKeyPolicy`** (beside `PlayerScreenPolicies`) lifts the media-key
decision table out of the `VideoPlayerScreen` composable:
`mediaKeyAction(keyCode, controlsVisible): PlayerKeyAction?` (sealed arms
TogglePlayPause…HideControls/Exit, media-key aliases, both ESC arms,
unknown → null); the screen keeps a one-line effect shell, and both
delivery paths (focused-chain `.onKeyEvent`, desktop key sink) are
provably identical. The TV D-pad handler stays out (stateful by design).
Pinned by `PlayerKeyPolicyTest`. `PlayerScreenPolicies.resumeSkipTargetMs`
(skip ≤ 0 → unchanged; else 0-floor) is the one resume-skip-back math,
with the `isPlaying` guard divergence DECLARED — `onRegain` applies it
unguarded (focus regain follows a transient loss), `resumePlayback` keeps
the guard (the play toggle must not scrub a playing stream) — and the two
byte-identical cinema-advance-else-close end-of-media bodies are one
private `onEndedWithNoNext()`.

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
  pipeline's `initializeTrickplay` hook runs `TrickplayPreparation`
  (`…/trickplay/TrickplayPreparation.kt`, the fold of the VM's former
  ~45-line inline selection): the mutually-exclusive three-way pick of
  server info cached into the download dir, a local bundle shipped with the
  download (`OfflineTrickplayHelper`), or a fresh server manifest whose
  fetched tiles cache into the download's trickplay dir (so the next
  offline session resolves through the local-bundle arm) — exactly one
  uiState write per successful arm, and the download's trickplay-dir
  derivation (the three former inline copies disagreed) has its single
  home there. The chosen info is stored in `uiPrefs.trickplayInfo` and
  the tile cache initialized in `TrickplayManager`
  (`shared/feature/player-video/src/androidMain/.../trickplay/`). The prefs
  `trickplayEnabled` and `trickplayOnSeekGesture` live in the `uiPrefs` slice;
  when gesture previews are on, the seek overlay calls
  `VideoPlayerViewModel.getTrickplayThumbnail(positionMs)` to render
  thumbnails while scrubbing.

`SubtitleManager.openSubtitleHub(resetFirst)` is the one hub-open
command (optional reset → `loadRemoteSubtitles` →
`loadSubtitleCultures` → `loadConfiguredProviders`, historical order);
the screen's three hand-copied cascades are gone: the click sites route
only (overflow sets `resetFirst = true` via a screen-local single-shot
pending flag), and the sheet router's `LaunchedEffect` is the SINGLE
trigger. Declared deltas: the Tracks-tab double-fetch is gone (one
remote-subtitle request per open, was two), and sheet-open loading
starts at composition rather than at click (sub-frame; the hub spinner
covers it). Pinned by `SubtitleManagerTest`.

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

**SyncPlay cores**: **`TimeSyncManager`** is the clock-projection home —
pure `projectCurrentTicks(positionTicks, elapsedMs)` beside the instance
`estimateCurrentTicks(ticks, whenMs)`, companion
`msToTicks`/`ticksToMs` (exact `* 10_000` / truncating `/ 10_000`) and
`parseIsoTimestamp` (the Instant→OffsetDateTime ladder); the three
byte-identical `estimateCurrentTicks` copies collapsed onto it and 12 raw
conversion sites swapped. ONE site deliberately left: PlaybackCore's
fractional `diffTicks / 10_000.0` feeding speed-to-sync (the truncating
helper would change correction behavior). Pinned by
`TimeSyncProjectionTest`. **Surface prune**: `SyncPlayRepository` 18→11→4
and `SyncPlayController` 16→13 — the pruned members had zero
repository-typed callers (join/leave call sites target `apiClient` or the
controller; a second census retired the seven ignored-Result transport
commands — pause/unpause/seek/stop/setRepeat/setShuffle/setIgnoreWait —
to `SyncPlayController` via the feature-local `SyncPlaySession`). The
repository surface is now the four AWAITED members
(`getSyncPlayGroups`/`createSyncPlayGroup`/`getSyncPlayInfo`/
`syncPlaySetNewQueue`); the controller is the ONE fire-and-forget wrapper
home and its `reportReady`/`reportBuffering` KDoc pins the clock contract
(`whenMs` is `timeSyncManager.remoteNow()`, never wall clock — the api
client's nullable-whenMs `LocalDateTime.now(UTC)` fallback is named as
exactly the forbidden behavior). Ratcheted by
`SyncPlayRepositorySurfaceTest` (baseline 4, "lower when the surface
shrinks, never raise" + a retired-names pin so re-addition fails even
under the cap). **Bridge fold**: `SyncPlayUiState.from(group)` /
`.cleared()` beside the model collapse the bridge's six populate/clear
blocks; position reconcile is core-owned —
`SyncPlayPlaybackCore.reconcileToServerPosition(serverTicks, whenMs,
lane, groupIsPlaying)` with `ReconcileLane` (SCHEDULED_UNPAUSE 500 ms /
QUEUE_UPDATE 300 ms — declared per-lane variants, values NOT unified,
both pinned); `scheduleUnpause` keeps its unconditional-seek branch at
its own call site (the tolerance gate would silently change it);
`pendingItemLoad` is core-owned (arm-only — the READY-arm clear and
reset stay internal). The `SyncPlayGroupInfo` fields STAY: live
writers/readers exist (SyncPlayViewModel writes
playingItemId/positionTicks from PlayQueueUpdate events; the screen
renders playingName) — only the server-producer never fills them, now
KDoc-relevant. Pinned by `SyncPlayPlaybackCoreReconcileTest`.

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
all — each controller exposes its own `StateFlow`. `SettingsProjector`'s
three per-axis sync helpers folded into one generic
`syncPref(selector, newValue, updater)` — the selector/updater lambdas
compose the slice traversal (a `KProperty1<VideoPlayerUiState,*>` cannot
reach a stored slice leaf), preserving the single-copy
distinct-until-changed guard for a leaf in any slice. The dual-home
`PlayerPrefsSeed` warning stands: seven leaves are mapped in BOTH the
change-time projector and the load-time seed — move or add a leaf in both
or drop one side deliberately (the guarded-diff semantics stay
deliberately unmerged into the seed).

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
untouched. Pure and unit-tested; no feature-code imports. The status
DECISIONS that ride on these enums are model-level too:
`SeerrStatusDecisions.kt` (`SeerrRequestItem.effectiveMediaStatus()` +
the `SeerrMediaStatus` availability predicates — see Shared UI
vocabulary); the label/color presentation stays requests-local.

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

**`SeerrRequestDefaults`** (shared/core/ui, beside the dialog) is the
request sheet's preselect decision table: default server index
(radarr/sonarr media-type-named), profile + root-folder defaults (root
folders matched by `path`), anime defaults with regular fallback, default
tags (empty anime tags treated as absent), the tags arrival-order
APPLICATION KEY (manual tag edits survive list churn, reset only on a
(server, anime) transition), and select-all-seasons — extracted pure (no
Compose types); the dialog calls the policy instead of inline closures.
Pinned by `SeerrRequestDefaultsTest`.

The Seerr **poll is refcounted + self-gating**: `startPolling`/`stopPolling`
are per-starter refcounted (last-starter-wins teardown, unpaired stop is
a no-op — the old blanket "idempotent" contract is gone), the loop polls
fast (60 s) only while a collector watches `pendingRequestCount`, idles
at 15 min otherwise, and skips polls offline. Related:
`getRequestCount()` stamps `pendingRequestCount` on one-shot calls so
Settings' badge refreshes via an `onSubscription` refetch instead of
holding the poll loop.

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
`transitionJob`, `discoverJob`, `userDataRefreshJob` — the user-data
deferral timer on its own job, so an echo arriving mid-fetch only re-arms
the delay instead of cancelling the in-flight fetch — each with its own
replacement/cancellation policy), the foreground/background-jittered cadence
loop, the discover TTL gate, the user-data-push
debounce/throttle/deferral chain (an echo landing inside the 60 s throttle
window is DEFERRED to expiry on a trailing-edge timer, not dropped, and the
`start()` flush of a pending change bypasses the throttle — the user is
back on the screen looking at it), and every offline-shaped field of
`HomeRefreshState` — the offline-mode mirror, the
online→offline content drop, and the user-initiated going-online handshake
(full-screen loader, playback-outbox drain through the injected
`awaitOutboxDrained` seam — a `suspend () -> Boolean` whose `false` says
the fetch raced a still-pending sync — 30 s-capped forced fetch, drain
re-await, and, if the drain completed under the loader, a second 30 s-capped
refetch; the `lastFetchRacedPendingSync` flag keeps the drain's late
completion echo out of the user-data throttle until a post-sync fetch runs
(the handshake's refetch or the user-data flush — a cancelled fetch leaves
it armed), and is reset by the identity transitions and the offline drop
that stop showing the raced sections (the handshake captures an identity
epoch before arming, so a transition landing mid-handshake cannot re-arm
the flag for the next identity); the timeout `finally`
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

**Discover-row roll registry.** `rollDiscoverRow`'s dice re-roll orders
against in-flight fetches through ONE registry, `rolledRowGenerations`
(`LinkedHashMap<String, RolledRowGeneration>` — rolled items + a monotonic
`rollGeneration` stamp; accessors `registerRolledRowGeneration` /
`applyRolledRowGenerations`): a roll landing mid-fetch re-seeds the network
cache that fetch already captured, so the fetch's single `sections` write
drains the registry and re-applies instead of transiently reverting the
on-screen roll. THE GENERATION INVARIANT (one KDoc owns the rationale): a
fetch re-applies every roll registered before the fetch's DRAIN POINT —
the last statement before the sections write, no suspension between drain
and write, so all three suspensions a fetch can park on (the main sections
await, the custom-Seerr splice await, the book-fraction decode) sit
strictly before it; a roll registered after applies itself (registration
happens-before its in-place patch; re-application idempotent; stamps
ordered, never compared). Vocabulary: "generation" in the feature layer —
`identityEpoch` owns "epoch" here, and the network/repo layers' store-local
epoch guards (`discoverRowEpoch` / `discoverRollEpoch`) compose with the
registry, are not replaced by it; identity transitions clear it wholesale.
Pinned by
`HomeRefresherTest.rollDiscoverRow_landingDuringBookFractionDecode_survivesTheFetchsSectionsWrite`.

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
re-encoded booleans) and the winning Continue-Reading fractions map (keyed
on the carried FEED — the offline gate's decodes for an offline feed, the
refresher's for an online one; `FallbackPending` renders the offline feed
and reads the offline map even while the mode mirror still says ONLINE —
the corner a former screen-side `renderingOffline` pick missed). The fold
relies on the equivalence
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
backs `LibraryApiClientImpl.getHomeSections` (fetching through
`HomeSectionsFetcher`, which supplies `HomeSectionsAssemblyInputs`). The
section-ordering policy (CW → Continue Reading → Next Up → per-folder Latest →
Recently-Added-insert-after-last-latest → Recommendations/suggestions →
pinned) is pinned by `HomeSectionsAssemblerTest`.

**`HomeSectionsFetcher`**
(`shared/core/network/src/commonMain/kotlin/.../library/HomeSectionsFetcher.kt`)
is the fetch half of the same split: ONE commonMain orchestrator owning the
sub-call schedule, the semaphore bounds (4 for the latest/pinned fan-outs, 3
for similar-items), the recommendations chain and the two
`NETWORK_SUBCALL_TTL_MS` TTL sub-caches — it decides what/when is fetched,
while the assembler decides what the fetched data becomes. Its
`HomeSectionSources` port (the eleven client sub-calls; parameter defaults
omitted because Kotlin forbids duplicate defaults across super-interfaces)
is satisfied by `LibraryApiClientImpl` for free via its
`LibraryApiClient` supertype. The fetcher's
suggestions pre-fetch condition (recommendations succeeded but empty) is the
SAME predicate the assembler's fallback branch renders on — the two are
pinned together by `HomeSectionsFetcherTest`. The client now memoises
under `CacheIdentity.UNKNOWN` pre-login, with the favorite-flag cache on the
shared commonMain `TtlCache` (access-order LRU eviction).

**`LibraryItemsQuerySpec`** (commonMain `library/`, 2026-09-10) is the
request-SHAPE half of the query convergence: the five non-trivial read
endpoints of the library client (`getMediaItems`, `getSearchHints`,
`getFavorites`, `getItemsByGenre`, `getItemsByStudio`) build ONE pure spec
(include/exclude kinds, filters, sort tokens + descending flag,
paging, fields — the path stays adapter-side: the client hits `/Items`
with its own per-client defaults) via `build*QuerySpec` beside the
assembler/fetcher — the
JVM client resolves spec → Jellyfin SDK typed args
(`LibraryItemsQueryResolvers`, jvmShared), so a filter decision (played-status,
resumable, sortOrder normalization, `libraryExcludeKinds` pruning,
empty-gating) is written once and pinned once by
`LibraryItemsQuerySpecTest` (commonTest). Trivial fixed-path endpoints
deliberately keep their per-client one-liners.

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
`resumeRowClick` is the one resume-row click routing every resume row funnels
through (the CW / Next Up wide rows call it directly; Continue Reading
reaches it through `posterRowClick`) — call sites differ only in the item
mapper, and the
ASK branch maps before the sink, so the Resume-vs-Details dialog wiring
cannot drift — and `posterRowClick` (beside it) is the matching selection
for the two poster rows: a resume-section poster row (`isResumeSection`,
the one CW/CR predicate both folds key on) rides `resumeRowClick`,
every other poster row opens through the caller's plain-click sink, so the
poster rows cannot drift on which section types honor the resume behavior
(`bookProgressFractionFor` in `BookProgressFractions.kt` is the same fold
for the rows' progress-bar lookup: decoded TOC fraction where the map knows
the item, percent fallback otherwise, remembered per fractions change —
pinned in `BookProgressFractionsTest`); `HomeRowChassisTest` pins the two
click folds. On the discover side, `DiscoverRowSlot` +
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
ratio). `OfflineMediaItem.isWatchedOffline` (`isPlayed ||
isFinishedOffline` — the derived-flip predicate, now greppable) and
`.hasPlaybackPosition` join the extensions. Declined as different
predicates, not drift: `DownloadInfoCard`'s `isPlayed || positionTicks > 0`
means "has any watch activity" (`hasWatchProgress` requires `!isPlayed`),
and `MediaDetailBody`'s position read is `target.startPositionTicks` (a
resume/chapter-start field, not the saved playback position).

Test surfaces (all kotlin.test on the module's `jvmTest`, ported with the
feature): `HomeRefresherTest` pins cadence, throttles, the offline
transitions, the going-online sequence and its timeout (slow-sync drain
races included: the under-the-loader refetch, and the late drain echo's
throttle bypass when the re-await gives up), the user-data deferral to
throttle expiry and the start-flush bypass, and `patchItems`;
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
store commands' read-modify-write + normalization — and, since the
Continue-Reading batch, the enabled-set VERSION-UNION read policy:
`HOME_ENABLED_SECTION_TYPES_VERSION` stamps every write of the persisted
enabled-section set, and a set read at an older stamp is unioned with the
sections shipped in each intervening version (v1 unions in
`CONTINUE_READING`), so a newly shipped section defaults to VISIBLE for
users whose persisted set predates it — one-shot only, because every write
stamps the current version, keeping a later disable the user's own choice
(the per-user parse cache keys on stamp+raw for the same reason: the CR
disable rewrites the SAME encoded set under the new stamp, so a cache keyed
on raw alone would serve the stale union until restart). The ORDER twin
ships with it: a persisted order missing a configurable section re-inserts
it at its DEFAULT-ORDER position (after the last present section whose
default index precedes it — CR lands between CW and Next Up, not at the
tail where the next `moveSection` read-modify-write would bake it), no
stamp needed because absence itself is the signal;
`HomeQuickActionsTest`
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
the tap/drag jump-target letter fold, rail-end clamps — extracted from
private functions inside the ~1900-line screen file; the composable keeps
dp/px conversion, drawing and pointer wiring. The fisheye lens itself
(`fisheyeScaleAt`'s peak/sigma constants and the gaussian falloff) moved
one level down into `FisheyeRailMath` (`shared/core/ui/components`, the
`BackExitConfirmation` precedent) when the reader's `TocRailGeometry`
turned out to hand-copy the same math: both rails adapt it — the library
clamps to its rail ends, the reader keeps a beyond-the-ends extrapolation
fold for drag-scrubbing — with the lens constants in ONE place.
`groupByLabel` stays in the screen deliberately (it resolves
`stringResource` display labels). Pinned by `AlphabetRailGeometryTest`
and `FisheyeRailMathTest` (fisheye edges, `#`-bucket targets, clamps,
degenerate rail height).

`ShortcutListPolicy` (beside `ShortcutsScreen`): the screen's query filter
(with the unresolved-labels false-"No results" guard), category fold, and
empty-state decision are Compose-free and pinned (12 tests); the screen
keeps chrome + back-handler wiring.

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

**`LiveTvLoad`** (livetv commonMain — the load-ladder fold's first module
slice, since folded into core:ui's `loadInto`; the object itself is gone)
owns the load ladder (start → fetch → dispatch to
exactly one arm; the returned `Result` is the continuation gate) folded
across Channels/Series/Recordings/ChannelDetail/Programs ViewModels —
the load-ladder fold's first module slice. Site-specific drift stays at
the call sites as declared arms: Recordings' legacy unconditional
`getOrDefault(emptyList())` settle is preserved verbatim (failure clears
the list — commented), Programs' `fullRender` variant rides the `start`
closure, ChannelDetail leg-gates on the returned Result, and
ScheduleViewModel is deliberately NOT folded (two independent fetches —
a single-Result dispatch would lose the surviving half on partial
failure). Pinned by `LiveTvLoadTest` (retargeted to `loadInto`,
assertions unchanged) with the six per-VM suites unmodified.

The music collections ride the **collection chassis**: `MusicCollectionKind`
is the pure decision table (sort admission, media-type binding, layout,
empty/error presentation) for the five collections; `PagedGrid` grew into
the ONE ladder (refresh-phase folds, append footer, pull-to-refresh, a
`PagedList` variant for tracks, `SimpleCollectionGrid` for the
list-sourced genres/playlists — its rung decision is the pure
`simpleCollectionRung`, pinned beside `pagedCollectionRung` in
`PagedCollectionLadderTest`); `MusicSortMenuButton` + `musicArtUrl` kill
the 4-copy dropdown and image-url copies; `SortedPagedCollection` takes
the kind. Error presentation rides the table for both families:
`SimpleListCollection`'s error is the failure `Throwable` itself, the
ladder renders `error.message` with the kind's `errorFallbackRes` as the
null-message fallback (no baked English literals), and its refresh is
supersession-guarded — only the newest refresh may write state back, so
a stale in-flight load can neither clear the loading flag early nor
overwrite a newer refresh (`SimpleListCollectionTest` pins both
directions). The standalone playlists screen keeps its own scaffolding
(create/edit/delete dialog host, per-row command menu, FAB, custom rows)
but its load ladder rides `SimpleListCollection`; its command/mutation
errors are the resource-carrying `PlaylistCommandError` (`Reported`
carries server text verbatim, `Declared` resolves at render) over the
`music_playlist_*` set, guarded by `runPlaylistMutation` +
`PlaylistMutationFailurePolicy` (KeepDialogOpen / KeepOptimistic /
Reload(rollback)) — the pinned per-site failure behaviors survive as
declared variants. Route signatures unchanged. Declared deltas: browse
pages gained pull-to-refresh/status/footer, browse artists gained
`DATE_PLAYED`, sort admission is data (menus offer exactly
`kind.sortOptions`; `MusicCollectionSortTableTest`).

`LiveTvPlayerViewModel`'s four hand-copied record blocks are the SIXTH
`RecordActions` adapter (the first feature→feature edge
`:shared:feature:player-live` → `:shared:feature:livetv`, the
shell→livetv precedent); the VM keeps one-line funnels, the screen API is
unchanged, and the adapter's message/refresh routing is pinned in
`LiveTvPlayerViewModelGapsTest`.

Perf sweep: `EpgScreen`'s 30 s now-tick is hoisted to a leaf
`NowIndicatorLine` so the grid doesn't recompose; `ChannelsViewModel`
partitions favorites (stable, not an O(n log n) sort);
`ArrQueueViewModel`'s search fan-out is bounded.

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
dp/px + Canvas drawing, and cell lookup + grid membership are model-owned
via `dayAt`/`isInsideGrid`. Pinned by `HeatmapGridModelTest` (leap-year
coverage — a 2024 grid is 52 columns and Dec 30–31 fall beyond it —
month-boundary labels, quartile edges, focus clamp).

`DetailPlayPolicies` (details, beside `DetailContentState`) holds the two
pure folds the media-detail callback adapter used to inline six times:
`resolvePlayStreamSelection` (the local-origin subtitle-index policy,
duplicated in onPlayClick/onPlayChapter) and
`requiresMarkPlayedConfirmation` (the series gate, open-coded 4×; season
branches confirm unconditionally via `isSeasonAction`). The adapter
lambdas keep only dispatch. Pinned by `DetailPlayPoliciesTest`. Two more
folds live there: `resolveDetailPlayDispatch` (table: SERIES → confirm;
MOVIE/EPISODE/SEASON/null → direct; any season action → confirm) and
`dispatchMarkPlayedAction`. `MediaItem.progressFraction(positionTicks)`
(core/model `MediaItemProgress.kt`) is the single resume-fraction home —
the core:ui twin extension is deleted, `rememberProgressFraction`
delegates.

**Download/offline cores (core/data):**

- **`OfflineDeletionCore`** (internal collaborator) is the ONE deletion
  choreography behind `deleteOfflineItem/Series/Season` and
  `DownloadRepositoryImpl.cleanupDownloadFiles`: artifacts-before-DB
  delete → capture-before-transaction → 4-table cascade → memo evict →
  cast prune → orphan prune; series-artwork cleanup is a
  series-scope-only hook. The cast prune is a per-candidate EXISTS scan
  over `peopleJson` (`OfflineMediaDao.isPersonReferenced`) — a person's
  image survives iff at least one surviving row references them.
  `OfflineRepositoryImpl` injects `TimeSource` for the `lastPlayedDate`
  stamps. Pinned by `OfflineRepositoryDeletionTest` (three scopes, shared
  cast image retention, zero orphans) + fake-clock `applyPlayedState`
  tests.
- **`PlaybackOutboxDrainer`** (jvmShared `worker/`) is the ONE drain
  choreography behind `suspend drainOnce(attempt): DrainResult`: offline
  gate, staged-intent collection, derived-watched flips, the retry-budget
  ladder (`MAX_RETRIES = 3` / `MAX_INTENT_RETRIES = 10`), dead-letter
  policy, superseded-telemetry skip, bounded reconcile batch (cap 50, via
  `Semaphore.mapConcurrent`), and the cache-invalidate +
  `notifyUserDataChanged` + `enqueueNow` tail. `PlaybackSyncWorker`
  shrinks to a thin adapter implementing the drainer's `Notifier`
  (foreground promotion / mid-drain update / dismissal) and mapping
  `DrainResult.retriesPending` → `Result.retry()`; `attempt` is the only
  seam the old `runAttemptCount` coupling needed. The 33-test worker
  suite moved from the CI-dark legacy lane into
  `:shared:core:data:jvmTest`, plus drainer/Notifier-protocol tests.
  Pinned by `PlaybackOutboxDrainerTest` +
  `PlaybackOutboxDrainerResilienceTest`.
- **`AutoDownloadCheck`** (jvmShared `worker/`): the verbatim
  Android↔desktop auto-download twin is ONE `checkOnce(attempt)` over
  injected seams (prefs gate, series index, per-season `startSeries`, the
  `isStopped` lambda) returning a 3-arm `Outcome`
  (`Complete`/`RetriesPending`/`Exhausted`) the adapters map to
  `Result.retry()` / in-process passes. `MAX_RETRIES` is declared once;
  `RETRY_DELAY_MS` references
  `DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS` for real.
  `AutoDownloadWorker` 99→55 lines; the desktop scheduler gains its
  first behavioural suite. One micro-drift folded to the Android-tested
  shape (null-tolerant prefs read).
- **`DownloadTransferGate`** (jvmShared `worker/`, beside
  `DownloadTransferRunner`) owns the transfer preamble both orchestrators
  hand-copied (activeUserId → token decrypt →
  `downloadConnections.coerceIn(1, 8)` → permit → post-QUEUED re-check
  (`onInactive` early-out) → DOWNLOADING write → runner construction over
  platform-supplied lambdas) AND the post-`Prepare` `execute()` body
  (resume-vs-fresh probe dispatch, the multi-vs-single threshold branch,
  CE rethrow, and the outer failure-classification ladder over
  `DownloadFailurePolicy.decide` + `applyTo`). The caller's transfer body
  runs INSIDE the same permit, so `maxConcurrentDownloads` still caps
  actual transfers (a first cut released the permit when `prepare`
  returned, un-capping them — caught in review, pinned). DECLARED FIX: a
  start-of-transfer `refreshSummary` fires through the notifications port
  on BOTH paths. Android foreground plumbing and desktop's supervisor
  orchestration stay per platform as declared divergences; the
  `notificationId` parameter stays platform-supplied. Orchestrators keep
  outcome mapping (WorkManager result vs desktop retry re-kick) plus ONE
  notification adapter each.
- **Track/quick download actions** (commonMain `download/`). The IDIOM
  RULE: a download read a feature needs is declared in core:data
  commonMain and implemented AND bound by core:data on both platforms —
  features never grow their own wall-crossing template. Since the
  promoted-interface pass (the `DownloadIntake` precedent: a surface that
  is core:model-only crosses commonMain verbatim) there are NO per-read
  adapters: the jvmShared engine implements the interfaces directly and
  dataJvmModule binds them over the engine singles. The seams:
  `QuickDownloadActions` (implemented by `MediaDownloadActions` itself;
  replaced the byte-identical library/search twins, `HomeDownloadActions`
  folded), `TrackDownloadStatusWindow` /
  `ActiveDownloadCount` (the former process-scoped
  `MusicTrackDownloads.activeDownloadCount()`) / `SeriesEpisodeDownloads`
  (all three implemented by `DownloadRepositoryImpl` — the window's
  `downloadsFor` IS the single `getDownloadsByMediaItemIdsFlow` IN-query,
  the deleted adapter's N-per-id-flow divergence reverted), and the
  downloads screen's `DownloadQueue` + `OfflineResync` (moved from
  feature:downloads — `DownloadRepositoryImpl` / `OfflineSyncManager`
  implement them, the field-identical `DownloadRowProgress` mirror died
  with `DownloadProgress` promoted to commonMain).
  **`TrackDownloadActions`** is the music/track download flip's START
  half — `flip(itemId)` (detail fetch → `intake.start`, failures
  swallowed via `runCatchingRethrowingCancellation`: cancellation now
  propagates where the pasted `catch (_: Exception)` masked it) and
  `bulk(items, concurrency = 3)` (the album admission table
  null‖FAILED‖CANCELLED + per-call Semaphore, absorbed verbatim);
  `AudioPlayerViewModel`/`AlbumDetailViewModel` inject the window
  directly. The COMPLETED→remove half STAYS at the call sites: the player
  confirms before removing, the album row removes directly — distinct
  policies the module must not swallow. Pinned by `TrackDownloadActionsTest`
  (8) + `DownloadRepositoryStatusWindowTest` (the window's id-honesty, at
  repository level).
- **`ReconnectTrigger`** (jvmShared; the `onReady` guard rides
  `runCatchingRethrowingCancellation`) is the shared reconnect
  vocabulary; the desktop downloads watcher and its character-identical
  `isReady` twin delete. `DesktopPlaybackSyncScheduler` collapses onto it
  with an `offlineModeManager` ctor dep and the DECLARED BEHAVIOR
  ALIGNMENT: the desktop playback drain fires on offline-mode→online
  transitions like Android's (the former network-only watch was an
  accident of the hand-copy), is Mutex-serialized with a fresh budget
  per pass (`attempt = 0`), and keeps the recorded
  no-periodic-backstop delta (`enqueuePeriodic` no-op, pinned).
- **`DownloadStartRequest`** value object replaces the
  15-positional-parameter wall (interface + override + internal twin +
  the delegate's unpack block with six episode-guards → ONE guard beside
  the entity build). Series-artwork seeding is ONE private helper
  through `DownloadArtifacts` (the raw `"${seriesId}_poster.jpg"`
  literals are dead); `downloadSeries`' fan-out rides
  `Semaphore.mapConcurrentCatching` (per-episode Log.w preserved).
- **downloads uiState split**: the 2 s transfer tick no longer re-emits
  the downloads list; moving bytes/speed ride
  `DownloadRepository.getActiveDownloadProgress()` (narrow 3-column
  projection, no `status` — structural status stays on
  `getAllDownloads`) into `DownloadsViewModel.progressById` /
  `totalStorageBytes`, with exit-transition retention for rows the
  structural list still shows DOWNLOADING; `DownloadProgress.status`
  deleted (speculative — no caller).
- Insight-side perf folds: the heatmap day-sheet aggregation is one
  `rememberedDayItemAggregates` (touch + TV variants) and its
  day-detail resolution fans out at `Semaphore(4)` parallelism.

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

The engine also grew the side-search kernel behind the search screen's
Seerr/offline rows: `MediaSearchEngine.sideSearch()` (+ the pure
`MediaSideSearchState`) gates the Seerr half behind the same gate the home
preview uses, caps it at `seerrLimit` (the offline half stays independently
capped), surfaces a failed gated round as `seerrError = true` where the
preview swallows silently — the screen renders its retry row — and never
throws.

**`PlaybackIdentity`** (`shared/core/data/src/commonMain/kotlin/.../playback/PlaybackIdentity.kt`)
is the playback-facing session-identity read: the token + base-URL pair a
playback consumer needs to address the active server (`serverUrl()` /
`accessToken()`, `null` = no active session). It was retired OFF the
`PlaybackRepository` surface, which used to smuggle the two session
credentials through as interface members (`getServerUrl`/`getAccessToken`) —
coupling every one of that interface's consumers to the identity vocabulary
the four actual readers (BookSessionLoader, LiveTvPlayerViewModel,
PlayerSessionManager, DownloadSidecarCore) now get by injecting this narrow
module instead. The production impl, `DefaultPlaybackIdentity`, is backed by
`AuthApiClient` — the same source the retired pass-through members read (the
engine's atomic `activeServerAddress`/`currentUser` session) — so semantics
are byte-identical; `DataKoinModule` binds the `PlaybackIdentity` single to
it, NOT to `PlaybackRepositoryImpl` (whose internal URL builders keep reading
the client directly). The `PlaybackRepositorySurfaceTest` ratchet dropped to
25 and pins the two members' absence.

## Core data repositories

**`LyricsRepositoryImpl`** (`shared/core/data/src/jvmShared/kotlin/.../repository/LyricsRepositoryImpl.kt`)
owns the whole LRC/LRCLIB fetch-parse-cache chain (cache read → Jellyfin
endpoint → LRCLIB best-match, skipped on Local networks → negative-result
caching, plus the hour-throttled eviction) with its own private deps
(`LrcLibApi`, `LyricsCacheDao`, `NetworkMonitor`) and an injected
`TimeSource` for its clock reads — throttle, cleanup cutoff, `fetchedAt`
stamps (same seam as `MediaRepositoryImpl`). `MediaRepository` does
NOT extend `LyricsRepository`: `AudioLyricsManager` and
`VideoPlayerViewModel` inject the narrow type directly, and the app's
`CacheMaintenanceInitializer` injects it instead of the union.
`DataKoinModule` binds `LyricsRepositoryImpl` as its own single;
`DataKoinModulesTest` pins resolution.

The DI monolith is an aggregate now: `dataJvmModule` (formerly ONE
914-line/149-single module) is `module { includes(...) }` over ten jvmShared
family modules in the same `di/` package (DataCoreLeaf / DataRepositories /
DataSessionPlayback / DataMediaRepository / DataDownloadsConveyor /
DataDownloadActions / DataPlaybackFamily / DataSubtitleProvider /
DataSeerrArr / DataAdmin Koin modules — the old file's comment-section
boundaries), and `androidCoreDataModule` likewise aggregates five androidMain
siblings (PlaybackFocus / PlaybackStack / RemoteCast / IntakeStorage /
WorkSchedulers). The aggregate names are unchanged — consumers and
`DataKoinModulesTest` untouched.

**`MediaRepository` union shrink (landed)**: `MediaRepository` no longer
extends `LiveTvRepository` / `SyncPlayRepository` / `NewsletterRepository` /
`PlaylistRepository` — its interface is its own 42 members (the former
86-member union forced every media consumer to learn four unrelated
families). `MediaRepositoryImpl` implements `MediaRepository` +
`SyncPlayRepository` plus the two cache-invalidation seams
(`MediaRepositoryCacheInvalidation`, `MediaCacheInvalidator`) and is bound
as the `MediaRepository` single; the three family surfaces have their own
jvmShared impls and singles (`LiveTvRepositoryImpl` / `NewsletterRepositoryImpl` /
`PlaylistRepositoryImpl` over the shared `MediaRepositoryInternals` — see
"MediaRepository facade split" below), so each family seam has its own
production single and test doubles. Single-family consumers inject the
narrow type (the livetv VMs, `LiveTvPlayerViewModel`, `NewsletterViewModel`,
`SyncPlayViewModel`, `WatchPartyActions`, `PlaylistTargets`); mixed
consumers inject BOTH `MediaRepository` and `PlaylistRepository` (music
browse/playlist VMs, `AudioPlayerViewModel`, `LibraryLayoutViewModel`,
`AudioLibraryBrowser`) — different singles since the facade split, with
cross-surface cache invalidation carried by the shared internals single.

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
No legacy unit-test file runs lane-less anymore: since the cutover
(2026-09-12) every former legacy-core Robolectric suite executes in its
shared module's androidHostTest lane — kmp-build.yml's android-app job runs
:shared:core:data:testAndroidHostTest (the 69 core:data + 8 notification
files: cast, worker and playback platform code included) and
:shared:core:ui:testAndroidHostTest (16, incl. RoutePredicatesTest and
TvDrawerFocusWiringTest, plus the former instrumented-only sheet/scrim
pairs, which now execute under Robolectric). Keep the Phase-X rule itself:
treat a legacy-only assertion as dead when its class moves to `androidMain`.
Still dark is execution, not compilation: the instrumented androidTest
sources are compile-gated only — :app
via :app:assemblePhoneDebugAndroidTest, no emulator lane. The stranded-:app
androidTest rescue rehomed 15 instrumented files into their
owning modules' androidHostTest Robolectric lanes: the 11 player-video
component tests plus ErrorScreenTest / NavigationRouteTest /
PinLockScreenTest → core:ui, SyncPlayScreenTest → feature:syncplay — the
player-video and syncplay lanes are newly wired (`withHostTest` +
`robolectric.properties` sdk=35). 28 stale assertions were repaired to the
current component contracts, zero deleted; NavigationRouteTest gained the
four missing routes (HomeSettings, DiscoverRows, DiscoverRowEditor,
ImportPreview).

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

Test doubles for that clock seam are shared from
**:shared:core:test-fixtures`** (new module, jvmShared-only — `TimeSource`
lives in core:data jvmShared, so commonMain is impossible): the canonical
`FakeTimeSource` (richest shape) + the public `FakeUserDataMutator`,
consumed test-scoped by livetv/details/home; core:data keeps its local
`FakeTimeSource` copy behind a sync-pointer KDoc.
`TestFixturesScopeGuardTest` (the module's jvmTest) is the tripwire — it
fails if any build script references the module outside test-scoped
blocks, wired into task inputs so it cannot go UP-TO-DATE stale.

Four more repository internals were deepened (public
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
for the six plugin-gated list fetches (caller-captured read of the shared
plugin-status flow — a page's gates see one status even if an admin refresh
lands mid-load; the enhanced batch's group gate stays open-coded because its
non-null deferred bundle IS the downstream gate in
`buildEnhancedStatistics`). **`PlaybackReportingStatusStore`**
(`shared/core/data` commonMain `session/`, a `DataKoinModule` single) is the
ONE owner of "is the Jellyfin Playback Reporting plugin installed on the
active server?": one `StateFlow<PlaybackReportingStatus>` (initial UNKNOWN)
plus one `refresh()` (`checkPlaybackReportingPlugin()`, failure fallback
UNAVAILABLE), cited by BOTH former owners — this repository's admin pages
AND `WatchHistoryRepositoryImpl`'s insights heatmap, which each held their
own `MutableStateFlow(UNKNOWN)` and their own same-named refresh of the same
API call (two independently stale answers to one question; an admin refresh
never updated the heatmap's copy and vice versa). Both repositories now
inject the single; each `refreshPlaybackReportingStatus()` keeps only its
own side effect around the store's refresh, order preserved (admin: refresh
then the 90-day audit prune; watch-history: played-items memo drop then
refresh). The store registers ONE `SessionCacheRegistry` action (owner
`playback-reporting-status`) resetting the flow to UNKNOWN on every
non-`SignedIn` transition — the one deliberate addition over the former
owners, which never cleared on identity change and let a previous identity's
verdict gate the next one's reads until some refresh happened to run.
`WatchHistoryRepositoryImpl`'s played-items memo stays a deliberately
TTL-less `SingleFlight` (entries live until the next plugin-status refresh;
no identity key, no registry registration) — a declared divergence from the
identity-keyed `TtlCache` idiom, documented on the memo's KDoc and in
`FreshnessCeilings` (its "how stale?" answer is "no TTL"). **`PlaybackRepositoryImpl.getMediaSegments`** rides
`SingleFlightFetcher(segmentsCache, segmentsEpoch)` like the detail cache
(the intro/credit fallback batch is the fetch lambda; a failed API fetch
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

**`UserErrorMessages`** (commonMain `core/data/error/`) is THE
error-message fold — the one resolver that turns a failed repository
answer (throwable or `Result`) plus a caller fallback into the
user-facing message string, replacing the ~50 per-feature hand copies of
`e.message ?: "<fallback>"` / `result.exceptionOrNull()?.message ?: "…"`
(admin dashboard + users, auth login + quick-connect, details
(MediaInfo/CastAndCrew/Person/Collection/ManageSeries ×10/downloads
lifecycle/Resync), home, library (folders + photo viewer), livetv channel
detail, music (mood/smart generate, paged grid, genre detail, playlists
detail + screen), newsletter, player-video subtitles + screenshot save,
desktop capture seam, and the shell's update coordinator). The
behavior-preserving contract: an `ApiException` ALWAYS wins with its own
message (the network layer classifies BEFORE the friendly text is
attached — `JellyfinErrorMapper` / the HTTP-status factories — so the
retryable/access-denied/http distinction already drove the wording at
throw time; the fold never demotes a classified message to the caller
fallback), a non-`ApiException` with a message keeps it verbatim, and
only a null message falls to the caller's literal — every swept site
passes its pre-existing fallback unchanged, so no UX string moved.
`rawOrNull` serves the message-presence branchers (auth's
Raw-vs-`AuthMessage.Resource` seal — `AuthMessage`'s shape untouched).
Sites whose behavior DISTINGUISHES the classification keep their own
ladders: DetailViewModel's access-denied/unavailable-offline UI-state
buckets, ArrRepositoryImpl's 401/403 → `ArrDiscoveryError.NoAdminPermission`,
and the LiveTv record `RecordOutcome.Error(message)` two-stage (raw
message resolved at the `RecordActions` source, tab-local fallback
literal applied at the tab) — when a future site needs a
retryable/access-denied message ladder, grow it in the fold first.
Deliberate skips (idiom look-alikes, not API-message forwarding):
`ResyncActions`' step-result message, the heatmap's
`localizedMessage ?: message ?: ""` platform chain, core:ui's
`BiometricAuthHelper` (core:ui cannot see core:data; local crypto
errors), the player engines' native `ExoErrorTaxonomy`/`VlcErrorMapper`
tables, and `WidgetHttpErrors`' null-guard. Pinned by
`UserErrorMessagesTest` (the classification table: retryable, 429,
401/403 access-denied, 404/plain-http, network-mapped, non-ApiException
verbatim + fallback, `Result` arms, rawOrNull).

**`ArrRepositoryImpl.withResolvedSonarrSeries(tvdbId)`** is the Sonarr
members' guard seam (the `withSeerrSession` precedent): resolves the
owning server + internal series id once inside the cache scope and hands
it to the action, failing with the shared no-server 404 when unresolved —
the nine hand-copied `resolveSonarrSeriesForSeries(tvdbId) ?:
return@withContext …` ladders (resolve/getEpisodes/monitor/deleteFile/
searchEpisodes/searchSeason/refresh/rescan/searchSeries) are one-line
command declarations over it, byte-identical in outcome.

**`AuthRepositoryImpl.decodeEnabledFolderIds`** is the
`enabledFolderIds` JSON column's ONE decode home: the `toUserInfo`
mapper and `restoreSession`'s token-gated hand-`setUser` path both route
through it (the restore path's bare non-memoising try/catch copy is
gone; same output, now memoised through the same `folderIdsCache` LRU).

The auth establishment path and the
server-address vocabulary were deepened. **`AuthRepositoryImpl`** folds the
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
copies across core:data / core:network (jvm failover probing)
/ auth's TLS-trust prompt / settings' trust toggle now call it, and the two
private twins are gone. Trim-only sites (`switchServerAddress`,
`NetworkOfflineStore`, `ServerAddressRouter`, `SocketUrl`) are a different
policy and stay local. The Jellyfin-12 auth batch adds the legacy-route
policy beside it: **`stripLegacyRoutePrefix`** (same file, pinned in
`ServerAddressTest`) recognizes a trailing `/emby` / `/mediabrowser` path —
the route aliases Jellyfin 12 removed. `ServerAddressRouter.probe` retries
the stripped bare address when the original answers without a server
identity (a 404 still counts "reachable", hence the identity check) or is
outright unreachable, and
reports it as `AddressProbeResult.resolvedAddress`; the auth client
persists the resolved form (`AuthApiClientImpl`, pinned by
`ServerAddressRouterTest` / `AuthApiClientTest`), so the next connect probes
the working address directly.

**`SelfSignedTrustRepository`** (core/data commonMain; the
`SelfSignedTrustRepositoryImpl` in jvmShared beside the
`ServerDiscoveryRepository` pattern, bound in `DataKoinModule`, pinned by
`SelfSignedTrustRepositoryImplTest`) is the narrow feature-visible
collaborator for the self-signed-trust DECISION:
`isSelfSignedTrustGranted(grants, address)` +
`selfSignedTrustGrantsCovering(grants, addresses)` — the revoke answer
returns the GRANTS to drop, never the addresses — delegating to
core:network's `SelfSignedTrustMatcher`, so what a feature answers can
never drift from what a TLS handshake honors. Grant WRITES stay on
`NetworkOfflineStore`; the granted set is passed BY THE CALLER (a
read-only seam). `ServerManagementViewModel` reaches the toggle/revoke
sweep/orphan-prune answers through it and no longer imports a
core:network type — the last star-topology core:network breach is
closed.

**`OfflineMediaMappers.kt`** (jvmShared, beside the two repositories) is
the single internal home for the `OfflineMediaEntity` / `DownloadEntity`
⟷ domain mappers `DownloadRepositoryImpl` and `OfflineRepositoryImpl`
used to carry as duplicated private members; the table's column
contract is KDoc'd ONCE — series/season subtitle clearing (write-side
normalization, read back verbatim), CSV `genres`/`studios` (empty list
persists as `""`, never null), the JSON blob columns written
null-when-empty with `peopleJson` persisting ACTORS ONLY, and the
`MediaType.UNKNOWN` / `DownloadStatus.FAILED` unparseable-value
fallbacks. Write-side normalization and read-side restoration are
deliberate mirror images, not duplication to flatten. Pinned by
`OfflineMediaMappersTest`.

The paged reads and the telemetry
capture side were deepened. **`JellyfinPagingSource`** (`shared/core/data` commonMain
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

**MediaRepository facade split**: `MediaRepositoryImpl` (1,071 lines)
implements Media + SyncPlay + cache-invalidation only (SyncPlay stays BY
DECISION — its 4 members interleave with the user-data channel).
`NewsletterRepositoryImpl` is a 3-forward adapter over
`MediaInfoApiClient`; `LiveTvRepositoryImpl` (15 forwards over
`LiveTvApiClient` + `MediaInfoApiClient`; `deleteRecording` →
`deleteItem` declared — only MediaInfo declares it) and
`PlaylistRepositoryImpl` (2 reads + 6 self-invalidating edits over
`LibraryApiClient` + the internals) are separate impls.
`MediaRepositoryInternals` is the deliberately MINIMAL internal holder —
it owns exactly the shared `detailCaches` group (the only shared state an
extracted surface touches); DAO/episodeCatalogue/realtime/played-state
stay on the Media impl. Ctor narrowing rode the split (family clients,
not the `JellyfinApiClient` union; behavior-identical because the union
is pure delegation over the same family singles). Declared divergences:
both impl ctors went `internal` (a public ctor cannot expose the
internal holder; grep-verified no external namer); the entity "leak" in
`PlaylistRepositories.kt` is a declared non-change (mappers already
private, every public signature domain-typed). The three playlist
invalidation tests now pin CROSS-surface invalidation (cache via the
media impl, drop via the playlist impl). Surface ratchets: LiveTv 15 /
Playlist 8 / Newsletter 3, lower-never-raise.

`PlaybackRepositoryImpl`'s pure decisions are lifted:
**`PlaybackMethodSelection`** (the live/direct-play/direct-stream/
transcode ladder — `selectPlaybackMethod` → play method + URL source) and
**`LegacySegmentFallback`** (the legacy intro/credit fallback synthesis —
`legacy-intro-`/`legacy-outro-` ids from the timestamps' own itemId,
strict `>` gating); the repository keeps choreography, and its 954-line
facade test passing UNMODIFIED is the behavior-identical pin, with
direct branch pins added for the ladder corners. The `PlaybackRepository`
interface trimmed 29→27 (`getIntroTimestamps`/`getCreditTimestamps` had
zero external callers — the impl's segments fallback calls
`playbackApiClient` directly); the impl constructor takes the four
family interfaces it actually uses instead of the `JellyfinApiClient`
ten-family union. The wholesale family retirement into feature modules
was REJECTED (the feature-layer core:network embargo in the
player-video/player-live/details/insights build files is load-bearing;
the repository members ARE the feature-visible narrow seams). Ratchet:
`PlaybackRepositorySurfaceTest`, baseline 27 — lower when the surface
shrinks, never raise. Deliberately NOT swept in that pass: the other 16
`JellyfinApiClient` constructor injectors (per-touch only,
opportunistically). The next per-touch batch closed the SyncPlay family
(Koin definitions change parameter types only): `SyncPlayManager`'s ctor
takes `syncPlayApiClient` + `authApiClient` (the postCapabilities
pre-join call), `SyncPlayController` narrows to `SyncPlayApiClient`,
`TimeSyncManager` to `PlaybackApiClient` (`getServerTime` is its only
call), and `EpisodeCatalogueImpl` to `LibraryApiClient`.

Migration chain hygiene: the chain is split into era files beside the
slim `Migrations.kt` registry — `Migrations1To23` / `Migration24To25` /
`Migrations25To45` / `Migrations46To56`, with `allMigrations` keeping the
strictly-ascending order (the source stays a reliable map of the upgrade
path and `MigrationTest` asserts contiguity).
`collectRowsThenUpdate` (private, Migrations.kt) is the one backfill
chassis for 24→25 and 53→54 — the mid-scan rule ("the UPDATE writes the
very column the cursor scans;
SQLite may revisit or skip rows") lives in ONE KDoc; 24→25's semantics
preserved exactly (`encrypt(null)` passthrough is the idempotency
mechanism). Fixture layer: schemas 1..12 are proven NEVER tracked
(schema export and 13.json landed together — recorded in the test,
nothing invented); the v24 tests replay `execSchema(db, 24)`, v50/52/53
tests adopt the room3 `MigrationTestHelper` (validate-after-migrate),
`migrateAllFromV1`'s duplicate inline servers CREATE folds into
`createServersTable`, and every remaining hand fixture names its anchor
versions in KDoc. DB schema 52→53 gained the covering index
`offline_media(mediaType, seriesId, seasonNumber, episodeNumber)` for
`getDownloadedEpisodes`'s WHERE + ORDER BY (SQLite was sorting up to
2000 joined rows per re-emission). `BookTocCacheDaoTest` gives the last
DAO-less DAO its real-SQLite lane. MigrationTest 25/25. CI gained
baseline-profile generation and startup-benchmark lanes.

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

The DURATION half of "how stale?" — the cache TTL ceilings themselves —
lives in **`FreshnessCeilings`** (`shared/core/model` commonMain, beside
`HomeFreshness` and `TtlCache`): one named constant per NON-home cache
policy, cited by each owning site instead of a private literal, so "what is
stale where" has one readable answer. `core:model` is the placement (the
common ancestor `core:data` and `core:network` both depend on) because the
sites span both layers. The named policies: `FOLDERS_TTL_MS` (10 min;
library folders + genres/studios), `LATEST_MEDIA_TTL_MS` (2 min),
`DETAIL_TTL_MS` (2 min; the detail cluster, the collection-items page cache,
AND `EpisodeCatalogueImpl`'s series snapshots — the catalogue's old
hand-synced "matches DETAIL_CACHE_TTL_MS" comment is now the shared
constant), `PHOTO_URLS_TTL_MS` (5 min), `SEGMENTS_TTL_MS` (5 min),
`SEERR_TTL_MS` (60 s), `ADMIN_SYSTEM_INFO_TTL_MS`/`ADMIN_ITEM_COUNTS_TTL_MS`
(2 min each), `MEDIA_INFO_SERVER_NAME_TTL_MS` (30 min), `FAVORITE_FLAGS_TTL_MS`
(15 min; `FavoriteFlagCache` keeps its file-private alias for the library
clients). Home's freshness family stays in `HomeFreshness` (its
network-subcall/repo-memory/Room-SWR TTLs plus refresh cadences) — the two
objects are sibling policy homes that cross-reference, not one merged table;
`ArrRepository.SERVER_CACHE_TTL_MS` (60 s) stays an interface constant (an
Arr-surface public API). Declared exception to the identity-keyed house
idiom: `AdminApiClientImpl`'s dashboard caches and
`MediaInfoApiClientImpl`'s server-name cache keep their bare server-scoped
`getOrPut(KEY)` keys — these are process-lifetime singles over the shared
`JellyfinApiEngine` and `core:network` has no identity source to key with,
so a server switch within the TTL window can serve the previous server's
system-info/counts/name; documented on both clients and left as-is (keying
them identity-aware would be a behavior change, not a naming fold).
`WatchHistoryRepositoryImpl`'s played-items memo is the one cache with NO
duration (TTL-less `SingleFlight`, invalidated by plugin-status refresh) —
declared in `FreshnessCeilings`' KDoc so the readable answer covers it too.
All values are byte-identical to the private literals they replaced.

## Concurrency (`shared/core/concurrency`)

**`:shared:core:concurrency`** (commonMain, zero-dependency leaf below
core:network) is the repo's one cancellation-safety seam.
**`runCatchingRethrowingCancellation`** is THE sanctioned wrapper for any
best-effort `runCatching` around suspend calls: stdlib `runCatching` captures
`CancellationException` and masks structured cancellation — the recurring
bug class (masked worker retries, half-applied offline flips, orphaned
observers) that pre-2026-09-07 commits kept re-fixing one file per commit.
The wrapper is born commonMain; `JellyfinApiEngine`'s `apiResult` is the
helper plus its own typed-exception mapping. Non-suspend bodies (JSON/enum parses
in mappers) keep stdlib `runCatching`. **`BareRunCatchingRatchetTest`**
(module `jvmTest`) is the source ratchet: bare `runCatching` inside
`suspend fun` bodies never increases — the guard is repo-complete, and
its guarded-root set is DISCOVERED, not
hand-listed (since 2026-09-09): the test parses every `include(...)` in
`settings.gradle.kts` and walks `shared/` + `apps/` for
`build.gradle.kts` dirs (pruning build output), unioning both — a new
module is guarded the moment it exists, and a canary assertion checks
discovery ⊇ the retired 40-path hand list (the single widening found,
`baselineprofile`, contained no `runCatching`; baseline unchanged).
Two known deliberate baseline entries are named in the
test's KDoc (HomeDiscoveryStore's best-effort migration swallow,
PluginConfigViewModel's asset read); `AddToTargetActions
.resolveTargetItemIds` — the one live hazard the widened sweep found — is
converted (a cancelled canonicalEpisodeIds fetch used to settle as the
couldn't-add message path). The heuristic can't see bare `runCatching`
inside suspend LAMBDAS; a review pass converted the two found
that way (AdminDashboardViewModel's and LogsViewModel's `loadInto` fetch
variants — recorded in the test KDoc too). Lower the baseline when another site
converts, never raise it; prefer extracting a legitimate parse out of
the suspend body over raising it. The ratchet runs at exactly the two
KDoc'd deliberate entries (baseline 2). The heuristic's one blind spot
(suspend lambdas) holds two benign occupants, both pure in-memory
parses the house rule allows: `AdminStatisticsRepositoryImpl`'s and
`MediaCleanupScanCore`'s JSON decodes inside a Flow `.map` (a third
blind-spot body — `DesktopEpubReaderHost`'s non-suspend `File.delete()`
swallow, nested inside a rethrowing wrapper — cannot mask cancellation
and isn't counted). The doctrine covers the `catch (e: Exception)` twin
too, which masks cancellation exactly like bare `runCatching` and is
invisible to the ratchet: the `CancellationException`-rethrow arm leads
the general arm in `EditorViewModel` (load + save),
`DownloadSidecarCore` (trickplay, subtitle pass outer + per-stream,
segments), and `MediaCleanupScanCore` (`runScan`, audit-log prune);
`SearchViewModel`'s filter persist/clear writes ride
`runCatchingRethrowingCancellation` outright. The ratchet's own scanner
ignores bodyless `suspend fun` declarations (interface members — the
next function's body must not graft onto their window) and hits carry
true source lines through the comment-stripping collapse (a
per-character line map — the test KDoc records both).
**`Semaphore.mapConcurrent` / `mapConcurrentCatching`**
(`MapConcurrent.kt`, same module) is the one bounded-parallel-map surface:
order-preserving `items.map { async { withPermit { … } } }.awaitAll()` written
once (plus the `Catching` variant for the drop-failed-item policy, which rides
`runCatchingRethrowingCancellation` — a failing item is dropped, a cancelling
one still cancels the caller). Every site keeps its own concurrency constant
and post-processing; only the permit ladder is shared. Adopters:
`HomeSectionsFetcher`'s three fan-outs, `MediaInfoApiClientImpl`,
`PhotoFolderPrefetcher`, `AdminStatisticsRepositoryImpl`,
`OfflineSyncManager`, `EpisodeCatalogueImpl`, `ArrRepositoryImpl` (whose
private `fanOut` is deleted) and `AudioPlaybackManager`'s queue-prewarm
build. Pinned by `MapConcurrentTest` (order under
randomized delays, permit bound, cancellation propagation both variants).
Fire-and-forget `launch`-per-item sites (UpcomingCalendar, AlbumDetail
downloads) deliberately stay off it — no awaited list, so `mapConcurrent`
would block the collector and change failure propagation.
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
is the one home for the request-level policies `LibraryApiClientImpl` used
to ship hand-copied: the 12-field detail projection (`DETAIL_PROJECTION_FIELDS`),
the list projection (`LIST_PROJECTION_FIELDS` — the two-field
"Overview"+"PrimaryImageAspectRatio" set every list-shaped query attaches;
the genre and playlists variants compose on top, resolved through the
wire-name ladder; pinned by `LibraryRequestPolicyTest` in commonTest),
the jellyfin-web search-suggestions shape, the SEASON/EPISODE exclude-drop,
the empty-library fallback ladder (`EmptyLibraryFallback` + the known-empty
memo probe and `emptyFallbackTotalCount`), and the favorite-flag cache-aside
toggle (`FavoriteFlagCache` over an identity-keyed `TtlCache`, 200 entries /
15 min). The client resolves the shared wire names against its own
enum/wire dialect and supplies only transport lambdas plus its
memo/threading regime (synchronized access-order LRU probed with
`containsKey`). `JellyfinDtoMappers.parseItemSortList` delegates to the canonical commonMain
tables (`parentalRatingAge` / the sort-token parser, now in
`library/LibraryWirePolicy.kt` beside `filterByParentalRating` and
`MediaType.toWireItemKind`) instead of carrying "verbatim" twins, and the
lyrics DTO mapping lives in the jvmShared `LyricsApi` (its SDK input type is
invisible to commonMain). The wasmJs-target removal deleted the commonMain
mapper twins wholesale — `LibraryWireMappers.kt` and the hand-rolled
`LibraryWireDto` set are gone; the two survivors (`MediaSourceInfoWire` /
`MediaStreamDtoWire`) live in `playback/PlaybackWireDto.kt` beside the
playbackInfo wire types. The client compiles against the single policy
in `:shared:core:network:jvmTest`.
`JellyfinApiEngine.requireUserId()` / `currentUserId()` (internal, beside
`requireApi()`) are the named user-id contract replacing the 15+
hand-rolled `currentUser.value?.id` guards across the jvmShared clients —
both read the ATOMIC `session` value (a user without a server is no
identity; the separate `currentUser` flow must not be re-combined for
this), pinned in `JellyfinApiEngineSessionTest`.

**`JellyfinRawRequester`** (jvmShared, beside the clients, internal —
the 2026-09-07 fold) is the ONE seam for the hand-built raw-OkHttp
requests the plugin catalogue, newsletter/playback-reporting plugin
endpoints and intro/credit probes used to copy per endpoint (~28 sites
across Plugin/MediaInfo/Playback clients, three incompatible private
guard adapters): session guard → `Authorization: MediaBrowser`
token header → `newCall().execute().use` → status check with the per-endpoint failure
text, over `getJson`/`postStatusOnly`/`deleteStatusOnly`/`getBodyText`
members. The load-bearing rule:
every member derives the base from `engine.activeServerAddress` (the
router's active endpoint, failover-correct) — the pre-fold Plugin and
MediaInfo sites built URLs from `currentServer.value?.address` (the
primary, stale after failover) and were rescued only by the failover
interceptor's absolute-URL promise. Pinned by
`JellyfinRawRequesterTest` (MockWebServer, the `SeerrApiClientTest`
setup) plus the first-ever `PluginApiClientImplTest` through the seam.
That bug class's last two stragglers are closed:
`AdminRepositoryImpl.getUserImageUrl` and `PluginAdminRepositoryImpl` now
build URLs from `engine.activeServerAddress` too, and the latter's
whole-engine ctor dep is deleted — `(pluginApiClient,
activeServerAddress: () -> String?, session: () -> ActiveSession?,
okHttpClient: OkHttpClient)`, the okHttp DI single injected unqualified
(the `DlnaCastStrategy` precedent); its webview credentials read ONE
atomic `ActiveSession` per the Session-identity rule. Failover-pinned in
both jvmTests: the URL follows failover.example.com over the primary.
Small folds landed around it:
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

**`JellyfinApiEngine.withApi(maxRetries, block)`** is THE way to call a
Jellyfin SDK endpoint: the `requireApi` guard + `apiResultWithRetry` (IO
dispatch, typed `ApiException` wrapping, retry with the throttled
failover re-select) composed in one member, so a new endpoint never
re-derives the guard/retry ritual. The guard resolves exactly where the
call sites it replaced resolved it — inside the retry block — so a
disconnected call fails with the same classified `ApiException` as
before and a non-retryable one still short-circuits without burning
retries; `.content` unwrapping and DTO→model mapping stay in the block
so each endpoint keeps its exact shape. The ~143 endpoint sites across
the 9 SDK clients folded onto it; `PluginApiClientImpl` stays on
`JellyfinRawRequester` (raw routes), and the deliberately irregular
flows keep hand-composed plumbing (bespoke retry counts, raw-requester
paths, fresh-client login/quick-connect legs, non-`Result` best-effort
fetches): `getMediaSegments` — its `requireApi` guard sits INSIDE the
swallowing `runCatching` on purpose, so a missing/unready session
degrades to empty segments rather than an error (pinned by
`PlaybackApiClientTest`), `getHomeSections` (the fetcher owns its cache
ladder), `toggleFavorite` (the `FavoriteFlagCache` cache-aside policy),
and the newsletter/playback-reporting `rawRequester` paths. Module
hygiene from the same pass: every `javax.inject` / `@Singleton`
annotation is stripped from core:network — the module is Koin-only.

The same Jellyfin-12 auth migration's query-param half: the playback
stream/subtitle and book-download builders (`PlaybackUrlBuilders`) and the
socket URL (`SocketUrl`) send the token as capital `ApiKey` — the lowercase
`api_key` alias is legacy, gated behind the server's
`EnableLegacyAuthorization` flag (off by default in Jellyfin 12), where
`/Items/{id}/Download` and the socket 401/403 on it — while
`StreamCacheKeys` strips BOTH spellings from byte-cache keys (pre-12
servers bake the lowercase alias into URLs they hand back, so old cached
keys must keep resolving), and the mpv token redaction widened to match —
the redaction regex set now lives in `MpvLogRedaction` (player-video
commonMain) so `MpvLogRedactionTest` pins it beside the fold.

**`SubtitleHttp`** (jvmShared `subtitle/`, beside `SubtitleRateLimiter`)
is the one HTTP chassis behind both subtitle providers: `execute`/
`executeForString`/`wrapNetwork` over an options record (`redactSecrets`,
`captureResponseBody`, `rewordSerializationErrors`). The two providers'
formerly hand-copied execute chassis and friendly ladders are
parameterised divergences now: only OpenSubtitles rewords
SerializationException (the JSON-token leak guard), only Wyzie redacts
secrets and captures the body (its 400-empty detection reads it). Pinned
by `SubtitleHttpTest` (MockWebServer) on top of both provider suites.
`SubtitleProviderRepositoryImpl` folds its two fan-outs' shared block
(no-credentials skip log → isolation `runCatchingRethrowingCancellation`
→ per-arm outcome log) into one private `externalOutcomeFor`; `search`
keeps awaitAll, streaming keeps launch+emitPartial (a declared
log-timing delta KDoc'd).
`EmptyLibraryFallbackTest` (commonTest) pins the fallback ladder — memo
short-circuit (zero transport), the three bypasses, `limit <= 0 → 50`
coercion, remember-only-genuinely-empty (a FAILED fetch degrades to
empty and IS remembered), cancellation propagation, and the
`emptyFallbackTotalCount` table — and makes explicit that a BLANK search
term is an unfiltered browse (the gate is `isNullOrBlank`) that pays the
fallback.

**`PlaybackUrlBuilders` adoption (JVM)**: `PlaybackApiClientImpl`'s three
hand-rolled URL methods (stream/subtitle delivery) now delegate to the
commonMain builders. Declared delta: the
helper's trailing-slash trim now applies on the JVM path (the old inline
code interpolated `activeBaseUrl` raw; no test pinned the raw form).
`resolveDeliveryUrlWithApiKey` is the one absolute-ize + append fold for
server-provided delivery URLs with the pre-baked-token guard in EITHER
spelling — core/data's transcode resolver used to hand-copy it without
the guard, so legacy-token URLs double-appended on the subtitle path
(drift fixed; pinned in `PlaybackUrlBuilderTest`).

**`FailoverPolicy`** (commonMain `failover/`) owns the address-failover
DECISIONS the router used to carry by KDoc: `ProbeOutcome`,
`answersWithIdentity`, the legacy-prefix strip-retry trigger +
`resolvedAddress` adoption (over core:model's `stripLegacyRoutePrefix`),
and `selectPreferredAddress` in two forms (injected suspend probe =
sequential; precomputed results = the JVM's fan-out).
`ServerAddressRouter` takes its decisions from the
core and KEEPS its declared divergences (latency capture,
primary-alone-first + concurrent fan-out, all-down-keeps-CURRENT-active).
Pinned by `FailoverPolicyTest` (commonTest) and `ServerAddressRouterTest`.

**`DeviceIdResolution.resolveDeviceId(store, scope)`** (jvmShared) is the
ONE device-id fallback ladder — the in-definition `ensureDeviceId()`
fallback went UNBOUNDED `runBlocking` → bounded (10 s) → random-UUID last
resort with a fire-and-forget
`runCatchingRethrowingCancellation` re-ensure into the application scope;
the Android/desktop twins are deleted, the platforms keep only the Koin
scope resolution.

**`JellyfinWebSocketClient.reconnects`** is the socket's reconnect
vocabulary: a `SharedFlow<Unit>` emitted in `onOpen` only after a
previous open, with `@Volatile hadConnectedOnce` cleared ONLY in
`disconnect()` (a declared divergence — clearing on failure would
suppress the emission on the automatic reconnect's open, defeating the
flow's purpose). The `RealtimeConnection` interface gains the flow and
`AuthRepositoryImpl` forwards it. Consumers: RealtimeSessionController
(the shells' shared realtime choreography, which absorbed
SessionCoordinator's collectors) keeps an explicit first-connect arm —
the `isConnected.first { it }` wait plus one immediate capability arm
(the old tracker DID fire on first connect — including the
already-up-at-start no-re-post subtlety);
ScheduledTasksRealtimeChannel keeps its own first-connect Start arm
(declared fix: the old deferred-job + edge pair double-sent Start on a
first connect while down) and rides `reconnects` for re-subscribes;
`SyncPlayManager.startReconnectWatcher` collects it (the old
`drop(1).filter { it }` raced the first join's handshake and, when the
collector won, re-ran a duplicate of joinGroup's own re-assert — true
reconnects only now). `JellyfinWebSocketClientReconnectTest` is the
repo's first test driving a real websocket reconnect (MockWebServer
upgrade dispatcher; the drop is a reflective server-socket FIN because
OkHttp 5.4's server-side `cancel()` NPEs and a graceful close lands in
`onClosed` — documented in the test). Background reconnect backoff is
exponential (60 s doubling to 15 min, reset on connect).

**`WebSocketBackoffPolicy`** (core/network commonMain) is the ONE
reconnect-backoff law — base 1 s doubling ×2 toward the 30 s cap across
5 attempts, the jitter source the only per-caller choice — consumed by
both the shared client's fast reconnect schedule and the activity-log
channel (deterministic there; past `maxAttempts` returns null, the
caller's leave-the-schedule signal).

## Navigation destinations

The **`NavDestination` registry** (core/ui `navigation/`) is the single home
for top-level destination facts — persisted customization key, icon, rail
label, rail group — as `NAV_DESTINATIONS` (+ `NAV_DESTINATION_BY_ROUTE`
lookup and `Route.navIcon`). The former monolithic `NavKey.kt` is split
SAME-PACKAGE into `NavKey.kt` (the `Route` hierarchy only, its class KDoc
still carrying the restore contract), `NavDestinationRegistry.kt`,
`NavCustomization.kt` (the persisted customization vocabulary) and
`NavTransitionPolicy.kt` (the `Route`→`NavRouteClass` projection) — the
package, and therefore every persisted binary name, the R8 keep rule and
desktop's sealedSubclasses enumeration, is unchanged. `NAV_KEYS_BY_ROUTE` is DERIVED from it, so the
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
`MusicHomeScreen`'s `MusicNavActions` facade wired once (the 7 identical
one-liners inside the module; the 2 audio-source reads supplied by `host`). The shells
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
`UpdateCoordinator` is structurally richer; the desktop check follows
`docs/adr/desktop-auto-update.md` — the inert sentinel was replaced by the
channel-flag decorator, and the staged v0.11.1 hardening adds the repo
allow-list + asset redirect gates). Android's rendered
homeMode stays `MainPreferences`-derived (the ADR's "other duties stay"),
so Android passes `homeModeChanges = null` while desktop feeds the store
flow for its optimistic rail switch. Platform-conditional blocks (rail,
media keys, surface probe, saved-state config) stay per-shell. Pinned by
`ShellSessionControllerTest` (11 tests).

**`UserMessageHost`** (`shared/feature/shell`,
`UserMessageHost.kt`) is the message-presentation seam behind every shell —
the fix for the two `:app` collectors that hand-copied the
severity→duration policy and the TV-Toast/phone-Snackbar fork, and for
desktop never collecting the shared `UserMessageBus` at all (shared-feature
error feedback was silently dropped on the desktop shell). Interface:
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
no-dismiss/Short snackbars were the drift). Pinned by `UserMessageHostTest`
(severity table, merge exactly-once/order, queue-not-drop, and the
desktop-receives-shared-bus regression).

The shells share the platform-free shell policy in `shared/feature/shell`:
**`AdminRefreshGate`** is the admin-status dedupe (30 s window + in-flight
guard, success-only `onRefreshCompleted` stamping — a failed refresh must
not push the next attempt a full window out) constructed over each shell's
own in-flight flag (read through a lambda) and a wall-clock lambda; Android's
`MainViewModel.refreshAdminStatus` and the desktop scaffold's lambda both
arbitrate through it, and the duplicated `ADMIN_REFRESH_INTERVAL_MS`
constant is gone. The rendered in-flight/admin state stays per-shell as
recorded. `RemoteNavigationRouting` moved to shared/feature/shell jvmShared
(`feature.shell.navigation`) as the ONE remote-target fold both shells run:
`routeForNavigationTarget(target)` (exhaustive `when` over the ROUTED
targets — a new server-emitted target is a compile-time decision, not a
silent `Route.Home` fall-through; the v0.11.1 companion-control navigation
ladder's four deliberately NON-route targets — GoBack pops, MoveFocus/
InvokeSelect/OpenContextMenu branch per-shell — return null) and
`popPlayerRoutes(backStacks)` (the Jellyfin-web "Stop" semantics:
contiguous player entries popped off the top of every back stack). Pinned
by shell jvmTest `RemoteNavigationRoutingTest` + the desktop
`DesktopRemoteNavigationTest`; `ShellSectionRegistryTest` pins the ledger
mechanics — the sentinel contentKey identity for unregistered routes,
replace-on-re-attach, and a non-null fallback entry. `:app`'s
`RemoteNavigationRouting.kt` keeps only the Android keycode vocabulary
(`keyCodeForFocusDirection`, `REMOTE_SELECT_KEYCODE`,
`REMOTE_CONTEXT_MENU_KEYCODE`) feeding `NavRequestCollector.dispatchKey`;
the NavigationTarget vocabulary lives in core/model remote
(`RemoteNavigationTargets.kt`).

**`NavRequestCollector`** (`:app` navigation, beside the keycode half of
`RemoteNavigationRouting`; the target→route folds live in feature/shell) is
the shell's one home for the five
collect-then-dispatch loops `MainContent` hand-rolled composable-inline:
pendingRoute (tab-vs-nested fork + consume-once), remote navigation
(ClosePlayer multi-stack pop / target routing — reuses the
feature/shell `RemoteNavigationRouting` folds), the remote-control now-playing
snackbar (title fallback + template), the dual user-message-bus
adaptation (severity projection; presentation POLICY stays in
`UserMessageHost`), and SyncPlay auto-open (player-open-anywhere guard).
Constructor-lambda controller + pure companion folds
(`pendingRouteDispatch` / `syncPlayAutoOpenRoute` /
`nowPlayingSnackbarMessage`); the composables keep one-line effects. The
external-player launch branch rides `ExternalPlayerHost` (App shell
section). Pinned by `NavRequestCollectorTest`.

**Play On one home**: `PlayOnViewModel` IS the controller —
`JellyfinRemotePlayCastStrategy` is private, and the shell resolves the
VM exactly ONCE in `MainContent` (hoisted above the TV/phone/full-screen
fork, so the companion survives a runtime TV-mode flip) and threads it
as an explicit `playOn` parameter through all three hosts; the companion
screen's identity-by-convention second `koinViewModel()` resolution is
deleted. `flingIfConnected(itemId, startPositionMs)` moved in from the
inline Home redirect adapter. Declared deltas: the `canFling` field is
DELETED (a `WhileSubscribed` stateIn nothing ever collected —
permanently false since it landed); the Home Play-On redirect is armed
on every host (was phone-layout-only); the 5 s status poll is pinned.
Perf narrowing: `PlayOnUiState` lost `positionMs`/`durationMs`/`volume`
(the ~1 Hz WebSocket session ticks recomposed the whole app shell) —
they are leaf-collected flows (the video-player rule), and the seek and
volume sliders are one shared leaf pair (`PlayOnCastSeekSlider`
arbitrates drag-vs-server-push: the local mirror wins while the thumb is
down, commit on release).

Shell pure folds: `externalPlayerPositionTicks` (extras
"position"/"positionMs" alias, Number coercion, ≥0 gate, ×10_000) leaves
`JellyPlayApp` with a test; `visibleTopLevelRoutes` (homeMode set + offline
LiveTv hide + nav customization) moved to core/ui navigation
(`VisibleTopLevelRoutes.kt` + its jvmTest; `:app`'s copy deleted — both
shells consume the one home);
`OnboardingGate.onboardingGateRoute(authenticated, completed, isTv)`
(shared/feature/shell) is the one gate behind both shells (desktop's
`DesktopOnboardingGate` is a thin `isTv = false` wrapper; Android's TV
auto-mark stays a call-site effect); `TilePlaybackState.policy` is the
tile's truth-table fold. **ShellInfra lazy providers**: every
`ShellInfra` field is a lazy provider, and the shell's
NetworkMonitor/remote-control members resolve at first composition
(AUTHENTICATED branch) instead of `MainActivity.onCreate` — startup no
longer constructs the full shell graph before the UI exists.

**Messenger family retired**: the seven per-feature expect/actual
feedback seams (calendar/arrqueue/downloads/livetv/library/settings/
admin, ~27 files: interface + `rememberXMessenger()` × 3 actuals each)
are gone — their jvmMain actuals returned null on the stale "desktop has
no message host" premise (desktop has hosted the shared bus since
`UserMessageHost`). Every former call site now reads commonMain
`core.ui.message.LocalUserMessageBus` directly; desktop provisions the
local beside its `UserMessageHost` wiring (`DesktopAppRoot`). Legacy `core.ui.feedback.LocalUserMessageBus`
(androidMain) is untouched, its own recorded lane. This is the
presentation seam only — the per-feature conveyor fold (VM posts onto
the bus) is still deferred.

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
aggregates the per-screen lists in one curated flat order (258 items — the
matcher's stable sort uses that order as the tiebreaker, so keep additions
deliberate). The `ss_<id>_title`/`ss_<id>_subtitle` strings live in
feature/settings' Compose resources; the 14 `ss_cat_*` category strings stay
in shared/core/ui because both feature modules render them.

**Rows own their identity (candidate C2)**: every row id is single-sourced —
each `*SearchItems.kt` file opens with an `*Ids` holder object (`AppearanceSettingsIds`,
`PlaybackSettingsIds`, …) whose `const val`s are THE declarations of that
screen's row ids; the `SettingsSearchItem(id = …)` declarations, the screens'
`highlighted = highlightSettingId == X` comparisons (including the appearance
screen's hand-built `appearanceItems` list and `when` dispatch, the settings
screen's `openSetting(...)`/`ACTION_ONLY_IDS`/screensaver targets, and the two
pass-through highlight ids `PINNED_ADD_HIGHLIGHT_ID`/`PRESET_LIST_HIGHLIGHT_ID`
declared in their consuming screens), the admissions keys, and the row-total
derivations all reference those constants — a raw id literal exists exactly
once per row. The literal is the persisted deep-link/recents contract, so it
changes only deliberately at the holder; the jvmTest suites that pin exact
strings (`SettingsSearchCatalogTest`'s order pin,
`SettingsSearchCatalogPlatformFilterTest`) keep raw literals on purpose as the
value ratchet. Referential integrity is runtime-pinned, not scanned:
`SettingsCatalogScreenContractTest` reflects every holder's const fields and
asserts bidirectional coverage — every holder id resolves to exactly one
catalog item (and one group), every catalog id comes from a registered holder
(a raw-literal declaration or an unregistered holder trips it), plus a
per-group check that admission keys are declared ids. That replaced the
922-line source-tree regex scanner: the screen-row ↔ catalog pairing is now
compile-pinned (row and declaration share the constant), and the test keeps
the behavioral pins (scroll resolution, the aggregation splits, the totals,
the declared admissions) instead of the noRowExceptions/derivation-usage
string ratchets, which policed duplication that no longer exists.

The row-twin render half is executed too: `SettingsRowRecord` (feature/
settings commonMain) is one record per row naming every title face once —
the screen row's `settings_*` title (rendered through `rowTitle`, so the
resource is referenced from exactly one place in code), the `ss_*_title`
search hit, and the deliberately-descriptive `ss_*_subtitle` marked by
field name — and, since the icon batch, every leading ICON once:
`rowIcon(id)` beside `rowTitle(id)` (same loud-miss `getValue` pattern;
non-composable `ImageVector` field, the record is the single icon source)
replaced the 108 hand-written `Tabler.Outline.*` row icons, and the seven
screen/record icon drifts resolved to the records
(DVR_RECORDING_QUALITY's hand-written Video → the record's BadgeHd, …).
Pinned by `SettingsRowRecordTest`.

The screens decomposed with it: `PlaybackSettingsScreen` (2,184 lines)
renders through eleven private group composables (`PlaybackPlayerGroup`,
`PlaybackPlayerAdvancedRows`, `PlaybackAdvancedVideoGroup`,
`PlaybackEngineGroup` + the per-engine Mpv/Vlc/Exo row groups,
`MediaSegments`, `SyncPlay`, `Casting`, `Dvr`); `SettingsScreen`'s
mid-composable `settingsSection` local is hoisted top-level with five
`SettingsGroup` sections extracted (Account / Activity / System /
Screensaver / IdleAmbient), picker/dialog state threading as
`activePicker`/`activeDialog` `MutableState`s.

The same pass finished the declared-admissions ratchet: the notification,
language-subtitles and security groups now declare per-id
`RowAdmission`s beside their items (`NotificationRowAdmissions`,
`LanguageSubtitlesRowAdmissions`, `SecurityRowAdmissions`) like
storage/playback/audio before them, and their screens' emission `if`s read the
declared gate via `SettingsSearchItemGroup.rowAdmitted` (the structural
`enabled`/`showAdvanced` wrappers carry those halves of the `All(...)` gates —
the playback advanced-video precedent; security's `pin_for_player_lock` stays
hand-gated, its missing declaration being the shipped count quirk). Two gate
vocabulary additions: `RowAdmission.Always` (the explicit unconditional gate
for the strict `?: false` totals — notifications enumerate every id, security
counts nothing undeclared) and the `RowAdmissionCapability.SystemNotificationSettings`/`.Biometric`
entries backing the notification system-settings row and the security
biometric row (the screen passes its gate-aware computed flag).

The settings ViewModels share one small shell: `SettingsEditorViewModel(editor)`
exposes the single `edit { }` command, `SettingsSectionViewModel` adds the
`AdvancedSettingsGate` pair (`showAdvancedSettings`/`setShowAdvancedSettings`)
and `resetCategory` — the nine per-VM copy-paste forwarders are gone and the
screens' call sites are unchanged. The per-screen ViewModels also dropped
their unused `store: UserPreferencesStore` constructor param (Koin + the VM
tests' relaxed mock with it). The onboarding wizard's twin `edit` one-liner
stays: feature/onboarding does not depend on feature/settings, and the shell
is feature-local (decision Q11a's locality discipline).

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
co-located `*SearchItems.kt` — ids added to the file's `*Ids` holder first,
then referenced by the declarations, the screen rows and any admission (+ the
new strings in feature/settings' Compose resources, + one line in
`SettingsSearchCatalog`, + one holder registration in
`SettingsCatalogScreenContractTest`). No core/ui edit, no new callback field.
`SettingsSearchCatalogTest` (feature/settings `jvmTest`,
kotlin.test — resource resolvability is compile-time-guaranteed by the
generated `StringResource` accessors, so the suite pins id uniqueness,
resource/category cardinality, keywords and the 258-item aggregation);
`SettingsSearchMatcherTest` (shared/core/ui `jvmTest`) is synthetic and pins
matching only.

The settings **icon prewarmer** derives its workload from the same catalog:
the 258 catalog rows × 3 resource slots (title/subtitle/category
`StringResource` accessors) = 774 reads over 530 distinct resources — the
dedup happens at the generated-accessor level, so the prewarmer warms the
530 distinct entries; the catalog test pins the 258-item aggregation, and
the distinct count is derivable from it (14 shared category strings).

## Settings platform visibility

**`SettingsCapabilities`**
(`shared/feature/settings/src/commonMain/.../SettingsCapabilities.kt`) is the
one visibility surface for platform-gated settings: `settingsCapabilities`
is an expect val with an androidMain and a jvmMain actual, and each flag
answers "can this binary's settings surface offer this row?" — hidden means
structurally absent, never rendered-then-disabled. The ownership rule is the
module's KDoc: capabilities own VISIBILITY; the behavior seams
(`BiometricGate`, `LogCollector`, `PlatformIntents`, and the shared
`LocalUserMessageBus` — the Messenger family's replacement)
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
(ANDROID/DESKTOP —
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
side is DONE — a 2026-09-07 pass finished the catalog-derivation
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
set: "action" is dialog semantics, not structure.

**`ConnectionProbe`** (settings commonMain, generic over
request/key/details): the three isomorphic service-probe machines (Arr /
Seerr / SubtitleProvider — three status seals, three lifecycles, three
drifted visuals) are one status machine
(`Idle`/`Testing`/`Connected(details)`/`Error(Failure)`) with RESTART
single-flight (a second probe cancels and supersedes; a stale outcome
never lands), `runCatchingRethrowingCancellation` discipline (a
cancelled probe never lands as Error — previously only Seerr had it), a
`refused` pre-flight seam (Subtitle's blank-credential guard), and
localized failure text (the three baked English literals +
required-field texts are `settings_probe_*` resources in all 9 locales;
`Failure.Reported` carries server text verbatim, `Failure.Declared`
resolves at render). One shared `ConnectionProbeStatusIndicator`
(pip/message/inline/banner styles) replaces the three visuals. Declared
deltas: a crash degrades to a fallback Error instead of killing the
scope; a refusal supersedes an in-flight probe; Subtitle double-tap
restarts instead of racing; Arr's `testAllServers` lost its
batch-level cancellation frame — a second batch supersedes a
still-running prior batch key by key under the board's RESTART policy
(leaving-set probes are reaped by `retain`).

Settings-screen additions: `SETTINGS_ENTRANCE_SECTIONS` +
`settingsEntranceStep(key)` derive the 19 entrance steps (pinned equal
to the old literal phone/tv pairs); `SettingsSearchPanelState` owns the
search panel machine with named focus delays; the dead import twin is
deleted — `PendingImport`/`parsePendingImport`/`confirmImport` gone
from `SettingsViewModel` (stage→navigate→`ImportPreviewViewModel`/
`BackupParser` is the only pipeline). Declared delta: `importSettings`
no longer reads the file at stage time, so an unopenable/corrupt backup
surfaces its error on the ImportPreview screen (pinned by
`ImportPreviewViewModelTest`) instead of as an inline settings-screen
status. The `edit(transform)`
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
both read. The border derivations ride the registry too:
`detailCardBorder(primary, secondary, outline, includeAurora = true)` is
the detail-screens subset of `cardBorder` — the four hand-rolled border
derivations (`SeerrDetailScreen`, `MediaDetailBody` ×2,
`MediaDetailSeasons`) are deleted; three of the four sites keep
`includeAurora = false`, preserving their historical null-on-aurora
(enabling aurora there is a deliberate visual follow-up, not this fold).
Per-variant schemes live beside the registry (`AuroraTheme.kt`,
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
one copy). STA-11 (2026-09 perf audit) narrowed the bind path to
memory-only reads — the store's eagerly-warmed StateFlow value and
`WidgetImageLoader`'s poster cache — with a new `remoteAdapterViewId`
ctor param and an async warmup-repaint tail (`scheduleWarmupRepaint`,
one pass per data generation) that warms a cold snapshot/uncached
posters off the bind path and repaints via
`notifyAppWidgetViewDataChanged`; `WidgetDataStore` keeps only
`continueWatchingSnapshot` (the broadcaster's read) — the Library/Seerr
`*Snapshot()` accessors are deleted with the bind no longer blocking on
them. The 2026-09-07 review completed the straggler family:
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

Three more deepenings completed the widget family. The
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

**`WidgetPushGate`** owns `lastItemId`/`lastArtwork`/`lastPushedRender`
with `decideOnMetadata` (full push; the post-artwork-load re-read is
what gets recorded) / `decideOnPositionTick` (partial via
`shouldPushPartialPosition`) / `reset()`. The skeleton's
`runWithPendingResult` + `launchFinishingOnMain` absorb both former
goAsync shapes in `NowPlayingWidget`. `WidgetWorkScheduler
.claimRefreshSlot` is a CAS loop — declared fix: the former
get-then-set let two triggers inside the 5 s window BOTH enqueue (its
own KDoc already claimed they didn't); the CAS loser is suppressed
without restamping (pinned: no window extension, 8-thread race single
winner). **`RecommendationWorkerSkeleton`** (widget/skeleton) owns the
recommendation workers' guard → fetch → empty-keep → cap/map/persist →
retry-fold chassis over `skipFetch`/`fetchItems`/`mapItem`/`persist`/
`logFailure` seams (per-site logging preserved: Library logs every
failure, Seerr only permanent). `WidgetGridFactory` gained
`bindGridCellTail`/`gridCellLoadingView` — the poster-or-fallback +
responsive-text bind-tail the Library/Seerr services re-copied
(ContinueWatching keeps its declared diverged tail);
`BaseWidgetConfigActivity` owns the config save tail (getInstance →
update seam → notify(gridViewId?) → refreshNow seam → finish) — the
four `saveAndFinish` hand-copies are gone.

The widget package's Koin service-locator idiom is folded onto
**`WidgetKoin`** (beside `WidgetPosterIdentity`): the 12 per-file
`private fun koinXxx() = KoinPlatform.getKoin()!!.get()` copies and the
two config activities' `by lazy { KoinPlatform.getKoin()!!.get() }`
properties now resolve through one accessor object (typed convenience
vals for the five repeated dependencies + a reified `get()` for the
rest); callers keep their own process-start-race policy (try/catch →
empty/fallback state), and the two NULL-degrading resolvers that
predate it (`WidgetProviderSkeleton.resolveWidgetDataStore`,
`AppWidgetWorkerFactory`) stay as they are — a `!!`-throwing accessor
cannot express their no-op-on-unstarted-Koin contract. The Library/Seerr
grids' `readSourceLabel` twins deduped beside it
(`readLibrarySourceLabel`/`readSeerrSourceLabel` over one `readSourceLabel`
core — source-picker display name with the per-grid fallback literal).

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

**`IncomingIntentRequest`** (app `deeplink/`): the pure classify step
behind MainActivity's intent handling — an intent →
`IncomingIntentDisposition` fold (launcher-shortcut arms, ACTION_VIEW
deep link, ACTION_SEND shared text, the search arms, or `None`) over
already-decoded values, owning the launcher-shortcut action → `Route`
table and the action-literal vocabulary. Pinned by
`IncomingIntentRequestTest`. **`OverlayDragPolicy`** (app `floating/`):
the pure drag/tap state machine behind `FloatingPlayerService`'s overlay
touch handler — slop threshold, drag-vs-tap, one-relayout-per-frame
coalescing; the service keeps only the `updateViewLayout` effects. Pinned
by `OverlayDragPolicyTest`.

**`BackExitConfirmation`** (`shared/core/ui/components`, the
`ScrollDirectionVisibility` precedent) is the double-back-to-exit policy both
shells shared as a hand-copied `ExitConfirmationTimeoutMs = 2000L` pair:
`onBack(nowMs, lastAtMs, atExitPoint)` → `Pop` / `Prompt(nowMs)` / `Exit`,
exit resets the window. `JellyPlayApp` and `TvNavigationDrawer` keep only the
  `moveTaskToBack` + Toast effects. Pinned by `BackExitConfirmationTest`
  (1999/2000 ms boundaries, window reset).

**`PipLifecyclePolicy`** (beside `PlayerActivity`, pure, internal) owns
the PiP ordering machine: `pipExited(phase,…)/onResume/onStop/
onUserLeaveHint/onTopResumedChanged` → `Decision(action, justExitedPip)`
plus `clampAspectRatio`/`isValidSourceRect`; the OEM-ordering comments
are its KDoc; 19-test callback-sequence table (confirmed PiP entry is
execution-only — no policy event). The two auto-enter predicates are
modelled SEPARATELY (`userLeaveAutoEnter` has no `isPlaying` term;
`topResumedLossAutoEnter` adds it; the pre-arm skips `controlsLocked`)
— no single existing predicate used all four terms, so they were not
unified. **`PipActionSet`** is the PiP action apparatus's pure halves:
`actionSpecs(isPlaying, hasNext)` (the ordered skip-back → play/pause
icon+title fork → skip-forward → gated-next fold, resource ids only) and
the `idFor`/`actionForId` wire codec plus the protocol constants, moved
verbatim (wire-stable across app updates); `PlayerActivity` keeps only
RemoteAction/PendingIntent wiring. Pinned by `PipActionSetTest`.

**One PiP port** (core:data): the players' PiP control seam is ONE commonMain
interface, `com.raulshma.jellyplay.core.data.playback.PipController` (+ the
single `PipAction`/`PipTransport` pair), in `shared/core/data` — the former
two module-local forks (player-video's and player-live's `PipController` +
`AndroidPipController` adapter twins, one per player over the same singleton)
are deleted, along with the drift they had already accrued (video carried the
#145 latch members, live didn't). The production impl is core:data
androidMain's **`AndroidPipController`** (renamed from the legacy
`PipController` class; runtime behavior identical, still the process Koin
single) — it implements the port AND keeps the Android-typed extras only the
host Activity needs (`Rational` aspect flow, `Rect` source hint,
`notifyPipDismissed`/`setPipMode`/`shouldAutoEnterPip`/`autoExitPip`), which
deliberately do not ride the common port. `androidCoreDataModule` binds the
concrete single plus the port key to the SAME instance (the FontProvider
one-instance-two-keys pattern); the players and `PlayerActivity` both resolve
it — no per-player adapter remains, and the desktop binding stays
player-video's `NoOpPipController`. Live's PiP wiring gained the deliberate
latch-members behavior by construction: `LiveTvPlayerViewModel` collects
`pipDismissed` (pause → `stop()` → `closePlayer` → re-clear the latch — the
VOD VM's auto-exit discharge, which live previously lacked: the flag merely
latched and nothing closed the window) and defensively clears both one-shot
latches in `initialize()`; the live screen folds `closePlayer` into its
`onBack`. The players' transport re-arm is shared with the same shape:
**`reArmPipTransport(pip, handler)`** (core:data commonMain
`playback/PipTransportReArm.kt`) owns the null-guard + transport
assignment + the lifecycle rationale (Activity-scoped VM,
`PipController.reset()` on teardown, `init` never re-runs). The per-action
MAPPING stays host-owned BY DESIGN — live maps to channel zaps + direct
engine calls (no funnels exist on live), VOD to the
`routedPlay`/`seekByStep`/`playNextEpisode` funnels with a `Log.w`
no-engine drop; pinned by `PipTransportReArmTest` (core:data jvmTest)
plus live's existing transport pins.

**`ExternalPlayerHost`** (navigation/playbackhost): the six-step launch
protocol (resolve → report-start → stash → chooser → failure clears
stash + error; result consumes once → ticks fold → report-stop) over
constructor lambdas; the shell keeps the remember construction, the
ActivityResult wiring, and one-call sites. The launcher arrives per-call
(the host must exist before the launcher's callback can reference it —
KDoc'd). Ordering pinned by `ExternalPlayerHostTest` (Robolectric,
fake-lambda choreography) — the pieces were tested before, the ORDERING
was not. Its chooser arm rides `runCatchingRethrowingCancellation`.

**`ExternalPlayerLaunch`** (navigation/playbackhost, beside the host): the
outbound extras vocabulary + identity fields of one hand-off (launch
`intent`, `itemId`, `startPositionTicks`, `playSessionId` for the
report-start/report-stop pair) as one data class with its builder —
formerly declared in the app root package beside its only construction
site; the extras literals are pinned by `ExternalPlayerLaunchTest`.

**Auto-lock**: `AppLockRedirect.shouldRelock(gate, timerMs,
backgroundedAtMs, nowMs)` (the `backgroundedAt > 0` "never backgrounded"
arm preserved) + `AppLockState.onBackgrounded/onResumed` move the timer
decision out of MainActivity's lifecycle callbacks — the third lock
decision, previously the only untested one beside
`PinGateController`/`AppLockRedirect`; truth-table pinned (incl.
exact-equal and one-ms-short boundary arms). STA-8 prewarm:
`PlayerActivity`'s persisted-security `runBlocking` keeps its
fail-closed timeout, but `AppStartupPrewarms.start()` (called from
`JellyPlayApplication.onCreate`) hydrates the same slice off main at
process start — the common case is an instant memory replay. MainActivity
adopts `JellyPlayPreferenceTheme` (the wrapper's
byte-identical ~98-line hand copy — 17 theme args, motion/performance
locals, filter chain — is deleted).

**`DesktopWindowPlacementController`** (apps/desktop): the
undecorated-window maximize dance (AWT `MAXIMIZED_BOTH` is broken on
`WS_POPUP` frames) — work-area on maximize, saved bounds on restore,
replay-once on windowOpened, skip persist in fullscreen, restore-bounds
preference — over a two-member `DesktopWindowPlacementHost` seam
(`bounds` + `workAreaOrNull()`; the AWT adapter and the test fake are
the two adapters that justify it). `DesktopWindowStateStore` is NOT
re-absorbed (persistence stays pinned by its own suite); `Main.kt` is
wiring-only. All five rules pinned in the existing `:apps:desktop:test`
lane, incl. a replay→restore→persist session sequence.

**`DesktopAccelerator`** (apps/desktop, beside the placement controller):
the accelerator rows Main.kt executes — the `requiresCtrl` semantics kept
exactly (plain R/Q fall through, F11 ignores modifiers) plus the
`DesktopTitleBar` display labels. Pinned by `DesktopAcceleratorTest`.

**`HarnessRunner` + `HarnessRobot`** (apps/desktop): the one runner
chassis behind the desktop E2E harness lanes — step ledger, poller,
auto-exit deadline, report — extracted from byte-identical private Runner
twins, with the robot issuing the input/state steps; desktop-main logic
is now drivable on the JVM.

**`desktopBackKeyDecision`** (apps/desktop `DesktopBackKey.kt`,
internal, pure): the one desktop back-key decision the two handlers that
hand-copied it — `DesktopAppRoot`'s scaffold Row (signed-in shell) and
the signed-out shell's `onPreviewKeyEvent` chrome, now the `content`
parameter of shared/feature/shell's `SignedOutAuthHost` — now share. **Esc** or **Alt+Left** pops the
current back stack, but only above the root (`stackDepth > 1` — every
stack is seeded with its tab root, so that IS "not at the root"); at the
root every key returns `false` and falls through unconsumed (no
quit-on-Esc convention — the window closes via titlebar/tray Quit).
Call sites keep the KeyDown gate and, in the shell, the video-player
media-key fallback on their side of the line. Truth table pinned by
`DesktopBackKeyDecisionTest` (jvmTest).

**`JellyPlayApp` split** (same package, `app/.../navigation/`): the
1,606-line `JellyPlayApp.kt` is now `JellyPlayApp.kt` (the shell session
gate + top-level wiring) + `MainContent.kt` + `ShellLayouts.kt` (the
`ShellNavParams` `@Immutable` bundle threading the shell hooks
(`shellHost: ShellHostHooks`, built once in `MainContent`) and `playOn`
whole, so each branch takes one value instead of a ~20-param funnel —
the layout subtree never names `MainViewModel`) + `MainNavDisplay.kt` +
`ShellOverlays.kt`, plus the pure `isFullScreenRouteActive`
(`FullScreenRoutePolicy.kt`, pinned by `FullScreenRoutePolicyTest` in
`:app`'s test lane). The `MainNavDisplay` Koin re-lookup is gone — the
shell hooks arrive through `ShellNavParams` instead of a
`LocalViewModelStoreOwner` + `mainViewModelFromKoin` hop; the
`OnboardingContent` wrapper is deleted. `MainViewModel` keeps its
coordinators private (never re-exported); the composition root
(`MainActivity` → `JellyPlayApp` → `MainContent`) threads them to the
few consumers that need them.

**`SignedOutAuthHost`** (`shared/feature/shell/navigation`, jvmShared)
is the signed-out half of the session gate, shared by both shells — the
former hand-copies (Android's inline `AuthContent` in `JellyPlayApp`,
desktop's `DesktopSignedOutAuthHost`, deleted) collapsed into one
composable beside the rest of the shared shell wiring. One top-level
route, `Route.ServerList` (the seed both shells ran identically —
deliberately NOT a Login prefilled with the last server address), with
authSection's five entries stacked on it; success needs no callback
wiring (the caller's `isAuthenticated` observer swaps the host out).
The `content` parameter is the per-shell frame (desktop passes its
back-key chrome); `savedStateConfiguration` stays a parameter because
the saved-state serializer seam is genuinely platform-specific. UI
seeding only — session policy stays on `ShellSessionController`
(ADR-0001 untouched).

## Book reader (`shared/feature/player-book`)

The reader (CBZ/CBR/PDF/EPUB; `docs/book-reader.md` is the user-facing
doc). Shape notes:

- **Marks are a data-layer feature**: Room schema 55 adds `book_bookmarks`
  + `book_annotations` (indexed by itemId), exposed through
  `ReaderAnnotationsRepository` in core/data (domain models + Markdown/JSON
  export); the reader only consumes the flows. Position encodings reuse
  `BookProgressPolicy` ticks verbatim; EPUB rows carry an opaque `cfi` the
  native side never parses beyond equality (ADR 0003).
- **`reader.js` is the EPUB engine, `EpubReaderHost` its only protocol**:
  every capability (typography, flow, CFI jumps, annotations paint, spine
  search, speech context, auto-scroll, JS tap zones) is a
  `window.jellyPlayReader` command or a posted JSON event, shaped once in
  `EpubReaderHost.kt` so the Android WebView bridge and the desktop CEF
  poll cannot drift. The initial appearance bundle rides the chunked
  `loadBookBegin` frame; post-boot `displayError` events degrade (stay on
  page) rather than veil the reader — stale CFIs must not kill a session.
- **Selection owns the pointer on reflowable content**: the Compose
  overlay is keyboard-only (`readerKeys`, zero `pointerInput`); JS tap
  events route through the same decision as the arrow keys (below). Paged
  content keeps the native tap zones (`readerInput`) — there is no web
  content to select. This split is why TV remotes and desktop keyboards
  behave identically.
- **The screen is decomposed**: `BookReaderScreen` is a ~160-line router;
  `ReaderChrome` / `ReaderContent` / `ReaderSheets` / `ReaderSelection` /
  `ReaderInput` are separate files. VM additions are parallel StateFlows
  (bookmarks, annotations, `currentEpubLocation`, speech, sleep); the
  sealed `Ready` state carries only what defines the content kind.
- **Paged zoom re-rasters**: `PageCache` keys on `(page, renderWidth)` and
  keeps exactly one width per page (zoom re-renders replace, never stack);
  `BookDocument.pageSize()` feeds the pure fit-mode width math
  (`ReaderFitMathTest`); the tile's `graphicsLayer` zoom snaps back to 1×
  when the sharper bitmap lands. `PageZoomState` owns the two pure
  decisions — `pageRasterTarget` (3× raster cap, 0.05 dead-band,
  base-scale skip) and `shouldClaimZoomGesture` (two fingers, or any
  finger while zoomed) — pinned by `PageZoomStateTest`.
- **Speech + sleep are Compose-free controllers**: `ReaderSpeechController`
  drives sentence-by-sentence utterances (paragraphs split by the
  controller's sentence heuristic; chapter advance detected by context
  identity, not timing; the `BookSpeechEngine` seam — Android
  `TextToSpeech`, desktop honestly UNAVAILABLE Noop via the
  `BookFormatProbe` Koin pattern; the controller takes the
  `() -> EpubReaderHandle?` seam directly — no VM hop). `ReaderSleepTimer`
  ticks countdown or end-of-chapter. Both jvmTest-pinned with value fakes.
- **Utterance decisions live in commonMain**: `SpeechUtteranceMachine`
  (beside the speech controller) owns the read-aloud policies —
  park-until-ready, the single-active completion guard, stop clears /
  error-completes, the INITIALIZING → AVAILABLE/UNAVAILABLE fold — behind
  the tiny `TtsBinding` seam (ensureStarted / default-language probe /
  speak(text, id) / stop / completion callbacks). The Android engine is a
  dumb `TextToSpeech` translator over it; desktop binds the shared
  `NoopBookSpeechEngine` directly (the verbatim `DesktopBookSpeechEngine`
  duplicate is gone). Pinned by `SpeechUtteranceMachineTest` over a fake
  binding — the seam's second adapter.

**Reader modules (jvmTest-pinned):**

- **`ReaderPreferences`** (beside the reader VM) is the preference
  choreography: every knob write — global or per-book-routed, clamped or
  stepped — is a command here, and the reader reads ONE
  `snapshot: StateFlow<ReaderPrefsSnapshot>` (raw globals, the item's
  `PerBookAppearance` override, the per-item direction + pin, and the
  EFFECTIVE appearance fold — override ?: global — as a derived property).
  Write-through is the mechanism: a command updates the snapshot
  synchronously and persists through `ReaderStore` async; while a persist
  is in flight (CAS counter) store emissions are not adopted, so a
  first-persist emission can never clobber a newer command — the
  mechanism that replaced the four hand-rolled DataStore-lag latches.
  `applyTypography` / `applyBehavior` own the settings sheets'
  whole-bundle commit diff ONCE (the diff reads the live snapshot; only
  changed axes persist). Theme/font-size writes route into the override
  when one is active ("use for this book only" seeds it with the
  effective values; switching off copies them back). `ReaderStore`
  (core:datastore) stays the storage seam; `attach(itemId)` re-points
  routing per load.
- **`BookSessionLoader`** owns the open-book pipeline (session-reset
  ordering, detail fetch, format-probe fallback, the `BookOpenError`
  classification, resume math, the PDF-vs-comic TOC-cache branch);
  uiState writes stay VM-side through constructor hooks
  (`onSessionReset` / `onDownloadProgress` / `onFormatResolved`; the
  `PlaybackSession` precedent). **`ReaderProgressReporter`** owns the
  progress-report choreography (debounce, final-flush idempotence across
  onDispose+onCleared, flush-scope escape, last-CFI ride-along).
  `BookReaderViewModel` is ratcheted at ≤ 43 members
  (`BookReaderViewModelOwnershipTest`; the god-count precedent).
- **`ReaderBookmarkCodec`** (pure, beside the marks) owns the
  "cfi == null ⇒ paged pageToTicks, else reflowable percentToTicks"
  branch (encode/matches/decode/decodeLabel/isJumpable/pagerJumpPage) —
  written once for the VM's four sites and the sheets' display decode;
  the bookmarks-sheet jump gates on `isJumpable`.
- **`ReaderSheetStack`** holds all seven sheet/dialog flags behind ONE
  `open` fold, consumed by the JS-tap nav gate and the chrome auto-hide
  (the note dialog counts as holding the screen everywhere); the VM holds
  no sheet state (`sheets.open` is the only read). **`ReaderSearchSession`**
  owns the in-book search token + result state machine: the sheet only
  debounces and reports the surviving query, the session stamps the token
  (`launchSearch(query) { scan }`) and drops stale-token results; reader.js
  stays as the second belt.
- **`EpubEventListener`** (epub/) is the host event seam: a single-listener
  `fun interface` carrying the sealed `EpubEvent`, with the
  `withStatusReceipt` decorator latching the delivery receipt.
  `BookReaderViewModel` has ONE `onEpubEvent` funnel; the screen's own
  listener handles the screen-local kinds (status veil, TOC mirror,
  search results, taps, auto-scroll stops) before forwarding the rest.
  Adding a reader event is parser-branch + sealed variant + one `when`
  arm. **`BookDeliveryTracker`** owns the boot-transfer gates
  (pageGeneration/deliveredGeneration/bookBase64: encode, send, payload
  release, appearance push) as pure decisions. The five-block effect
  ladder that CONSUMES those gates — receipt wiring, encode, chunked
  send, payload release, appearance push — is shared too: commonMain
  **`BookTransferEffects`** (epub/, a composable over the Compose-free
  `encodeBookIfDue`/`sendBookIfDue` steps + the
  `EpubEventListener.failBoot()` extension) owns it once, keyed exactly
  as the tracker decides, with `rememberUpdatedState` lambda params so
  stale captures cannot re-send. The hosts keep only their genuinely
  divergent halves (WebView bridge vs CEF poll, live vs latched
  page-load fact, and the failure source — declared divergences in
  KDoc). Android's encode + HTML build previously had NO failure arm
  (an IOException there was an uncaught composition-effect crash);
  both hosts now fold encode/build failures into `failBoot()` →
  `EpubEvent.Status(ERROR)` on the RAW seam → the existing error veil
  (retryable, nothing latched). Pinned by `BookTransferEffectsTest`
  (encode-failure path, cancellation rethrow, full ladder over a fake
  eval receiver, reload re-run). **`EpubProtocolMirrorTest`** is the
  JS↔Kotlin semantic mirror pin: source-scans reader.js against the
  Kotlin sides — `locations.generate(N)` == `EPUB_LOCATION_PAGE_CHARS`,
  every posted `type:'…'` string ⊆ `EpubEventParser`'s dispatch keys,
  the `window.jellyPlayReader` handler set ≡ the emitted builders (the
  one declared exemption: `loadBook`, the small-book test entry the
  hosts never ride), and the annotation swatch palette hex map ≡
  `ReaderSelection`'s swatch arms. The desktop host gates its
  WebView on **`KcefStatus`** (IDLE/STARTING/READY/FAILED/RESTART_REQUIRED):
  a dead viewer fails the boot into the error veil via the RAW event seam
  (never the receipt-wrapped one — a receipt for an undelivered generation
  would bar the retry's encode), init retries on the next reader open, and
  the page-identity probe re-polls on a bounded retry (the desktop evaluate
  path swallows null results without invoking the callback, so one shot can
  strand the boot); `DesktopViewerBootTest` pins the probe vocabulary and
  the retry admission. The desktop chrome is in-flow, not floating
  (`epubChromeOverlaysContent`: windowed CEF paints above every Compose
  overlay, so top/bottom bars, the boot strip, the error screen, the
  selection bar and the tick rail lay out around the browser; sheets and
  dialogs hide it — `syncBrowserSurfaceVisibility` plus the 0px size
  collapse while a sheet is open — so the shared sheets show cleanly) —
  the brightness row is dropped there (a dim
  veil cannot cover the native view) and the root requests Compose focus on
  entry (clicks land in CEF, which never yields it, or keyboard paging
  would be dead).
- **`ReflowableReaderSession`** (`ReaderControllers.kt`) is the reflowable
  session module: the late-bound host handle, the three screen-side
  controllers (`ReaderAnnotationSync` / `ReaderAutoScroll` /
  `ReaderSearchSession`) wired to that one handle, the exact-resume
  latch, JS-tap routing and the search choreography; the composable is a
  render shell (five one-line effects keyed on composition). The speech
  chapter-continuation protocol is controller→host→JS→event→controller
  (the former 8-hop `hostCommands` channel is deleted; the two
  non-speech consumers became synchronous `ReaderSessionPort` calls).
- **Percent single carrier**: `foldEpubPosition` is the ONE clamp +
  speech-report + progress-schedule path (the bare `percent` and the
  richer `relocated` events both route through it; `openBook` seeds the
  location flow with the resume percent); `EpubLocation` is the only
  percent holder.
- **One tap decision**: `ReaderNavDecision` +
  `readerNavDecision(zone, direction, sheetOpen, selectionActive)`
  (`ReaderInput.kt`) serves native paged taps, JS bridge taps and swipes;
  `tapZoneFor` (`EpubEventParser.kt`) is the single geometry→zone
  resolver both input worlds share (`ReaderInputTest` pins the dead
  tap/swipe wiring bug family).
- **`PagedPagerCoordinator`** (`PagedPagerCoordinator.kt`, beside the
  paged renderer it serves; the `ReflowableReaderSession` controller
  convention — Compose-free, fakeable in jvmTest over its `PagerHandle`
  seam): the paged reader's ONE page-turn protocol — the animate-vs-snap
  ladder (`animatedPageTurns` read at dispatch, user swipes always
  animate) and the two named landing orderings that previously lived as
  three hand-copied ladders in `PagedReaderContent`. `turnTo` is
  VM-first (keyboard/tap-zone/volume-key turns and the outline/bookmark
  sheets move uiState; the sync effect re-offers the page, the same-page/
  in-flight guard keeps the round trip idempotent); `jumpTo` is
  pager-first (TOC tick-rail + slider seek scroll immediately without
  drowning the VM, which learns through the settle collector `attach`
  installs — the only swipe-reporting path there is, seed emission
  dropped). Pinned by `PagedPagerCoordinatorTest`.

## Playback focus (ADR-0004)

**`PlaybackFocus`** (core/data commonMain `playback/focus/`) is the ONE
owner of cross-player exclusivity — "who is playing, and who must pause
whom", the policy that used to be an OS accident spread over engine
configs, `PlayerAudioLifecycle`, two user prefs, and per-shell code.
Interface: `claimState` (Idle / Held / Suspended) + `acquire` / `release`.
The matrix (`PlaybackFocusMatrix`) is pure and total over the closed
`PlaybackSurfaceId` world; rulings: newest-wins, pause-not-duck (no duck
vocabulary exists), manual-resume (the `playWhenReady` guard makes that
true at the OS level: the music surface's `pause()` clears playWhenReady,
and since the migration slice music has no media3 focus stack of its own —
the module's seat is abandoned at release and `Regained` is ignored, so
nothing can resurrect it).
`DefaultPlaybackFocus` is the commonMain executor; `FocusArbiter` (OS
seat — `AndroidFocusArbiter`, one AudioFocusRequest per attributes
identity, AUDIOFOCUS_GAIN, no delayed gain) and `PlaybackSurface`
(commandable victims AND suspended holders) are the two ports, EACH with
two production adapters: `FocusArbiter.request(attributes, listener)`
takes the claimant's `FocusAudioAttributes` (matrix rows via
`PlaybackFocusMatrix.attributesOf` — READ_ALOUD keeps
USAGE_MEDIA+CONTENT_TYPE_SPEECH, MUSIC→MUSIC, VIDEO→MOVIE), the desktop
in-process `DesktopFocusArbiter` grants vacuously, and
  `DesktopAudioQueueManagerSurface` gives desktop music the same
  `onPlayingEdge` claim chokepoint Android music has (Denied mirrors to a
  pause). Desktop binds `DefaultPlaybackFocus`; `NoopPlaybackFocus` remains
  for tests. Music's play edge HONORS `acquire()`'s outcome: on
`FocusOutcome.Denied` (a displaced holder Suspended under an OS loss —
e.g. read-aloud during a phone call) the manager pauses instead of
producing audio the interface forbade. The reader claims/releases around
the read-aloud session and pauses its loop on a displaced `Held`. The
migration slice LANDED (2026-09-17): Android music's OS leg is the
module's — `handleAudioFocus` is off on BOTH music players (primary and
crossfade secondary; built-in handling would fight the seat and let the
secondary's GAIN request focus-loss-pause the primary mid-fade), MUSIC
claims the seat on the play edge with the MUSIC attributes row, and OS
losses on the holder are ENFORCED (the holder has no `claimState`
observer, so the module commands its surface pause; `Regained` stays
ignored — resume is manual). Video is denied until its slice adds a
matrix row (the exhaustive `when` makes it a build error, not a silent
overlap). See docs/adr/0004-playback-focus.md.

## Audio playback, effects & cast cores (core/data)

- **`AudioQueuePolicy`** (commonMain `playback/`, beside
  `QueueUndoStack`/`NowPlayingTracker`) is the ONE pure owner of the queue
  semantics both audio managers used to duplicate nearly verbatim:
  `nextIndex`/`previousIndex` advance-wrap rules per repeat mode,
  `planMove` (the `moveQueueItem` index remap + the moved row as undo
  payload), `skipsPreviousRestart` (the 3,000 ms strictly-greater
  threshold), the `cycleAbLoop`/`markAbLoop*` marker machine,
  `finalStopPositionTicks` (the end-of-stream stop position: the last
  published position wins, the item's full duration stands in on a zero
  position, 100-ns ticks), and
  `positionTickPlan` (A–B enforcement seek + the pre-seek position
  publish, publish dedup on coerced values, duration coercion, the
  lyric-index gate). Declared divergences encoded, not copied: repeat-ONE
  at track end stays adapter-side (media3 internal / desktop engine
  replay); Android-only tick duties (crossfade check, bandwidth sampling)
  remain in the Android ticker. `AudioPlaybackManager` (androidMain) and
  `DesktopAudioQueueManager` are thin policy callers; the desktop KDoc
  parity table points at this module as the pin.
  `AudioQueuePolicyTest` pins it.
- **`AudioQueueStateCore`** (commonMain `playback/`, beside the policy)
  owns the queue-state chassis the audio-manager twins mirrored (~1,100
  lines under comment-enforced parity): the 11 state flows,
  `QueueUndoStack` + undo events, `AudioQueuePolicy` selection,
  `QueueSnapshot` publication, and the transition choreography (tracker
  publish → stop(prev) report → lyrics → start(next) report → prepare)
  over the narrow `EngineDispatch` port (`isLive`/`prepare`/`play`/
  `pause`/`stop`/`seekTo`/`setPlaybackSpeed`) plus `AudioEffectsSession`
  (jvmMain port severing the app-side effects type) and the
  `AudioTrackResolver` seam both platforms see. The 35-member
  `AudioPlayerEngine` queue-surface interface lives beside the manager
  (core:data `playback/`; `AudioPlaybackManager` implements it directly
  — the former app-module delegate and its Koin alias are deleted).
  `DesktopAudioQueueManager` lives here too (jvmMain, relocated from
  apps/desktop — JVM-target-only, `java.awt`'s EventQueue guard has no
  Android bootclasspath twin), its queue mutations all riding its
  private dispatch object; lifecycle, observers, effects pushes and
  release stay direct engine touches (the manager KDoc's declared
  carve-outs). PlaybackFocus edges and observer ordering are
  byte-identical. DECLARED DIVERGENCE: Android's
  `AudioPlaybackManager` stays on its own choreography — its mutation
  sites interleave playlist-owning media3 writes, `queueLoadingJob`
  guards, crossfader and Play-On routing; routing them through the port
  is a redesign (see Deferred designs). Pinned by
  `AudioQueueStateCoreTest` (22 tests over a recording dispatch) + the
  `DesktopAudioQueueManagerTest` suite (jvmTest, beside it).
- **`PeriodicProgressReportLoop`** (commonMain `playback/`, beside the
  reporters) is the ONE periodic progress loop, shared by both local
  players: `AudioProgressReporter` (local audio) and player-video's
  `PlaybackProgressReporter` (video; keeps its position-tracking /
  auto-skip / watched-threshold half) are thin shells over it. The
  loop's invariants live — and are virtual-time-pinned
  (`PeriodicProgressReportLoopTest`) — there, once: CADENCE (every
  `PROGRESS_REPORT_INTERVAL_MS`), PAUSED DEDUP (exactly one paused row
  per unmoving position; a seek while paused reports again), START GATE
  (audio's remote-session check — a gated start still cancels the prior
  loop and re-seeds the dedup) and CYCLE GATE (video's incognito check
  skips a cycle without touching dedup), plus the null-position /
  null-item skips. **`AudioProgressReporter`** (commonMain; the only
  Android member became `positionMsProvider`/`isPlayingProvider`
  lambdas) owns the local-session bookkeeping the loop core deliberately
  does not (the load-bearing stop ordering: stop launched NEVER awaited,
  session id rotated SYNCHRONOUSLY). **`stopAndCancel()`** is the
  teardown entry both managers' hand-rolled tails collapsed into:
  cancels the loop, launches the final stop report fire-and-forget,
  rotates the session id even when no report fires (a declared delta vs
  `reportStopped`'s early return). Must run BEFORE the adapter releases
  its engine (it snapshots through the constructor providers).
  `DesktopAudioQueueManager` constructs `EnginePositionTicker` for its
  position loop — its plain-`delay` loop held resumes up to 2.5 s where
  the ticker wakes reactively.
- **`NowPlayingTracker`** (commonMain, beside `AudioPreferencesReducer`)
  is the sole writer of the six now-playing metadata flows: three publish
  shapes — `publishDetail` / `publishQueueItem` / `publishLocalFile` —
  plus `clear()`, each recording its deliberate divergence (queue
  transitions leave `artistId` untouched because `AudioQueueItem` carries
  no artist id; the local fallback also leaves `albumArtUrl`; `clear()`
  never resets `artistId`), pinned by `NowPlayingTrackerTest`. Both audio
  managers re-expose the flows by reference (same instances), so all 14
  consumers and the widget are unchanged.
- **`AudioEffectsStateCore`** (commonMain `playback/`) is the
  audio-effects STATE machine's one home: abstract, implements
  `AudioEffectsManager` (interface byte-identical), owns the 16 shared
  flows, strength cells, night-mode params, and the per-track ReplayGain
  context + math. Every command flips state then fires a fine-grained
  hook whose DEFAULT is one coarse `onEffectsStateChanged()` funnel —
  desktop overrides only the funnel (→ mpv snapshot push), Android
  overrides the ~15 specific DSP hooks. The out-of-range equalizer-band
  guard is unconditional in the core now — both halves construct guarded
  and the Android half's historical IndexOutOfBoundsException is retired.
  Named state divergences:
  `onEqualizerSettingsChanged(levelsRewritten)`
  (Android's CUSTOM preset pushed nothing), the ReplayGain recompute
  split (Android re-applies with null context = pre-amp; desktop re-folds
  the stored context), and `onVisualizerEnabledChanged` deliberately
  outside the funnel. `AudioEffectsProcessor` implements the interface
  (context-free conformance shims over its context-taking overloads);
  `AudioPlaybackManager` rides `AudioEffectsManager by effectsProcessor`
  class delegation, and its three surviving overrides (replay-gain pair,
  pitch) are the visible answer to "which effects read the queue".
  Pinned by `AudioEffectsStateCoreTest` plus both platform suites
  unmodified.
- **`EffectsCommandCore<S>`** (commonMain `playback/`, beside
  `AudioEffectsStateCore`) is the feature-layer effects CHOREOGRAPHY's
  one home: owns the state `StateFlow`,
  `applyAndPersist(apply, update, persist)` with apply/update synchronous
  and persist fire-and-forget on the injected scope, plus `updateState`
  for mirrors/seeding. The apply-ordering divergence is
  CONSTRUCTOR-ENCODED (`Order.APPLY_FIRST` — player-audio, the DSP
  manager is source of truth; `Order.STATE_FIRST` — player-video, the
  state slice feeds `syncConfig`); KDoc states why flipping either
  adapter onto the other's order is a silent behavior change.
  `VideoEffectsController`'s public surface is byte-identical (its suites
  unchanged); `AudioEffectsController` mirrors the manager flows (moved
  from the VM's init) and `AudioPlayerUiState.effects` is DELETED — the
  screen reads `viewModel.effectsState` (the `SubtitlePreviewController`
  precedent). The audio write path's apply-twice timing (the recorded
  `AudioPreferencesReducer` snapshot-fold blocker) is untouched. Pinned
  by `EffectsCommandCoreTest` interleavings.
- **`AudioSleepTimerController`** (player-audio commonMain) folds the
  four hand-rolled sleep functions; the explicit-pause rationale comment
  lives once. Video's `SleepTimerController` is NOT reused (player-audio
  cannot depend on player-video; video's variant carries video-only fade
  concerns).
- **`PlaylistPickerStateHolder`** (player-audio, beside the VM — the
  `InstantMixStateHolder` shape) owns the playlist-picker choreography
  that lived as 5 hand-synced uiState fields + 4 VM funs: one
  `state: StateFlow<PlaylistPickerState>` + `open` / `dismiss` (fenced
  while adding) / `addTo` (item id captured at entry) / `clearMessage`;
  the VM holds it as `playlistPicker` (3 one-line forwards).
- **`SingleFlight<K, V>`** (jvmShared `concurrency/`) is the
  cache-agnostic single-flight core (mutex-guarded in-flight map,
  epoch-guarded write-back, cancellation ladder); `SingleFlightFetcher`
  is its TtlCache/CacheIdentity adapter; `WatchHistoryRepository`'s
  played-items memo constructs the core directly. `SingleFlightTest`
  owns the ladder invariants (concurrent-join, generation-veto,
  cancellation-ladder re-entry).
- **`CastStateFanout`** (commonMain `cast/`) is the pure per-strategy
  field fan behind `updateCastState`: null = the strategy doesn't own the
  field, the manager keeps its previous flow value;
  `CastStrategyNames` is the one dispatch-name vocabulary. Declared
  divergences pinned: only Jellyfin contributes title/subtitle (Google
  Cast titles ride MediaItem metadata), only the local player contributes
  bufferedPositionMs. **`CastQueryParams`** (`String.withCastQueryParams`)
  sits beside `CastMediaOptions`/`CastStateFanout` (the Android-side
  `MediaItem.withCastOptions` fold delegates; pinned for param order,
  `?`/`&` choice, existing-param preservation, the declared
  `mediaSourceId` exclusion).

## Admin feature (`shared/feature/admin`)

- **`AdminLoad`** (jvmShared, the `LiveTvLoad` shape — since folded into
  core:ui's `loadInto`; the object itself is gone) is the admin slice
  of the load-ladder fold: 10 VMs (Dashboard, Devices, Logs, Plugin
  Detail, Plugins, Stats, Stats Detail, Scheduled Tasks, Users,
  androidMain Plugin Config), both ladder shapes — start → single
  suspend fetch → exactly-one-arm dispatch; settles stay per-VM as
  declared variants (final-update, flavour starts, Dashboard's
  persisted-error try/catch expressed as a `runCatching{getOrThrow}`
  fetch, Logs' parallel pair under one catch). Declared timing
  unification: Plugins/ScheduledTasks' legacy fire-and-forget inner
  launch now awaits. Pinned by `AdminLoadTest` (retargeted to
  `loadInto`, assertions unchanged).
- **`StatisticsMath`** (core/data, pure) owns watch-time breakdown +
  viewing-streak math out of `AdminStatisticsRepositoryImpl`; the watched
  scan rides `AdminStatisticsLabelProvider`. The repo `formatSize` twin
  was KEPT deliberately: it feeds strings persisted to Room (see the
  storage-byte vocabulary below for which UI copies folded and which
  remain private ÷1024 residue).
- **`MediaCleanupScanStateHolder` + `MediaCleanupScreenScaffold`**
  (mediacleanup/): the stale-media / watched-cleanup twins' whole scan
  lifecycle — startScan → detect → observeScanProgress → COMPLETED →
  results-JSON decode → selection → confirm → delete, plus the 3-tab
  scaffold, sort dropdown, select-all row and delete sheet — single-homed
  over a constructor-lambda repository seam; the screens shrink to config
  forms + item cards. Declared deltas: the twins' progress re-collect
  race is FIXED (each scan leaked its progress collector; the chassis
  cancels single-flight), select-all stays an all↔none TOGGLE (not
  `SelectionState.selectAll`'s unconditional select), and the chassis is
  fully localized.
- **`ChartGeometry`** (jvmShared, beside `ChartComponents`): the chart
  file's pure decisions Compose-free and pinned — bar/trend
  label-admission ladders, height normalization, pie sweep accumulation
  (adjacency-safe under animation progress), top-5 legend + percentages,
  `formatNumber`/`formatDuration`. Composables draw only.
- **`PicoConfigHtml`** (commonMain `plugins/`, beside
  `PluginBridgeScript`): the pico WebView builders (`colorToHex`,
  `buildPicoOverrides`, `buildWrappedHtml`) live where CI can reach them;
  androidMain keeps WebView wiring. Pinned by `PicoConfigHtmlTest`.
- `LogsScreen`'s pagination guard is `LogsState.isLoadingMoreActivity`
  (live guard + footer spinner, synchronous early-return in
  `loadMoreActivity`, flag cleared on both settle arms) — the re-entrant
  double-append defect is fixed and pinned.

## Preference stores (core:datastore)

- **`sliceStateFlow` / `dataDegradingToDefaults`** (`SliceStateFlow.kt`,
  internal): the ONE store read-chassis —
  `data.catch{emit(emptyPreferences())}.map(read).distinctUntilChanged()
  .stateIn(Eagerly, seed)` — replacing the ~22 per-store hand copies whose
  corrupt-read arm had drifted into three spellings and two semantics. The
  16+ stores spelling it `.catch { _ -> emptyPreferences() }` (lambda value
  coerces to `Unit`) silently completed, freezing their eager StateFlow at
  its seed with the read projection never running — latent only because
  every seed equals the defaults. Declared policy (KDoc'd): a corrupt read
  degrades to the all-defaults snapshot run through the same read
  projection (per-key clamping/derivation still applies); never throws at
  collectors, never freezes at the seed. 22 stores migrated wholesale
  (21 slice stores + `AppRuntimeStateStore`, which previously had NO arm —
  a failed read propagated uncaught into its eager collector);
  `ArrPreferencesStore` / `SubtitleProviderPreferencesStore` (encrypted-side
  combine/tick shapes) and `HomeDiscoveryStore` (per-user combine) keep
  their projections but ride `dataDegradingToDefaults()`;
  `ExperimentalStore`/`SecurityStore` keep a `sharedPrefs` arm for knob /
  first-persisted reads over the same helper. Unchanged (not preference
  stores, no corrupt-read arm added): `ServerIdentityStore` (session
  state), `WidgetDataStore` (widget I/O buffer), `PinRateLimiter`. The
  facade's dead `sharedPrefs` copy was deleted. Pinned by
  `SliceStateFlowTest` (fake DataStore with a throwing `data` flow:
  degrades to the defaults projection — not the seed — never throws, still
  collectable; store-level via `NavigationStore`).
- **`String?.toEnumOrNull()`** (`EnumPreferenceParsing.kt`, public reified
  inline, never-throws) is the repo-wide seam for persisted-string →
  enum: ~50 former `valueOf` / try-catch / `runCatching{}.getOrDefault`
  / name→enum-map sites across all 14 stores and the core/data
  repositories flow through it with explicit defaults. Declared delta: a
  corrupt persisted enum falls back to the documented default instead of
  throwing during the read projection (a throw trips the store-level
  `.catch { emptyPreferences() }`, wiping ALL preferences); corrupt
  outbox `eventType` drains as `START` (the one replay path that sends
  nothing). Pinned by `EnumPreferenceParsingTest` plus garbage-write
  tests per module.
- **`resetKeys` derivation**: 17 stores replace the hand-written
  aggregate with `PreferenceResetCategory.entries.flatMap(::resetKeysFor)`
  — the union invariant holds by construction. Two justified skips:
  HomeDiscoveryStore (ownership inverted — its `resetKeysFor` DELEGATES
  to `resetKeys`, the live list) and ReaderStore (no `resetKeysFor`; its
  list is the single source for `clearAll()`, which routes through it).
  The drift found in the dead lists was unobservable (production never
  read them).
- **`SliceBinding` fan-out table** (`UserPreferencesStore.kt`, private):
  the backup / per-category-restore / reset fan-outs iterate ONE
  descriptor table (18 rows) instead of three hand-written 19-slice
  fan-outs. Each row names the slice's wire key (`BackupSliceKey`), the
  `PreferenceResetCategory` set whose fields live in it, the read, the
  `KSerializer`, the restore, and the owning store's `resetKeysFor`
  delegation — plus the two optional hooks that capture the only
  per-slice deviations (`restoreSensitive` — SecurityStore's lock
  config, restored just when the caller opts in; `merge` — the six
  co-owned slices' field-level `SliceCategoryMergers` path on the
  per-category import). Adding a slice is one row; decode-or-skip
  forward-compat (an older v2 export predating a slice still imports)
  lives in the row's `decodeOrNull`, not in the slice decoders.
- **`cachedJson` / `PreferenceCodec`**: the cached-JSON decode helper
  with a REQUIRED `CachedJsonNullPolicy` parameter (`MemoizeNull` — the
  majority idiom; `NoMemoOnNull` — VideoPlayerStore's
  legacy-boolean-fallback variant, whose null-raw value rides `onNull`);
  decode failures cache like any result; a composite `cacheKey` covers
  HomeDiscovery's version-stamp union reads. 32 call sites across 12
  stores; WidgetDataStore's `decodedListStateFlow` deliberately stays
  hand-rolled (its `reified` decode cannot cross the non-inline lambda;
  it gained proper per-flow `ParsedCache` fields, reason KDoc'd).
  Pinned by `CachedJsonTest` (counting parse lambdas prove no re-decode).
- **`PreferenceSpec<T>` + `Knob<T>`** (`spec/`, Stage A — machinery +
  pilot): a knob's single declaration at its owning store.
  `PreferenceSearchSpec` is plain data (string resource KEYS,
  `routeKind` id, `isAdvanced`, `PreferencePlatformRule` — no
  Compose/resource/Route types cross into the datastore module); builders
  are only the kinds the pilot uses (boolean/string/custom with a
  store-owned codec — `int`/`long`/`float` slots and an `ANDROID_ONLY`
  rule return with their first real declaration). A knob's `set` IS the
  store's setter (no second write path); `value` rides the store's prefs
  flow. The feature adapter `SettingsSearchBinding.toSettingsSearchItems`
  maps specs → the untouched `SettingsSearchItem` via an
  id→StringResource/ImageVector binding table plus a domain
  routeKind→Route map that fails fast on unbound ids, resource-key drift
  and unknown route kinds. Pilot domain: Experimental (10
  knobs + 5 entries; the three feature toggles are three specs over the
  ONE `enabled_experimental_features` JSON key);
  `ExperimentalSettingsSearchItems` is reduced to the binding table +
  route map + derivation call. `SettingsCatalogScreenContractTest` passes
  UNCHANGED
  (the binding table keeps literal `id = "..."` arguments visible for
  its source scan). NOT yet derived: `resetKeysFor` (Stage B — deriving
  would move `app_language`/`prefer_audio_description` reset ownership
  away from SubtitleLanguageStore's documented split) and most other
  domains. Derived since: the shared projection field-sets (see
  `DeclaredProjectionFields.kt` above), and the legacy `UserPreferences.kt`
  aggregate is deleted outright (see `PreferenceSliceSnapshot`).
- **`directArrEnabled`** (beside `ExperimentalStore`): one extension pair
  (store-Flow + slice shapes) replaces the five hand-copied
  - **Declared projection field-sets** (`DeclaredProjectionFields.kt`,
  internal, beside `PreferenceProjections`): the field lists that more than
  one projection lane consumes are declared ONCE — an explicit values holder
  per domain (`AudioSurfaceValues` 33 fields, `AppearanceCoreValues` 18,
  `appearanceTheme()` for the artwork quad) whose properties read their
  owning slices, with one named-arg assembly per target lane. The two audio
  surfaces (30/38 fields), the appearance core (per-domain + screen lanes)
  and the five former `AppearanceTheme(...)` hand-builds derive from these
  declarations; `PreferenceProjections.kt` keeps only combine/stateIn
  plumbing and single-lane field lists. No reflection — plain accessors
  (R8-safe); every lane keeps its exact return type and combine shape, and
  `DeclaredProjectionFieldsTest` pins each derived lane against a fully
  explicit expected construction. Adding a preference to one of these
  domains = one property in the declared holder + one argument per lane
  that surfaces it (plus the slice field itself) — not a re-copied field
  list per lane.
- **`PreferenceSliceSnapshot`** (core:datastore settings) + the
  feature-side `PreferenceDiffSnapshot` wrapper: the import-preview and
  factory-reset diffs run directly on the 18 slices + runtime + PIN
  lockout. The former `UserPreferencesSnapshotBuilder` (the ~150-assignment
  slices→legacy-aggregate re-mapper) is deleted; the incoming side decodes
  per-slice via `buildPreferenceSliceSnapshotFromBackup` (same lenient
  decode-or-defaults forward-compat). The legacy
  `core/model/legacy/UserPreferences.kt` aggregate is now DELETED (its two
  compile residues — the `UserPreferences.isExperimentalEnabled` extension
  beside `ExperimentalFeature` and an unused import in `feature/details`
  `MediaDetailBody` — are gone with it; the `ExperimentalPreferences` /
  `MainPreferences` `isExperimentalEnabled` helpers and the per-slice
  projections are the live surface, and the legacy-type tests were retired
  with it).
  Diff equivalence is preserved field-for-field (same rows, same order,
  same formatted-value comparison); pinned by `PreferenceSliceSnapshotTest`,
  the rewritten `PreferenceCategoryPresentationTest` and the two VM tests.
- **`PreferenceStores` + `PreferenceSnapshotReader`** (core:datastore
  settings) are the store bundle the projections take and its read-side
  twin: `PreferenceStores` bundles the NINETEEN domain stores, enumerated
  once (here + the Koin single — the `PlayerStores`/`HomeStores`
  construction-seam precedent), and `PreferenceSnapshotReader.snapshotOnce()`
  builds the one-shot `PreferenceSliceSnapshot` behind the factory-reset
  diff (`FactoryResetViewModel` takes reader + editor + label resolver —
  three params). Pinned by `PreferenceSnapshotReaderTest`.
- **Slice-derived, localized reset/import review** (`PreferenceCategoryPresentation.kt`):
  the ~246 hand-written English diff rows became declared `DiffField` rows —
  label resource + slice read + formatter — per category over the snapshot.
  Labels reuse the EXISTING settings-search `ss_*` titles wherever the
  preference has a search row (same wording as the settings screens);
  the residue got 38 `diff_*` keys in the DEFAULT locale only (other
  locales fall back; translators fill later). Labels resolve per snapshot
  generation (`resolveDiffLabels`, one suspend `getString` pass in the VM
  init; injected resolver in tests) and ride `PreferenceDiffSnapshot`, so
  the rendering components keep taking plain strings. Declared delta: the
  five `appRuntimeFields` extras rows keep literal labels — their call site
  is the import-preview screen itself, which passes only the none-label.
  `SliceCategoryMergers` is deliberately NOT derived from the declared sets:
  building `copy(field = if (import) incoming.x else current.x)` chains
  dynamically needs reflection, so the 88 merger branches stay hand-written
  (same rationale as the spec's Stage B skips).
- **`PreferencesEditor` slimmed to its real surface**: `edit`,
  `hashPin`, `verifyPin`, `resetCategory`, `clearAllPreferences` — the
  ~50 one-line named setters had zero production callers (every VM writes
  through `editor.edit { store.setX() }`) and were deleted with their
  routing tests. Same pass removed the `UserPreferencesStore` constructor's
  unused `PreferenceProjections` injection (zero uses in the 686-line
  facade; the KDoc no longer promises slice flows), the dead `readBool`
  wrapper, and `SecurityStore`'s dead legacy-aggregate
  `restoreSecuritySensitive` overload (the slice overload is the wired one).

## Shared UI vocabulary (core/ui + core/model)

- **`PendingConfirmation<T>`** (core/model, beside `SelectionState`) is
  the ONE confirm-dialog pending-item machine behind the former 17 hand
  copies. Pure value + pure folds: `item`/`isPending`, `hold(t)`,
  `dismiss(inFlight)`, `confirm(inFlight): T?`, `clear()`. Invariant:
  one pending item, at most one in-flight action — BOTH dismiss and
  confirm are silent no-ops while in-flight (the dialog stays open until
  the action settles; a refused confirm cannot drop the pending item
  mid-flight). The guard RULE lives in the machine; the in-flight FACT is
  caller-owned (a per-site flag passed into each call —
  `isDeleting`/`isStoppingSession`/`actionInProgress` or a screen-local
  `remember`), never a second machine-held truth. `confirm` GATES but
  never clears: settle timing (success-only clear vs clear-on-both vs
  clear-at-action-start) is a DECLARED PER-SITE ARM named in each site's
  KDoc (Recordings/AdminDashboard failure-keeps-dialog, MediaCleanup
  failure-closes, SyncPlay clear-at-action-start, …). Screen-held
  machines split by how the action runs: FIRE-AND-FORGET sites (non-
  suspend VM commands that launch internally) settle SYNCHRONOUSLY at the
  confirm tap — double-fire structurally impossible, in-flight guard arm
  unreachable; ImportPreview's `All` arm is the exception (a REAL
  screen-local flag gates dismiss while the import runs and resets on
  terminal import events). The visual half is host-owned: `ConfirmDialog`
  rescinds its exit when `confirmLoading` turns on (the panel returns
  showing the spinner) and refuses exit requests (back, scrim tap,
  Cancel) while loading. Deliberately out of scope: the boolean-only ring
  (expressible as `PendingConfirmation<Unit>`, no drift problem) and
  core/ui's `ConfirmState` (composition-scoped closure-payload machine),
  which COEXISTS with a corrected KDoc.
- **`MediaFilterSheet` + `FilterSection` + `MediaFilterDraft`** (core/ui
  components) is the shared filter-sheet apparatus: `FilterSection`
  enumerates the sections and the per-host active sets (library vs
  search), `MediaFilterDraft` is the pure draft record, and the
  `MediaFilterSheet` composable renders both hosts' sheets over them.
  Pinned by `MediaFilterSheetSectionsTest`.
- **`InlineConfirmState` + `rememberInlineConfirm`** (core/ui components)
  is the auto-reset inline confirm window — first tap arms ("Confirm?"),
  second tap fires, arming auto-resets after the shared 3 s constant; the
  button-flavored counterpart of `ConfirmState`. Pinned by
  `InlineConfirmStateTest`.
- **`SelectionState<T>`** (core/model, beside `LibraryFilters`) is the
  one list-selection algebra: `ids` + derived `active`, pure
  `toggled`/`cleared`/`selectAll` — the
  `selectionMode = next.isNotEmpty()` derivation lives once. ArrQueue
  (`String`), Downloads (`String`) and Requests (`Int`) ViewModels store
  one in uiState with derived properties; ArrQueue's queue actions funnel
  through one `runQueueAction`. Declared delta:
  `selectAll(empty)` stays inactive (the old VMs flipped
  `selectionMode = true`, showing a 0-count bar). The three screens'
  `SelectionActionBar` composables remain deliberately separate (their
  visual drift is a product decision — see Deferred).
- **`QuickActionIntake`** (`components/QuickActionIntake.kt`, the
  `HomeQuickActions` shape generalized): the pure
  `quickActionEffect(item, action)` fold over sealed `QuickActionEffect`
  (Play / MarkPlayed / Download / RemoveDownload / OpenDetail /
  ToggleFavorite) plus `rememberQuickActionIntake` + `QuickActionAdapter`
  + `QuickActionIntakeHost` (sheet controller, TV focus key,
  remove-download confirm) — the ~45-line intake block eight screens
  hand-copied (Library/Favorites/Studio, Search, Media/Collection/Person
  detail, OfflineLibrary) is deleted; adapters are navigation lambdas
  only. Declared: `ADD_TO_PLAYLIST` folds onto `OpenDetail` (its only
  offering host always routed it there); Home's own fold stays
  home-shaped. The default `isDownloaded` resolver is one top-level
  `notDownloaded` instance, not a per-recomposition lambda (a fresh
  default churned the remember keys and closed an open sheet).
- **`DateLabels`** (`components/`, beside `PlatformTime`) is the ONE
  date-label seam — the root cause of the twin families was `PlatformTime`
  being jvmShared-only. Shared shapes promoted once (short/long date,
  month-year, weekday header, `relativeDayLabel` Today/Yesterday,
  `oneDecimal`, strict ISO parse transports); java.time actual
  (jvmShared, locale-per-call preserved). calendar/requests/newsletter
  label files are thin façades;
  editor folded only `formatOneDecimal` (now a façade over `oneDecimal`).
  The integer-pattern half moved the other way: core/ui `PlatformTime`
  gained the PUBLIC `formatIntPattern(pattern, value)` — the one renderer
  for every feature's "N days / N minutes / N downloads" translation
  patterns — and the settings/editor `PlatformFormats` expect/actual
  twins for it are deleted (settings keeps its genuinely distinct
  `formatOneDecimal`/`formatTwoDecimals`/`formatSignedInt` expects).
  Pinned by `DateLabelsTest`, `DateLabelsJvmTest` +
  `PlatformTimeJvmTest`.
- **Card footer + decode seams**: `MediaCardFooters.kt` owns the card
  footer's whole meta-line decision as pure values. `bookFooterPercent`
  is the ONE book-footer admission + percent derivation for
  poster/wide/offline cards — the sites had hand-copied it and drifted;
  caller-side `takeIf` guards died with it (non-book overrides can no
  longer produce a label), and `WideMediaCard` gained
  `bookProgressFractionOverride` for parity. `mediaCardFooterMeta` is
  the widened fold: the ENTIRE trailing meta segment (sealed
  `MediaCardFooterMeta` — `BookProgress` / `TimeLeft` / `Runtime`) that
  the PosterCard and WideMediaCard footers previously hand-derived as the
  same `hasValidDuration` / `hasWatchProgress` / remaining-total ladder,
  pasted verbatim in both (down to the "Books never render runtime/time-
  left meta" comment). Precedence lives in the fold: book-in-progress >
  remaining time (unfinished + `hasMeaningfulRuntime`) > total runtime
  (unstarted + meaningful) > nothing; a mid-playback item whose remaining
  math comes back empty renders nothing (no fallback to total — the old
  ladder gated the total arm on NOT having watch progress). The card
  shells are thin renderers and keep their own chrome: the poster card
  always draws the "•" divider, the wide card only after a leading
  subtitle/year text; divider-glyph color and text style stay per-card.
  Pinned branch-for-branch against the old ladder in
  `MediaCardFootersTest` (movie with progress, episode, book, played
  movie, live-ish no-runtime, series container, position-past-runtime,
  sub-minute runtime).
- **Card chrome layer + scaffold diet** (`components/CardChrome.kt`,
  beside `MediaCardScaffold`): `rememberCardChrome` is the ONE
  interaction-chrome implementation under the card family — the focus
  interaction (`rememberJellyFocusableInteraction`), the animated press
  scale, the combined click (`CardChrome.clickModifier`: long-press
  resolves to the caller's handler before the peek's; the
  reduced-motion indication arm is the scaffold's, opt-out per site),
  and the press-and-hold peek wiring end-to-end (bounds tracking +
  release-dismiss ride the chrome because it owns the interaction
  source the peek system keys off). `MediaCardScaffold` consumes it for
  its own Column layout; differently-shaped cards seat on the chrome
  WITHOUT that layout: the library `ThumbCard` — a 153-line hand copy
  with its own press scale, the legacy `focusIndicator()` and a
  redundant `.focusable()` stacked on `combinedClickable` — is now a
  chrome specialization like `WideMediaCard` is of the scaffold (its
  full-bleed scrim, overlaid title and 3dp Material
  `LinearProgressIndicator` stay local: layout-shaped, and forcing them
  through the chrome would change visuals). Declared: ThumbCard's old
  copy stacked an UNANIMATED 0.95 `graphicsLayer` on top of the animated
  `pressScale` (compound 0.95×0.95 at full press, snap-then-settle); the
  reseat keeps only the animated 0.95 (PosterCard parity the comment
  always claimed). The details `EpisodeCard` (horizontal
  thumbnail+metadata row) reseats with its historical values as chrome
  parameters — 0.96 press scale on the fast effects spec, scaling even
  under reduced motion (`pressScaleOverridesReducedMotion`), 1.03
  nominal focus scale, smooth16 indicator — and its watch-progress bar
  plus the scaffold's render through the shared
  `MediaCardProgressOverlay` (the track arm is per-site). Deliberately
  NOT reseated: `CompactEpisodeRow` (its 0.98 full-width-row scale is
  its own value) and the Seerr episode row (static, non-interactive —
  no chrome to share). Scaffold interface diet: the five play knobs
  (`onPlayClick`/`playButtonDominantColor`/`playButtonSize`/`playIcon`/
  `playIconContentDescription`) fold into one `PlayAffordance?` and
  `scrimBrush`/`scrimHeight` into one `CardScrim?` — nullable knobs
  resolve to the historical defaults at render (theme/string resources
  need composition); 24 → 19 params with the variant public interfaces
  unchanged. Strip chassis: `FocusRestoringItemRow<T>` (core/ui tv, the
  `HomeItemRow` slot pattern) is the ONE LazyRow + `focusGroup` +
  `tvFocusRestorer` shell — the four hand-rolled Seerr detail rows
  (seasons/cast/videos/similar) fold onto it. The remaining fork is
  documented, not deleted: the Jellyfin seasons/episodes rows stay on
  `TvFocusableItemRow` because their TV behavior depends on its on-enter
  grab, focused-index restore and D-pad cache window (swapping would
  change D-pad behavior), and the Seerr season chip (poster + selection
  border/scrim/chevron) vs the Jellyfin split-button tab remain separate
  widgets — a visual fork, not a chassis one.
- **`episodeCode` — the ONE plain-string SxxExx derivation**
  (`components/EpisodeFormatting.kt`, under `episodeContextLine`):
  `episodeCode(seasonNumber, episodeNumber, padded, separator)` renders
  the pair with UNIFORM padding — tight `S01E01` (the default, the
  context line + downloads sheet) or bare `S1E1` (`padded = false`, the
  player chrome + newsletter rows) — plus the single-number legs
  (`E01`, `S1`), null with neither. The card family's MIXED style
  (bare season, padded episode) is its sibling `episodeCardCode`:
  `S1 E01` spaced on `EpisodeChip` and the PosterCard footer
  (`separator = " "`), `S1E01` tight in WideMediaCard's subtitle — the
  two share one leg format, and the split is what keeps every adopted
  site byte-identical to its former hand copy. It exists because
  `episodeContextLine` (the declared SSOT) is `AnnotatedString`-typed
  and plain-string consumers could not cross that seam — eleven sites
  had re-derived the ladder and drifted on padding and null-legs.
  Adopted (leg guards that skip a leg stay call-site-local): `EpisodeChip`,
  the PosterCard footer, WideMediaCard's subtitle (code only when a
  season is present), details' `DownloadDetailsSheet` context (code only
  when an episode number exists), and the newsletter's
  `episodeSeriesSubtitle` helper (unpadded `Name S1E2`, shared by
  ContinueWatching ×2 + RecentlyAdded). The player chrome's duplicate
  pair CONVERGED instead of being preserved: `episodePlayerSubtitle`
  (beside the core) renders `Series · S1E5` for both
  `PlayerSessionManager.buildEpisodeSubtitle` (overview fallback stays
  VM-side) and `NextEpisodeOverlay` — the two buildStrings had already
  split on the blank-series-name edge (the VM suppressed blank names,
  the overlay rendered a stray separator after them); both now share
  the one rule. `episodeContextLine` itself delegates its tag to the
  core. Pinned in `EpisodeFormattingTest`.
  `ImageBitmapFactory` (`argbPixelsToImageBitmap` +
  `decodeImageBytes(bytes, maxEdgePx)`) is the PUBLIC bounded-decode seam
  (player-book's parallel `BookDecodeSeam` expect/actual trio was
  deleted; only the jvmMain `BufferedImage.toImageBitmap` PDFBox adapter
  remains).
- **`ErrorBanner` + `SectionHeader`** (`components/
  ErrorBannerSectionHeader.kt`, beside `LoadingErrorComponents`) are the
  two new shared seams: `ErrorBanner(message, onDismiss, dismissLabel,
  modifier)` is the inline dismissible banner for failed in-place
  operations (the admin plugins and editor hand-copies folded; the
  dismiss label is a caller parameter because features localize it
  differently; the dismiss button carries the TV focus treatment —
  `rememberTvFocusState` + `tvFocusIndicator`), companion to
  `ErrorScreen` (whole-screen unrecoverable vs in-content partial
  state). `SectionHeader(title, contentPad)` is the grouped-list section
  label (livetv recordings/schedule folded; deliberately not focusable
  on TV — it is a label, not a control). arrqueue's private `ErrorState`
  folded onto `ErrorScreen`, restoring its TV focus requester.
- **`loadInto(start, fetch, onSuccess, onFailure)`** (`viewmodel/
  LoadInto.kt`, beside `JellyPlayViewModel`) is the ONE load ladder —
  raise the caller's start flags + clear the error, run the single
  fetch, dispatch the `Result` to EXACTLY ONE arm, return the fetch's
  own `Result` after its arm (the leg-gate); `suspend` arms, no
  try/catch (cancellation never masked). The five per-feature slice
  objects are deleted and their call sites route through it directly —
  the fold's semantics and declared non-converts are recorded in
  Deferred designs.
- **`SeerrStatusDecisions.kt`** (core/model, beside the Seerr state
  models) is the enum-level half of the request-status mapping:
  `SeerrRequestItem.effectiveMediaStatus()` (the 4K-aware pick — a 4K
  request reads `media.status4k`, everything else `media.status`) plus
  `SeerrMediaStatus.isAvailable` (AVAILABLE or PARTIALLY_AVAILABLE —
  partial counts as present) / `isPending` / `isProcessing`. The
  requests feature (`RequestListItem`, `RequestDetailBottomSheet`)
  carried the mapping verbatim and `SeerrDetailScreen` re-derived the
  predicates a third way — all now fold through, along with the two
  later strays: core/ui's `SeerrMediaCard` (which had re-merged
  PROCESSING into a local `isPending`) and `SeerrDetailViewModel`'s
  `resolveJellyfinItemId` availability gate. Intentional behavior fix
  riding that adoption: the card now renders PROCESSING as its own
  in-flight state — `seerrStatusBadgePresentation`
  (core/ui, beside the card) maps status → (glyph, color): ✓ available /
  ⟳ processing (info blue) / ⏳ pending / → requested — the card-badge
  counterpart of requests' `requestStatusPresentation` and the detail
  screen's action-button `when`, which both already showed processing
  distinctly; previously the card showed a downloading item under the
  pending hourglass. The label/color half otherwise stays feature-local:
  requests' `RequestStatusPresentation.requestStatusPresentation` (its
  strings are the requests compose-resources, invisible to core modules
  and sibling features). SeerrDetailScreen's `formatRatingOneDecimal` /
  `formatUsCurrency` / `releaseTypePresentation` moved beside it to
  feature/details' `SeerrDetailUtils`.
- **`seerrCardClickHandler(...)` + `ProvideSeerrCardPrefetching`**
  (core/ui `components/SeerrCardLoadingState.kt`): the route-agnostic
  Seerr card click choreography — startLoading → prefetch detail →
  stopLoading → navigate — written once (both the `SeerrHorizontalSection`
  and `SeerrItemsRow` cascades folded), and the prefetch
  `CompositionLocal` provider construction deduped behind the
  `ProvideSeerrCardPrefetching(prefetchDetail, content)` composable.
  Deliberately not migrated: home's `SeerrDiscoverRow` variant (provides
  an extra local). Known remaining occurrences: auth's ServerListScreen /
  UserSelectionScreen and music's `ArtistDetailScreen`.
- **`BiometricAuthHelper`** (core:ui androidMain `components/`) is
  consolidated behind one prompt engine: `launchPrompt(activity, spec, …)`
  over `PromptSpec` (authenticators / title / negative button / crypto /
  cancellations), ONE cipher factory `initCipher(CryptoBinding)` — the
  `KeyPermanentlyInvalidatedException` recovery written once — and ONE key
  factory `createAuthKey(CryptoBinding)`; the three public `authenticate*`
  variants are thin builders, public surface unchanged. Cancellation
  classification is extracted pure, pinned by
  `BiometricAuthHelperCancellationTest` (core:ui androidHostTest).
- **Meaningful-runtime + watch-progress predicates** (core/model
  `MediaItem.kt`): `hasPlaybackPosition` (non-null, > 0 ticks),
  `hasWatchProgress` (+ `!isPlayed` — the "time remaining" predicate),
  and `hasMeaningfulRuntime` (`runTimeTicks > 0 && !SERIES && !BOOK` —
  the duration-row gate) are the ONE ladder for every "show progress /
  time left / a duration" decision. Adopted by both card footers (via
  `mediaCardFooterMeta`), HomeHero's resume affordance, and the
  newsletter rows (ContinueWatching remaining-time + duration chips,
  CuratedPicks + RecentlyAdded duration chips, SectionListScreen
  progress bar). Declared drift deltas on adoption: the newsletter
  duration rows had excluded only SERIES (or neither) — a book with
  stray runtime ticks would have rendered a bogus duration there; those
  rows now exclude BOOK too, and the ContinueWatching remaining-time
  label additionally dropped explicit-zero positions (which used to
  render the full runtime as "Xm left") and finished items (which are
  not "continuing").
- **Storage-byte vocabulary** (core/model `ByteFormatter`): a
  `Long.toStorageBytesValue()` band table backs `formatBytes`; the four
  drifted UI copies (Logs, PhotoViewer, DetailDownloadDialog, ArrQueue)
  migrated onto it. Declared deltas: the ÷1000 sites joined the ÷1024
  house convention; Logs' integer-KB and ArrQueue's `%.0f KB` collapsed
  to the one-decimal band table (500 B showed `0.5 KB`, now `500 B`);
  Logs gained the GB band; DetailDownload keeps localization via a
  per-unit `localizedStorageSize` wrapper. Known residue:
  core/ui's `FormatFileSize.kt` (SI, own test), PlaybackInfoOverlay's
  private copy, the player stats overlay's private `formatBytes`
  (integer-rounded KB band, no GB band) and `MediaCleanupScanCore`'s
  private `formatSize` (integer-divided KB, locale-sensitive
  `String.format`; its empty-for-nonpositive rule lives in the function)
  — the two adoptions onto the band table were REVERTED as
  non-output-equivalent (one-decimal KB and the GB band change on-screen
  text: "2 KB" → "1.5 KB", and sizes ≥ 1 GB moved between bands), so
  both remain remaining private ÷1024 copies — and the player stats
  overlay's `formatBitrate`/`formatBandwidth` ladders (SI-decimal ÷1000
  NETWORK RATES, not storage sizes — deliberately local) — different
  surface, opportunistic.
- Lazy lists carry `contentType` lambdas (~8 screens) so recycled item
  types don't cross-compose.

## Editor feature (`shared/feature/editor`)

**`EditableItemMetadataForm`**: the editor's ~30 metadata fields are
enumerated ONCE (inbound load map, outbound save map and dirty detection
all derive) — one form value (`runtimeMinutes` the declared string-edit
twin of outbound `runtimeTicks`) plus `MetadataEditSession(value,
original)`; `isDirty` is structural equality (`computeDirtyHash` is
DELETED); `EditorUiState` embeds the form as one slice; `updateField` is
form-typed. The drift-class pins the old shape could not express are
reflection-enumerated over the form's OWN properties (the
`ResetCoverageGuard` precedent, no kotlin-reflect): a NEW field
auto-fails until wired — fromDetail→toEditable round-trips every field,
mutating any field individually trips dirty, untouched stays clean.
Declared deltas (KDoc'd, none UI-reachable): person id/primaryImageTag
edits trip dirty; providerIds compare order-insensitively; pre-load
edits no longer dirty.

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
- **`build-logic` convention plugin (script-plugin form)**: type-safe
  accessors do not generate for source-set manipulation performed from a
  precompiled script plugin's transitive classpath (`KotlinSourceSet
  with name 'jvmMain' not found` persisted through every withPlugin-guard
  variant) — empirically rejected and fully reverted. The recorded
  viable shape — a class-based convention plugin in a
  `build-logic-convention` module (buildSrc-style, registering source
  sets through the `KotlinSourceSetContainer` API directly) — has now
  LANDED (2026-09-21) as the `jellyplay.kmp.library.base` +
  `jellyplay.kmp.library.compose` plugins; all 34 KMP library modules
  are migrated onto them. Do not re-attempt the script-plugin form.

## Deferred designs

Designed but deliberately not landed — recorded so future work neither
re-derives the designs nor lands them casually.

- **Audio playback snapshots**: `AudioPlaybackManager`'s flow members (47
  today, 112 public members total, 14 consumer files) fold into
  now-playing / queue / effects / connection snapshots per the
  `SeerrRequestStateHolder` pattern. Deferred because the consumer files
  across app/widgets/tile rewrite onto it at once and nothing pins
  current behaviour — do it when audio/cast churn resumes, tests first.
  The now-playing slice already has its core (`NowPlayingTracker`, the
  audio cores section), the widget push race guard is pure
  (`WidgetPushSnapshot` + `sameRenderAs`/`sameNonPositionRenderAs`/
  `shouldPushPartialPosition`, pinned by `WidgetPushSnapshotTest`), the
  mini-player wiring is one `AppMiniPlayerHost`, and
  `NowPlayingWidgetPolicy` (the responsive layout ladder +
  position/seek/progress math + metadata fallbacks) completes the widget
  package's tests-first base. Still blocking: the effects toggle path
  applies twice (VM immediate apply + the `AudioPreferencesReducer` diff
  — the reducer tracks only the last store slices, not processor state;
  fixing means a processor-state read-through or dropping the immediate
  apply, a behaviour-timing change deserving a listen-pass) and the fold
  itself (the consumer rewrites onto snapshots).
- **Audio-queue chassis Android adoption**: routing
  `AudioPlaybackManager`'s mutations through the `AudioQueueStateCore` /
  `EngineDispatch` port (see the audio cores section) — its mutation
  sites interleave playlist-owning media3 writes, `queueLoadingJob`
  guards, crossfader and Play-On routing, so adoption is a redesign,
  not a fold. Tests first.
- **Settings category-merge module** (`SettingsCategoryMerge`): one
  `merge(category, incoming, current)` interface in core/datastore with
  legacy v0/v1 and factory-reset as adapters, folding
  `ImportPreviewViewModel.mergeForCategory` (~250 lines) and the
  duplicate snapshot builders. Deferred: backup/restore is the
  destructive path and deserves a dedicated session with the diff UI in
  the loop.
- **`SeerrDetailPresentation` fold**: the movie/tv union is coalesced at
  ~17 sites through 3 nesting levels of `SeerrDetailScreen` (~600
  deletable lines incl. 4 near-verbatim chassis copies from the
  media-detail side: backdrop `DetailBackdrop`, trailer dialog,
  `VideosSection`, Seerr row). Design: one pure presentation fold beside
  the `withPendingRequest` precedent; sections take a single value.
  Deferred: ~15 composable-signature changes through a 2213-line file
  whose regressions are visual-only — deserves a session with screenshot
  verification.
- **Side-load id contract** (the `SideloadedTrackIdRegistry` design,
  executed in pieces): the id grammar (`external:`/`offline:`/`provider:`/
  `local:`) is constructed in `TrackSelectionHelper`/`SubtitleManager` and
  matched in `TrackSelectionPolicy`; "keep the caller id alive across the
  engine's track republish" folded where it could — BOTH mpv engines run
  the commonMain `MpvTrackCatalog`/`MpvSubtitleSideLoadPlan` pair
  (player-contract `engine/`, pinned by player-video jvmTest
  `MpvTrackCatalogTrackSelectionContractTest` + the pair's own commonTests)
  and Exo keeps its commonMain `StableSideloadedTrackId` codec. VLC's
  androidMain spu-diff + map is the one implementation no JVM test reaches.
- **Feature-VM load-ladder fold** (landed 2026-09-20): the recorded
  `loadInto`-shaped helper shipped in core:ui next
  to `JellyPlayViewModel` — one signature covering every folded shape
  (start → suspend fetch → exactly-one-arm fold, the fetch's own `Result`
  returned after its arm so a choreography can leg-gate, `suspend` arms
  so SyncPlay's join continuation rides the same ladder). The five
  per-feature slices (`LiveTvLoad`, `RequestsLoad`, `CalendarLoad`,
  `SyncPlayLoad`, `AdminLoad`) are deleted; their 19 call sites route
  through `loadInto` directly, and their pinned tests were retargeted
  with assertions unchanged. Settle timing stays per-VM as declared
  variants (per-arm vs final-update vs flavour starts vs Recordings'
  clearing failure arm) — no SettleMode switch, because the helper owns
  no settle in any slice; it owns only the guard ladder and the fold
  routing, and VMs map Success payloads into their own state. Two
  hand-rolled ladders whose behaviour exactly matched a supported shape
  converted: ArrQueueViewModel.refresh (the calendar shape — empty
  success arm, collector-fed list) and WatchProgressHeatmapViewModel (its
  try/catch rethrowing cancellation became a
  `runCatchingRethrowingCancellation` fetch, the Dashboard/Logs
  precedent). Declared non-converts (a fold would change behaviour):
  ImportPreviewViewModel (mutex guard + `finally` settle — settles on
  cancellation too), LicensesViewModel (success-path error arm + parse
  inside the try), MusicHomeViewModel (already on the
  DeferredFetchCoordinator chassis), ManageSeriesViewModel /
  SeerrDetailViewModel / PhotoViewerViewModel (multi-leg choreographies
  with success-path failures, per-leg error text, or a null-item leg
  gate the Result cannot express). Pinned by `LoadIntoTest` (core:ui:
  guard sequence, settle modes, returned-Result gate, non-suspend
  function-reference arms, cancellation transparency). The editor slice
  executed: `EditorLoad`/`EditorLoadTest` are deleted, `EditorViewModel`
  rides `loadInto`, pinned by `EditorLoadIntoTest`.
- **`DetailViewModel` intent fold** (landed 2026-09-21): sealed
  `DetailUiEvent` (jvmShared, beside the VM) + one `onEvent` funnel — the
  27 command funs went private behind it byte-identically, `MediaDetailScreen`
  dispatches events through the unchanged `DetailContentCallbacks` lambda
  bodies, and `DetailViewModelOwnershipTest` NEW-pins the 22-member
  read/helper/funnel surface (ManageSeries template, walker retargeted to
  `src/jvmShared/kotlin`). The fold also executed the repo's dead-surface
  rule: `playLocalTrack` (zero producers) died and took the orphaned
  `DetailAudioPlayback` DI seam with it (interface, both platform defs,
  the app-side interop adapter), as did the test-only per-item
  `toggleFavorite` and `markEpisodePlayed` (the re-entry pin re-homed on
  the production `MarkRowItemPlayed` path). The `DetailContentCallbacks`
  adapter itself stays — it is the screen's section-capability bundling,
  not a forwarding stratum.
- **`TrickplayPreviewSource`** (player-video): "fetch a trickplay
  thumbnail for this position?" is a 3-way split — seek-lane gate,
  gesture-lane gate (info-null check inside the collect body), and the VM
  prefs double-check — with the 4-step fetch choreography hand-copied in
  two `snapshotFlow` collectors. Design: a constructor-lambda controller
  owning ONE gate predicate, the fetch, and per-lane clear/linger (seek
  clears immediate, gesture lingers 1 s). Deferred: overlay-timing
  regressions are visual-only; needs device eyes.
- **`SubtitleStyleController`** (player-video, landed 2026-09-18): the
  recorded design shipped (two-step: mirror write VM-side first, then
  the move) — the subtitle style/delay/dialogue-boost write
  choreography sits behind constructor lambdas beside the other player
  controllers; `SubtitleStyleControllerTest` pins the
  offsetMs-never-persists invariant (the in-memory offsetMs is the
  per-item resolved delay and must never persist into the global store)
  and the debounce; god-count ratchet stays 3.
- **Settings/Library/Playback/Appearance section hosts**: `SettingsScreen`'s
  root composable holds ~1150 lines, `LibraryScreen` ~1270-line body,
  `PlaybackSettingsScreen` ~1565 (the largest undocumented one),
  `AppearanceSettingsScreen` ~1050. Design: a section-list seam matching
  `SETTINGS_ENTRANCE_SECTIONS`, one private composable per section.
  Deferred: composition-shape only, zero behaviour — do settings +
  library together, never bundled with behaviour changes.
- **`PageAppender`** (core:ui, landed 2026-09-18): the drifted
  append-page vocabularies (Requests guarded on `isLoading`, StatsDetail
  on `!isLoadingMore && hasMoreItems`; the Logs defect is FIXED — see
  the admin section) ride one stateless appender beside
  `JellyPlayViewModel` owning the in-flight guard, `hasMore`, and
  page-index/skip math — deliberately STATELESS (sites keep the
  ui-state flag the screen renders). Adopted by `RequestsViewModel`
  (skip math + guard) and admin's `UserStatisticsDetailViewModel`;
  pinned by `PageAppenderTest` plus per-site interleaved-completion
  tests (the suppressed tap bumps nothing, a completed load owns the
  state, terminal at totalPages).
- **SelectionActionBar unification**: three per-screen bars
  (arrqueue/downloads/requests) share anatomy but have drifted visually
  (corner radius, container color, icon-vs-text buttons, approve/decline
  placement); downloads adds `has*` enable flags computed in-screen.
  Deferred: pixel changes are a product call — screenshots first.
- **Pull-to-refresh spinner policy**: `PullToRefreshBox` is shared (good)
  but "when does the spinner show" is re-decided per call site — livetv
  Series spins on cold load (full-screen blank + spinner), music Albums
  and calendar guard with "have content". Deferred: pixel-visible product
  decision (the livetv cold-load spinner is probably wrong, but that's a
  call, not a fold).
- **Mood/Smart generated-playlist state idiom** (landed 2026-09-17): the
  recorded `GeneratedPlaylistState` design shipped — one snapshot holder
  (generate pipeline + `"custom-"` id convention + clearGenerated/playAll)
  now owned by both VMs, per-kind filter/sort staying pure adapters; state
  is snapshot-backed, not flows, and errors stay `String?` (screens read
  plain getters in composition and render the error verbatim).
- **`DetailContentBody` section admission** (`MediaDetailBody.kt`): which
  sections render in what order is an inline mediaType × origin ×
  capabilities decision table inside a ~935-line composable — the last
  untested decision surface in the details screen tree
  (`SeasonsSection`'s 24-parameter interface is the same hand-splicing
  `DetailContentState` was built to avoid). Design: a pure
  `DetailSectionPolicy` + the state bundle threaded whole. Deferred: the
  sequencing blocker is gone (the `DetailViewModel` intent fold landed
  2026-09-21), but the work is composition-shape with pixel-visible
  regression risk — still deserves device eyes, do not batch it.
- **User-feedback conveyor completion** (the VM-POSTS half): the seven
  Messenger trios are gone (features read commonMain
  `LocalUserMessageBus` directly; see Navigation destinations). What
  remains deferred: the per-feature message seals (screens still forward
  through their own channels in arrqueue/downloads/livetv/settings — 10
  seals, 6 with identical `asText()` collapses), the two private buses
  (music's, player-video's), and the per-screen SnackbarHostState sites
  (SyncPlay, Newsletter, UserDetail, ManageSeries, both players). The
  shared bus's `UiText.Resource(args)` already covers every seal's shape.
  Presentation is the blocker: several screens own their
  SnackbarHostState, and moving VM posts onto the
  shared bus changes what the desktop shell renders. Design: land per
  feature (VM posts `UserMessage` with resolved `UiText`), starting with
  a feature whose screen already defers to `UserMessageHost`; needs a
  per-shell presentation mapping decision first — deserves the grilling
  loop, not an autonomous batch.
- **Settings row-admission functions** (landed 2026-09-17): the recorded
  design shipped — pure per-group admission totals beside
  `SettingsScreenGroups` (`SettingsScreenRowAdmission.kt`: storage cache/
  downloads, playback player/advanced-video/engine-prefix, notification,
  language trio/subtitles, appearance library, security; the audio
  `audioScreenRowTotal` idiom extended), each screen feeding
  `SettingsItemList(total = …)` from them while the core container's
  auto-index owns the per-row index — the hand-incremented `*Idx++`
  counters and inline predicate counts are gone, rendered output
  byte-identical, per-function totals + retired-counter ratchet pinned in
  `SettingsCatalogScreenContractTest`.
- **`Resilient*` wrapper deletion** (landed 2026-09-17): the five
  pass-through wrapper modules (~504 lines — Seerr/Sonarr/Radarr/Tmdb/
  SubtitleProvider) and their DI indirection are deleted; retry moved
  into each family's request funnel. jvmShared rides
  one execute chassis — `HttpExecutor` (`core/network/api/`, internal)
  with the member vocabulary `parseJson` /
  `parseUnit` / `executeForText` / `executeForCookie` over an `Options`
  record that carries the genuine per-family divergences (error shapers,
  Retry-After capture, empty-body shape) as declared data — it also
  absorbed the former `ApiResponseParsing` ladder. Retry is one
  declared count, `HttpExecutor.MAX_RETRIES` (4): Arr/Seerr set
  `Options.retryHttpCalls` on the chassis, `SubtitleHttp` wraps its own
  funnel (moving UP from 3 — the former unpinned divergence resolved by
  declaring it), TMDB wraps `tmdbFetch` — in-funnel retry is now one
  idiom. GitHub/LrcLib never had wrappers: their
  executors leave the flag off, keeping direct-construction semantics.
  The formerly deferred semantic check held — every Seerr funnel
  (parse/text/cookie) is a single HTTP call per attempt, so call-level
  retry IS method-level there. Pinned by `HttpExecutorTest` + the
  per-impl retry suites (`TmdbApiClientImplTest`,
  `RadarrApiClientImplTest`).
- **Small folds (opportunistic only)**: the three
  `*SecureCredentialsStore` classes (Arr/Seerr/SubtitleProvider) are
  three get/put/clear + memo copies over `SecureKeyValueStorage` with an
  inconsistent memo policy — fold onto a keyed-credentials core only if
  touched for other reasons (field sets genuinely differ; the deletion
  test only marginally concentrates).
- **`ReaderScaffold`**: the paged/reflowable renderers hand-assemble the
  same chrome shell (~8 corresponding wiring blocks: top bar, dim
  overlay, bottom bar, BookmarksSheet, dismiss-then-open choreography;
  the jump split is the deliberate divergence). Design: a slot-composable
  taking the content core + per-format chrome params; the ReaderChrome
  bottom-bar parameter funnel (16 params) folds into it naturally.
  Deferred: composition-shape only with pixel-visible regression risk
  across two full renderers — do with device eyes, never bundled with
  behaviour work.
- **`STRATEGY_LIBVLC` cast strategy** (resolved 2026-09-21, deleted):
  `CastStrategyNames.LIBVLC` and the wholly unregistered
  `LibVlcCastStrategy` (142 lines, zero callers, no persistence path could
  hold the value) are gone; the cast transport registry contains exactly
  its real adapters (Google / DLNA / Jellyfin-remote). The
  `CastStateFanoutTest` unknown-name arm survives on the
  `"custom-strategy"` fixture.
- **God-ViewModel cohort funnels** (cohort COMPLETE 2026-09-21): Library /
  Search / Editor / ManageSeries / Newsletter / AudioPlayer / Detail
  ViewModels all ride sealed-intent funnels (`HomeViewModel` `onEvent`
  precedent). The AudioPlayer fold (2026-09-21) landed `AudioPlayerUiEvent`
  (45 events) over a 1:1 rename of the former 55-command surface, ratchet
  81 → 27; the fold DELETED rather than enshrined the command funs with no
  production caller (`onCastDisconnected` no-op; cast
  play/pause/seek/volume — the screen drives `castController` directly;
  crossfade/gapless/replay-gain-pre-amp — the audio settings screen owns
  those stores and `AudioEffectsController.seedForPlayback` re-applies
  them at track start; `stopPlayback`; the end-of-episode trigger —
  fired by the platform queue managers themselves, Android on track end,
  desktop on queue exhaustion; the orphaned `AudioEffectsController`
  apply-and-persist twins for those three axes died with them). The
  editor fold's residual died the same day: the 11 ImagesTab/SubtitlesTab
  delegate funs are gone (both tabs dispatch `EditorUiEvent`), ratchet
  16 → 5. The Detail fold (2026-09-21) landed `DetailUiEvent`
  (jvmShared, 24 events after the dead-surface cut) over the
  MediaDetailScreen/DetailViewModel pair, ownership ratchet NEW at 22
  (walker retargeted to `src/jvmShared/kotlin`); its dead-surface cut
  removed `playLocalTrack` + `DetailAudioPlayback` (the DI seam's only
  caller — interface, both platform no-ops/actuals, app-side
  `AppDetailAudioPlayback` adapter and Koin def all deleted), the
  per-item `toggleFavorite` overload, and the test-only
  `markEpisodePlayed` placebo (its re-entry pin survives as
  `markRowItemPlayed_reentryKeepsTheNewWatchedState`, the production
  path). The member-count ratchets are in place (one `*OwnershipTest`
  per VM, baseline = current counts, never-raise).
- **Cancellation-safety sweep, ratchet-invisible class** (2026-09-21):
  ten bare `runCatching` sites wrapping SUSPEND calls inside lambdas —
  invisible to `BareRunCatchingRatchetTest`'s `suspend fun` scanner —
  converted to `runCatchingRethrowingCancellation`:
  BookReaderViewModel ×5 (annotations writes), ReaderPreferences ×2
  (`setLastCfi`, the `write()` persist leg — its `finally` still lowers
  the in-flight CAS through cancellation), ReaderProgressReporter,
  HomeViewModel's `homeLayoutProvider` (corrupt-blob degrade preserved,
  cancellation now propagates), EditorViewModel ×2 (`EditorPickedFile.
  readBytes`). feature/home gained the `:shared:core:concurrency` dep.
- **Reader long tail** (speculative): the typography pref axis still
  bounces the store slice → `EpubAppearance` → JSON → reader.js
  `pending` chain (the `EpubAppearance.from(snapshot)` snapshot→typed
  factory landed), `ReaderPreferences` keeps a 20+ setter wall around
  its genuinely deep write-through core, and `MiniJson` (~140 lines)
  remains vendored in the feature module. The annotation swatch palette
  mirror is pinned (`EpubProtocolMirrorTest`). Fold opportunistically on
  the next touch of each file.
- **VM-owned reader session**: invert `attachReaderSession` — the VM
  constructs `ReflowableReaderSession` (its constructor is pure
  lambdas), the screen renders its state, and the nullable
  `readerSession` port + the attach `LaunchedEffect` die. Deferred
  because the back-channel is single-site (deletion test: nothing
  replicates across callers), the 43-member ratchet is pressure working
  as designed, and the clean shape requires the SESSION to own
  `ReaderSheetStack` (currently screen-remembered) — compatible with the
  recorded "VM holds no sheet state" ruling but still an ownership move.
  Land with the next reader behaviour change, design in hand.
- **Unpicked candidates** (named during reviews, no design recorded):
  MediaDetailScreen dialog coordinator; signed-out auth shell.

## v0.11.1 hardening wave (2026-09-22)

The staged hardening wave — eight new architectural surfaces, each wired
both shells and pinned:

- **Update security**: `GitHubRepoAllowList` (core/network
  jvmShared) is the compiled-in owner+repo pin; four fail-closed gates
  (final post-redirect endpoint, html_url + per-asset, cached-info
  re-verify + download-redirect landing, desktop browser handoff) throw
  `UpdateSecurityException` before any feed-controlled URL drives a
  download or browse. Amends `docs/adr/desktop-auto-update.md`
  (implementation addendum there; design: `scratch/mpv-shim-implementation-plan.md`
  — jvmShared, not the recorded commonMain, because every
  enforcement point is JVM). Pinned by `GitHubRepoAllowListTest` +
  `GitHubReleasesApiImplTest` fail-closed arms.
- **Client certificates (mTLS)**:
  `ClientCertificateManager`/`ClientCertificateProvider`/`ClientCertificateFacade`
  (core/network jvmShared) own the installed cert/key/CA triple in the
  app-private cert dir — import normalizes PKCS#12 vs PEM pairs, the
  key-manager cache stamps (mtime,length,content) and fails closed when
  enabled-but-missing, `SelfSignedTrustManager` merges the custom-CA
  anchoring with the self-signed grant short-circuit. The settings
  surface is `ServerManagementScreen` + `ServerManagementViewModel`
  (certificate messages are the typed `CertificateUserMessage`, resolved
  to resources by the screen — the `PrivacyUserMessage` pattern) over the
  `CertificateFilePicker` expect/actuals (SAF on Android, AWT on
  desktop). Pinned by `ClientCertificateManagerTest` +
  `SelfSignedTrustClientAuthTest`.
- **Render profile + Anime4K**: `MpvConfigMapping`
  (feature/player-video commonMain, public for the desktop adapter) is
  the ONE ordered mpv pair list both engines apply — runtime transitions
  are diff-then-write over `lastApplied` maps, and EVERY owned key is
  explicit (tone-mapping AUTO and interpolation-off write mpv defaults
  so preset→AUTO / on→off transitions reach the core instead of sticking
  until restart). `RenderProfileResolver` + `SessionRenderState` fold the
  per-item/series override (`ItemPlaybackPreference.renderProfile`,
  migration 56→57) over the global slice; the sheet is
  `components/RenderSheet`. Desktop installs the Anime4K v4.0.1 pack at
  startup (`Anime4KShaderInstaller`, third-party notices in packaging).
  Pinned by `MpvConfigMappingTest`, `RenderProfileResolverTest`,
  `Anime4KShaderInstallerTest`.
- **`EngineConfigDelta`** (player-contract commonMain `engine/`, beside
  `MpvConfigMapping`): the pure slice-diff between two `EngineConfig`
  values — both mpv hosts switch on its flags in `onConfigChanged` and
  write only what moved (the two hand-mirrored ladders had drifted), each
  keeping its own `lastApplied*` diff caches and dispatch threading.
  Pinned by `EngineConfigDeltaTest`.
- **Track-language rules + remembered codec (56→57)**:
  `TrackResolutionEngine`/`TrackSelectionPolicy`/`TrackSelectionHelper`
  resolve audio/subtitle picks through the ordered `LanguageRuleSet`
  (`SubtitleLanguageStore`, `TrackSelectionSettings` editor sheets in
  the language settings screen), with the cross-episode remembered track
  now carrying its container codec (`rememberedAudioCodec`/
  `rememberedSubtitleCodec` columns — the label-churn re-match rung).
  Pinned by `TrackResolutionEngineTest` + `TrackSelectionHelperTest`.
- **Volume memory**: `VolumeProfileStore`
  (core/datastore `volume/`) keeps per-`VolumeBucket` normalized levels
  + the master toggle; `VolumeMemoryPolicy` (feature/player-video)
  restores at session start and captures user changes (desktop mpv +
  audio players; Android video stays on STREAM_MUSIC). Backup key rides
  `SettingsBackup`.
- **Companion control expansion**: `RemoteControlReceiver` (core/data
  jvmShared) grew the General-command ladder — DisplayContent is
  idle-gated + consent-gated through `firstPersistedSecurity` (never the
  seeded StateFlow read), the nav ladder (GoBack/MoveFocus/InvokeSelect/
  OpenContextMenu) feeds `RemoteNavigationBridge`, and both platforms'
  dispatchers (`DesktopRemoteControlDispatchers` new) share the sealed
  `RemoteControlRequests` vocabulary with a capability mirror test
  against the server's `SUPPORTED_REMOTE_COMMANDS` list.
- **Desktop session surface**: `DesktopIdleMonitor`/`DesktopIdleOverlay`
  (AWT-idle detection + the screensaver-consenting dim blanket),
  `DesktopKeySynthesizer` (the ladder's robot key events), and
  `DesktopAudioDeviceEnumerator` (mpv `audio-device` list for the
  playback settings row); the realtime session choreography lives in
  shared/feature/shell's `RealtimeSessionController`, constructed in
  `DesktopAppRoot` with clientName "JellyPlay Desktop" (Android's
  `SessionCoordinator` delegates to it); `DesktopIdleAmbientController`
  owns the idle "Ready-to-play" ambient seam the scaffold used to inline
  (idle decision + active-remote-session count + overlay in one object).
