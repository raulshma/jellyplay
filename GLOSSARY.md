# JellyPlay glossary

Shared vocabulary for the client codebase. The companion plugin's vocabulary
(contract, capability probe, namespaces, tombstones, dispatch, resilient
fetch…) lives in `jellyfin-plugin-jellyplay/GLOSSARY.md` and is not duplicated
here.

## State sync (client side)

**Sync reservation** — the `jpsync.*` key space: mirrors, cursors, per-namespace
toggles, device id. One module owns the grammar; adapters and the sync engine
acquire keys through it, never through raw string literals. The reservation
keeps mirror and cursor state out of every adapter's synced set. It is also
the one classifier of key kinds in the synced stores: a derived per-device
payload stored beside settings (e.g. a widget's continue-watching buffer) is
never a setting, and its exclusion from the synced set belongs to the
reservation's policy — a store hand-rolling its own exclusion is a defect.

**Sync mirror** — the last-synced snapshot a `ProfileSyncAdapter` compares
against to detect dirty values. Exactly one implementation; every adapter
delegates to it (an adapter hand-rolling the comparison is a defect).

**Mirror cycle read** — the engine's cycle-start mirror state: each adapter's
dirty and tombstone faces are read ONCE into a `CycleNamespaceState` (two
mirror-store reads per adapter per cycle), and the pending face is arithmetic
over the cycle's inputs, never a re-diff. A dirty value equal to its remote
value resolves as synced there — never pending forever. The interface stays
commonMain, so the reads ride the two `ProfileSyncAdapter` verbs rather than a
wider jvmShared one.

**Sync adapter base** — the jvmShared base module over the delegation seam
between `ProfileSyncAdapter` and the sync mirror (dirty/delete/markSynced
verbs, the `deleteRemote` clear-tail, the tombstone and key-agree rules).
`MirrorProfileSyncAdapter` is the base; an adapter implements namespace,
snapshot, applyRemote and a deleteLocally hook.
The interface is commonMain; the base is jvmShared because the mirror is.
Tombstone-roaming namespaces override `deletedKeys`; delegating it for
value-only namespaces would push resets as tombstones. The nine bindings
multi-bind the interface and `ProfileSyncRepository` consumes them sorted by
`REGISTRATION_ORDER` (Koin's `getAll` on secondary binds is index-allocation
order, not declaration order — the engine's own sequence is the contract;
`DataKoinModulesTest` pins the resolved order through `activeNamespaces`). The
prefs adapter keeps a frozen root mirror prefix and a whole `deleteRemote`
override; value-only namespaces keep the SPI-default `deletedKeys`.

**Playlist row codec** — ONE home for the smart/mood/moodpref row shape:
`PlaylistRowCodec` owns entity↔payload, payload↔domain (lenient JSON decode)
and domain↔entity (wall-clock stamps), plus the criteria-blob schema. The
repositories' `toDomain`/`toEntity` mapping delegates to it, and so does the
backup slice source's mapper. The `{family}/{id}` key grammar has one home
beside `parseSlashKey` (`backupKey()`); the slice source and the playlists
sync adapter both build keys through it. JSON blobs stay opaque
writer-produced strings; same wire shape, same keys.

## Plugin client

**Plugin role** — a narrow, consumer-facing interface over the one plugin
client family (ADR-0010 §5): settings sync routes, device registry + push,
analytics, transcodes, ratings, markers. One family implementation satisfies
all roles; consumers and test fakes depend on the one role they use.

**Library client families** — the impl-side counterpart of the consumer
seams: `PlaylistApiClientImpl` (8 members), `CollectionApiClientImpl` (4) and
the `HomeSectionsCachePortAdapter` are small ctor-injected adapters over the
one `JellyfinApiEngine` single, while `LibraryApiClientImpl` keeps only the
library-browse/home/user-data families (44 overrides). The port adapter takes
engine + fetcher (the fetcher is constructed with `sources = this` inside the
impl), and `HomeSectionSources.getCollectionItems` is a one-line forward to
the same collection adapter single — each family's wire bodies exist in
exactly one place.

## Data layer

**Library feed seam** — the library/browse family of the `MediaRepository`
union as its own interface, `LibraryFeed` (the HomeFeed family-seam pattern):
the folders, latest-media, search (incl. paged), paged-browse, genres,
studios and favorites reads. Browse-only consumers (library screens,
search, the music collection family, photo albums, the settings library
views) inject the seam alone; mixed browse+detail consumers keep the union.
No member carries a default argument — the family-seam rule (a
dual-declaring override cannot take defaults from both superinterfaces), so
seam-typed callers pass the defaults explicitly. `getFavoritesPaged` is on
the seam; the paged-grid quick-actions factory takes the
`Flow<UserDataChange>` it reads instead of the union. The detail/collection
families have their own seams — `DetailFeed` (getMediaDetail,
getSimilarItems) and `CollectionOperations` (the read/write trio) —
dual-declared on the impl; CastAndCrew/MediaInfo/Reviews/ManageSeries/
ResyncActions consume `DetailFeed`, CollectionAddTarget consumes
`CollectionOperations`, and `markPlayed` lives only on
`UserDataWriteOperations` (the drainer uses `recordDrainedPlays`), so the
union is not a write surface. The remaining union residents are mixtures:
DetailViewModel (hands the seams to its helpers), Collection/Person detail VMs
(detail + the change feed), MetadataAdminActions (union-only `search`),
UnifiedMediaDetailProviderImpl (detail + album tracks — the mixture the
DetailFeed KDoc reserves for the union), AudioLibraryBrowser /
NewMediaCheckWorker / PhotoFolderPrefetcher, and the browse+change-feed VMs
(library, music detail, search, notification settings).

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
references). The registration carries the row's read (wire params stated
once, both arms consume it), the enabled gate, and a nullable refresh arm — a
row without one states that absence on the registration instead of an absent
table entry. `HomeRowTransports` lives in core/network's `HomeRowFetchArms.kt`.

