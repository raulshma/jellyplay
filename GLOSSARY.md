# JellyPlay glossary

Shared vocabulary for the client codebase. The companion plugin's vocabulary
(contract, capability probe, namespaces, tombstones, dispatch, resilient
fetch…) lives in `jellyfin-plugin-jellyplay/GLOSSARY.md` and is not duplicated
here.

## State sync (client side)

**Sync reservation** — the `jpsync.*` key space: mirrors, cursors, per-namespace
toggles, device id. One module owns the grammar; adapters and the sync engine
acquire keys through it, never through raw string literals. The reservation is
what keeps mirror and cursor state out of every adapter's synced set.

**Sync mirror** — the last-synced snapshot a `ProfileSyncAdapter` compares
against to detect dirty values. Exactly one implementation; every adapter
delegates to it (an adapter hand-rolling the comparison is a defect).

## Plugin client

**Plugin role** — a narrow, consumer-facing interface over the one plugin
client family (ADR-0010 §5): settings sync routes, device registry + push,
analytics, transcodes, ratings, markers. One family implementation satisfies
all roles; consumers and test fakes depend on the one role they use.

## Home

**Home row module** — everything one `HomeSectionType` knows about itself:
descriptor, fetch arm, single-row refresh arm, assembly arm, offline
projection, behind one small interface registered in the home row registry.
The continue-watching rule (played-row exclusion, hidden ids, NextUp merge)
belongs to the ContinueWatching row module alone — online and offline both
consume it from there.

## Playback

**mpv core** — the commonMain implementation of the mpv engine choreography:
load, seek, track selection, subtitle style application, property intake,
ordering rules. Platform code shrinks to a **mpv binding** — an adapter over
the platform handle (Android JNI, desktop libmpv) satisfying the binding seam.
Two bindings make the seam real; a fake binding tests the core without libmpv.

---

Terms above are ratified names from the 2026-10-09 architecture review; see
`docs/adr/` for the decisions that constrain how they are used (0009, 0010,
0011). Status: implemented.
