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

## Playback

**Up Next module** — everything "what plays next" knows about itself:
queue-vs-sibling advance precedence, the Up Next card projection, and the
one settle-then-consume protocol for activating a queue row. The card and
the advance read the same precedence verdict — queue head over episode
sibling. The SyncPlay gate is the one deliberate split: the display
projections gate on the uiState mirror, the advance on the manager flag.
Segment display math (`SegmentCalculator`) and the autoplay gate consume
the precedence verdict; neither carries queue fragments.

**mpv core** — the commonMain implementation of the mpv engine choreography:
load, seek, track selection, subtitle style application, property intake,
ordering rules. Platform code shrinks to a **mpv binding** — an adapter over
the platform handle (Android JNI, desktop libmpv) satisfying the binding seam.
Two bindings make the seam real; a fake binding tests the core without libmpv.
The core deliberately spans two halves — a pure fold/projection half and the
binding-driving half; both are the core, and "where does mpv behavior live"
has this term as its one-word answer (see also the plugin glossary's
vocabulary for what the plugin side owns).

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

## Cross-feature

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
