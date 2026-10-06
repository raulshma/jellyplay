# 0010 — Companion server plugin contract (jellyfin-plugin-jellyplay)

- **Status:** accepted
- **Date:** 2026-10-06
- **Scope:** `shared/core/network`, `shared/core/data`, future feature modules;
  companion repo `raulshma/jellyfin-plugin-jellyplay`

## Context

JellyPlay ships features that need a server-side counterpart the stock
Jellyfin API cannot express (settings/profile sync, Seerr SSO bridging,
newsletter send, aggregated ratings, anime markers). The ecosystem pattern is
a companion server plugin consumed exclusively by one client
(jellyfin-plugin-streamyfin, Moonfin). The plugin lives in its own repo
(.NET 10, Jellyfin 10.11 ABI) and ships independently of the app.

Two prior in-repo precedents anticipated this: the newsletter routes are
already stubbed client-side ("lights up once the route lands"), and the
playback-reporting / Intro Skipper integrations established per-plugin probe
gating.

## Decision

**1. The plugin is optional, always.** Every plugin-backed feature gates on a
capability probe — `GET jellyplay/capabilities` — surfaced through ONE
owner: `JellyPlayPluginStatusStore` (the `PlaybackReportingStatusStore`
pattern: StateFlow, session-reset via `SessionCacheRegistry`, probe failure =
UNAVAILABLE, never an error surface). Clients never build feature logic on
per-endpoint 404s.

**2. The contract is versioned by an integer + feature keys.**
`{contractVersion, features[], pluginVersion}`. Additive changes don't bump;
renames/removals/semantic changes bump. A client that receives a contract
major it doesn't know reports UNAVAILABLE. Feature keys are the stability
boundary: no behavior change under an existing key without a bump. The full
wire spec lives in the plugin repo's `docs/CONTRACT.md`; the client mirrors
the feature keys in `JellyPlayPluginFeatures`.

**3. Settings sync is opaque server-side.** The plugin stores per-user,
per-profile JSON blobs it never parses; schema versions and migrations are
client-owned. Conflict rule: per-key last-write-wins server-enforced, equal
timestamps reject (no oscillation). Client-side, the sync engine
(`ProfileSyncRepository`) is adapter-based — stores opt in by implementing
`ProfileSyncAdapter`; local change detection is mirror-based (last-synced
snapshot), never wall-clock-based. Sync is opt-in per user, identity-scoped,
and never carries credentials or tokens.

**4. Events ride SSE, not push.** v1 delivery is while-the-app-is-running
only (settings stream + events stream over the raw requester, hand-rolled
SSE parse). The durable counterpart is the plugin's inbox messages. No FCM /
relay infrastructure exists client-side; adding background push later means
a new capability key, not a contract break.

**5. Network access goes through one family.** All plugin routes ride
`JellyPlayPluginApiClient` (commonMain interface, jvmShared impl over
`JellyfinRawRequester`), registered in the `JellyfinApiClient` composite —
the same one-family-per-server-surface idiom as every other client family.

**6. Future in-app extension SPI.** The capability registry
(`features: StateFlow<Set<String>>`) is deliberately the ONLY gating
mechanism. A future in-app extension system plugs in at the same seam:
extensions declare a feature key, register against the registry, and are
gated identically. Nothing else may read raw probe results.

## Consequences

- The app stays fully functional against stock servers; plugin features are
  additive light-ups.
- Cross-repo drift is guarded by the contract handshake, not lockstep
  releases.
- The first UI consumer (settings "Sync across devices" section) and the
  per-feature UIs (ratings rows, anime badges, messages inbox) consume the
  stores/repos built here; they are wired feature-by-feature.
- Plugin repo owns its CI, manifest, and ABI upgrades; the client tracks
  only the contract.
