# 0015 — Armed series outlive local files

- **Status:** accepted
- **Date:** 2026-11-19
- **Scope:** `shared/core/data` (auto-download check, download repository,
  policy store), `shared/core/model` (`AutoDownloadMode`), `shared/core/database`
  (policy rows), `shared/feature/downloads` (armed-series surface)
- **Issue:** gh-225

## Context

The auto-download pass derives its universe from local rows only:
`downloadCoverage().seriesIds` is `SELECT DISTINCT seriesId FROM downloads`.
Any deletion that takes a series to zero rows — auto-delete-after-watch,
the keep-days retention sweep, manual deletes — runs
`maybeClearSeriesAutoDownloadState`, which deletes the series'
`series_download_policies` row and its pending Ask prompts. A weekly-airing
series watched to completion then auto-deleted loses its per-series policy
state AND its membership in the checked universe, so next week's episode
never auto-downloads (gh-225). The resume mechanics themselves are sound:
the lookahead anchor is positional over downloaded-or-played episodes, so a
series that merely STAYED in scope would pick up new episodes correctly.

Separately, `show_missing_episodes` (a Library & Cards display pref) leaks
into the shared episode catalogue, so with it ON, virtual/unaired episodes
become auto-download candidates that fail per-item and burn pass budget
(the gh-225 comment's resource complaint). And in legacy mode
(`auto_download_lookahead = 0`) the take-all candidate set has no
`isPlayed` exclusion at all.

## Decision

**1. The arm IS the policy row — no new store.** gh-181's
`series_download_policies` table becomes the series' auto-download intent
record: a row ("arm") keeps the series in the check's universe regardless
of local files. A separate "watchlist" database would be a second home for
series auto-download intent. The old rationale on
`maybeClearSeriesAutoDownloadState` — an override on a series with nothing
downloaded never joins the universe — is superseded: armed series join it
by construction.

**2. Arm creation: implicit-on-download beside explicit sheet choices.**
Any series-shaped download (manual or auto-enqueued) ensures an arm row;
explicit per-series sheet choices keep working (an explicit NEVER always
un-arms). Without implicit arming, users running the global
INSTANT/ASK mode have no policy row and the bug stays broken for them.
Finished/ended series are harmless as arms: the lookahead anchor sits on
the played last episode and finds nothing after it.

**3. `FOLLOW_GLOBAL` is the arm row's mode.** A new `AutoDownloadMode`
value carried by implicit arm rows meaning "armed, follow the global
preference"; per-series resolution becomes
`row == FOLLOW_GLOBAL ? global : row`. The entity column stores enum-name
strings deliberately (the `downloads.status` convention), so the new VALUE
itself needs no migration: a build that doesn't know the name decodes it
via `toEnumOrNull` to null — "no override", which under old coverage
semantics is inert (fail-safe: no download, no state corruption). The
arm's display name (backfilled from the first series-shaped download's
entity, blank on sheet-only arms) did add a `seriesName` column —
`Migration63To64`, database version 63 → 64.

**4. Scope union happens inside the check.** `AutoDownloadCheck` unions
`downloadCoverage().seriesIds` with the armed series ids.
`DownloadCoverage` keeps its disk-derived semantics — `MediaDownloadActions`
and the home screen consume it as "what's on disk" and must not start
reporting armed-but-empty series.

**5. Un-arm rule: watch-boundary deletions keep the arm; list-management
deletions un-arm at zero rows.** The boundary is the WATCHING FLOW, not
pref-vs-gesture: a user who habitually confirms the #14 delete offer after
each episode is doing the same hygiene act as `auto_delete_after_watch`
automation, and both must keep the weekly arming alive.

- Keep arm: `auto_delete_after_watch` hook, smart-downloads cleanup, the
  #14 post-playback delete offer, the keep-days retention sweep.
- Un-arm at zero rows: Downloads-screen single/bulk delete, offline-library
  series remove, detail/card remove-download.
- Un-arm unconditionally: explicit NEVER in the series sheet.

**6. Candidate hygiene rides the same change.** (a) Played episodes are
never candidates in ANY mode — legacy take-all included; otherwise an
armed, fully-watched, fully-deleted series would re-download its history
(the lookahead ladder already implies this; legacy mode must match).
(b) Virtual/unaired episodes are never candidates (client-side guard),
decoupling download behavior from the `show_missing_episodes` display
pref — closing the gh-225 comment's failing-download noise.

**7. Cadence, sync, and movies are unchanged.** The 6 h periodic pass +
library-scan trigger + manual button stay (a weekly episode lands within
≤6 h of airing). Arms are device-local like downloads themselves — no new
sync namespace (a synced arm would make every signed-in device download a
series it never stored). Movies are deferred: the engine is series-shaped
end to end (seriesId coverage, episode catalogue, positional lookahead
anchor); a movie arm has no anchor concept and is its own vertical.

## Considered options

- **New standalone watchlist database** (the issue's literal request):
  rejected — second home for the same concern; the policy table already
  carries per-series modes, Ask prompts, and scheduler keep-alive.
- **Snapshot the global mode into arm rows**: rejected — flipping the
  global preference later would not discharge old arms (stale intent).
- **Sync arms via a new namespace** (ADR-0011 family): rejected —
  downloads are per-device; cross-device arms surprise storage-wise.
- **Only series-remove un-arms / nothing implicit un-arms**: rejected —
  the first leaves a manually-emptied ongoing show downloading; the second
  leaves zombie arms with no list-management escape hatch.
- **Plugin new-media wake / air-time-aligned scheduling**: deferred — the
  periodic pass fits the weekly use case; revisit if immediacy is asked for.
- **Movies in this change**: rejected — different vertical, no anchor
  concept; noted on the issue as a separate request.

## Consequences

- Armed series are invisible in the downloads list at zero rows, and
  implicit arming makes arms something users never explicitly created —
  the Downloads screen gains an armed-series list (series, resolved mode,
  un-arm) or the state is undiscoverable.
- Scheduler keep-alive (`hasSeriesPolicies`) counts FOLLOW_GLOBAL rows: a
  global NEVER with only implicit arms keeps the periodic pass scheduled;
  the passes are cheap (visit, resolve NEVER, skip) and the per-series
  NEVER rows already had this property.
- Ask-mode prompts for armed series survive at zero rows until answered
  (they used to die with the series' download state).
- Legacy lookahead=0 users see a deliberate behavior change: watched
  episodes stop being download candidates.
- The un-arm verb split (watch-boundary vs list-management) touches the
  delete funnel's callers; the sweep's direct
  `maybeClearSeriesAutoDownloadState` call and the offline-library series
  remove path gain the explicit un-arm.
