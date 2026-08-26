# Spike w-10C: `shared/core/ui` wasmJs target — feasibility

**Verdict: GO WITH ITEMS — M1 (JVM-in-commonMain extraction), S1 (expect/actual wasm actuals), M2 (markdown-renderer 2.4-ABI wall), S2 (LRU/synchronized seams), S3 (model jvmShared leakage). No hard-missing artifacts remain once the JB nav3-ui fork substitution is applied; everything else is bounded mechanical work.**

Question: can `shared/core/ui` gain a wasmJs target today, and what exactly stands
between the web shell and rendering real shared UI?

Method: Maven directory/metadata ground truth first (repo1 + dl.google.com direct
probes), then compiler truth in this worktree — `wasmJs { browser() }` added to
`shared/core/ui/build.gradle.kts` and driven through four rounds of
`:shared:core:ui:compileKotlinWasmJs` (Kotlin 2.3.21 / CMP 1.11.1 / AGP 9.3.1,
unchanged toolchain). All experimental build edits were reverted before commit;
this branch carries only this report.

---

## 1. Missing-artifact table

| Coordinate · version | Needed for | Status | Evidence URL |
|---|---|---|---|
| `androidx.navigation3:navigation3-ui:1.1.5` (google) | NavDisplay in commonMain | **NO WEB TARGETS AT ALL** — module metadata offers android aar, `jvmStubs`, `linuxx64Stubs` only | https://dl.google.com/android/maven2/androidx/navigation3/navigation3-ui/1.1.5/navigation3-ui-1.1.5.module |
| `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1` (JB fork) | same, on web | **GO** — root `.module` advertises a wasmJs variant (`available-at` redirect); real klib bytes exist at the pinned version | https://repo1.maven.org/maven2/org/jetbrains/androidx/navigation3/navigation3-ui-wasm-js/1.1.1/navigation3-ui-wasm-js-1.1.1.klib |
| `androidx.navigation3:navigation3-runtime:1.1.5` (google) | NavBackStack/rememberNavBackStack/NavKey | GO — full KMP publication incl. sibling `navigation3-runtime-wasm-js`; note its wasm module *publishes Gradle dependencyConstraints on google `-ui`* (metadata, not a hard edge — but every real consumer of core/ui has `-ui` in-graph, so the substitution need stands) | `…/navigation3-runtime/1.1.5/navigation3-runtime-1.1.5.module` → available-at `navigation3-runtime-wasm-js` |
| `androidx.paging:paging-common:3.5.0` | paging state layer | GO — declares wasmJs variant, klib bytes verified (200) | https://dl.google.com/android/maven2/androidx/paging/paging-common-wasm-js/3.5.0/paging-common-wasm-js-3.5.0.klib |
| `androidx.paging:paging-compose:3.5.0` | `LazyPagingItems` in core/ui commonMain | GO — same shape | https://dl.google.com/android/maven2/androidx/paging/paging-compose-wasm-js/3.5.0/paging-compose-wasm-js-3.5.0.klib |
| `androidx.paging:paging-testing:3.5.0` | tests | GO — same shape | `…/paging-testing-wasm-js/3.5.0/paging-testing-wasm-js-3.5.0.klib` |
| `io.coil-kt.coil3:coil-compose:3.5.0` | AsyncImage/ImageRequest in commonMain | EXISTS but unusable — resolves onto classpath then is **silently skipped by the KLIB loader** ("Incompatible ABI version 2.4.0"), reproduced in our own R2 log | klib present: https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose-wasm-js/3.5.0/coil-compose-wasm-js-3.5.0.klib |
| `io.coil-kt.coil3:coil-compose(-core):3.4.0` | same post-wave-10B pin | GO locally — flipping the catalog pin to 3.4.0 removed all coil ABI warnings and coil references resolved (local-experiment-only evidence; wave-10B owns that pin) | https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose-wasm-js/3.4.0/coil-compose-wasm-js-3.4.0.klib |
| `com.mikepenz:multiplatform-markdown-renderer(+m3):0.43.0` | MarkdownText in core/ui commonMain | **NEW WALL (not previously catalogued)** — wasm-js klibs exist but are built with Kotlin **2.4.0** → skipped by our loader exactly like coil 3.5.0 | https://repo1.maven.org/maven2/com/mikepenz/multiplatform-markdown-renderer-m3-wasm-js/0.43.0/multiplatform-markdown-renderer-m3-wasm-js-0.43.0.klib (exists, but ABI-skipped) |
| `com.composables:icons-tabler-outline-cmp:2.2.1` | icon set | GO — `-wasm-js` klib published (200), module declares wasm platform | https://repo1.maven.org/maven2/com/composables/icons-tabler-outline-cmp-wasm-js/2.2.1/icons-tabler-outline-cmp-wasm-js-2.2.1.klib |
| `androidx.lifecycle:lifecycle-viewmodel(runtime-compose):2.11.0` | collectAsStateWithLifecycle etc. | GO — wasm-js klibs on google maven | `…/lifecycle-viewmodel-wasm-js/2.11.0/lifecycle-viewmodel-wasm-js-2.11.0.klib` |
| CMP compose.runtime/ui/foundation/animation/material3/components.resources (1.11.1 / m3 alpha07) | everything | GO — already proven by apps/web consuming designsystem on wasm; R2+ resolved them without complaint | n/a |

