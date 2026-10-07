# 0011 — State surfaces, background sync, silent push, and the bookmarks migration

- **Status:** accepted
- **Date:** 2026-10-07
- **Scope:** `shared/core/data` (engine, adapters, workers), `shared/core/network`
  (SSE resume, caps), `shared/feature/settings` (sync screen); plugin
  counterpart: schema v7 (tombstones, registry, quotas)

## Context

ADR 0010 left settings sync as ONE namespace (`prefs`) driven by one engine,
and the reader's bookmarks on a dedicated per-feature route trio
(`GET/POST/DELETE jellyplay/bookmarks/{itemId}`). Four more user-state
surfaces now want to roam (search history, continue-watching removals, reader
annotations, home layout), the plugin gained tombstones + a device registry +
per-namespace quotas (its ADR-0005), and the engine had no story for the
windows where neither the foreground nor the SSE stream runs (backgrounded
process, screen off).

## Decision

**1. Every state surface rides the general protocol — one engine,
adapter-per-namespace; no more dedicated per-feature routes.** The dedicated
bookmark route proved the ceiling: its fixed DTO drops the EPUB CFI on the
wire (`position`/`chapterIndex` only), it has no deletes, no quotas, no
conflict surface, and no selective sync. The general protocol gives all of
those to every surface for free; an adapter is one class over a store the
feature already owns. The namespace census (keys, values opaque to the
server, server-side byte caps per the plugin contract):

| Namespace | Key | Value | Server cap |
|---|---|---|---|
| `prefs` | DataStore key | the pref value | 3 MB |
| `books` | `{itemId}/{positionTicks}` | full bookmark payload incl. CFI | user total |
| `search` | `sha1(query)` | `{query, searchedAt}` | 64 KB |
| `cw` | `hidden/{itemId}` | `true` | 64 KB |
| `reader` | `ann/{itemId}` | the book's annotation array | 1.5 MB |
| `homelayout` | the store's 6 canonical `home_*` layout keys | the store's structured JSON | 64 KB |

The sha1 search key keeps raw queries out of route-logged URL patterns and
the key-size budget; per-item `reader` keys bound the blast radius of the
free-form annotation TEXTs; `cw` and `homelayout` are pure client-side
overlays over native data (augment-native: the server's watch state and the
server-driven row content never roam through them).

**2. Tombstones are adapter SPI, default off.** `ProfileSyncAdapter` gains
`deletedKeys()` (outbound — mirror holds the key, store no longer does → the
engine pushes a null-value `deleted: true` write) and `deleteRemote(keys)`
(inbound — the delta's `deleted[]` rows; removes the local value AND its
mirror entry so an adopted delete never resurrects or re-reads as dirty).
Both default to nothing: for `prefs` (and `homelayout`) a removed key means
"reset to default here", never "delete everywhere". `books`, `search`, `cw`
and `reader` — stores where rows are real user data — override both.
One deliberate exception to the default: `prefs` overrides `deleteRemote`
ALONE — an inbound prefs tombstone (the server-side namespace reset, or the
conflict-resolution confirm path, which can push a tombstone even for prefs)
adopts as "reset to default here": the local value and its mirror entry go,
so the adopted delete neither resurrects nor re-reads as a pending edit.
`deletedKeys` stays the SPI default — prefs never pushes tombstones.

**3. Selective sync is the reserved device-local keys.** Per-namespace
toggles persist as `jpsync.ns.enabled.<ns>` prefs (missing = enabled) inside
the same reserved `jpsync.*` prefix that keeps mirrors and cursors out of
every adapter's synced set — device-local by construction, so a phone can
sync annotations while the TV does not. The ENGINE honors the toggle at both
faces of a cycle (no dirty collection AND no adopt), the adapters stay
toggle-blind, and compile-time exclusions still win.

**4. Reader annotation backup is the ONE default-off namespace.** ADR 0003
made marks local-first; backup must never change that. The `reader` toggle
defaults off — the sync screen's namespace row IS the opt-in — and on it
behaves exactly like every other namespace (best-effort push, failures are
the engine's ordinary retry, never a reader error or a blocked local op).

**5. Background sync mirrors the playback-sync worker conventions.**
`SettingsSyncWorker` runs one `requestSync` through
`UniqueWorkSchedules.uniqueOnce` (KEEP, `NetworkType.CONNECTED`, never
expedited — house convention), triggered by the app-background edge, the
network-reconnect edge, and the stores' dirty-write seams; a 12h periodic
catch-up is armed only while sync is enabled. The engine's own gates stay
authoritative — a disabled or plugin-absent run is a cheap success. Desktop
flushes in-process (`DesktopSettingsSyncScheduler`: start, reconnect,
dirty-write, window-focus; periodic is a deliberate no-op). The scheduler is
consumed through an interface resolved via `getOrNull` from the jvmShared
graph — commonMain stays platform-blind — and the whole family starts from
the deferred background-scheduler startup group, so nothing in the eager
graph depends on a platform worker module (no Koin cycle).

**6. Silent push is caps-gated, data-only, and folds into the engine.** The
plugin sends kind `sync-nudge` only to devices whose registered `caps`
include `"silent-push"` — the registry replaces caps on every registration,
so the client asserts `CAP_SILENT_PUSH` on every `registerDevice`. The gate
exists for OLD clients: a client that renders unknown kinds as visible
notifications never advertised the cap, so it can never be nudged into
posting garbage. On receipt the UnifiedPush receiver calls `requestSync`
directly — never a notification, gated on the sync toggle (not the push
toggle).

**7. SSE resume closes the reconnect gap.** Settings-stream event ids are
anchored to the change-log head; the client persists the last-seen id per
user, sends it as `Last-Event-ID` on every (re)connect, and persists the
delta-sweep cursor (`jpsync.cursor.*`, per-identity) so a restart resumes
mid-log instead of re-reading. The events-stream replay ring is best-effort;
the inbox reconcile after reconnect stays the durable rule.

## Considered options

- **Dedicated routes per new surface** (the bookmarks pattern): rejected —
  N× wire surface, no roaming deletes or quotas, and the CFI loss showed
  fixed DTOs rot as features grow.
- **Server-side typed parsing of the new namespaces**: rejected — values stay
  opaque per ADR 0010 §3; the server enforces byte caps, not schemas.
- **Always-on push as the trigger**: rejected — plugin push is explicitly
  fire-and-forget; WorkManager + SSE + the nudge cover the windows.
- **Reader backup default-on**: rejected — ADR 0003 heritage; opt-in is the
  contract.

## Consequences

- One cycle, one screen, one conflict surface cover every surface; a new
  surface is one adapter + (optionally) a server quota entry.
- The bookmarks migration is staged: the `books` adapter roams full payloads
  now, while the legacy dedicated routes and `BookmarksSyncRepository` stay
  wired for one release (compat ladder); retiring them is a contract-bump
  window decision, not a client-only flip.
- The `tv` device profile exists server-side but no client claims it yet —
  Android TV binaries ride `phone` until a form-factor provider seam exists.
- Namespace keys are a cross-repo convention: the census lives in
  `docs/jellyplay-plugin.md`, the quotas in the plugin's CONTRACT.md; there
  is no generator pinning them (unlike the settings catalog) — additions are
  additive wire, renames would be a contract bump.
