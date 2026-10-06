# JellyPlay companion server plugin

The companion Jellyfin **server plugin** lives in its own repo:
`raulshma/jellyfin-plugin-jellyplay` (.NET 10, Jellyfin 10.11+ hosts). This
doc covers the client side; the wire protocol is specified in the plugin
repo's `docs/CONTRACT.md` and is the single source of truth for both sides.

## What it gives JellyPlay

| Module | Client face |
|---|---|
| Settings/profile sync | `ProfileSyncRepository` — opt-in per-user sync of the `user_prefs` DataStore (mirror-based dirty detection, server-side per-key LWW, per-device profiles `desktop`/`phone`) |
| Events & messages | `JellyPlayEventsRepository` — live SSE events (new media, broadcasts), inbox messages, device registry |
| Seerr bridge | `seerrLogin/Status/Logout` on the api client — server-brokered Seerr SSO incl. Quick Connect; direct client mode remains the fallback |
| Newsletter | The pre-existing `/newsletter/send` + `/newsletter/test` stubs in `MediaInfoApiClient` light up when the plugin is installed and SMTP configured |
| Ratings / rows / anime markers / recommendations / bookmarks / transcodes | Typed endpoints on `JellyPlayPluginApiClient`, gated via the capability registry — wired per feature |

## Client architecture (ADR 0010)

```
shared/core/network
  JellyPlayPluginApiClient (+ Impl over JellyfinRawRequester, hand-rolled SSE)
shared/core/data
  session/JellyPlayPluginStatusStore     — the ONE probe/gate (UNKNOWN/AVAILABLE/UNAVAILABLE)
  repository/ProfileSyncRepository       — sync engine (adapter SPI)
  repository/JellyPlayPreferencesSyncAdapter — user_prefs DataStore adapter
  repository/JellyPlayEventsRepository   — SSE stream + inbox + device registration
shared/core/model
  JellyPlayPluginStatus, JellyPlayPluginFeatures (wire-key mirror)
```

Gating rule: feature code checks `statusStore.hasFeature(JellyPlayPluginFeatures.X)`
(reactively: collect `features`). Nothing else may consume raw probe results.

## Settings sync semantics

- Opt-in; default off. Identity-scoped (sign-out/user/server switch resets).
- Cycle: pull `resolved/{profile}` → ADOPT clean keys the server won → PUSH
  locally-dirty keys (server applies per-key LWW; rejects retry next cycle).
- Local dirty detection is mirror-based (`jpsync.mirror.*` keys inside the
  same DataStore, reserved, never synced).
- Kind preservation: a synced key keeps its local DataStore type (an `Int`
  stays `Int`) — the adapter coerces remote primitives; new keys take the
  incoming kind.
- Never synced: secure stores, identity, byte arrays, `dream`/`screensaver`
  namespaces (per-device by definition), the `jpsync.*` reserved prefix.

## Live events

`JellyPlayEventsRepository.start()` registers the device, then holds the
events SSE stream with linear→60s backoff. Events decode into
`JellyPlayPluginEvent` (NewMedia / Broadcast / SessionStarted /
PlaybackStarted / UserLockedOut / Unknown-forward-compatible). Durable
alternatives: inbox messages (`inbox` StateFlow).

## Testing

- `ProfileSyncRepositoryTest` (commonTest) — engine cycle: opt-in gating,
  adopt/push split, LWW-reject retry, convergence.
- `JellyPlayPreferencesSyncAdapterTest` (jvmTest) — real temp-file DataStore:
  dirty semantics, kind preservation, reserved-key exclusion.
- Server side: 30 tests in the plugin repo (LWW, quotas, changelog cursor,
  SSE hub, season grouping, scrapers, circuit breaker).

## Status / next steps

Wired end-to-end: capability gating, sync engine + adapter, events repo, DI.
Remaining UI work (per-feature wiring consumes the pieces above):

- DONE: Settings screen "Sync across devices" section (opt-in toggle + sync-now,
  capability-gated group on the root screen; opt-in persisted device-scoped)
- DONE: Seerr settings "via server" mode (capability-gated "Direct connection |
  Via JellyPlay server" selector, `seerrStatus` linked-state pane, Quick
  Connect link flow reusing the Jellyfin QC primitives, logout; mode persisted
  as `SeerrPreferences.useServerBridge`). Remaining seam: the Seerr DATA PATH
  still talks to the Seerr server directly — the follow-up reads that flag to
  point `SeerrRepository`/`SeerrApiClient` at the plugin's
  `jellyplay/seerr/{**path}` proxy (the flag's KDoc names the consumer seam).
- Messages inbox + new-media notifications into `NotificationStore`/widgets
- Ratings row, anime badges, custom/seasonal home rows, similar-items on
  details, bookmarks in the book reader, admin transcodes tab
