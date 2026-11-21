# 0017 — Settings information architecture: two-level hub, one taxonomy, hidden advanced

- **Status:** accepted and implemented (2026-11-21; design settled in a
  grill-with-docs session, implementation in the same change set)
- **Scope:** `shared/feature/settings` (screens, rows, search catalog),
  `core/ui` settings routes, restore-wizard step derivation
- **Vocabulary:** `GLOSSARY.md` "Settings" section

## Context

Measured inventory (2026-11-21): Playback declares **173 rows** (the visible
Player group alone is 85), Appearance **92 (+11 screen-local**, one 52-row
"Library & Cards" group), Home **66 (+3**, one 43-row "Home Display" group).
The root already is a hub but leaks: TV screensaver (8 rows), desktop
idle-ambient (2) + Discord (1) + shell hooks (6) are inline tuning groups on
it, the companion-plugin section dumps up to 20 rows, and it mixes quick
links and settings entrances. Search (catalog + deep-link-with-highlight +
recents) and the power-user advanced gate already exist and work; the gate
simply tags too little.

## Decision

**IA-only, zero semantic change.** No setting is removed, merged, renamed
functionally, or behaviorally changed; only location, visibility, and
routing move. Keys, backup/sync, reset review, and search totality stay
intact.

1. **Headers + flat entrances root.** The root stays one flat list wrapped
   in non-clickable category headers (Account pinned top; Activity &
   Insights quick links stay; Media & playback; Look & feel; System & data;
   Integrations; About), re-ordered media-first. Two-level category cards
   were REJECTED (one extra click/D-pad step per destination); collapsible
   groups were REJECTED (hidden content + a new collapse-state axis).
2. **Domain hub split.** Screens still over the visible-row bar (~40) after
   gating split into leaf screens behind a hub: Playback → 8 leaves
   (basics, controls & gestures, player chrome, video & transcoding,
   player engines, skipping & segments, syncplay & casting, live TV & DVR),
   Appearance → 4, Home → 3 + advanced group. Row ids never move, so
   search/deep-link/highlight resolution is split-transparent.
3. **Entrance summaries at every hub level.** Each hub entrance carries a
   live current-value summary via per-domain `*SummaryParts` folds
   (SettingsSummaryPart precedent, extended). Hand-written per ADR-0009
   grain; no row-grain registry.
4. **Root leaks fixed.** Plugin section becomes one entrance + screen;
   screensaver + idle-ambient merge into one capability-gated **Ambient**
   screen; Discord presence + shell hooks move into Integrations.
5. **One taxonomy across phone/TV/desktop.** Per-surface differences ride
   `SettingsCapabilities` and the TV fork only — never a second category
   list (today's separate phone/TV entrance step lists converge).
6. **Advanced tier: moderate reclassification behind the existing global
   gate.** The power-user toggle stays THE gate (persisted pref, in-place
   reveal, search auto-arm — all existing behavior); clearly expert/edge
   fine-tuning groups move behind it (home cadence/poll/limit tuning,
   appearance density/limit tuning, network diagnostics, audio gain
   fine-tuning). Conservative (today's tags only) and aggressive
   (first-run-essential-only-visible) were REJECTED.
7. **Wizard mirrors the hub.** The entrance declaration keeps driving both
   hub emission and the restore wizard's step list; regrouping the IA
   regroups the wizard by construction. Reset categories
   (`PreferenceResetCategory`) stay untouched — the hub's visual taxonomy
   is a UI projection, not a reset taxonomy.
8. **No formal governance rule for future settings** — default visibility
   stays a per-case review call (explicit user decision; no mandatory
   tier, no totality ratchet).

## Consequences

- Existing screen routes keep their keys and become hubs; leaf screens add
  new routes; spec `routeKind` is the only row face that moves.
  Catalog/contract/ratchet tests (`FusedRowsRatchetTest`,
  `SettingsCatalogScreenContractTest`, spec derivation tests) update per
  split; the search→screen deep-link totality contract holds by
  construction (row ids unchanged).
- New `*SummaryParts` folds per hub entrance, jvmTest-pinned like the
  existing appearance/experimental folds.
- Adding a setting keeps ADR-0009 economics (group-grain declarations,
  hand-written row bodies). This ADR does NOT reopen ADR-0009's row-grain
  registry prohibition; all new mechanisms are group/screen-grain.
- The gate mechanism itself is unchanged — only the number of rows it
  hides grows. The catalog's item count is NOT pinned by a global number
  here: the per-group size ratchet in `SettingsSearchCatalogTest`
  documents each group, and any count drift fails at its own group (the
  historical marks: 528 items at the split — 513 pre-split + the 15 hub
  entrances; both earlier prose figures, CONTEXT.md's 314 and this ADR's
  interim 258, were stale — and 533 once the group-card hits joined).
- Implementation facts beyond the design-time numbers, pinned by the same
  ratchet: the Player-chrome leaf shipped wholly advancedOnly (one gated
  group; the leaf carries the advanced toggle + hidden hint); Home's hub
  Fine-tuning group is 8 rows; Appearance's Detail-leaf "Density &
  limits" tuning is 13; Storage's "Network diagnostics" is 4; Audio's
  "Fine-tuning" is 7.
- The advancedOnly groups that render a labeled card are searchable as
  ONE group-card hit each (`SettingsSearchItemGroup.cardItem`, declared
  GROUP-grain on the group): id = the group's id, title = the rendered
  card's label resource (no new strings), the hit auto-arms the
  power-user gate, and it lands on the group's FIRST member row id baked
  into its route — expand + scroll + glow ride the existing row-id
  highlight machinery, never a row-grain registry.