Plan §1.3's paging claim verifies TRUE at the exact pinned 3.5.0 coordinates.
The plan's W-phase nav3 picture gets one correction and one confirmation:
google `-ui` ships no web stubs either (only android + desktop stubs), and the
JB fork covers web from every release line back through 1.0.0-alpha01 — the
pinned 1.1.1 included, so no bump of `jbNavigation3Ui` is needed for wasm.

## 2. Compiler round log (all rounds reverted; conclusions marked ⚙=compiler-ground, 🌐=artifact-listing-only)

- **R0 baseline**: module has android+jvm only; commonMain deps include google
  `navigation3-runtime`, google `navigation3-ui`, `paging-compose`,
  `coil-compose`, mikepenz x2, tabler icons.
- **R1** — add `wasmJs { browser() }`, nothing else: dependency resolution fails:
  `Could not resolve androidx.navigation3:navigation3-ui:1.1.5 … No matching
  variant … required 'wasm'/'js'`. Required-by chain shows the failure would hit
  even consumers that only want the runtime:
  `project ':shared:core:ui' > androidx.navigation3:navigation3-runtime:1.1.5 > navigation3-runtime-wasm-js:1.1.5`. ⚙
- **R2** — add the apps/desktop-style graph-wide `dependencySubstitution`
  (`androidx.navigation3:navigation3-ui` → JB fork 1.1.1) inside the module:
  resolution succeeds (fork wasm klibs load cleanly — no ABI warning for them);
  compilation ICEs: `FileAnalysisException … NavKey.kt … NullPointerException:
  null cannot be cast to non-null type FirRegularClassSymbol`, preceded by
  `KLIB loader: Incompatible ABI version 2.4.0` skips of coil x2 and
  markdown-renderer x2. ⚙
- **R3** — catalog flip `coil = "3.4.0"` (TEMPORARY ONLY): coil warnings gone,
  **but** the two mikepenz wasm klibs are *also* 2.4-built, and a transitive
  dep evicts `kotlin-stdlib-wasm-js` to **2.4.0**, which the loader then skips
  too — the missing stdlib is what crashes FIR. Same ICE. ⚙
- **R4** — additionally `force("org.jetbrains.kotlin:kotlin-stdlib(:-wasm-js):2.3.21")`
  (TEMPORARY ONLY): frontend survives, yielding the complete diagnostic
  inventory below (109 errors). Confirms the ICE was purely stdlib-eviction
  fallout, not a code defect in NavKey.kt. ⚙

## 3. Failure inventory grouped by fix class (R4, 109 diagnostics)

**A. JVM-platform APIs used directly in commonMain (~84 errors) — extract or rewrite**
- `DateFormatHelper.kt` (27): `java.text.SimpleDateFormat`, `java.util.Locale/Date`, `ThreadLocal`.
- `DurationFormatter.kt` (27): `java.time.OffsetDateTime/LocalDateTime/ZoneId`, `System.currentTimeMillis`, `SimpleDateFormat`.
- `JellyPlayPreferenceTheme.kt` (11): `System`, `Calendar`.
- `String.format` static calls (9 across `CardBadges`, `SubtitleResultMetadata`, `FormatFileSize`, `MediaPreviewOverlay`) — JVM-stdlib extension absent on wasm.
- `AmbientColorBackdrop.kt` (3): explicit `Math.` usage.
- `SeerrMediaCard.kt` (3): `java.time.LocalDate`.
- `YearRangePresets.kt` (4): `Calendar` (+ regex MatchGroup typing fallout).
Fix template already exists in-repo: `shared/core/model`'s `jvmShared`
intermediate source set, with pure-Kotlin wasm replacements where needed.

