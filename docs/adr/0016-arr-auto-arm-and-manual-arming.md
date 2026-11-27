# 0016 — arr auto-arm and manual arming

- **Status:** accepted
- **Date:** 2026-11-20
- **Scope:** `shared/core/network` (Sonarr series list), `shared/core/data`
  (policy store, auto-download check, arr auto-arm stage), `shared/core/model`
  (`MediaItem.providerIds`), `shared/core/datastore` (downloads preference),
  `shared/feature/downloads` (armed list + arm picker), `shared/feature/details`
  (download sheet arm action), `shared/feature/settings` (fused settings row)
- **Issue:** gh-225

## Context

ADR-0015 arms a series when it downloads — implicit-on-download plus the
explicit sheet choices — which fixed the lost-arm bug but left the gh-225
comment's opening ask unmet: a Sonarr user monitors dozens of series the
client has never downloaded a single episode from, and none of them join
the auto-download check until the user hand-downloads one episode each.
The follow-up comment asks for exactly that — arm-before-first-download,
sourced from Sonarr — plus a manual toggle: arm a series by hand without
downloading anything. ADR-0015 decision 7 deferred movies; that deferral
was re-affirmed with the maintainer before building this change rather
than re-litigated by inference.

The manual toggle immediately collides with the arr stage it shares a
table with: an arr sync that re-arms rowless series resurrects anything
a user deliberately stopped, so the disarm verb can no longer be "delete
the row" — v0.11.15's un-arm semantics are insufficient, not merely
extended. The two asks land together because the second makes the first
safe.

## Decision

**1. Series-only; Radarr movies stay deferred.** The engine is
series-shaped end to end (tvdb join key, seriesId coverage, episode
catalogue, positional lookahead anchor); a movie arm has no anchor
concept and is its own vertical (ADR-0015 decision 7). The maintainer
re-affirmed the deferral for this change; the Sonarr-only sourcing is a
consequence, not an oversight — no Radarr client is built here.

**2. The Sonarr list client is one call with a sentinel tvdb id.**
`SonarrApiClient.getSeriesList(server)` → `GET /api/v3/series`
returning `SonarrSeriesInfo` rows; `tvdbId: Int` with 0 as the
wire-omission sentinel (Sonarr omits the field for series with no TVDB
mapping). A 0 join key would match every Jellyfin row that fails its
own tvdb parse, so sentinel rows are skipped on BOTH sides of the join.

**3. `armSeries` is the add-only arm verb — insert-if-absent, arms
without enqueueing.** `SeriesDownloadPolicyStore.armSeries(seriesId,
seriesName)` inserts a FOLLOW_GLOBAL arm row only when absent (the
OfflineDownloadWriterCore implicit-arm write pattern): an existing
override, tombstone, or earlier arm is never clobbered or demoted, and
a blank display name is backfilled without overwriting a set one. It
writes policy state only — nothing enqueues; downloads still come from
the pass's own candidate loop. ADR-0015 decision 2's
implicit-on-download keeps working beside it. The MANUAL entries ride
`manualArmSeries` (decision 7) — `armSeries` alone stays the arr
sync's verb so a sync can never resurrect a tombstone.

**4. Manual disarm is a NEVER tombstone; the re-arm affordance keys on
the row's RAW mode.** `DownloadsSeriesPolicyActions.unArmSeries` ("Stop
auto-download") writes `setSeriesPolicy(id, NEVER)` instead of deleting
the row: decision 6's arr sync re-arms rowless series, so deletion
would be resurrected next pass. The stopped series stays in the armed
list as a Never row whose button flips to "Auto-download" —
`reArmSeries` → `setSeriesPolicy(id, FOLLOW_GLOBAL)` (`armSeries` would
no-op on the existing row: insert-if-absent). The picker and the sheet
arm action re-arm the same way through `manualArmSeries` (decision 7).
The affordance keys on the ROW's raw mode == NEVER, never the resolved
mode — a FOLLOW_GLOBAL row under a global NEVER resolves to Never but
is not a tombstone (it is already armed and must not offer re-arm).
The sheet's `seriesArmed` state applies the same raw-mode rule: a
tombstoned series reads NOT armed, so the sheet offers the arm action
again instead of an "armed" confirmation that contradicts the Never
chip below it.

