# JellyPlay glossary

Shared vocabulary for the client codebase. The companion plugin's vocabulary
(contract, capability probe, namespaces, tombstones, dispatch, resilient
fetch…) lives in `jellyfin-plugin-jellyplay/GLOSSARY.md` and is not duplicated
here.

## State sync (client side)

**Sync reservation** — the `jpsync.*` key space: mirrors, cursors, per-namespace
toggles, device id. One module owns the grammar; adapters and the sync engine
acquire keys through it, never through raw string literals. The reservation is
what keeps mirror and cursor state out of every adapter's synced set. It is
also the one classifier of key kinds in the synced stores: a derived
per-device payload stored beside settings (e.g. a widget's continue-watching
buffer) is never a setting, and its exclusion from the synced set belongs to
the reservation's policy — a store hand-rolling its own exclusion is a defect.

**Sync mirror** — the last-synced snapshot a `ProfileSyncAdapter` compares
against to detect dirty values. Exactly one implementation; every adapter
delegates to it (an adapter hand-rolling the comparison is a defect).

**Mirror cycle read** — the engine's cycle-start mirror state: each
adapter's dirty and tombstone faces are read ONCE into a
`CycleNamespaceState` (two mirror-store reads per adapter per cycle, down
from the four the re-diffed pending face used to cost), and the pending
face is arithmetic over the cycle's inputs, never a re-diff. A dirty value
equal to its remote value resolves as synced there — never pending
forever. The interface stays commonMain, so the reads ride the two
`ProfileSyncAdapter` verbs rather than a wider jvmShared one.
Status: implemented 2026-10-14 (the engine's cycle-start
`CycleNamespaceState` and pending arithmetic, `ProfileSyncRepository`).

**Sync adapter base** — the jvmShared base module that absorbs the
delegation seam between `ProfileSyncAdapter` and the sync mirror
(dirty/delete/markSynced verbs, the `deleteRemote` clear-tail, the tombstone
and key-agree rules). An adapter's implementation shrinks to namespace,
snapshot, applyRemote and a deleteLocally hook — "one class over a store"
made literal. The interface stays commonMain; the base is jvmShared because
the mirror is. Tombstone-roaming namespaces keep their own `deletedKeys`
override (delegating it for value-only namespaces pushed resets as
tombstones). Status: implemented 2026-10-14 (`MirrorProfileSyncAdapter`;
all nine adapters ride it — the prefs adapter with its frozen root mirror
prefix and whole `deleteRemote` override, the value-only namespaces keeping
the SPI-default `deletedKeys`).

## Plugin client

**Plugin role** — a narrow, consumer-facing interface over the one plugin
client family (ADR-0010 §5): settings sync routes, device registry + push,
analytics, transcodes, ratings, markers. One family implementation satisfies
all roles; consumers and test fakes depend on the one role they use.

## Home

**Home row module** — everything one `HomeSectionType` knows about itself:
descriptor, fetch arm, single-row refresh arm, assembly arm, offline
projection, behind one small interface registered in the home row registry.
The continue-watching rule (played-row exclusion, hidden ids, NextUp merge)
belongs to the ContinueWatching row module alone — online and offline both
consume it from there.