**B. JVM-intrinsic seams inside hand-rolled caches (16 errors) — size S**
- `BlurHashImage.kt` (8) and `PlatformDominantColor.kt` (8):
  `kotlin.synchronized(lock)` is not an intrinsic on wasm, and
  `LinkedHashMap(cap, loadFactor, accessOrder=true)` (the java.util access-order
  constructor) does not exist there — wasm sees only exotic overloads, hence the
  odd argument-mismatch reports. Small local rewrite (plain field + copy-aware
  LRU or expect-provided lock primitive).

**C. Kotlin-2.4-ABI klibs skipped by the 2.3.21 loader (4 errors here, class-broken without force)**
- coil 3.5.0 — known; wave-10B fixes via the 3.4.0 pin (verified working locally).
- **mikepenz multiplatform-markdown-renderer 0.43.0 — newly discovered** in this
  spike; same disease, and it additionally drags `kotlin-stdlib 2.4.0` over the
  toolchain stdlib unless forced/excluded, which Ices the whole compile. Work
  item: find the last 2.3-built mikepenz release (mirroring the 10B method) or
  ride the future toolchain bump; note the stdlib-eviction hazard in whichever
  module consumes it.

**D. Cross-module jvmShared leakage (4 errors) — size S**
- `SubtitleResultMetadata.kt`: `SubtitleLanguageCodes` comes from
  `shared/core:model`'s android+jvm-only `jvmShared` set, so it simply doesn't
  exist for wasm consumers. Needs those tables promoted to model commonMain or
  given the pure-Kotlin wasm replacement its build file already anticipates.

**E. Residual single error**
- `NavKey.kt:555` — `VIDEO_TOP_LEVEL_ROUTES.keys.union(…)` infers `Set<Any>`
  under wasm. Likely inference fall-out of A/B; recheck after those land (S).

**F. Expect/actual gaps — 10 declarations have android+jvm actuals only**
Not surfaced as compiler errors in R4 because FIR never reached the
missing-actual phase while A–C were failing; enumerated by source scan instead:
`JellyPlayBackHandler`, `bestDateTimePattern(Locale)`, `rememberIs24HourFormat`,
`imeDialogProperties()`, `HideDialogSystemBars()`, `logUiWarning`, `logUiDebug`,
`argbPixelsToImageBitmap`, `extractDominantColor(PlatformContext, String)`,
`KeyEvent.toDpadKeyEvent()`. Most have trivial wasm shapes (jvmMain already
shows the pattern: dominant color returns null; `toDpadKeyEvent()` returns null;
`argbPixelsToImageBitmap` maps to Skiko-in-Browser via `Image.makeRaster`-equivalent).
Classification: trivial-to-S each except possibly BackHandler on browser
(browser history integration decides depth).

**G. AndroidMain-bound files staying put (zero cost)**
Android Palette pipeline (`PlatformDominantColor.android` uses `android.graphics`),
`nativeKeyCode` D-pad mapping, system-bar hiding — these live in androidMain
already by construction; they do not appear as blockers, only as wasm-side
"no-op/null" behaviors per jvmMain precedent.

Not blockers (verified loading or configured fine in R2+): compose-resources
9-locale trees with generated `Res` accessors, tabler icons, serialization on
NavKey hierarchy, window-size-class derivation off `LocalWindowInfo`,
`LocalIsOnline.kt` composition locals (pure compose).

## 4. Estimated work items

| Item | Scope | Notes |
|---|---|---|
| S1 — nav3-ui substitution recipe for web | S | mirror apps/desktop's graph-wide substitution in apps/web (and any ui-consuming wasm module until convention-level config); no version bump needed |
| S2 — expect/actual wasmJsMain set | S–M | ~10 small actuals; Skia raster bitmap path is the only non-trivial one |
| M1 — JVM-API extraction (class A) | M | two heavy files (DateFormatHelper, DurationFormatter) plus eight light ones (the String.format row spans 4 files); kotlin-datetime or pattern-localized formatting decision needed |
| S3 — LRU/synchronized seams (class B) | S | BlurHash + DominantColor caches |
| M2 — markdown renderer 2.3-built pin hunt (class C) | M | same archaeology as coil/10B; mind the kotlin-stdlib eviction trap; if none exists: defer MarkdownText behind seam or await toolchain 2.4 move |
| S4 — model language tables to wasm reach (class D) | S | model-module change, unblocks SubtitleResultMetadata |
| S5 — recheck NavKey union inference (class E) | S | expected to vanish |

