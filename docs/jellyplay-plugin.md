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
PlaybackStarted / UserLockedOut / Unknown-forward-compatible). Durable
alternatives: inbox messages (`inbox` StateFlow).

## Testing

- `ProfileSyncRepositoryTest` (commonTest) — engine cycle: opt-in gating,
  adopt/push split, LWW-reject retry, convergence.
- `JellyPlayPreferencesSyncAdapterTest` (jvmTest) — real temp-file DataStore:
  dirty semantics, kind preservation, reserved-key exclusion.
- Server side: 46 tests in the plugin repo (LWW, quotas, changelog cursor,
  SSE hub, season grouping, scrapers, circuit breaker, stream fold, strings).

## Status

All planned client surfaces are wired and gated: capability gating, sync
engine + adapter (settings screen section), events/messages inbox, Seerr
bridge mode (with the data path riding the plugin proxy), details
ratings/similar/anime faces, bookmarks sync, admin transcodes monitor,
custom/seasonal home rows, newsletter.

Server plugin: all 12 modules, dashboard (localized config page with a
tri-state client-defaults editor + backup/restore), YAML editor, quotas,
rate limits, scheduled tasks, JF12 reflection provider.

Known-good pairing: Jellyfin 10.11.x hosts; the plugin probes
`jellyplay/capabilities` and everything degrades gracefully on its absence.
