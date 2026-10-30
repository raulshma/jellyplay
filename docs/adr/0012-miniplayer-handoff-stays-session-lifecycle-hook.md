# 0012 — Mini-player handoff stays as the SessionLifecycleHooks gate

- **Status:** accepted
- **Date:** 2026-10-26
- **Scope:** `shared/feature/player-video` — `PlaybackSession`,
  `SessionLifecycleHooks`, `PlayerSessionManager`

## Context

The 2026-10 architecture review flagged the mini-player handoff
(`tryReclaimMiniPlayer` / `onMiniPlayerReclaimed` / `hydrateReclaimedItem` /
`releaseMiniPlayerState`) as a candidate for extraction into a standalone
`MiniPlayerHandoff` module, symmetric with the `SessionPositionLedger` carve
it approved in the same round.

Verification against the source rejected it: the handoff already sits behind
a designed seam. `SessionLifecycleHooks` (PlaybackSession.kt) declares the
reclaim GATE as a hook with a documented rationale — "the gate stays a hook
because the mini-player state it reads is owner-lifecycle knowledge" — while
the reclaim BODY (`loadReclaimedEngine`) stays session-side. The split is
load-bearing, not accidental:

- the gate reads `PlayerSessionManager`'s mini-player state, which is
  owner-lifecycle knowledge the hook implementation already owns;
- the body needs the full session graph (detail fetch, engine bind, media
  session, tracking restart), so a standalone module would take the same
  wide collaborator set the session already wires — a shallow module whose
  interface is nearly as complex as its implementation;
- no drift hazard exists behind it (one implementation, one caller inside
  the module) and no test constructs the session solely to reach the
  handoff.

## Decision

The mini-player handoff stays as the `SessionLifecycleHooks` gate/body
split. Do not re-suggest extracting a `MiniPlayerHandoff` module in future
architecture reviews. If a SECOND reclaim consumer ever appears (e.g. a
desktop picture-in-picture reclaim that is not the mini-player), the seam
gains a second adapter and this ADR should be revisited — one adapter means
a hypothetical seam, two make it real.

## Consequences

- `PlaybackSession` remains the home of the reclaim body
  (`loadReclaimedEngine`); the gate stays on the hooks interface.
- The position/report family DID carve out (`SessionPositionLedger`,
  same review, accepted) — that family passed the deletion test
  independently; this one does not.