## 5. Web UI beyond shell — implication sketch (outline only)

Given the above, `apps/web` rendering real shared UI needs, in order:
1. **NavDisplay**: after the S1 substitution, `MainContent`'s existing
   `Route : NavKey` stack renders from the JB fork's wasm klib against google's
   wasm runtime. Watch item: the fork's desktop inertness assumptions don't all
   transfer (browser back gesture vs JellyPlayBackHandler — see S2).
2. **Composition locals provisioning**: `LocalNetworkStatus`/`LocalServerHealth`
   (commonMain `LocalIsOnline.kt`) are pure compose locals needing a wasm
   provider — navigator.onLine event wiring is enough for v1.
3. **Images**: post-10B coil 3.4.0 + `coil-network-ktor3` +
   `setSingletonImageLoaderFactory(KtorNetworkFetcherFactory(HttpClient(Js)))`
   per the existing placeholder in apps/web `Main.kt`; 3.4.0 availability on
   wasm is machine-verified here but the pin itself is 10B's deliverable.
4. **Player route**: `shared/core/player-contract` is already wasm-visible;
   `HtmlVideoEngine` implements the contract and Route.VideoPlayer mounts the
   shared screen once items S2/M1/S3 clear. Open hole: fullscreen/orientation
   control has no browser equivalent in the current contract seams.
5. **Paging screens**: nothing extra — 3.5.0 wasm klibs resolve; only careful
   `LazyPagingItems` recompose behavior on scroll-jank-prone browsers needs a
   look when list screens land.

Honest open holes: not attempted here — running any shared UI component in a
real browser, wasm binary-size budget, and keyboard-focus parity for TV-style
D-pad components on desktop-class web layouts.

## 6. Assumption log / evidence provenance

- Conclusions resting on **local-experiment-only state** (all reverted from this
  branch): coil 3.4.0 wasm resolvability (R3/R4), the markdown 0.43.0 ABI +
  stdlib-eviction mechanics (R3/R4), and the NavKey ICE being stdlib fallout
  (R4). The last 2.3-built mikepenz version was NOT determined.
- Artifact-table rows rest on repository listings/metadata fetched 2026-08-27
  (URLs above); "200" checks confirm bytes exist, not their ABI (except where
  the compiler told us in R2–R4).
- The exact missing-actual error set (class F) is source-derived, not
  compiler-derived, because the compile never reached that phase.
- Not verified from this machine: whether google plans web variants of
  navigation3-ui beyond 1.1.5 (no such variants probed beyond the pinned line);
  iOS/tvOS claims untouched; Chrome-dependent wasm test lanes out of scope.

## 7. Followup: last-Kotlin-≤2.3-built mikepenz release (post-spike archaeology)

Answer (probed 2026-08-27): **`com.mikepenz:multiplatform-markdown-renderer(-m3):0.41.0`** —
newest publication whose wasm-js klibs are Kotlin-2.3-built. Closes M2 as a plain
catalog pin flip; no seam/fork needed for the renderer itself.

Method delta vs the coil/10B hunt: mikepenz jars carry **no**
`META-INF/kotlin-tooling-metadata.json` at all (verified absent in the 0.42.0 jvm
jar), so ground truth came from two other surfaces: each release's Gradle `.module`
declares the exact compiled-against `kotlin-stdlib` version, and the wasm `.klib`
(a zip) self-reports in `default/manifest` via `compiler_version=` / `abi_version=` —
the latter read directly on both sides of the boundary.

| Version | `.module` kotlin-stdlib | wasm-klib `compiler_version` / abi | published |
|---|---|---|---|
| 0.44.0 | 2.4.10 | (not dissected) | 2026-08-18 |
| 0.43.0 | 2.4.0 | **2.4.0 / abi 2.4.0** | 2026-06-22 |
| 0.42.0 (b01 06-07, b02 06-16, stable 06-20) | 2.4.0 (all three) | 2.4.0 / abi 2.4.0 | 2026-06-20 |
| **0.41.0** (b01 05-12) | **2.3.21** | **2.3.21 / abi 2.3.0**, `builtins_platform=WASM`, `wasm_targets=wasm-js` | **2026-05-17** |
| 0.40.0/.1/.2 | 2.3.20 | not dissected | — |
| 0.39.0/.1/.2 | 2.3.0 | not dissected | — |
| 0.38.0-b01/0.38.0/0.38.1 | 2.2.20 / 2.2.21 / 2.2.21 | not dissected | — |
| 0.37.0 | 2.2.20 | not dissected | — |
| 0.35.0 | 2.1.21 | not dissected | — |

