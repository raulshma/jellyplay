# Web input "dead region below y≈600" — investigation result (wave 17A)

**Verdict: the claimed CMP-wasm input dead region does not exist.** Synthetic
CDP clicks (`Input.dispatchMouseEvent`) were measured delivering everywhere
inside the viewport — including every row spanning and beyond the claimed
y≈600 boundary, at device scale factor 1 and 1.5 — on a clean-room Compose
pane driven by `tools/e2e/input-probe.mjs`. The wave-16 observation that
motivated the claim is attributed to contamination: that wave's
`SeerrDetailViewModel` construction crash froze composition after the
demo-button click landed, so every subsequent click hit a dead UI at any y.
The residual real geometry effect — clicks into below-the-fold content do
not deliver — is plain browser viewport behavior, not a Compose bug.

## Background

Wave 16 recorded (commit e44eb5c46's lane change): synthetic clicks below
y≈600 in a 1400×900 window on the Diagnostics pane "never reached Compose",
"reproduces with the video host hidden", "a pre-existing CMP-wasm input
quirk". The lane was therefore switched to a gated `?e2eRoute=` boot param.
Standing contradiction: the same lane's step 12 clicks the Diagnostics
pane's **Back** button — the pane's lowest interactive element — and passes
30/30, before and after.

Ranked suspects going in: (1) `DOM.getBoxModel` returns device px while
`Input.dispatchMouseEvent` expects CSS px (at DPR 1.5 a CSS center y>600
dispatches outside the 900px window — 900/1.5=600 exactly); (2)
ComposeViewport canvas hit-test region = viewport/dpr; (3) canvas height
frozen pre-navigation in `--headless=new`; (4) a DOM occluder; (5) the whole
observation was contaminated by the (now-fixed) wave-16 Koin crash that
froze composition.

## Method

`?e2eRoute=inputprobe` boots `WebInputProbePane` standalone (no NavDisplay,
no session gate, no server, no Coil, no video host) — an 18-row button
lattice (row centers ≈ y = 24 + i·50 CSS px) with per-button AX-visible
click counters (`P<i>: <n>` StaticTexts outside the buttons).
`tools/e2e/input-probe.mjs` drives it through web-verify's exact CDP
machinery (Cdp class, Edge spawn flags, serve.mjs staging) across a window
height × `--force-device-scale-factor` × plain/scroll matrix, measuring:

- **ENV** — `devicePixelRatio`, inner/outer window size, canvas inventory
  (`querySelectorAll('canvas')` + rect vs bitmap attributes),
  `document.elementFromPoint` at six depths, body-child inventory
  (occluder hunt: tag/rect/pointerEvents/zIndex).
- **CLICK** — every PROBE button through four coordinate modes:
  - `quad` — `DOM.getBoxModel` border-quad center (web-verify's `centerOf`,
    verbatim);
  - `quaddpr` — the same center divided by measured `devicePixelRatio`;
  - `rect` — `getBoundingClientRect()` center of the button's own DOM node
    (unavailable: see F5);
  - `layout` — first-principles CSS coords from the pane's fixed geometry.
  Each dispatch is asserted via the `P<i>: <n>` counters; increments of
  OTHER counters are recorded (the mis-scale signature).
- **SCROLL** (scroll variant) — a 600px `mouseWheel`, then a post-scroll
  click.

Edge: `--headless=new`, Windows 10.0.26200 host at 150% display scaling,
Microsoft Edge 151.0.4129.107, Compose Multiplatform ui 1.12.0 (wasmJs).

## Environment table (measured)

| config | devicePixelRatio | window-size | innerHeight | canvas elements | body children |
|---|---|---|---|---|---|
| H900-default | 1 | 1400,900 | 805 | **0** | one DIV 0,0 1370×809, pe=auto, z=auto |
| H900-default-scroll | 1 | 1400,900 | 805 | 0 | same |
| H1800-default | 1 | 1400,1800 | 1705 | 0 | same (taller) |
| H900-dsf1 | 1 | 1400,900 | 805 | 0 | same |
| H900-dsf15 | **1.5** | 1400,900 | 805 | 0 | same |
| H1800-dsf15 | **1.5** | 1400,1800 | 1705 | 0 | same |

Three env facts kill three suspects outright:

- **DPR**: `--headless=new` reports `devicePixelRatio = 1` by default on
  this 150%-scaled host; 1.5 appears only under
  `--force-device-scale-factor=1.5`. The lane spawns Edge without the flag,
  so the "device-px at DPR 1.5" mechanism (suspect 1) could never have
  applied to its runs.
- **No canvas**: there is no `<canvas>` element anywhere in the document in
  CMP 1.12.0 web — the app lives in a single full-viewport DIV and material
  semantics as real DOM nodes (that is where the AX tree's
  `backendDOMNodeId` boxes come from). Suspect 2 (canvas hit-test region =
  viewport/dpr) has no canvas to be true of. Suspect 3 (frozen canvas
  height) likewise.
- **No occluder**: exactly one body child, `pointer-events: auto`, no
  z-index games; `elementFromPoint` returns that app DIV at every probed
  depth inside the viewport (100/300/590/610/700 — and 850 only when
  innerHeight = 1705) and `none` below the fold. Suspect 4 is out.

## Click matrix (measured)

`quad` (raw `DOM.getBoxModel` centers — the lane's machinery):

| config | result | lowest delivered y |
|---|---|---|
| H900-default | 17/18 ok | **803.5** (viewport is 805) |
| H900-default-scroll | 17/18 ok | 802.5 |
| H1800-default | 6/6 ok | 874 |
| H900-dsf1 | 5/6 ok | 774 |
| H900-dsf15 | **5/6 ok** | **774** |
| H1800-dsf15 | **6/6 ok** | **874** |

Rows 12–15 — centers y = 624/674/724/774, squarely across the claimed
y≈600 boundary — delivered in every config, at DPR 1 and 1.5. The only
`quad` miss at window height 900 is row 17: content below the fold. Its
AX box is **zeroed at (0,0)** by the browser (the quad literally reports
x=0 y=0), so the dispatch lands at the origin and hits nothing — see F2.
(The first-principles `layout` mode, which cannot see clipping, also
missed the below-fold row 16 at y=824.)

`quaddpr` (the suspect-1 "fix" — divide by DPR):

| config | result |
|---|---|
| H900-dsf15 | 1/6 ok, 4 wrong-row |
| H1800-dsf15 | 1/6 ok, 5 wrong-row |

Dividing CSS-true centers by DPR **misclicks**: intended row 5 landed on
P3, 11→P7, 12→P8, 15→P10, 17→P11 — each delivered y is exactly
intended/1.5 (274→182.7, 574→382.7, 624→416, 774→516, 874→582.7). The
only successes are rows whose divided y still falls inside the SAME top
row (row 0: 24/1.5 = 16). See F3.

`layout` (first-principles CSS coords): 16/18 at window height 900 (the
same two below-fold rows; the formula also drifts ~0.5–1px per row against
measured boxes), 6/6 at height 1800. Good enough to confirm delivery, not
good enough to replace measurement — same conclusion the scroll phase
reaches: after a 600px `mouseWheel` (which DID scroll the pane), a
computed post-scroll coordinate clicked P12 instead of the intended P17.

## Findings

1. **No dead region (measured).** Synthetic clicks deliver at every
   in-viewport y in every config — up to y=803.5 of an 805px viewport at
   DPR 1, and through the claimed y≈600 boundary at DPR 1.5. There is no
   boundary at 600 and none at viewport/dpr.
2. **The only hard boundary is the viewport bottom (measured).**
   `--window-size=1400,900` yields `innerHeight = 805` (headless "new"
   subtracts ~95px of virtual browser chrome — the effective viewport was
   never 900 tall). Content laid out below the fold in a non-scrollable
   column reports a zeroed AX box (row 17: quad center exactly (0,0)) and
   clicks there never deliver; raise the window to 1800 (inner 1705) and
   the same row delivers at y=874. A partially-visible row (16) is clipped
   to the viewport edge (box center 803.5) and the click at the clipped
   center still delivers.
3. **`DOM.getBoxModel` returns CSS px here — the suspect-1 direction is
   exactly backwards (measured).** Raw quad centers are CSS-true and
   deliver correctly at DPR 1.5 (5/6; the one miss is the below-fold
   (0,0) box). Dividing by DPR produces the wrong-row signature above.
   web-verify.mjs's `centerOf` + `Input.dispatchMouseEvent` machinery is
   correct as-is; "fixing" it to divide by devicePixelRatio would have
   introduced real misclicks. (Mechanism: CDP input and box-model
   coordinates both live in CSS/client space on this Edge/CDP stack, with
   DPR handled internally at the browser layer.)
4. **Headless DPR default (measured).** `--headless=new` on this host
   reports `devicePixelRatio = 1` regardless of the OS 150% scaling; 1.5
   requires `--force-device-scale-factor`. Suspect 1's precondition
   (lane running at DPR 1.5) never held.
5. **No `<canvas>`, no occluder (measured).** CMP 1.12.0 web renders into
   a plain DIV with materialized DOM semantics; single body child,
   `elementFromPoint` clean at all in-viewport depths.
6. **Wheel input delivers (measured).** The scroll variant's 600px
   `mouseWheel` scrolled the pane (post-scroll click landed on a row
   consistent with a large scroll), i.e. scrollable containers receive
   synthetic input too; only the post-scroll coordinate COMPUTATION was
   wrong (first-principles guess without re-measurement).

## Root cause of the original wave-16 observation (inference, labeled)

The five suspects above are eliminated by measurement; what remains is
suspect 5, for which the timeline evidence is:

- The wave-16 lane change (e44eb5c46) landed together with the fix for a
  real `SeerrDetailViewModel` **death-by-construction** crash: narrow
  MediaRepository's `userDataChanges` `val` override eagerly initialized
  with the off-web throw, so ANY composition of the SeerrDetail screen
  threw `InstanceCreationException` and killed composition — before the
  fix, clicking the demo button (whose entire purpose is navigating to
  that screen) froze the UI at the moment of delivery.
- The probe shows a delivered click cannot silently do nothing on a live
  UI; a frozen UI makes every later click — at any y, Back included —
  appear dead, which matches "clicks below y≈600 died (Back died with
  it; video host display:none'd changed nothing)".
- The same lane's step-12 Back click — the pane's lowest element — has
  passed 30/30 before and after the claim, which the claim can only
  survive if Back's y sat above the frozen moment or above 600; today it
  demonstrably delivers.

Reconstruction: the wave-16 demo-button click delivered, navigated, and
the construction crash froze composition; subsequent probes of lower
elements hit the frozen UI and the y-coordinate pattern was overfit to
where the attempted targets happened to sit, with headless viewport
geometry (805, not 900) and below-fold zero-boxes blurring the picture.
Direct re-measurement (this probe) cannot reproduce any dead region, so
the quirk is retracted rather than characterized.

## Actions taken

- The gated `?e2eRoute=` boot param **stays** (all three boot routes): a
  lane that never needs click coordinates cannot regress with them. This
  is now lane hygiene, not a workaround.
- web-verify.mjs's click machinery is **unchanged** — measurement proved
  it correct (and proved the DPR-division "fix" harmful). Only its
  step-26 HISTORY comment was corrected.
- The probe pane (`WebInputProbe.kt`, `?e2eRoute=inputprobe[&variant=scroll]`)
  and driver (`tools/e2e/input-probe.mjs`) are landed as regression
  tooling: re-run `node tools/e2e/input-probe.mjs` after any
  input-pipeline change and compare against the tables above.
- The four stale comment sites were corrected to this document's findings:
  `WebAppRoot.kt` (backStack note), `WebDiagnostics.kt` (demo-button
  KDoc), `Main.kt` (`parseE2eBootRoute` KDoc), `web-verify.mjs` (step-26
  HISTORY).

## Reproduction

```bash
./gradlew :apps:web:compileKotlinWasmJs :apps:web:wasmJsBrowserDevelopmentWebpack
node tools/e2e/input-probe.mjs            # full matrix, exit 0 = complete
node tools/e2e/input-probe.mjs --configs H900-dsf15,H1800-dsf15   # the DPR experiments
```

Raw evidence shape: `input-probe-result.json` in the run's temp out-dir
(never committed): per-config env block, per-click {mode, i, x, y, ok,
hitOther}, scroll phase.
