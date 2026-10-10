# JellyPlay companion server plugin

The companion Jellyfin **server plugin** lives in its own repo:
`raulshma/jellyfin-plugin-jellyplay` (.NET 10, Jellyfin 10.11+ hosts). This
doc covers the client side; the wire protocol is specified in the plugin
repo's `docs/CONTRACT.md` and is the single source of truth for both sides.

## What it gives JellyPlay

| Module | Client face |
|---|---|
| Settings/state sync | `ProfileSyncRepository` — opt-in per-user sync of six namespaces (`prefs`, `books`, `search`, `cw`, `reader`, `homelayout`) over adapter SPI (mirror-based dirty detection, server-side per-key LWW + tombstones, per-device profiles `desktop`/`phone` — TV binaries ride `phone` until a form-factor seam exists) |
| Sync observability | Sync screen (`JellyPlaySyncScreen`): server status + quota bars, history ledger with per-key diffs, namespace toggles, conflicts, devices, restore points, export/import |
| Background sync | `SettingsSyncWorker` (WorkManager) / `DesktopSettingsSyncScheduler` — app-background, reconnect, dirty-write and 12h-periodic flushes |
| Silent push | `sync-nudge` data-only push (caps-gated) folded into a sync cycle — never a notification |
| Events & messages | `JellyPlayEventsRepository` — live SSE events (new media, broadcasts; `Last-Event-ID` ring replay), inbox messages, device registry |
| Seerr bridge | `seerrLogin/Status/Logout` on the api client — server-brokered Seerr SSO incl. Quick Connect; direct client mode remains the fallback |
| Newsletter | The pre-existing `/newsletter/send` + `/newsletter/test` stubs in `MediaInfoApiClient` light up when the plugin is installed and SMTP configured |
| Ratings / rows / anime markers / recommendations / bookmarks (legacy) / transcodes | Typed endpoints on `JellyPlayPluginApiClient`, gated via the capability registry — wired per feature |

## Client architecture (ADR 0010, ADR 0011)

```
shared/core/network
  JellyPlayPluginApiClient (+ Impl over JellyfinRawRequester, hand-rolled SSE
  with Last-Event-ID resume)
shared/core/data
  session/JellyPlayPluginStatusStore     — the ONE probe/gate (UNKNOWN/AVAILABLE/UNAVAILABLE)
  repository/ProfileSyncRepository       — sync engine (adapter SPI, tombstones, selective sync, conflicts)
  repository/JellyPlay*SyncAdapter       — one adapter per namespace (prefs/books/search/cw/reader/homelayout)
  repository/JellyPlayEventsRepository   — SSE stream + inbox + device registration
  repository/JellyPlayLiveResyncConnector — settings SSE stream folding into requestSync
  worker/SettingsSyncScheduler(/Worker)  — background flush family (Android WorkManager / desktop in-process)
shared/core/model
  JellyPlayPluginStatus, JellyPlayPluginFeatures (wire-key mirror)
shared/feature/settings
  JellyPlaySyncScreen(/ViewModel)        — the sync surfaces' one UI (TV variant collapses to d-pad essentials)
```

Gating rule: feature code checks `statusStore.hasFeature(JellyPlayPluginFeatures.X)`
(reactively: collect `features`). Nothing else may consume raw probe results.

## Settings/state sync semantics

- Opt-in; default off. Identity-scoped (sign-out/user/server switch resets).
- **Cycle** (`runCycle`, serialized by one mutex, gated on probe +
  `settings-sync`):
  1. **resolved pull** — `GET settings/resolved/{profile}` (merged
     base + profile overlay + admin defaults, with the additive `modes` map
     the forced-key locks read);
  2. **adopt** — clean keys (not locally dirty) where remote differs are
     applied and marked synced; keys with a pending local delete are excluded
     so the tombstone push lands first (no resurrect);
  3. **push** — dirty values plus `deletedKeys()` as `deleted: true`
     (null-value) tombstone writes; only APPLIED keys mark synced, so
     `stale-write` rejects retry next cycle and converge;
  4. **delta sweep** — paged `GET settings/changed?since=<cursor>` following
     `nextCursor` to the end: adopts the freshness tail the resolved pull
     missed and applies `deleted[]` through the adapters' `deleteRemote`.
     The per-user cursor (`jpsync.cursor.*`, reserved) advances only on a
     fully drained sweep.
