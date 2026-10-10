# 0005 — Desktop single-instance guard; no OS protocol registration in v1

- **Status:** accepted (guard landed 2026-10-01; the registration decision is
  recorded-as-rejected for v1); **amended 2026-10-04 — contended launches now
  hand off open-with links** (see Amendment below)
- **Date:** 2026-10-01
- **Scope:** `apps/desktop`

## Context

Android is `singleTask` and declares `jellyplay://`, the https app link, and
`ACTION_SEND`. The desktop shell had none of this: a second launch of the
installed app started a second JVM over the same `DesktopPaths` config/data
dirs, making DataStore, Room, `window-state.properties`, and the crash marker
multi-writer — the process-scale version of the corruption class
`DesktopWindowStateStore`'s atomic-move handling guards against. Separately,
desktop consumes the shared deep-link grammar only in-process (Discord Rich
Presence join), while OS-level protocol/file registration was simply absent —
undocumented, therefore indistinguishable from an oversight.

## Decision

1. **One JVM per config dir, enforced by an OS file lock.**
   `DesktopSingleInstanceGuard` takes `FileChannel.tryLock` on
   `<configDir>/single-instance.lock` before `startKoin` and holds the handle
   for process lifetime. A contended launch prints a clear message and exits 0.
   A kernel-tracked lock (not a PID file) is the point: the OS releases it on
   process death, so a crashed run cannot strand a stale lock. Activating the
   existing window on contention is out of scope for v1.
2. **No OS protocol registration or file associations in v1.** Desktop entry
   stays in-process (launch + Discord join). The shared link grammar already
   runs on desktop, so a future registration is additive wiring in
   `nativeDistributions` (`fileAssociations`/protocol config) plus a lock-aware
   handoff to the running instance — not a grammar change. Recording the
   absence here makes it a decision rather than a gap.

## Consequences

- `DesktopSingleInstanceGuardTest` drives real cross-process contention
  (child-JVM holder): fresh acquire, contended rejection, post-release
  acquisition, and post-death re-acquisition are pinned.
- CI lanes and packaging are unaffected; the guard is a few lines at the top
  of `Main.kt`.
- If window-activation handoff lands later, it replaces the exit-0 path and
  this ADR gets an amendment, not a rewrite.

## Amendment (2026-10-04) — contended launches hand off open-with links

Commit 6b463a9c3 (desktop open-with links) added a lock-aware handoff: a
contended second launch whose argv carries parsable links forwards them to
the running instance through `DesktopOpenRequestChannel.enqueue`
(`apps/desktop/.../Main.kt`, after the guard acquires) and then exits, so
`jellyplay://`/https/file opens reach the live window instead of dying with
the second process. A plain contended launch with no link argv still prints
the message and exits 0 — decision 1 is unchanged on that path, and window
activation on contention remains out of scope. Decision 2 also stands: no OS
protocol/file registration was added; the handoff serves the in-process link
grammar on desktop.