**Row transport registration** — the home row module's network-layer
counterpart: one registration per `HomeSectionType` stating the batch
subcall and the single-row refresh arm once (wire flags, limits, fold
references), where two parallel tables used to hold them in agreement by
comment. The registration carries the row's read (wire params stated once,
both arms consume it), the enabled gate, and a nullable refresh arm — a
row without one states that absence on the registration instead of an
absent table entry. Status: implemented 2026-10-14 (`HomeRowTransports`,
core/network's `HomeRowFetchArms.kt`).

## Details

**Detail section module** — everything one detail section knows about itself:
admission (does this media get this section), fetch/enrichment arm, renderer,
and event projection, behind one small interface registered in the detail
section registry. The section-module pattern the Home row module proved,
applied where a section's inputs currently restate themselves in four places.
Status: implemented as a registry with two pilot sections (TMDB reviews,
plugin ratings); remaining sections migrate into it as touched.

## Downloads

**Download action module** — everything "what can I do to a download"
knows about itself: admission (the `DownloadActions` table, consumed —
never re-encoded as a per-status `when`; the row re-encoding it today
violates the table's own "neither re-encodes the table" contract), the
composite verbs (resume/retry + enqueue) that the ViewModel and the
notification receiver both dispatch, the COMPLETED-is-playable fold
(five restatements today), and the post-action effects (message,
selection clear) — behind one interface the row renderer, the screen's
action sink and the receiver consume. The row's per-status placement
stays code keyed on the module's available-action set; a placement
flag table is the rejected shape. The row interface shrinks from
twenty parameters (ten action callbacks) to an item plus a sink.
Status: implemented 2026-10-18 (`DownloadActions.availableActions` /
`isPlayable`; composite resume/retry+enqueue folded inside
`DownloadRepositoryImpl` — the DownloadRepositorySurfaceTest member
ratchet ruled out a new queue verb; the row's five callbacks collapsed
into one `onAction` sink; the notification receiver dispatches the
composite verb alone; the effects fold stayed VM-side — DELETE-only,
already single-sited).

## Playback

**Up Next module** — everything "what plays next" knows about itself:
queue-vs-sibling advance precedence, the Up Next card projection, and the
one settle-then-consume protocol for activating a queue row. The card and
the advance read the same precedence verdict — queue head over episode
sibling. The SyncPlay gate is the one deliberate split: the display
projections gate on the uiState mirror, the advance on the manager flag.
Segment display math (`SegmentCalculator`) and the autoplay gate consume
the precedence verdict; neither carries queue fragments.

**Transport routing module** — the SyncPlay → cast → local seek/play precedence ladder plus its per-arm bypass rules (clamp only on user-initiated local seeks, no position-flow write on group/cast arms, resume-skip import on the local play arm), behind one small interface owned by `PlaybackSession`. The session's collaborators call the router directly; `SessionHostLambdas` carries only policy outcomes (segment skip, end-of-playback), and the ViewModel's transport funnel arms are one-line forwards. The ladder is asserted behaviorally (recording arms), not by source text.
Status: implemented 2026-10-18 (`TransportRouter` beside `PipTransportController`; `SessionHostLambdas` 8→3; the skipped-segment notice stays VM-side via a narrow ctor callback).

**Player UI commands module** — the preference-write choreography (guard → uiState mirror → persist launch → apply, with the sibling-snapshot-captured-before-write and engine-before-mirror rules), one module instead of ten hand-rolled setter copies. Each setter's divergence stays code, not a flag table.
Status: implemented 2026-10-18 (`PlayerUiCommands` owned by `PlaybackSession`; `applyPlaybackPrefChange` absorbed; aspect-ratio included despite persisting nothing — the name says UI commands).

**Config delta** — one decision half for all four engines: `EngineConfigDelta.of(old, new)` computes which config slices moved, and every adapter's `onConfigChanged` switches on flags instead of hand-rolled `old != new` ladders. The subtitle carve-out (style change excluding the delay that rides `SubtitleStyle.offsetMs`) is computed inside `of()` from the raw configs — never derived from the other flags.
Status: implemented 2026-10-18 (`subtitleStyleChangedExcludingDelay`; Exo and VLC consume the delta, joining the two mpv hosts).

**Player sheet module** — everything one player sheet knows about
itself: its entry points (chip, overflow, gear, cross-sheet links),
its commands, and its slice region — registered in a sheet registry at
the Home row module's grain (feature-grain; NOT ADR-0009's rejected
settings-group grain). The 19-branch router `when` and the manual
`ALL_SHEETS` list dissolve into the registry; a new sheet lands in one
module instead of touching six-plus files (the HDR sheet measured
nineteen). Migration is staged: pilot sheets first, the rest as
touched — the Detail section module precedent.
Status: implemented as a registry with two pilot sheets (Hdr, Render) — `PlayerSheetModule.kt`: interface + scope + explicit no-reflection registry with an exhaustive-`when` totality gate; `PlayerSheetSaver` restores via the registry; the pilot router branches delegate to their modules and the chip/gear/cross-sheet entries reference the modules' keys. The router `when` remains for the unmigrated 17 (migrate as touched); the commands, slice-region and entry-bundle halves are later waves.

**HDR mode verdict** — the playback-mode derivation
(`resolveHdrPlaybackMode`, today a 74-line decision tree inside the
`HdrOptionsSheet` presentation file) and the HDR family vocabulary
(dovi / hdr10+ / hlg spellings, today normalized three times with
three spelling sets that must agree) live in `HdrOutputPolicy`; the
sheet renders the verdict, the metadata row labels through the one
family parser, the engines consume the same decision.
`HdrPlaybackModeTest` moves from pinning sheet-file internals to the
policy's own JVM lane — the interface becomes the test surface again.
Status: implemented 2026-10-18 (`HdrOutputPolicy` absorbed
`resolveHdrPlaybackMode`/`resolveDetectedDisplayLabel`/`hdrLabelFor`
verbatim, internal→public; the one family parser is `hdrFamilyOf`,
feeding `hdrContentFromStreams`, the sheet and the metadata row;
`HdrPlaybackModeTest` moved to player-contract's jvmTest, all 18 cases
verbatim; sheet 555→391 lines).

**mpv core** — the commonMain implementation of the mpv engine choreography:
load, seek, track selection, subtitle style application, property intake,
ordering rules. Platform code shrinks to a **mpv binding** — an adapter over
the platform handle (Android JNI, desktop libmpv) satisfying the binding seam.
Two bindings make the seam real; a fake binding tests the core without libmpv.
The core deliberately spans two halves — a pure fold/projection half and the
binding-driving half; both are the core, and "where does mpv behavior live"
has this term as its one-word answer (see also the plugin glossary's
vocabulary for what the plugin side owns).

**mpv recovery choreography** — the stall-watchdog wiring (the
sticky-hwdec precedence ladder spanning initOptions, load,
onConfigChanged and recovery — untested in androidMain today) and the
HDR probe machine (the per-host @Volatile probe twins, the
change-cached hint write, `hdrPassthroughRequested` duplicated verbatim
as "the desktop twin") owned by the mpv core behind the binding seam.
Both bindings ride it — the desktop host gains stall recovery it
currently lacks, and the fake binding can pin the precedence ladder.
The pure decision modules (`MpvStallWatchdog`, `HdrOutputPolicy`) stay
where they are; this term is their wiring's home.
Status: implemented 2026-10-18 (the ladder, retirement, per-load
reset, tick funnel, log feed and recovery execution live in `MpvCore`
through `MpvCore.Hosts` (`writeStallHwdec`, `stallHwdecFallbackValue`,
`writeTargetColorspaceHint`); MpvPlayerEngine's seven sites are thin
delegations; the desktop engine rides the same machinery and gains
stall recovery; the "desktop twin" `hdrPassthroughRequested` died into
the core; nine fake-binding ladder/HDR pins added to `MpvCoreTest`).
Host-side remainders, with reasons: the probe reads
(`DisplayHdrCapabilitiesProbe`, desktop gamma read), the configured
hwdec dialects, and the desktop one-shot hint gate.

**Session slice** — the one read seam between the player UI and the playback
session: per-region projections (overlay, status, sheets, cast) whose
interfaces the screens program against. Controllers sit behind the seam; a
screen reading a controller directly, or a ViewModel re-exposing one as a
pass-through, is a defect. Collection placement follows the slice: hot flows
are collected in the leaf that reads them, never in the screen root.
Status: implemented (wave 1: the five region slices, screens migrated
off the reach-through convention; the cast region's route-button
projection closed the VM's last public pass-through — the legacy cast
manager — the same day). Wave 2 implemented 2026-10-14: the audio
family's lyric index left the uiState projection for a dedicated VM flow
collected at the artwork leaf, and the book reader's renderer root
collects only cold flows (hot epub-location/speech/sleep flows collected
in their leaves) — the placement rule extended to the other player
families.

**Player chrome module** — everything the controls chrome knows about
itself: visibility state, the auto-hide timer, the summon policy, focus-grab
arming, the BACK hide-or-exit ladder, and the visibility→poll-cadence
switch, behind one small interface at the player-contract chrome seam (where
`PlayerChromePolicies` sits today). VOD and live both adopt it — today the
predicates and timer loops are parallel per-screen copies; the book
reader's chrome is a possible future adapter.
Status: implemented 2026-10-14 (`PlayerChromeController` composed over the
kept pure predicates; the screens bind it through
`rememberPlayerChromeController`, which keeps the VOD saveable visibility
state; live's parallel gate/ladder/arm copies are deleted).

**Input dispatch module** — one persisted input map's other half: map
resolution plus the discrete-arm executor behind one interface, with each
surface (VOD seek family, live zap family) registering its arm sets. The
gesture modifier and the key sink consume the one instance, and live TV's
D-pad finally rides the persisted map.
Status: implemented 2026-10-14 (`PlayerInputDispatcher` beside
`PlayerInputPolicy` in player-video state; the VOD D-pad config is built
once per executor instance and shared by the modifier and the key sink, so
the sink handle stops rebuilding per recomposition; live resolves its zap
enable gates and the SELECT ladder through it over the same
`videoInputBindings` store, zap behavior preserved as the default
binding).

**Detail content state core** — the screen's content state is the ViewModel's `DetailUiState` carried wholesale plus the handful of genuinely screen-resolved extras (navigation id, persisted season, selected version, downloads slice, resync, manage flag). No verbatim fold restates the core's fields, so a new enrichment field lands in one bag; the fields that must not cross (dialog snapshots, VM-internal combine inputs) are named on the type.
Status: implemented 2026-10-18 (`DetailContentState.core` + extras incl. the nested `DownloadsExtras`; the 43-line fold deleted; reader renames are `state.core.X` — no passthrough properties).

**Model-typed enrichment roles** — the detail-facing plugin roles (mdbList ratings, TMDB season ratings, anime markers, similar items) return `core:model` types; wire DTOs decode at the role implementation, completing the mapper's stated "DTOs stop at the client boundary." Presentation rules (dropping scoreless entries) stay in the section module arm, not the role.
Status: implemented 2026-10-18 (the three detail-facing roles re-typed; `DetailSectionEnrichmentHost` model-only; settings/admin roles keep their DTO-in-ViewModel convention — a future review's term if it ever ratifies).

## Cross-feature

**Screen-shape declaration** — each settings screen's ordered section list, typed as payload-free `SettingsScreenSection(idSet, advancedOnly)`; the scroll-adjust arithmetic derives from it (`deriveAdjustForAdvanced`) and a source-scan guard ties each `advancedOnly` flag to an advanced-gated emission block. The declaration must never grow a composable slot — that would be ADR-0009's rejected registry at group grain. Emission blocks and engine-dependent hidden-count literals stay hand-written.
Status: implemented 2026-10-18 (Playback + Appearance converted — the only two non-identity adjust functions).

**Koin registration totality** — plain ViewModel registrations ride `viewModelOf(::X)`; hand-written blocks remain only where wiring is genuinely non-obvious (nullable platform seams, function-typed ctor defaults). A source-scan guard asserts every ViewModel class has exactly one registration.
Status: implemented 2026-10-18 (`SettingsKoinModule` 398→238 lines, 25 converted / 3 hand-kept; settings only — the hand-written convention stands elsewhere).

**Preference spec tier (subtitle domain)** — the seventh spec domain: the subtitle store's keys, reads, restore, reset lists, defaults and search faces derive from `SubtitlePreferenceSpecs` rows; invariant writes (LRU per-item delay, identity-by-absence sets, nullable set-or-remove) stay hand setters beside the rows — the documented `derived` opt-out. Migrating a domain requires the derivation test before the reset rewire and a plugin-catalog artifact regeneration.
Status: implemented 2026-10-18 (subtitle store public surface byte-identical; `SubtitleSpecDerivationTest` pins key identity; catalog artifact +11 entries).

**Receiver intake fold** — each currently-untested entry receiver's
decode (verb, extras — including `NotificationActionReceiver`'s
hand-validated parallel arrays — and the deep-link intent it rebuilds
by string) as a pure fold beside one goAsync scope-window idiom
(three drifted private copies today; the widget family's skeleton
already owns its variant and keeps it). Receivers shrink to
fold + dispatch; the decode becomes JVM-testable without Robolectric,
giving `NotificationActionReceiver` and `JellyPlayUnifiedPushReceiver`
their first seam. Deep-link intent construction rides `DeepLinkGrammar`
(killing the four-plus-one mirror). Widget, shortcut and PiP action
strings stay pair-local — a universal twenty-six-verb intent grammar
is the rejected god-module shape.
Status: implemented 2026-10-18 (`NotificationEntryFold`,
`DownloadEntryFold`, `PushMessageIntake` decode folds;
`BroadcastScope.runWithBroadcastScope` — one goAsync idiom, the
Throwable catch widened to all three receivers deliberately;
`mediaDeepLinkIntent` killed the dispatcher's three copies plus the
receiver's; NotificationActionReceiver 116→68 lines, UnifiedPush
129→111; first fold suites: 10 + 8 + 3 tests; the receiver's
MediaDeepLinkIntent lives beside the dispatcher, TvWatchNextPublisher's
different-flags copy deliberately left pair-local).

**Now-playing verdict** — one fold over the video-engine /
audio-manager fork answering "is anything playing, anywhere" — today
restated thirteen times in three formulations, plus the mutable
`remoteSessionActive` flag whose single-writer contract lives in a
prose note. The verdict module derives that flag instead;
`RemotePlaybackReporter`, `RemoteControlReceiver`, the widget updater
and the tile read the one interface. `NowPlayingSurface` stays the
audio metadata surface; this term is the cross-family verdict beside
it.
Status: implemented 2026-10-18 (`NowPlayingVerdict` + its
`AudioNowPlayingFace` input, commonMain beside `NowPlayingSurface` —
the video registry + both audio managers satisfy it; the reporter's
position/paused fork and the receiver's idle gate moved in verbatim,
and the widget updater + QS tile read the audio family through it, the
cross-family faces left for the day those surfaces go cross-family).
Flag resolution: the cell MOVED into the verdict, not derived — a
cast/Play-To session can be active while the local engine idles, so
deriving from the playing flows would gate the local reporter off
during plain local playback; `RemotePlaybackReporter` stays the single
writer via the verdict's internal mark at the same two protocol points,
and the audio progress loop reads the same value through a ctor gate
lambda.

**Refresh cycle module** — one module owning a poll loop's whole contract:
start/stop, interval or backoff policy, and the visibility gate, so a
back-stacked screen cannot poll by construction. Today the cancel-delay-
fetch trio is hand-rolled per ViewModel with four different gating
strategies. Status: implemented 2026-10-14 (`RefreshCycle` beside
`boundedPoll`/`PollSpec` in core:concurrency; the EPG, settings-sessions,
transcodes and SyncPlay loops registered — the SyncPlay loop moved out of
the screen composable into its VM; the restore ladder collapsed onto
`boundedPoll`'s new `initialDelayMs`; Requests' poll stays the repository's
refcounted loop behind the visibility binding).

**Plugin capability gate** — the probe-once-while-UNKNOWN, re-arm-on-
identity-reset, gate-reads-the-store discipline behind one interface; the
transcodes/analytics/dashboard/settings status ViewModels each re-wire it
today. Status: implemented 2026-10-14 (`JellyPlayCapabilityGate` +
`JellyPlayCapabilityState` in core:data's session package; the transcodes,
analytics, dashboard and settings ViewModels consume it — the local
`TranscodesGate`/`AnalyticsGate` enums retired into the shared state).

**Download summary surface** — the notification-group summary's in-flight
count as one observed flow with a single collector through the notification
port; call sites stop re-issuing COUNT queries (seven restatements across
four files today, per-tick on the single-connection path).
Status: implemented 2026-10-14 (`DownloadSummarySurface` in core:data's
worker package over the DAO's `getInFlightDownloadCountFlow`; all seven
count-fetching call sites deleted — repo mutations reach it through DAO
invalidation; the same-count memo stays behind the port).

**TV focus cursor** — everything "where does restored focus land" knows
about itself: the fallback leaf, one-shot initial grab, refresh-generation
re-grab, and modifier-order policy — one module the TV column/grid/item-row
containers parameterize by layout kind (today three byte-identical private
copies held in agreement by KDoc).
Status: implemented 2026-10-14 (`TvFocusCursor` in core:ui's tv package;
the three containers consume it and the focused-index read stays inside
the leaf; a Robolectric contract suite pins the grab/re-grab ladder).

---

Terms above are ratified names from the 2026-10-09 architecture review; see
`docs/adr/` for the decisions that constrain how they are used (0009, 0010,
0011). The 2026-10-14 review selected and implemented **Detail section
module** (registry, two pilot sections) and **Session slice**, plus the
Sync reservation's derived-payload exclusion rule. A 2026-10-14 second pass
(performance lens) selected the **Player chrome module**, **Input dispatch
module**, **Mirror cycle read**, **Sync adapter base** and **Row transport
registration**; a full-tree second sweep the same day (settings, search,
library, shell, music, livetv, photos, syncplay, audio, downloads, reader,
onboarding, core/ui, platform shells) selected the cross-feature terms and
the settings-search resolve-once pipeline, the deep-link route fold beside
`Route`, the PagedMediaGridHost quick-action factory
(`pagedGridQuickActions`), and the secure-storage heal state machine.
Every selected term was implemented the same day; their entries above
carry the implementing type.

The 2026-10-18 review (candidates from the architecture-report pass,
deepened per the grilling loop) implemented the **Transport routing
module**, **Player UI commands module**, **Config delta**, **Detail
content state core**, **Model-typed enrichment roles**,
**Screen-shape declaration**, **Koin registration totality** and the
subtitle **Preference spec tier**, plus the `positionAwareState()`
shim deletion and `HomeRowModule.titleCarriesSeed` (the write-effect
lift was REJECTED: the facts fail ADR-0008's deletion test — one
consumer, mutex-scoped pairing, pinned twice).

A second 2026-10-18 pass (downloads / player / app-scope lens over the
commit hot spots, auto-grilled per candidate) selected the **Download
action module**, **Player sheet module**, **HDR mode verdict**, **mpv
recovery choreography**, **Receiver intake fold** and **Now-playing
verdict** — all six implemented the same day. The startup prewarm
declared list was REJECTED: single-consumer startup choreography fails
the deletion test (order-as-data adds interface without depth; the
29-parameter constructor is a testability extraction, not a seam);
only the desktop timeout-twin merge stands, as chore rather than
architecture.