- Local dirty detection is mirror-based (`jpsync.mirror.<ns>.*` keys inside
  the user-prefs DataStore, reserved, never synced): current ≠ mirror ==
  dirty. Never wall-clock-based.
- **Tombstones** (plugin schema v7): local removals roam out
  (`deletedKeys`) and server deletions roam in (`deleteRemote` removes the
  local value AND its mirror entry). Default-off SPI — `prefs`/`homelayout`
  treat a removed key as "reset to default here"; `books`/`search`/`cw`/
  `reader` treat rows as real data and roam deletes both ways.
- **Namespaces** (one adapter each; values opaque to the server):

  | ns | key | value | server cap |
  |---|---|---|---|
  | `prefs` | DataStore key | the pref value | 3 MB |
  | `books` | `{itemId}/{positionTicks}` | full bookmark payload incl. EPUB CFI | user total |
  | `search` | `sha1(query)` | `{query, searchedAt}` (client keeps its 50-entry cap) | 64 KB |
  | `cw` | `hidden/{itemId}` | `true` (the home overlay the CW/Next-Up pipelines filter on) | 64 KB |
  | `reader` | `ann/{itemId}` | the book's whole annotation array (opt-in, default off) | 1.5 MB |
  | `homelayout` | `home_enabled_section_types`, `home_section_order`, `home_library_section_overrides`, `pinned_home_sections`, `home_discover_rows`, `home_layout_presets` | the store's structured JSON (layout only — row content stays server-side) | 64 KB |

- **Selective sync**: per-namespace device-local toggles under the reserved
  `jpsync.ns.enabled.<ns>` prefs (missing = on; `reader` is the one
  default-off). Honored at both faces of a cycle — a disabled namespace's
  pending local state parks untouched until re-enabled.
- Kind preservation (prefs): a synced key keeps its local DataStore type (an
  `Int` stays `Int`) — the adapter coerces remote primitives; new keys take
  the incoming kind.
- Never synced: secure stores, identity, byte arrays, `dream`/`screensaver`
  namespaces (per-device by definition), the `jpsync.*` reserved prefix.
  The exclusions live at the adapter's registration (core/data DI): the
  owning stores export `SyncExcludedKeys` sets (`SecurityStore`,
  `PinRateLimiter`, `ServerIdentityStore`) so renames stay in lockstep, and
  the prefixes come from one shared `PreferenceSyncPolicy` constant. The
  adapter enforces the exclusions in BOTH directions — excluded and reserved
  names are dropped on inbound `applyRemote`/`markSynced` too, so a server
  still holding pre-exclusion leaked rows (or a hostile one) cannot write
  secrets, identity, or mirror state back onto a device. Servers that synced
  before these exclusions existed may still hold leaked rows server-side; an
  admin namespace reset (`DELETE jellyplay/settings/prefs`) clears them.
- Non-retryable rejects — `clock-skew` (device clock ahead of the server) and
  `device-revoked` (this device was revoked server-side) — surface in the
  sync state's error face instead of silently retrying. `stale-write`
  rejects become conflict entries with ours/theirs previews; "keep mine"
  re-pushes with a fresh stamp, "take theirs" adopts the current server row.
- **Live re-sync**: `JellyPlayLiveResyncConnector` holds the settings SSE
  stream while sync is enabled, folding `settings.changed`/`settings.reset`
  (debounced) into `requestSync`. Every (re)connect sends the last-seen
  event id as `Last-Event-ID` — the plugin anchors settings-stream ids to
  the change-log head and replays the gap.

### Background triggers

Android: `SettingsSyncWorker` — one WorkManager one-shot (`uniqueOnce`:
KEEP idempotent, `NetworkType.CONNECTED`, never expedited — house
convention) running one cycle; enqueued on the app-background edge, the
network-reconnect edge and the stores' dirty-write seams, plus a 12h
periodic catch-up armed only while sync is enabled. Desktop
(`DesktopSettingsSyncScheduler`): flushes in-process on start, reconnect,
dirty-write and window-focus; the periodic backstop is a deliberate no-op.

### Silent push (`sync-nudge`)