**Fetcher cache spec** — the fetcher-local list every sub-call cache
declares itself on, `invalidateCaches` iterates it — the
`MediaRepositoryImpl.cacheSpecs` idiom applied to `HomeSectionsFetcher`: one
list, so a new sub-call cannot be forgotten at clear time (a forgotten clear
serves stale badges for a TTL). The suggestions-fallback predicate lives
beside the assembler; both sites call the one function.

**Home write generation** — the home pipeline's one cache-write token,
branded: `HomeWriteGeneration` (core:network, beside the port verbs it
qualifies) crosses the repo → port → fetcher-mirror seam named, so a raw
epoch of a different axis does not type-check where the token is required. A
data class with a private constructor + `of()` deliberately — a `@JvmInline`
value class's JVM slot is its underlying `long`, which breaks mockk captures
on the port verbs (boxed Long → CCE); the token crosses test seams far more
often than hot loops. The protocol is stated in the type's KDoc.

**Home content state core** — the eleven `HomeUiState` fields the content
list reads (pref mirrors, click behavior, visit stamp, stats-card and
genre-rail data) travel as one embedded `HomeContentState.Core` value: the
fold at the construction site is a one-line copy per field, every reader
qualifies through `HomeContentState.core`, and "which surface facts come
from the VM" is answerable by looking at one type. Deliberately not the
`DetailContentState.core` wholesale shape: the home bag narrows to the fields
the content list consumes, so an unchanged `Core` copy stays referentially
stable while the refresher/dialog fields of `HomeUiState` move; the feed,
featured hero, discover rows and status banners remain the screen-derived
half.

**Home feature gate** — one reactive spine for the VM's opt-in decoration
features (H17 stats card, H18 genre rail, H19 presence poll): combine the
feature's gate flows into one value, dedupe, run the effect once per distinct
emission with collectLatest cancel-and-restart. The presence poll's cadence
loop lives in `HomePresenceChipsController.poll(scope, gate)` — probe-once,
session filtering, dismiss memory AND the poll loop are one owner. The
ADR-0008 write-effect lift stays rejected. The folded verdicts live on
`HomeUiState` and each reader takes the one it means — the surface's HardError
arm keys on `isHardError` (the three-term fold), the header chip on the
refresher's `fetchFailed`; a raw `error != null` restatement at either site is
a defect.

