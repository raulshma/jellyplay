# Coil wasm memory-cache eviction + non-Jellyfin hosts — verification result (wave 20B)

**Verdict: both remaining honest cuts are closed with committed, re-runnable
e2e evidence.** Under 1.47× decode-size pressure the Coil wasm memory cache's
strong LRU evicts and stays byte-bounded (max observed 80,209,200 ≤ the
measured 80,530,636-byte cap; plateau at exactly 5 × 14,745,600 bytes), and
Coil's ktor3 fetcher loads cross-origin artwork from a non-Jellyfin origin
that sends CORS headers. One honest negative surfaced: a revisit of an
LRU-evicted poster can still be **resurrected by Coil's WeakMemoryCache**
even after pane disposal and three forced full GCs — the LRU eviction is
real, but "evicted ⇒ next request re-fetches" is not guaranteed on this
platform. Cross-reload persistence remains impossible by upstream design
(no disk cache on wasm).

## Background

Wave 18A soak-verified the memory cache's HIT behavior (50 navigation
cycles: cold = 2 fetches, every warm cycle served from memory, heap/DOM
flat) and left two cuts recorded in `apps/web` Main.kt's loader-factory
comment: large-library LRU eviction ("the fixture has exactly one poster —
arithmetic, not measurement") and non-Jellyfin image hosts. This wave grew
the fixture and added a third e2e lane to measure both.

## Fixture arithmetic (and two traps found while building it)

`tools/e2e/bootstrap-jellyfin.sh` now generates 8 additional movies
("Cache Probe Clip 1..8", 3 s testsrc2 clips) each with a DISTINCT
2560x1440 poster (hue-rotated testsrc2 JPEGs, 119–142 KB on disk):

- decoded entry = 2560 × 1440 × 4 = **14,745,600 bytes**
- 8 × 14,745,600 = **117,964,800 bytes** = **1.47×** the measured
  wasm memory-cache cap (`maxSizePercent` = 15% of Coil's 512 MiB wasm
  budget = **80,530,636 bytes**) — so a sequential full-decode pass must
  evict, and the most-recently-resident set is exactly
  ⌊80,530,636 / 14,745,600⌋ = 5 entries.

Traps measured while wiring the fixture (all three fixed in the script):

1. **Jellyfin's file parser splits "Name (Year).mp4"** into
   Name="Cache Probe Clip 1" + ProductionYear=2026 — an exact-name lookup
   keyed on the filename stem never matches. The lookup now passes display
   names.
2. **The library scan EXTRACTS a primary image from the video's own
   pixels** (8853-byte frames appeared on freshly scanned items), so a
   "has any primary" check would skip uploading the large posters and
   silently defeat the sizing. `ensure_primary_image` now byte-compares
   the SERVED primary against the intended poster file and re-uploads on
   any mismatch (idempotent: equal size = no-op).
3. **A RE-CREATED container with a completed-wizard config still serves
   `startupwizardcompleted:false` for a startup window** (intermittent,
   ~1-in-3 re-runs measured): acting on the first false reading runs the
   wizard into 401s and FATALs before poster repair — the "fixture rot"
   previous waves hit. The script now re-verifies a false reading across
   a settle window; only a STABLE false runs the wizard. (Two
   consecutive re-runs after the fix: exit 0, zero uploads, identical
   item ids.)

## Method

- Diagnostics pane (wave 20B cards, appended BELOW the pane's Back button
  so no earlier lane's click geometry moves): "Probe all" enumerates the
  library (limit 20) and loads every video item's Primary poster
  sequentially through the app-wide loader at `Size.ORIGINAL` (an
  explicitly-sized request keeps Coil from constraint-sampling — the
  memory-cache entry must BE the 14.7 MB bitmap). Per settled item the
  pane renders the audit line `CACHE_PROBE: idx=<i>/<n> item=<name>
  state=<OK|ERR>`; "Revisit #1" re-requests item[0]'s poster
  (`CACHE_REVISIT: state=OK|ERR`); the gated `?foreignImage=` boot param
  loads a non-Jellyfin image through both the raw painter and the shared
  `MediaImage` (`FOREIGN_HOST: OK|ERR|skipped (no param)`). All new AX
  strings are append-only; existing strings untouched.
- Second origin: `tools/e2e/foreign-origin.mjs` (serve.mjs fork) serves a
  posters dir on 127.0.0.1:8599 with `Access-Control-Allow-Origin: *` (a
  different port is a different origin, so the browser fetch inside Coil's
  KtorNetworkFetcherFactory runs in CORS mode). The lane generates the
  foreign poster itself (smptebars 1600x900 — distinct from every fixture
  poster) and kills the server by PID.
- Lane: `tools/e2e/web-cache-eviction.mjs` — web-verify's CDP machinery
  (AX tree → backendDOMNode → DOM.getBoxModel → synthetic click), a TALLER
  window (1400×2000; below-fold nodes report zeroed boxes — see
  docs/e2e/web-input-dead-region.md) plus a wheel-scroll fallback, a
  step-by-step ledger, and exit codes preserved through no pipes.

## Measured results (two consecutive PASS runs, Jellyfin 10.11.11)

| measurement | run 1 | run 2 |
|---|---|---|
| probe items settled OK | 9/9 (8 large + harness) | 9/9 |
| probe COIL_STATS deltas | misses+9 net+9 hits+0 fail+0 | identical |
| max observed COIL_CACHE size | 73,728,000 (= 5 × 14,745,600) | 80,209,200 (5 large + foreign + small entries co-resident) |
| COIL_CACHE maxSize | 80,530,636 | 80,530,636 |
| revisit outcome (after pane-fresh + 3× forced GC) | weak-hit: hits+1 misses+0 net+0, cache size unchanged | identical |
| final COIL_STATS | hits=5 misses=13 net=13 fail=0 | identical shape |
| FOREIGN_HOST | OK; 2 × status-200 cross-origin responses observed | identical |

The counter accounting closes exactly: 13 misses = 4 first-entry loads
(pane artwork painter + MediaImage + foreign painter + foreign MediaImage)
+ 9 probe posters; 5 hits = the same 4 loads served warm on pane re-entry
+ the weak-layer revisit. `net==misses` on every cold load re-confirms
18A's no-disk-cache reality.

## Findings

1. **LRU eviction is real and byte-bounded (measured).** 117,964,800
   decoded bytes of distinct posters flowed through; the strong cache
   plateaued at exactly the arithmetic 5-entry set (73,728,000) and never
   exceeded maxSize on any 400 ms sample of either run.
2. **Evicted entries CAN be resurrected by the weak layer (measured,
   honest negative).** Coil's StrongMemoryCache demotes every evicted
   value into WeakMemoryCache (a JS `WeakRef` over a `JsReference` —
   coil3's wasmJs `WeakReference` actual). The lane's revisit of the
   first-loaded (LRU-evicted) poster HIT even after the Diagnostics pane
   was popped (dropping every painter/frame it holds) and three
   `HeapProfiler.collectGarbage` rounds: the evicted bitmap remained
   reachable through something outside the pane. The retention source is
   UNATTRIBUTED (candidates: V8 wasm-gc externalized-wrapper pinning
   through the JsReference, or a skiko-level registry). Corroboration
   that it was the weak layer and not strong residency: the revisit hit
   left COIL_CACHE byte-identical (73,728,000 → 73,728,000) while the
   plateau arithmetic excludes item[0] from the 5-entry strong set.
3. **Full-decode sizing requires an explicit request size.** The probe
   requests set `Size.ORIGINAL`; a constraint-resolved load would sample
   to the 300×170 box (~0.2 MB entries) and the pass would never press
   the cache.
4. **Cross-origin artwork works when the origin allows it (measured).**
   The pane's foreign card decoded and rendered the :8599 poster through
   the same loader (KtorNetworkFetcherFactory over the browser fetch
   engine); the CDP Network domain observed both fetches (status 200).
   This is the CORS-positive half — origins WITHOUT
   Access-Control-Allow-Origin remain untested (expected to fail the
   fetch; not asserted).
5. **Cross-reload persistence stays impossible (upstream design).**
   wasm has no Coil disk cache (`singletonDiskCache()` is null), so
   nothing survives a page reload except whatever the browser HTTP cache
   does with server headers (18A measured fromDiskCache=false).

## Actions taken

- `tools/e2e/bootstrap-jellyfin.sh`: 8-probe-clip library + name-scoped
  id lookup + byte-compared poster replacement + the wizard-artifact
  re-verify (idempotent re-runs; stderr-only logging so command
  substitution cannot capture log lines — a first-run bug where ITEM_ID
  became "<log>\n<id>").
- `apps/web` WebDiagnostics.kt: the gated CacheProbe/ForeignHost cards
  (new AX strings append-only; card placement below Back preserves the
  older lanes' geometry — web-verify re-run 31/31 PASS and web-soak
  re-run 50/50 cycles PASS after the change: zeroRefetchAfterCold=true,
  heap ratio 0.997, warm cycles all hits).
- `tools/e2e/foreign-origin.mjs` + `tools/e2e/web-cache-eviction.mjs`
  (self-contained: spawns/kills both servers and Edge by PID).
- README web-capability line and Main.kt's loader-factory comment updated
  to the measured truth (this document holds the numbers).

## Reproduction

```bash
tools/e2e/bootstrap-jellyfin.sh                 # idempotent; 9 items, 8 large posters
./gradlew :apps:web:wasmJsBrowserDevelopmentWebpack   # fresh dist (stale-dist trap)
node tools/e2e/web-cache-eviction.mjs           # exit 0 = PASS
```

Raw evidence shape: `result.json` + `eviction.png` in the run's temp
out-dir (never committed): step ledger, per-item probe lines, COIL
deltas, max observed cache size, revisit outcome classification, foreign
responses, console error inventory.
