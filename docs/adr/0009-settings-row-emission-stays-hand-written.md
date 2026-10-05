# 0009 — Settings screens keep hand-written emission bodies; rows own faces, admission and totals

- **Status:** accepted and implemented (2026-10-05;
  implementation in the same change set: `subtitleRes` landed with the
  screen migrations, totals derive via `rowTotalFor`, the contract tests
  assert naive derivations)
- **Date:** 2026-10-05
- **Scope:** `shared/feature/settings` (screens, `SettingsRow`,
  row-admission totals, catalog contract tests)

## Context

The settings feature's
add-a-setting cost (13 production + 3 test files for one row, traced
through 691dc91db) was measured on 2026-10-05, and the first proposal
was "row-driven emission": screens render
from the ordered row list instead of hand-writing per-row emission
blocks. Follow-up fact-finding measured what emission actually is:

- ~367 emission executions across 17 screens; ~60% static subtitles,
  ~38% dynamic value reads (preference current-values, on/off resource
  pairs chosen by state, `displayName` enums), ~3% none.
- Four `Setting*Item` variants with per-row payloads: toggles carry
  `checked`/`onCheckedChange`, picker rows open `PickerState` sheets
  capturing `viewModel.edit` closures, reorderables carry drag callbacks
  with manual indexing, accent pickers render custom content.
- Structural exceptions: engine rows render one prefix-branch at a time,
  `ResetEngineDefaults` is declared once but emitted three times, and
  highlight has variant forms (`THEME_HIGHLIGHT_IDS` sets, screen-local
  string ids, the root screen's local `lastClickedSettingId`).

## Decision

**Rejected: a row-driven emission loop (a per-row content-slot registry
rendered by one iterator).** The registry's interface — one composable
slot per row id, registered per screen — would be nearly as complex as
the implementation it replaces (the emission bodies themselves); by the
deletion test, deleting the registry just returns the bodies inline
without concentrating complexity. The loop cannot absorb dynamic value
subtitles, picker payloads, branch-duplicated rows or reorderable
indexing without growing arms equivalent to today's hand-written blocks.
Screens are behaviour holders (pickers, onClick, trailing values) and
stay hand-written.

**Accepted: the three shallow dualities around emission are deepened
onto the row declaration.**

1. `SettingsRow` gains an optional screen-face `subtitleRes` (default
   null, source-compatible — nothing pins the field set). Static
   subtitle rows take their subtitle from the row; dynamic value
   subtitles stay at emission sites (they read state, not resources).
2. Row-admission totals derive from one projection: `rowTotalFor`
   everywhere it is derivable; the wrapper functions die where the
   declaration supports the count, and the documented non-derivables
   (storageNetwork's literal, content-gated theme terms, screen-local
   +1 terms like the cache info row) stay explicit at their screens.
3. The contract tests replace hand-maintained absolute totals with
   assertions derived from the declarations
   (`rows.count { admitted(flags) }`); the `undeclaredExceptions` map
   and screen-local +1 literals remain hand-written (they are the pin),
   and the 65-line archaeology comment in `SettingsSearchCatalogTest`
   is deletable.

## Consequences

- Adding a setting row stops touching three test files for integer
  bumps; the admission-wiring pins (which rows drop under which flags)
  survive as derived assertions over the same seam screens consume.
- The emission order guarantee stops being convention-only where totals
  derive from the declared lists — order and admission have one home.
- Future proposals: do not re-propose an emission loop or
  slot registry for settings screens; the measured cost of the maximal
  version buys a shallow module. A future proposal must start from why
  the three dualities above are insufficient.