**Home user-data reactor** — the user-data reaction chain as its own module:
the debounced `UserDataChanges` collector, the pending-flush latch, the
trailing-edge deferral timer (never inside `refreshJob`), the throttle
decision, and the echo/push discrimination. The protocol invariants are
private to it: every change fans out into exactly one silent refresh (never
dropped), an echo of own writes must not re-trigger the forced path, and the
completion echo of a drained play bypasses both throttle and demotion.
`HomeRefresher` keeps WHEN (scheduling, the flush body's job shape) and reads
the reactor through a five-member port (foreground, offline, last-refresh,
raced-sync, `launchUserDataRefresh`). The raced-sync flag stays
refresher-owned — its writers are the offline handshake and identity
transitions. `HomeUserDataReactorTest` holds the thirteen behavioral pins.

**HomeDiscovery slice rows** — the store's per-user read and restore rows are
declared on the spec tier (`HomeDiscoverySliceRow` + the store-blind
`HomeDiscoveryUserJsonReads` decodes; star-projection-safe
`readInto`/`writeFrom` fold steps, no reflection, no unchecked casts);
`slice()` and `restore()` iterate the rows. 42 of 47 rows derive; the five
hand-kept rows are documented one-line exclusions (the enabled-section-types
version-union decode spanning two rows, the migration-source hidden-library
ids, and the two global markers outside every namespace), each pinned by
`HomeDiscoverySpecDerivationTest` (sliceRows == `all` minus exactly the
documented exclusions, plus a whole-derivation restore→re-read round-trip).

## Details

**Detail section module** — everything one detail section knows about itself:
admission (does this media get this section), fetch/enrichment arm, renderer,
and event projection, behind one small interface registered in the detail
section registry (the Home row module's pattern). Six kinds are module-owned,
including SPECIAL_FEATURES, COLLECTION_ITEMS, UPCOMING_EPISODES and
MORE_IN_COLLECTION, each with admission, renderer and its enrichment arm
(specialFeatures, collectionItems, boxSetsContaining franchise members,
upcomingEpisodes). `DetailSectionRegistryTest` pins exactly one owner per
kind: a kind admitted by both a fold row and a module would render twice.
Other kinds render through the fold.

**Detail play sequencer** — the play click's ordered effects, decided once:
`DetailPlaySequencer` composes the pure `resolveDetailPlayDispatch` (stream
tail) and `resolveTrailerPreroll` (the trailer-then-feature gate), beside
`DetailPlayPolicies`. Input: one `DetailPlayContext`; output: ordered
`DetailPlayEffect`s (EnqueueNext the feature, PlaySession the trailer/preroll
arm) that the screen's `onPlayClick` lambda executes in order. The screen's
process-wide seams (video queue, syncplay, player store, cinema override)
arrive as one `MediaDetailPlaybackSeams` constructor bundle instead of four
mid-body `koinInject` pulls. The raw backdrop getter is part of
`ArtworkCallbacks` (image domain, not playback).

**Detail constructor arms** — the ViewModel's nine nullable "unwired"
constructor arms travel as two bundles with neutral defaults:
`DetailPluginArms` (status store, feature gate — ONE gate member per
ADR-0010 — ratings/recommendations/markers apis; members stay nullable
because two seams are core:data final classes no no-op can stand in
for; each operation folds the null once inside the arm method) and
`DetailBookArms` (toc cache, annotations repository, toc prober,
normalized to no-op adapters once in the constructor —
`NoopReaderAnnotationsRepository` observes `emptyFlow` so the marks
collector parks without writing). Silent absence is decided at one place per
arm, not spelled at every read site. TMDB client and appearance store stay
nullable on purpose: single read site each (stock fallback / an OFF toggle),
not force-fit. Tests construct bundles, not nine doubles.

**Detail content state core** — the screen's content state is the ViewModel's
`DetailUiState` carried wholesale plus the handful of genuinely
screen-resolved extras (navigation id, persisted season, selected version,
downloads slice, resync, manage flag). No fold restates the core's fields, so
a new enrichment field lands in one bag; the fields that must not cross
(dialog snapshots, VM-internal combine inputs) are named on the type.
`DetailContentState.core` carries the extras, including the nested
`DownloadsExtras`; readers use `state.core.X`, not passthrough properties.

**Model-typed enrichment roles** — the detail-facing plugin roles (mdbList
ratings, TMDB season ratings, anime markers, similar items) return
`core:model` types; wire DTOs decode at the role implementation, completing
the mapper's stated "DTOs stop at the client boundary." Presentation rules
(dropping scoreless entries) stay in the section module arm, not the role.
`DetailSectionEnrichmentHost` is model-only; settings/admin roles keep their
DTO-in-ViewModel convention.

## Downloads

**Download action module** — everything "what can I do to a download"
knows about itself: admission (the `DownloadActions.availableActions` table,
consumed — never re-encoded as a per-status `when`; a row that re-encodes it
violates the table's own contract), the composite verbs (resume/retry +
enqueue) that the ViewModel and the notification receiver both dispatch, the
COMPLETED-is-playable fold (`isPlayable`), and the post-action effects
(message, selection clear) — behind one interface the row renderer, the
screen's action sink and the receiver consume. The row's per-status
placement stays code keyed on the module's available-action set; a placement
flag table is the rejected shape. The row interface is an item plus one
`onAction` sink.
The composite resume/retry+enqueue verb lives inside `DownloadRepositoryImpl`
(the `DownloadRepositorySurfaceTest` member ratchet rules out a new queue
verb); the notification receiver dispatches it alone. The effects fold stays
VM-side — DELETE-only, single-sited.

## Playback

**Up Next module** — everything "what plays next" knows about itself:
queue-vs-sibling advance precedence, the Up Next card projection, and the
one settle-then-consume protocol for activating a queue row. The card and
the advance read the same precedence verdict — queue head over episode
sibling. The SyncPlay gate is the one deliberate split: the display
projections gate on the uiState mirror, the advance on the manager flag.
Segment display math (`SegmentCalculator`) and the autoplay gate consume
the precedence verdict; neither carries queue fragments.

**Transport routing module** — the SyncPlay → cast → local seek/play
precedence ladder plus its per-arm bypass rules (clamp only on
user-initiated local seeks, no position-flow write on group/cast arms,
resume-skip import on the local play arm), behind one small interface owned
by `PlaybackSession`. The session's collaborators call the router directly;
`SessionHostLambdas` carries only policy outcomes (segment skip,
end-of-playback), and the ViewModel's transport funnel arms are one-line
forwards. The ladder is asserted behaviorally (recording arms), not by source
text. `TransportRouter` sits beside `PipTransportController`; the
skipped-segment notice stays VM-side via a narrow ctor callback.

**Player UI commands module** — the preference-write choreography (guard →
uiState mirror → persist launch → apply, with the
sibling-snapshot-captured-before-write and engine-before-mirror rules) as one
module rather than per-setter copies. Each setter's divergence stays code,
not a flag table. `PlayerUiCommands` is owned by `PlaybackSession`;
`applyPlaybackPrefChange` lives there. Aspect-ratio belongs here despite
persisting nothing — the name says UI commands.

**Config delta** — one decision half for all four engines:
`EngineConfigDelta.of(old, new)` computes which config slices moved, and
every adapter's `onConfigChanged` switches on flags rather than per-field
`old != new` ladders. The subtitle carve-out (style change excluding the
delay that rides `SubtitleStyle.offsetMs`) is computed inside `of()` from the
raw configs — never derived from the other flags.
`subtitleStyleChangedExcludingDelay` feeds Exo and VLC, which consume the
delta alongside the two mpv hosts.

**Session position ledger** — the position/report family of the playback
session as one module: the play-session id resolution (one id per session
lifecycle for every report and persist), the seek latch and report
position (fresh seek < 3 s over engine position), the stop-report pair
with its incognito gate and Stop dedup, the process-death persistence
(wall-clock throttle, store snapshot, coalesced offline-mirror write), and
the lifecycle phase verbs (new-item reset, item-loaded bookkeeping, the
teardown bracket) whose pinned orders — the stop-inputs snapshot BEFORE
any teardown statement, restore/allocate before the persistence-window
reset — are private implementation. `SessionPositionLedger` is constructed
inside the session root; the session keeps thin delegates. Its seven internal
fields are private behind the phase verbs, and the lifecycle/reporting suites
drive the interface.

**Playback reload coordinator** — the five reload shapes (engine retry,
same-engine retry, transcode fallback, stream change, source switch) as one
module with ONE verb: `PlaybackReload.reload(variant: ReloadVariant)`. Each
variant is a sealed case carrying its own deltas (persist the preferred
player, clear pending streams, restore paused state) as data; the ordering
invariants — position captured before teardown, reporters cancelled
before release — are private to the coordinator. The session's five
entry points are one-line forwards; a new reload-shaped feature adds a
variant, not another copy of the choreography. The shared rebind tail stays
session-owned (cinema/reclaim reuse it) — deliberately outside the
coordinator. The pure variant→delta folds are pinned by `PlaybackReloadTest`.

**Player preference leaf table** — ONE home for "which preference feeds
which player uiState leaf": typed rows (aggregate read, optional state
read, write lens, engine-config rebuild flag), plain data + lambdas, no
reflection. The three execution strategies read it: the seed (load-time,
unguarded fold of every row), the projector (change-time, guarded diff of
the projected rows, one uiState copy per changed leaf), the fanout (the
rebuild-reaction diffs). It is the only mapping between preferences and
player uiState leaves. Non-rows stay hand-written in their strategy: the
network-slice adaptiveBitrateEnabled leaf, the HDR-aware subtitle-style
resolution, the PIN pair's one-copy double write, and every reaction that
is not a uiState leaf (controller seeds, autoplay flip, focus push).
`PLAYER_PREF_LEAVES` is the table; `PlayerPrefsSeed` / `SettingsProjector` /
`PlayerPrefsFanout` are strategies over it.

**Player sheet module** — everything one player sheet knows about
itself: its entry points (chip, overflow, gear, cross-sheet links),
its commands, and its slice region — registered in a sheet registry at
the Home row module's grain (feature-grain; NOT ADR-0009's rejected
settings-group grain). A new sheet lands in one module. Three sheets are
registry-owned (Hdr, Render, SubtitleHub): SubtitleHub reads the
`SheetsSessionSlice` through `SubtitleHubSheetModule` with per-tab input
bundles. The remaining sheets route through the router `when`; the
commands, slice-region and entry-bundle halves remain outside the registry.
The hub's remote-subtitle fetch starts at sheet composition, and a
config-change restore re-entry refetches. The sheet tests derive from the
registry (`PlayerSheetRegistry.all`) — a hand sheet list there is the defect
shape.