Coverage note: every entry in maven-metadata.xml from 0.37.0 through 0.44.0 was
probed via `.module` (all betas/rc lines included); no gaps between 0.41.0 and
0.42.0, so 0.41.0 is proven maximal. `-m3` flavor cross-checked at 0.41.0 (same
stdlib declaration 2.3.21; both `-wasm-js` klibs exist at ~17:02–17:05 UTC 2026-05-17).

Evidence URLs:
- https://repo1.maven.org/maven2/com/mikepenz/multiplatform-markdown-renderer-m3/maven-metadata.xml (version inventory)
- https://repo1.maven.org/maven2/com/mikepenz/multiplatform-markdown-renderer/<v>/multiplatform-markdown-renderer-<v>.module (kotlin-stdlib per version)
- https://repo1.maven.org/maven2/com/mikepenz/multiplatform-markdown-renderer-m3-wasm-js/0.41.0/multiplatform-markdown-renderer-m3-wasm-js-0.41.0.klib → `unzip -p … default/manifest` reads `compiler_version=2.3.21`, `abi_version=2.3.0`
- same probe on 0.42.0/0.43.0 klibs reads `compiler_version=2.4.0`, `abi_version=2.4.0`
- publication dates: HTTP Last-Modified per artifact file on repo1 (above)

Compat notes stepping down 0.43.0 → 0.41.0 (our usage: `m3.Markdown(content, modifier,
typography)` + fully-named-param `markdownTypography(h1…inlineCode)`):
- No break at our call site. v0.42/v0.43 changelogs flag no breaking changes, and
  `markdownTypography` incl. the `inlineCode` param verifiably exists inside the
  0.41.0 klib (IR strings/linkdata).
- Given up (nothing used by JellyPlay's MarkdownText): streaming APIs
  (`StreamingMarkdownState`, `Flow.asMarkdownState()` — 0.42) and synchronous
  `parseMarkdown` (0.43); render fixes TalkBack paragraph-with-link skip (#570),
  inline-code hardcoded size removal (#578), em/unspecified TextUnit crash (#582);
  plus whatever landed in 0.44.
- Dependency graph falls back onto the toolchain: 0.41.0 declares kotlin-stdlib
  2.3.21 (== pin), kotlinx-collections-immutable 0.4.0, org.jetbrains:markdown 0.7.3;
  the kotlinx-coroutines edge first appears at 0.42.0 (streaming feature). No coil
  coordinate in either version's root Gradle metadata, so the wave-10B coil 3.4.0
  pin stays orthogonal — and the R3 stdlib-eviction-to-2.4 ICE trap disappears
  entirely with the downgrade.
- Long view: holding pin only — ride forward to ≥0.43 when the toolchain reaches
  Kotlin 2.4 (0.44.0 is 2.4.10-built and adds nothing we need today).

## 8. Wave-11A landing notes — wasm runtime degrades (documented cuts)

All landed in `shared/core/ui` `wasmJsMain` actuals; compile gates only, no
real-browser pass yet (same honesty rule as HtmlVideoEngine/10B):

- **Markdown**: wasm renders styled monospace plain text (the commonMain
  seam moved mikepenz to android/jvm-scoped deps). Fidelity upgrade path =
  flip the catalog pin to the §7 coordinate (0.41.0) and swap the wasm
  actual; deferred for v1.
- **SYSTEM date order**: region table is a mapped subset (MDY list incl.
  KE/GH by ICU convention); an UNMAPPED region degrades to DMY, and a
  REGION-LESS browser locale (`en`, `fr`) falls back to MDY — diverging from
  real ICU for some EU users. Display-only, SYSTEM preference only.
- **Date inputs**: bare-local ISO stamps resolve in browser-tz via
  `Date.parse` under a strict shape regex (+ field-range validation);
  long month names and AM/PM letters are fixed English.
- **BackHandler**: inert on web — naive popstate binding would fight
  NavDisplay's stack management; browser-history integration deferred until
  the web shell navigates for real.
- **Language tables**: coverage bounded by the compiled-in matrix instead of
  host CLDR (`no:nor` + JDK legacy aliases iw/in/ji included post-review);
  unregistered short codes return null instead of passthrough on the
  2-letter path only.
- **Logs**: `println` reaches the JS console; no levels beyond W/D shapes.