**5. Hygiene amendment to ADR-0015: NEVER rows survive
`clearSeriesArmIfNoDownloads`.** The zero-rows un-arm rule (ADR-0015
decision 5) now skips explicit NEVER rows — an erased tombstone would
be resurrected by the arr sync exactly like a deleted one; an explicit
NO survives list-management hygiene the same way an arm does.
`setSeriesPolicy(id, null)`'s row-deletion semantics are unchanged, but
no UI caller uses it anymore.

**6. The arr auto-arm stage rides the auto-download pass — one stage,
add-only, fail-closed.** New `ArrAutoArmSync` collaborator (core:data
jvmShared, `download/`) runs inside `AutoDownloadCheck.checkOnce` after
the retention sweep and BEFORE universe derivation, so newly armed
series join the SAME pass (their lookahead anchor builds from the
catalogue snapshot that pass loads; a stage that ran after derivation
would only arm for next time). Gate order cheapest-first, any closed
gate a silent no-op: the `autoArmFromArr` pref (read off the slice the
pass already loaded — no second DataStore read) → the
`directArrEnabled` experimental flag → configured Sonarr servers
(`ArrRepository.resolveServers`, all servers fan-out; a series-list
failure on ANY server skips the whole stage — arming from the healthy
half of a partially-down fleet would make the armed set depend on which
server answered first, and the next pass re-arms the rest anyway since
arming is add-only). The match is ONE paged series enumeration
(`getItemsWithUserData`, the WatchHistoryRepository paging precedent,
projection widened at that call site only) now carrying
`MediaItem.providerIds` — a lowercase-key map, null = not projected,
the fifth projection-conditional enrichment field — joined in memory
against the monitored Sonarr tvdb ids; the former per-series
`getMediaDetail` fan-out would cold-read the whole library's detail
cache every pass. `armSeries` fires for monitored ∧ matched ∧ row-less
series, name precedence Jellyfin title → Sonarr title. ADD-ONLY:
nothing deletes, un-arms, or rewrites a row (the candidate filter skips
every series that already carries one — arm, override, or tombstone).
Whole-stage fail-closed: server resolution, any Sonarr list, or an
enumeration page failure skips the stage and the pass continues. No
`maxPerPass` consumption: arming writes rows, never transfers. There is
no offline-specific gate — arr and Jellyfin reads fail offline exactly
as on every other arr surface, and the failure arms above turn that
into "stage skipped".

**7. Manual arm UI: two entry points beside the sheet chips.** The
Downloads screen gains an "Arm a series" search picker beside the armed
section (the `LibraryFeed` browse-family seam, SERIES-filtered query,
blank query renders idle — features never call the network layer
directly); the series detail download sheet gains an explicit
"Auto-download" arm action (`DownloadLifecycleActions.armSeries`).
Both write `manualArmSeries` — the store's one manual verb:
insert-if-absent `armSeries` PLUS a raw NEVER tombstone flip through
`setSeriesPolicy(FOLLOW_GLOBAL)` — so both genuinely double as re-arm
entries for a stopped series (plain `armSeries` would no-op on the
tombstone row), while an explicit INSTANT/ASK override is never
demoted. The arr sync keeps calling `armSeries` directly: only a user
action re-arms a stop. The sheet's policy-chips flow is untouched.

**8. Opt-in setting + scheduler keep-alive.** `auto_arm_from_arr`
downloads spec row (plain boolean, default false, DOWNLOADS_NETWORK
reset category); the fused Storage settings row `AutoArmFromArr` is
`RowAdmission.ContentGated` on the Direct *arr Integration experimental
flag (the flag gates the whole feature, the pref gates the stage); the
plugin settings catalog artifact is regenerated and strings propagated
to all 8 locales (PENDING TRANSLATION). The toggle's write rides a VM
member that pokes `autoDownloadSync.sync()` after the pref write (the
`setAutoDownloadMode` shape) — the gate must be re-evaluated on BOTH
edges, not only at the next app startup. `shouldStayScheduled`
(Android) is also true while the setting is on — a user arming from
Sonarr with zero local downloads still needs the 6 h pass running; the
desktop loop runs unconditionally by construction (KDoc'd + pinned by
test).