The plugin nudges devices with the registered `"silent-push"` cap when
settings change and no SSE subscriber is live. The client asserts the cap on
every `registerDevice` (caps are replaced, not merged, server-side); on
receipt `JellyPlayUnifiedPushReceiver` calls `requestSync` directly — the
kind never reaches the notification dispatcher, and it gates on the sync
toggle, not the push toggle. Old clients that never advertised the cap are
never nudged (the plugin contract's compat gate).

### Sync UI surfaces (feature/settings)

`JellyPlaySyncScreen` is the one roof: opt-in toggle + sync now / force
re-pull, error banner, server-side usage (quota bars per namespace,
`sync/status`), per-namespace selective-sync toggles with pending counts,
the conflict list (ours/theirs, keep-mine/take-theirs), the device registry
(rename this device, revoke others — revoke is a wipe server-side),
`sync/history` with per-key diffs, restore points (`settings/snapshots`
list/create/restore) and settings export/import (share JSON / file pick).
TV collapses to status + sync-now + namespace toggles.

### Settings catalog (the plugin's known-keys list)

The dashboard's client-defaults editor picks keys from a catalog the PLUGIN
serves (`GET jellyplay/settings/catalog`) — admins never hand-type `ns/key`
strings or raw values. The catalog is generated FROM THIS REPO:

```
./gradlew :shared:core:datastore:generateSettingsCatalog
```

walks the `PreferenceSpec` declarations (the same rows the stores persist
through) and writes `jellyfin-plugin-jellyplay/src/Jellyfin.Plugin.JellyPlay/
Resources/jellyplay-settings-catalog.json`, which the plugin embeds and
serves. `checkSettingsCatalog` (wired into `check`) fails CI when the
committed artifact is stale. Coverage: the six spec-backed domains plus a
small supplemental allowlist (`volume_profiles`,
`remember_volume_per_content_type`) — stores still on hand-written `Keys`
objects join the catalog as they migrate to specs. Secrets and identity keys
are denylisted at the generator (hard error, not a skip); the denylist is
DERIVED from the same `SyncExcludedKeys` sets the sync adapter excludes, and
the excluded prefixes come from the same shared `PreferenceSyncPolicy`
constant, so the catalog cannot drift from what actually syncs.

## Live events

`JellyPlayEventsRepository.start()` registers the device, then holds the
events SSE stream with linear→60s backoff. Events decode into
`JellyPlayPluginEvent` (NewMedia / Broadcast / SessionStarted /
PlaybackStarted / UserLockedOut / Unknown-forward-compatible). A reconnect
sends `Last-Event-ID` — the plugin replays what its per-user ring still
holds (best-effort; the inbox reconcile stays the durable path). Durable
alternatives: inbox messages (`inbox` StateFlow).

## Testing

- `ProfileSyncRepositoryTest` (commonTest) — engine cycle: opt-in gating,
  adopt/push split, LWW-reject retry, tombstone adoption/anti-resurrection,
  pagination loop, cursor resume, selective sync, conflicts, convergence.
- Adapter tests (jvmTest) — real temp-file DataStore / Room: dirty
  semantics, kind preservation, reserved-key exclusion, per-namespace key
  schemas and delete roaming.
- Server side: ~330 tests in the plugin repo (LWW + tombstones, quotas,
  changelog cursor, SSE hub + ring replay, pagination, registry
  revoke/wipe, snapshots, export/import, season grouping, scrapers,
  circuit breaker, stream fold, strings) — `ContractTruthApiTests` pins
  CONTRACT.md's route table against the controllers.

## Status

All planned client surfaces are wired and gated: capability gating, sync
engine + six namespace adapters (settings screen section + dedicated sync
screen with TV variant), events/messages inbox, Seerr bridge mode (with the
data path riding the plugin proxy), details ratings/similar/anime faces,
custom/seasonal home rows, newsletter, background sync family, silent push.

Server plugin: all 12 modules, dashboard (localized config page with a
tri-state client-defaults editor + backup/restore + the sync drill-down/
live-monitor/preview pages), YAML editor, quotas (per-user + per-namespace),
rate limits, scheduled tasks, JF12 reflection provider.

Known-good pairing: Jellyfin 10.11.x hosts; the plugin probes
`jellyplay/capabilities` and everything degrades gracefully on its absence.