**Player parameter bundles** — the overflow menu takes
`capabilities: EngineCapabilities` + one `PlayerOverflowMenuActions` bundle
instead of re-flattening the capability projection into per-call-site
booleans (the codebase's own stated idiom); `ticksToFraction` (state/)
is the one home for the seek-bar's ticks→fraction clamp policy.

**mpv load plan** — the per-request mpv option ladder as one pure plan:
`MpvLoadOptionsPlan` (beside the subtitle side-load plan and stats
projection) turns a playback request into ordered (option, value)
writes — TLS reset trio, normalized + blank-skipped language lists, the
User-Agent extraction into the `user-agent` option, the header
persistence discipline (reset unconditional in BOTH spellings:
`REPLACE_LIST` replace-write for Android and desktop,
`RESET_AND_APPEND` where the transport supports list-append), readahead.
Each engine supplies only its transport seam. The desktop engine shares the
same ladder (UA extraction, language normalization, blank-skip);
`MpvDesktopEngineAndroidParityTest` pins it.

**mpv option vocabulary home** — the mpv option vocabulary
(`MpvConfigMapping`, `MpvConfigApplier`, `MpvErrorTaxonomy`,
`MpvAudioMappings`, `MpvLoadOptionsPlan`, `MpvProperties`) lives in
core/player-contract beside `MpvPropertyIntake`/`MpvStyleMapping`; the
engine choreography (`MpvCore`, `MpvBinding`, both host engines) stays in
feature/player-video. The files keep their `feature.player.video` package
name — a deliberate split package (one package spanning two modules, core
carrying a `feature.*` namespace) to avoid consumer import churn;
re-homing the package is a per-file rename, not an API break.
`MpvAudioMappings`' three helpers are public: `internal` is module-scoped to
player-video and `MpvCore` consumes them across the edge.

**Stats publish guard** — the engines' video-stats change-guard
(last-value cache, scalar-change predicate, forced periodic full re-read,
publish funnel) as one `StatsPublishGuard` in player-contract beside
`MpvStatsProjection`, parameterized by each engine's read source; the
published-shape comparison semantics (the HDR fold computed before the
dedup guard) are the guard's pinned contract. Exo and mpv ride it.

**Audio shell seam** — the app shell's read surface over the audio
family: `ShellAudioSurface` (the narrow interface the shell consumes —
current-playing item and the shell-read state) instead of the concrete
androidMain `AudioPlaybackManager` class crossing into shell code. The
manager implements it; the Koin binding is the seam.

**HDR mode verdict** — the playback-mode derivation
(`resolveHdrPlaybackMode`) and the HDR family vocabulary (dovi / hdr10+ /
hlg spellings) live in `HdrOutputPolicy`; the sheet renders the verdict, the
metadata row labels through the one family parser, the engines consume the
same decision. `HdrPlaybackModeTest` pins the policy's own JVM lane, and
`hdrFamilyOf` is the one family parser, feeding `hdrContentFromStreams`, the
sheet and the metadata row.

**mpv core** — the commonMain implementation of the mpv engine choreography:
load, seek, track selection, subtitle style application, property intake,
ordering rules. Platform code is an **mpv binding** — an adapter over
the platform handle (Android JNI, desktop libmpv) satisfying the binding seam.
Two bindings make the seam real; a fake binding tests the core without libmpv.
The core deliberately spans two halves — a pure fold/projection half and the
binding-driving half; both are the core, and "where does mpv behavior live"
has this term as its one-word answer (see also the plugin glossary's
vocabulary for what the plugin side owns).

**mpv recovery choreography** — the stall-watchdog wiring (the
sticky-hwdec precedence ladder spanning initOptions, load,
onConfigChanged and recovery) and the HDR probe machine (the per-host
@Volatile probe twins, the change-cached hint write,
`hdrPassthroughRequested` as one core flag) owned by the mpv core behind the
binding seam. Both bindings ride it, and the fake binding pins the
precedence ladder. The pure decision modules (`MpvStallWatchdog`,
`HdrOutputPolicy`) stay where they are; this term is their wiring's home.
The ladder, retirement, per-load reset, tick funnel, log feed and recovery
execution live in `MpvCore` through `MpvCore.Hosts` (`writeStallHwdec`,
`stallHwdecFallbackValue`, `writeTargetColorspaceHint`); `MpvPlayerEngine`'s
seven sites are thin delegations. Host-side remainders: the probe reads
(`DisplayHdrCapabilitiesProbe`, desktop gamma read), the configured hwdec
dialects, and the desktop one-shot hint gate.

**Session slice** — the one read seam between the player UI and the playback
session: per-region projections (overlay, status, sheets, cast) whose
interfaces the screens program against. Controllers sit behind the seam; a
screen reading a controller directly, or a ViewModel re-exposing one as a
pass-through, is a defect. Collection placement follows the slice: hot flows
are collected in the leaf that reads them, never in the screen root. The
cast region's route-button projection is a slice member, so the cast manager
is not a public VM member. The audio family's lyric index is a dedicated VM
flow collected at the artwork leaf, and the book reader's renderer root
collects only cold flows (hot epub-location/speech/sleep flows are collected
in their leaves).

**Player chrome module** — everything the controls chrome knows about
itself: visibility state, the auto-hide timer, the summon policy, focus-grab
arming, the BACK hide-or-exit ladder, and the visibility→poll-cadence
switch, behind one small interface at the player-contract chrome seam (where
`PlayerChromePolicies` sits). VOD and live both use it; the book reader's
chrome is a possible future adapter. `PlayerChromeController` composes over
the pure predicates; the screens bind it through
`rememberPlayerChromeController`, which keeps the VOD saveable visibility
state.

**Input dispatch module** — one persisted input map's other half: map
resolution plus the discrete-arm executor behind one interface, with each
surface (VOD seek family, live zap family) registering its arm sets. The
gesture modifier and the key sink consume the one instance, and live TV's
D-pad rides the persisted map. `PlayerInputDispatcher` sits beside
`PlayerInputPolicy` in player-video state; the VOD D-pad config is built
once per executor instance and shared by the modifier and the key sink, so
the sink handle does not rebuild per recomposition; live resolves its zap
enable gates and the SELECT ladder through it over the same
`videoInputBindings` store, with zap behavior preserved as the default
binding.

## Admin (logs, plugins)

**Log line parse** — the log screen's severity-highlight decode as plain
JVM types (`parseLogLineSpans` / `LogLineRole` / `LogLineSpan`, jvmShared):
the whole-word severity regex (alternation ordered so `WARN` yields to
`WARNING` inside the longer word) and the token → role fold live beside a
parse test; the composable only maps role → tint and draws. The parse stays
plain JVM so it is testable without Compose.

**Plugin settle-then-refetch** — the plugins' wait-for-server-effect idiom
as one `settleThenRefetch(delayMs, refetch)`, used by `PluginsViewModel` and
`PluginDetailViewModel`. The delay literals are per command — installs
1000 ms, the other commands 500 ms — with no recorded rationale to unify
them against.

**Plugin tab loader** — the plugin-tab fetch dance as one
`PluginTabLoader<Q, T>`: the gate spelling (ONE —
`isAvailableNowOrProbe`), the stale-guard (only the currently-selected
fetch may land, on BOTH Result arms — a superseded fetch's failure is
as forbidden as its success), and the quiet-degrade fold (fresh failure
lands the `quiet` value). The UserRatings and YourWatching VMs keep
only their query type, state shape and select verb. Messages and the
Sync gate are hand-written deliberately: Messages' state lives in the
repository (no fetch-Result, no stale tab — a loader would own
nothing), and the Sync gate is a non-suspend meta-key read from
synchronous contexts (outside the toggleable-features ladder).
`PluginTabLoaderTest` pins the stale-guard, quiet and gate-closed cases.

## Cross-feature

**Screen-shape declaration** — each settings screen's ordered section list,
typed as payload-free `SettingsScreenSection(idSet, advancedOnly)`; the
scroll-adjust arithmetic derives from it (`deriveAdjustForAdvanced`) and a
source-scan guard ties each `advancedOnly` flag to an advanced-gated emission
block. The declaration must never grow a composable slot — that would be
ADR-0009's rejected registry at group grain. Emission blocks and
engine-dependent hidden-count literals stay hand-written.
`hiddenRowsFor(groups, flags)` derives six screens' hidden-count hints from
the same declarations (Playback's `9` is a documented frozen literal — no
derivation reproduces it across the (isTv, engine) combos), pinned by
`FusedRowsRatchetTest`. The navigation customization's row list derives from
core:ui's `NAV_DESTINATIONS` (the registry wins on icon/order); only the
route→subtitle map stays local. The app module's three ViewModel defs ride
`viewModelOf(::X)` with a source-scan totality guard.

**Reset review cross-pin** — every spec-backed preference in a reset
category has a `DiffField` row, so the Factory-Reset / restore-wizard review
surface's totality is not self-referential; sanctioned omissions ride an
explicit pinned-exception set in `PreferenceSpecDiffFieldRatchetTest`.

**Restore wizard selection target** — the ONE selection verb's payload:
`WizardSelectionTarget` names which wizard group a toggle addresses, and
`RestoreWizardViewModel.toggle` routes the tap. The selection math stays in
the model folds (`FileSelection.toggledCategory` and siblings).

**Request action table** — "which actions are legal for this request
status" as one `requestActionsFor(status, isAdmin)` beside the module's
status-presentation table; the list row and the detail sheet render the
one table (placement/styling stays per-surface). The admin TaskState
family has the same single home (`TaskStatePresentation`: color role,
cancellable, startable, label — labels single-sited; i18n is a follow-up),
as do the requests-side ArrDownloadStatus rows.

**Seerr media kind** — `SeerrMediaKind` + `SeerrRequestItem.kind` derived
once in core:model beside `effectiveMediaStatus` (ignoreCase); the thirteen
`type.equals("movie")` parse sites across five modules read the model. The
core/data sites use ignoreCase — a `"Movie"` payload would otherwise
misroute.

**Scan grace window** — the dashboard's optimistic scan grace-window
machine and the WS-freshness rule as small tested holders beside the
`MediaCleanupScanStateHolder` precedent; the dashboard VM delegates.
`LibraryScanWindow` is the holder.

**Subtitle upload choreography** — the editor's provider-subtitle
save → encode → upload → reload → attribute sequencing lives in
`EditorSubtitleStore.saveUploadAndAttribute` (one caller; the
persist/attribute halves live there too); the VM keeps only
busy/error flags and the degradation wording.

**User-data announcer** — the synthetic-push seam for confirmed writes:
`UserDataAnnouncer` (the flow + the record) is a leaf single, so the write
paths upstream of the repository's construction graph
(`PlayedStateSyncImpl`, `PlaybackRepositoryImpl`) announce eagerly without
the `lazy { get<MediaRepository>() }` cycle-breaks. The repository funnels
its own union member through the same seam. The outbox drainer names the
leaf directly for its drain-tail fan-out: its derived flips ride the write
seam's `recordDrainedPlays` (the per-item `markPlayed` + cache-dropping +
announce-on-delivery pairing, one batched call).