**9. Lookahead amendment to ADR-0015: a no-anchor season falls back to
the season head.** The positional lookahead window opened only AFTER a
downloaded-or-watched anchor, and a season with neither enqueued
nothing. That silently defeated this change's core ask under the
default lookahead (3): an arr- or picker-armed series the user never
engaged with has no anchor in ANY season, so episode 1 never
downloaded until a hand-download created one. The no-anchor case now
falls back to the season head — the first `lookahead` real episodes
(the same played/virtual hygiene filter applies; `drop(anchorIndex +
1)` with anchorIndex −1 is the whole mechanism, no second branch).
Engaged seasons are unchanged: the anchor still wins, so a mid-season
start never back-fills earlier episodes.

## Considered options

- **Movies in this change** (re-asked): re-affirmed rejection — no
  anchor concept, different vertical, and no Radarr client built; the
  maintainer re-confirmed after ADR-0015 rather than deciding by
  inference.
- **Arm-source column** (which entry armed the row): rejected — no
  consumer; the armed list is mode+name, add-only means provenance
  never changes a decision, and the sheet/arr arms are visibly
  distinguishable by when they appear.
- **Invisible tombstone column** (keep disarm-as-delete, hide the row):
  rejected — a second marker state beside the mode enum; NEVER already
  means "never auto-download", and a visible Never row in the armed
  list is the discovery surface an invisible marker would still need.
- **Re-arm on next sync** (disarm stays row deletion, tolerate
  resurrection): rejected — manual disarm must win unconditionally, not
  until the next 6 h pass; the tombstone is the mechanism that makes it
  win.
- **Dedicated arr background worker vs riding the pass**: riding the
  pass chosen — the stage needs the pass's already-loaded slice, its
  runCatching boundary, and same-pass pickup; a separate worker
  re-reads prefs, re-resolves servers, and arms mid-interval with no
  pass there to pick the series up.
- **Title/year matching fallback** when the tvdb id is missing or 0:
  rejected — silently arming the wrong series is worse than missing
  one; the tvdb join key is the contract, and unmatched series simply
  stay un-armed until metadata supplies the id.
- **No-anchor seasons keep enqueueing nothing** (require a hand-first
  download): rejected — under the default lookahead it silently
  defeats the issue's core ask: an arr-armed never-engaged series
  would never download episode 1. The season-head fallback (decision
  9) is the amendment; the anchor rule for engaged seasons is
  unchanged.
- **Picker/sheet arm stays plain `armSeries`** (tombstones re-armable
  only from the armed list's button): rejected — both entries were
  documented as re-arm entries, and insert-if-absent no-ops on a
  tombstone, so arming a stopped series from either would silently do
  nothing. `manualArmSeries` (decision 7) makes the claim true while
  the arr sync's add-only verb stays tombstone-safe.
- **`seriesArmed` keys on row presence** (a tombstone reads armed):
  rejected — the sheet would show a stopped series as armed beside
  its own Never chip; the raw-mode exclusion (decision 4) keeps the
  two surfaces consistent and re-offers the arm action.

## Consequences

- **Stop now leaves a visible Never row — for non-arr users too.** The
  disarm verb writes the tombstone regardless of whether arr auto-arm
  is enabled, so v0.11.15 users see armed-list entries that never
  disappear until re-armed; the row is the state, not debris.
- ASK-mode global users get a suggestion prompt per new episode of
  every monitored series — and, via decision 9's season-head fallback,
  per first episode of every never-engaged monitored series too.
  Arming a large Sonarr monitor list under ASK mode scales the
  suggestion card, not the queue (nothing downloads until answered);
  under INSTANT the same arms transfer each series' first lookahead
  episodes on the arming pass.
- Armed list volume reflects the Sonarr monitor list ∩ the Jellyfin
  library — the armed card can hold dozens of rows where v0.11.15 held
  a handful.
- `getItemsWithUserData` callers receive ProviderIds-projected rows —
  the projection is widened at the call site, so every caller of that
  member sees the extra field (additive, null when not projected).
- Scheduler stays alive while the setting is on: `shouldStayScheduled`
  returns true on the pref alone, so even an all-tombstone armed list
  under an on pref keeps the 6 h pass scheduled; the passes stay cheap
  (gates close, or the stage no-ops), but the worker remains resident.
