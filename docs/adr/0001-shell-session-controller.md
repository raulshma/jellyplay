# 0001 — Shell session-policy wiring moves to a shared `ShellSessionController`

- **Status:** accepted (landed 2026-09-07)
- **Date:** 2026-09-07
- **Scope:** `:app` (Android shell), `:apps:desktop` (desktop shell), `shared/feature/shell`

## Context

CONTEXT.md's navigation section recorded a deliberate decision: admin/logout/homeMode
policy stays per-shell — "one consumer per policy, and forcing it through would drag
`AuthRepository` into a 'shared' module for one shell's sake." That rationale was
written when the desktop shell was a thin preview.

The desktop alpha channel made the rationale stale. `DesktopAppRoot.kt:340–418` now
carries the Android `MainViewModel`'s session duties inlined ("the Android shell's
MainViewModel duties, inlined for desktop (no desktop MainViewModel exists)" — its own
comment), so the same wiring exists twice: ~80–100 lines per shell for admin-refresh
arbitration, homeMode collect/persist, logout, and update-check→message mapping. The
Android copy is covered by `MainViewModelTest`; the desktop copy is composable-inline
and untested. `AdminRefreshGate` (shared/feature/shell) already crosses the seam —
only the wiring *around* the policy stayed duplicated.

## Decision

Reverse the per-shell ruling for the *wiring* (not the policy): a commonMain
`ShellSessionController` lives in `shared/feature/shell` — the recorded shell-policy
home — owning admin-status state + `AdminRefreshGate` arbitration, homeMode
collect/persist, logout, and update-check→message mapping, over a
constructor-injected scope with the repository collaborators passed as plain
flows / suspend lambdas — no Koin binding, the same direct construction
`AdminRefreshGate` already had, so the module keeps its repository-free
dependency set. Each shell constructs it directly and serves its
`ShellHostHooks` from it: desktop wires the hooks straight off the controller;
Android's `MainViewModel` delegates to it behind its existing public surface.

The desktop auto-update ADR is unaffected: this decision moves wiring, not update
strategy. The desktop update surface stays inert-per-`999999.0.0`-sentinel until its
revisit triggers fire; the controller only maps a completed check to shell messaging.

## Considered options

- **Keep per-shell wiring** (status quo): the duplication is now two real consumers,
  one untested; the recorded rationale no longer holds.
- **New `shared/core/session` module**: no dependency fact demands a new module;
  `shared/feature/shell` is already the shell-policy home.
- **Absorb into `ShellHostHooks`**: rejected in the original ruling for the right
  reason — hooks are a registration surface, not a behaviour owner; a controller
  beside them keeps the hook interface narrow.

## Consequences

- Platform-conditional blocks stay put: rail, media-key bridge, video-surface probe,
  desktop nav saved-state configuration, and the Android `MainViewModel`'s other
  duties (server/user bootstrap, auth timeouts) are not absorbed.
- Desktop's stranded session choreography becomes jvmTest-covered through the
  controller interface.
- `MainViewModel` shrinks by delegation; its tests follow the moved members.

## Revisit triggers

- If the controller accretes UI-shaped concerns (snackbar text, dialog gating), split
  the surface back out per shell and keep only the decision core shared.

## Amendment (2026-09-24) — dependency set, not signature

The RealtimeSessionController wiring was hand-duplicated per shell (same 12-param
lambda bundles in `SessionCoordinator` and composable-inline in `DesktopAppRoot`).
A `create(...)` factory beside the controller now owns the wiring, which required
typing three collaborators concretely (`RealtimeConnection`, `RemoteControlReceiver`,
`ServerIdentityStore`) — so `shared/feature/shell` gained `implementation` deps on
`:shared:core:data` and `:shared:core:datastore`. The recorded *signature* rule
still holds — no `*Repository` type crosses any surface; auth seams stay plain
flows and suspend lambdas, construction stays direct, no Koin — but the module's
dependency set is no longer repository-free in the Gradle sense. `RemoteControlReceiver`
and `ServerIdentityStore` are final classes without narrow commonMain interfaces;
the transport-surface dependency (`RealtimeConnection`'s KDoc: "split out of
AuthRepository so the session shell depends on the transport surface alone") is the
recorded intent this amendment makes explicit.