**Test fixtures home** — ADR-0006 rule 4: the desktop-audio queue fixtures
live once in `:shared:core:test-fixtures` (jvmMain — the fakes reference
core:data's jvmMain-only `AudioEffectsSession`; both consumer lanes are
JVM-only).

**Koin registration totality** — plain ViewModel registrations ride
`viewModelOf(::X)`; hand-written blocks remain only where wiring is genuinely
non-obvious (nullable platform seams, function-typed ctor defaults). A
source-scan guard asserts every ViewModel class has exactly one registration.
`SettingsKoinModule` follows the convention; the hand-written form stands
elsewhere.

**Preference spec tier (subtitle domain)** — the seventh spec domain: the
subtitle store's keys, reads, restore, reset lists, defaults and search faces
derive from `SubtitlePreferenceSpecs` rows; invariant writes (LRU per-item
delay, identity-by-absence sets, nullable set-or-remove) stay hand setters
beside the rows — the documented `derived` opt-out. Migrating a domain
requires the derivation test before the reset rewire and a plugin-catalog
artifact regeneration. `SubtitleSpecDerivationTest` pins key identity.

**Receiver intake fold** — each entry receiver's decode (verb, extras —
including `NotificationActionReceiver`'s hand-validated parallel arrays — and
the deep-link intent it rebuilds by string) as a pure fold
(`NotificationEntryFold`, `DownloadEntryFold`, `PushMessageIntake`) beside one
goAsync scope-window idiom (`BroadcastScope.runWithBroadcastScope`; the widget
family's skeleton owns its own variant). Receivers are a fold plus
dispatch; the decode is JVM-testable without Robolectric, which gives
`NotificationActionReceiver` and `JellyPlayUnifiedPushReceiver` their seam.
Deep-link intent construction rides `DeepLinkGrammar`
(`mediaDeepLinkIntent`). Widget, shortcut and PiP action strings stay
pair-local — a universal twenty-six-verb intent grammar is the rejected
god-module shape.

**Now-playing verdict** — one fold over the video-engine /
audio-manager fork answering "is anything playing, anywhere"; the mutable
`remoteSessionActive` flag's single-writer contract lives in the verdict,
which derives that flag. `RemotePlaybackReporter`, `RemoteControlReceiver`,
the widget updater and the tile read the one interface. `NowPlayingSurface`
stays the audio metadata surface; this term is the cross-family verdict
beside it. `NowPlayingVerdict` takes its `AudioNowPlayingFace` input in
commonMain beside `NowPlayingSurface` — the video registry and both audio
managers satisfy it. Flag resolution: the cell lives in the verdict, not
derived — a cast/Play-To session can be active while the local engine idles,
so deriving from the playing flows would gate the local reporter off during
plain local playback; `RemotePlaybackReporter` stays the single writer via
the verdict's internal mark at the same two protocol points, and the audio
progress loop reads the same value through a ctor gate lambda.

**Refresh cycle module** — one module owning a poll loop's whole contract:
start/stop, interval or backoff policy, and the visibility gate, so a
back-stacked screen cannot poll by construction. `RefreshCycle` sits beside
`boundedPoll`/`PollSpec` in core:concurrency; the EPG, settings-sessions,
transcodes and SyncPlay loops register — the SyncPlay loop lives in its VM;
the restore ladder uses `boundedPoll`'s `initialDelayMs`; Requests' poll is
the repository's refcounted loop behind the visibility binding.

**Plugin capability gate** — the discipline of probing once while UNKNOWN,
re-arming on identity reset, and gating on the store, behind one interface;
the transcodes/analytics/dashboard/settings status ViewModels consume it.
`JellyPlayCapabilityGate` + `JellyPlayCapabilityState` live in core:data's
session package.

**Download summary surface** — the notification-group summary's in-flight
count as one observed flow with a single collector through the notification
port; call sites read the flow instead of re-issuing COUNT queries (per-tick
on the single-connection path). `DownloadSummarySurface` sits in core:data's
worker package over the DAO's `getInFlightDownloadCountFlow`; repo mutations
reach it through DAO invalidation, and the same-count memo stays behind the
port.

**TV focus cursor** — everything "where does restored focus land" knows
about itself: the fallback leaf, one-shot initial grab, refresh-generation
re-grab, and modifier-order policy — one module the TV column/grid/item-row
containers parameterize by layout kind. `TvFocusCursor` lives in core:ui's tv
package; the three containers consume it and the focused-index read stays
inside the leaf; a Robolectric contract suite pins the grab/re-grab ladder.

**Shortcut action surface** — ShortcutAction's execution as one per-shell
module beside `ShellHostHooks` (the ShellSessionController pattern: wiring
shared, arms per-shell): `ShortcutActionSurface(focusHome, armSurprise,
requestPlayOn, toggleOffline, onUnsupported)` exposes `supports(action)` /
`execute(action)`; `supportedActions` derives from the armed arms, so a
shell cannot claim support while no-op-ing. The navigate-home-before-arm-
surprise ordering is private to `execute`. `ShellHomeHooks` carries the
surface (required — no silent `onShortcutAction = {}` default; forgetting
stops compiling), the home dock and the overflow fold both dispatch through
it, and the customize editor/picker/prune consume capability so unsupported
actions are never offered and stored pins targeting them are pruned. Desktop
arms Surprise (buffered channel into the shared hero) and offline toggle for
real; Play On stays unsupported and LOUD (snackbar, not silence).

**Overflow shortcut decoration** — the overflow pill's decode +
icon/label/badge/highlight/progress presentation as one pure fold
`overflowShortcutDecoration(target, isOfflineActive, isGoingOnline,
downloadCount)` (no Compose types cross the file — the icon slot
resolves at the call site); the action×offline-state matrix is pinned
by app-lane tests. A second hand-fold of it is a defect.

**Nav primary-surface descriptor** — "may this destination be primary
on this surface" lives on the `NavDestination` registry row:
`primarySurfaces: Set<NavPrimarySurface>` (`PhoneBar`, `TvDrawer`,
`DesktopRail`), default derived from `topLevelGroups` (bar tab ⇒ all
three; otherwise DesktopRail-only); only Shortcuts deviates explicitly.
`tvPrimaryRoutes` (base + TvDrawer∖PhoneBar), the settings
customization fork and the desktop rail's eligibility all derive from
it; the rail's explicit list stays as its own ORDER policy (registry
order differs), pinned by a both-ways contract test. The TV
`menu_shortcuts` label override stays at the call site — per-
destination presentation, not eligibility (a localized resource vs the
registry's English labels).

**Preference stores aggregate** — `UserPreferencesStore` and
`PreferencesEditScope` take the grouped `PreferenceStores` value
instead of re-enumerating every store positionally; the edit scope keeps
`appRuntimeState` explicit — the backup-extras store is not a slice, and
`PreferenceStores`' member names mirror the slice names one-to-one. Adding a
preference family touches three sites: the `PreferenceStores` field, the
`PreferenceSliceSnapshot` row, the Koin single. A missed aggregate update
fails at compile time, not by silent positional drift.
