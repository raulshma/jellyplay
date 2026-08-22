# JellyPlay → Kotlin Multiplatform Migration Plan

Status: **Draft for review** · Written: Aug 2026 · Target order: **Desktop → Web → iOS**

This is the engineering plan for migrating JellyPlay from an Android-only app
(30 modules, ~1,666 Kotlin files, ~308K LOC) to a Kotlin Multiplatform codebase
running on Android phone/TV, Windows, macOS, Linux, Web (wasm), and later iOS.
User-facing feature docs stay in `docs/`; this file is about the migration itself.

---

## 0. Locked decisions

| Decision | Choice |
|---|---|
| Platform priority | Desktop first (Win/mac/Linux share one JVM target), then Web, then iOS |
| Web scope | Full app, best-effort playback (HTML5 video; codec/CORS limits accepted) |
| DI framework | **Koin** (+ koin-annotations) replacing Hilt everywhere |
| Desktop playback | **libmpv** engine behind the existing `MediaEngine` contract |
| Offline downloads | Ported to desktop via coroutine download manager (no WorkManager) |
| Migration shape | **Parallel new shell**: build `shared/` KMP tree alongside, strangler-fig cutover, Android keeps shipping throughout |
| iOS pre-investment | Discipline only (commonMain purity rules; no active refactor) |
| Execution model | AI-agent-heavy development; mechanical mass refactors are cheap, so prefer many small verifiable moves over big-bang branches |

**Operating rule for "parallel shell" (anti-drift):** no logic may live in two
places for more than one phase. When a component migrates into `shared/`, the
legacy module either becomes a thin shim that re-exports it (`:app` keeps
compiling) or is deleted in the same phase. The old tree never gets feature
work after its phase completes.

---

## 1. Current-state audit (measured)

### 1.1 Scale

- 30 source modules + 2 baselineprofile modules (`settings.gradle.kts`)
- ~1,666 `.kt` files, ~308K lines
- `:app` already builds two product flavors (`phone`, `tv`) off one
  `platform` dimension — Android TV ships today as a flavor, which stays.

### 1.2 Android-coupling hotspots (`import android.*` per module)

| Module | kt files | android-coupled | Notes |
|---|---:|---:|---|
| core:data | 290 | 98 | WorkManager workers (34 files repo-wide), receivers, outbox/sync |
| feature/player/video | 228 | 48 | ExoPlayer/Mpv/LibVlc engines, session/pipeline mostly pure |
| :app | 76 | 40 | Activities, services, widgets, tiles, Cast, TV drawer |
| core:ui | 165 | 19 | LocalContext (21 files), Palette (20 files), biometric helper |
| feature/admin | 75 | 13 | mostly incidental Context use |
| core:network | 92 | 12 | OkHttp + Jellyfin SDK wiring |
| everything else | — | ≤9 each | largely portable |

### 1.3 Framework inventory vs KMP readiness (verified Aug 2026)

| Dependency (current) | KMP status | Disposition |
|---|---|---|
| Compose BOM 2026.06 / M3 1.5-alpha25 / adaptive 1.3-rc01 | CMP 1.11 stable desktop+iOS, web Beta; androidx compose multiplatform | Keep; align versions to CMP release notes (spike S3) |
| Navigation 3 1.1.5 (32 files) | Non-Android support shipped with CMP 1.10 | Keep — major win, no nav rewrite |
| Lifecycle / ViewModel 2.11 | Tier-1 multiplatform | Keep |
| DataStore Preferences 1.2.1 | Multiplatform | Keep; secure-store needs expect/actual (§4) |
| Room 2.8.4 (48 files, ~20 entities) | KMP: Android + JVM + iOS | Keep; move entities/DAOs to commonMain, builders per target |
| Paging 3.5 (28 files) | paging-common/-compose/-testing multiplatform incl. wasm (runtime stays Android) | Keep |
| Coil 3.5 | Multiplatform (engine per target) | Keep; swap network engines per target |
| kotlinx-serialization / coroutines | Multiplatform | Keep |
| **Jellyfin SDK 1.8.12** (29 files) | **Android + JVM only** | Keep on android+desktop; wasm needs own client (§5 Phase W); iOS later (Swift SDK behind seam) |
| OkHttp 5.4 (73 files) | JVM only | Confine to jvm shared source set; never import from commonMain |
| Media3 / ExoPlayer 1.10 + ffmpeg-decoder + ass-media | Android-only | Stays as Android engine impls |
| libmpv-android-lib / libvlc-android | Android natives | Android engines unchanged; desktop gets libmpv (§ Phase D6) |
| **Hilt 2.60** (~100+ VMs/workers/services/receivers) | Android-only | **Replace with Koin** (biggest single workstream) |
| WorkManager 2.11 (34 files) | Android-only | Android keeps it; desktop/web get coroutine schedulers |
| security-crypto (Keystore AES-GCM) | Android-only | expect/actual `SecureCredentialsStore` (§4 seams) |
| Biometric (~15 files) | Android-only | expect/actual auth gate; desktop = OS prompt later, fallback passphrase |
| Play Services Cast (4 files) | Android/GMS | Android-only capability, gated like other engine capabilities |
| androidx.palette (20 files) | Android-only | Replace with small common color-extraction util |
| tv-material / tvprovider | Android TV | Stays in TV flavor permanently |
| mockk (tests) | JVM-only | House style already favors hand-written fakes (CONTEXT.md) — port tests to fakes in commonTest |
| Robolectric | Android/JVM | Stays for androidMain tests |

### 1.4 Resources

~40,000 string entries across **9 languages** (en, de, es, fr, it, ja, ko, pt,
zh) in 254 `values*` dirs; `feature/settings` alone holds 1,642 en strings.
All of it migrates to Compose Resources (per-module `strings.xml` kept).
Mechanical but broad: every `stringResource(R.string.x)` and every
`SettingsSearchItem(titleRes = …)` changes type (`Int` → `StringResource`).

---

## 2. Target architecture

```
shared/                          # new KMP tree ("parallel shell")
  core/model                     # pure Kotlin (+compose.runtime annotations)
  core/designsystem              # theme, icons, compose resources
  core/datastore                 # preferences + SecureCredentialsStore expect/actual
  core/database                  # Room KMP (entities/DAOs common, builders actual)
  core/network                   # jvmShared: jellyfin-sdk+okhttp (today's impl)
                                 # wasmJs: Ktor client impl behind same interfaces
  core/data                      # repositories, session identity, caches, sync loops
  core/ui                        # components, settings-search pipeline
  core/player-contract           # MediaEngine + EngineConfig/State (from player/core)
  feature/*                      # screens + ViewModels + controllers (Koin)
apps/
  android/ (:app today)          # phone+tv flavors, Hilt-free at cutover,
                                 # hosts all Android-native surfaces (§6)
  desktop/                       # Compose Window entrypoint, libmpv engine,
                                 # tray/menus/file dialogs, packaging
  web/                           # wasmJs entrypoint, HtmlVideoEngine
```

**Source-set strategy per module:** `commonMain` → `jvmShared` intermediate
(groups androidTarget + jvm desktop where code may use JDK/OkHttp/SDK) →
target sets (`androidMain`, `jvmDesktopMain`, `wasmJsMain`). iOS joins later by
adding targets; nothing in `commonMain` may reference `java.*`, `android.*`,
OkHttp, or SDK types.

**Gradle plugins:** modules adopt `org.jetbrains.kotlin.multiplatform` +
AGP 9's built-in `com.android.kotlin.multiplatform.library` (already on AGP
9.3.1, so no AGP upgrade). Convention plugins added in Phase 0 keep the 30+
module configs declarative.

---

## 3. Phased roadmap

Each phase ends with: green CI (all configured targets), Android release still
shippable, legacy surface deleted/shimmed, and a short ADR-style note in this
doc's appendix if a decision changed.

### Phase 0 — Foundations & risk spikes *(start here)*

> **Status: DONE (Aug 2026)** — `shared/core/model` skeleton compiles and tests
> green on all three targets (`jvmJar`, `compileKotlinWasmJs`,
> `compileAndroidMain`, `jvmTest`); `.github/workflows/kmp-build.yml` runs the
> matrix on ubuntu/windows/macos.
>
> Notes recorded during implementation:
> - AGP 9.3.1 KMP library plugin configures the Android target inside
>   `kotlin { android { … } }` (the `androidLibrary {}` DSL is deprecated);
>   there are **no build variants** — verification task is
>   `compileAndroidMain`, not `assembleDebug`.
> - `wasmJs { browser() }` needs `@file:OptIn(ExperimentalWasmDsl::class)`;
>   target-level `compilerOptions { }` takes a direct lambda (no `.configure`).
> - Catalog entries for koin/ktor/okio/CMP were **deferred** to their consuming
>   phases (C4/W/S3) to avoid pinning versions months before use; only
>   `kotlin-multiplatform` + `android-kotlin-multiplatform-library` were added.

1. Version catalog: add `kotlin-multiplatform`, `composeMultiplatform`,
   `koin-*`, `ktor-*`, okio entries; pin CMP per S3 outcome.
2. Convention plugins (`jellyplay.kmp.library`, `jellyplay.compose.multiplatform`)
   + empty `shared/core/model` skeleton proving androidTarget+jvm+wasmJs compile.
3. CI matrix: Windows/Linux/macOS JVM builds + wasmJs compile check + existing
   Android lanes untouched. Desktop packaging dry-runs (msi/deb/dmg) early.
4. **Risk spikes (timeboxed, written up):**
   - **S1 libmpv-on-desktop**: JNA mapping of `client.h`; render path decision —
     native-window embedding (`wid`) inside `SwingPanel` (Win/X11 easy, macOS
     hard) vs render-API→Skia `ImageBitmap`. Fallback contingency: VLCJ +
     `vlc-setup` plugin (proven JetBrains sample). This is the highest-risk item.
   - **S2 wasm networking**: Ktor wasm client against a real Jellyfin server;
     CORS behavior documented; DTO subset generation script from
     `openapi.json`.
   - **S3 version parity**: CMP 1.11 × M3 1.5-alpha25 × adaptive 1.3-rc01 × Nav3
     multiplatform artifacts — resolve exact compatible set.
   - **S4 Room KMP reuse**: existing schema JSONs + `Migrations.kt` run on JVM
     driver unmodified.

### Phase C1 — Core model, design system, resources

> **Status: model DONE (Aug 2026)** — all 92 main sources of `core:model` now
> live in `shared/core/model`; legacy `:core:model` is an empty
> `api(project(":shared:core:model"))` shim; `:app:compilePhoneDebugKotlin`
> and every consumer assemble green; 268 tests (29 classes) ported to
> kotlin.test run via `jvmTest`.
>
> Implementation notes:
> - **jvmShared intermediate source set** hosts JVM-semantics code shared by
>   android+desktop verbatim: `TtlCache`, `BoundedCollections`,
>   `CacheIdentity` (@JvmInline still required on Kotlin 2.3 — plain value
>   classes unsupported), `LanguageUtils`, `SubtitleLanguageCodes`
>   (java.util.Locale). Wasm replacements deferred to Phase W.
> - **Platform seams** (`PlatformDefaults.kt`, expect/actual):
>   `deviceModel()` (Build.MODEL / os.name / "Web"), `monotonicNowMillis()`
>   (elapsedRealtime / nanoTime / TimeSource.Monotonic), `wallNowMillis()`
>   (currentTimeMillis / Date.now via `js()` body on wasm).
> - **Non-common API fixes**: `ReverbPreset` constants hardcoded to literals
>   (verified against android-37 via javap: PRESET_SMALLROOM=1 … PLATE=6);
>   `"%.1f".format` replaced by integer-math one-decimal formatting;
>   `"%02d".format` → `padStart(2,'0')`; `Math.abs` → kotlin.math;
>   `java.util.Calendar.SATURDAY` → literal 7.
> - **JUnit4 → kotlin.test port caveat**: kotlin.test flips argument order
>   (message LAST); 186 message-first call sites were mechanically flipped by
>   a paren-balancing script — audit confirmed no two-string-arg assertEquals
>   existed, so no silent semantic flips.
> - **Rule learned**: when a later module starts depending directly on a
>   `shared/*` module, it must drop its legacy-module dependency in the same
>   change to avoid duplicate classpaths.
>
> **Status: designsystem DONE (Aug 2026)** — all 17 theme sources + 2 test
> classes live in `shared/core/designsystem`; legacy `:core:designsystem` is an
> empty `api(project(":shared:core:designsystem"))` shim (same package, so
> consumers compile unchanged); verified `compileAndroidMain` + `jvmTest` +
> `compileKotlinWasmJs` green and `:app` phone+tv Kotlin compile green.
>
> Implementation notes:
> - **Platform seams** (expect/actual, three targets each):
>   `dynamicPlatformColorScheme()` (Android 12+ Material You / null / null),
>   `rememberArtworkColors(url)` (Palette+Coil on Android / Skia-pixel port on
>   JVM / null on wasm — consumers already handle null), `noFontPaddingStyle`
>   (Android PlatformTextStyle / null elsewhere), `FontFamilies` (Google Fonts
>   provider on Android / platform defaults on JVM+wasm).
> - **androidx.palette replaced in-module**: the swatch classification
>   (dominant/vibrant/muted scoring) was ported to common code over
>   `ArtworkColorExtractor`; Android keeps only the Palette→pixel bridge
>   (`AndroidArtworkPalette.kt`). The 20 external Palette call sites in
>   `core:ui` are still on androidx.palette until V1.
> - **Vendored smooth-corner-rect** (`racra.compose.smooth_corner_rect_library`,
>   Apache-2.0) into commonMain — the published artifact is Android-only and the
>   shape remains implementation-detail behind `JellyPlayShape.kt` wrappers.
> - `font_certs.xml` moved to androidMain res (AGP 9 KMP library: single
>   `values/` dir, no version qualifiers); `androidResources.enable = true` on
>   the Android target generates the R class for it.
> - Catalog now pins `composeMultiplatform = 1.11.1` / M3 `1.11.0-alpha07`
>   (S3 parity resolved empirically: JB CMP 1.11.1 redirects Android targets to
>   androidx artifacts compatible with the current BOM; jvm+wasm resolve the JB
>   multiplatform binaries).
>
> **Scope decision (strings)**: the C1 "convert all strings.xml" bullet is
> rescoped to per-module conversion at the moment each module migrates into
> `shared/` — the V3 per-feature checklist already sequences it that way, and
> converting strings for modules that stay Android-only until V3 would create
> drift risk with no shared consumer. `core:model`/`core:designsystem` carried
> no user-facing strings, so C1 closes with none converted.

- Move `core:model` (121 files, only 3 android-coupled) → `shared/core/model`;
  fix the 3 stragglers (expect Uri-ish type or pure model).
- Convert all strings.xml trees → Compose Resources (scripted, per module);
  update `stringResource` call sites and settings-search items/tests.
- Designsystem/theme to common; delete legacy module (shim for `:app`).

### Phase C2 — Persistence
- `core:datastore`: preferences stores → common; `SecureCredentialsStore`
  expect/actual (Android Keystore impl preserved byte-for-byte; desktop =
  OS keychain via keyring lib; web deferred).
- `core:database`: entities/DAOs → commonMain; builders actual-per-target
  (Android context path unchanged, desktop file-path); verify migrations on
  JVM driver; DAO tests gain `jvmTest` runs alongside Robolectric.

### Phase C3 — Network seams
- `core:network` splits: `jvmShared` keeps today's OkHttp+SDK impls untouched;
  extract the interface surface `core:data` actually consumes so wasm can slot
  a Ktor impl later without touching repositories. Measure how far SDK model
  types leak into `core:data` (29 importing files) and map those to seam types
  incrementally — desktop does NOT need this done to ship (JVM sees SDK types),
  so prioritize only what Phase W blocks on.
- SLF4J-nop stays JVM; logging facade for common.

### Phase C4 — core:data common-ization + Koin introduction
- Split `core:data` (98 coupled / 290): repositories, `HomeSession`,
  `SessionCacheRegistry`, `HomeRefresher`, `TtlCache`s, `AdaptiveBitrateManager`,
  playback reporting/outbox logic → common (inject clock/scope/filesystem);
  workers, notification, receivers, DownloadWorker stay Android-side.
- Introduce **Koin here** (not at the edges): every migrated component gets
  Koin module wiring; Android-only leftovers keep working through thin
  factories. By end of phase both frameworks coexist only inside `:app`.

### Phase V1 — UI foundation + vertical slice (auth → home → library → details)
- `core:ui` to common: replace Palette (20 call sites) with common extraction;
  abstract `LocalContext` uses (21 files) behind platform locals (clipboard,
  url-opener, toast→snackbar); biometric gate becomes expect/actual screen.
- Desktop shell app: Window/tray/menubar/fullscreen, keyboard shortcuts,
  window-size-class-driven adaptive layouts.
- Ship runnable desktop build: sign-in, browse home sections, open details.
  This proves the whole stack end-to-end before mass migration.

### Phase V2 — Player on desktop (the big rock)
- `MpvDesktopEngine` implementing `MediaEngine` (contract moves to
  `shared/player-contract`): load/seek/rate/volume, track/subtitle selection,
  `EngineCapabilities` profile for desktop (direct-play nearly always viable),
  ASS subtitles via mpv native rendering, stats projection, position ticker
  reuse (`EnginePositionTicker` already lives in player/core).
- Audio engine = same backend; SyncPlay bridge works as-is (JVM WebSocket).
- OS integration increments: media keys / SMTC (Win) via JNA, Now Playing
  (macOS), taskbar progress — each optional, none blocking.

### Phase V3 — Feature conveyor (agent-driven, one PR per feature)
Order (by coupling risk ascending):
search, library, music, livetv, downloads(+desktop coroutine download manager:
Range-resumable fetches, appdata storage via okio/appdirs, trickplay bundles,
offline DB reuse, auto-download scheduler as in-process loop), syncplay UI,
settings (storage paths/licenses), admin, editor, requests, calendar,
newsletter, insights, shortcuts, arrqueue, onboarding, subtitle-tester.
Per-feature checklist: migrate screens+VMs → flip DI → convert strings →
port tests to fakes/commonTest → delete shim → desktop smoke test.

### Phase W — Web target
1. Ktor wasm client implementing the Phase C3 seam; DTO subset generated from
   `openapi.json` (scripted, grows on demand).
2. Persistence: prefs via browser storage adapter (or DataStore-wasm if spike
   confirms support); **no Room on wasm v1** — session-scoped state only,
   server remains source of truth; search-history etc. degrade gracefully.
3. `HtmlVideoEngine`: `<video>` element interop; HLS via native/Safari +
   hls.js on Chromium; subtitle limits documented.
4. Coil wasm image engine; CORS setup guide for server admins (reverse-proxy
   snippet) published to docs/.
5. Web v1 scope cuts (explicit): downloads, Cast, biometric, offline DB.

### Phase X — Cutover & hardening
- Flip `:app` fully onto `shared/`; remove Hilt entirely (workers/services/
  receivers get Koin-backed factories); delete shims; consolidate catalogs.
- Release engineering: signed installers per OS (msi/msix, dmg/pkg, deb/AppImage),
  auto-update channel decision, crash reporting per platform, performance pass
  (Skia startup/memory), baseline profiles remain Android-only.
- Re-run god-count ratchet (`ControllerOwnershipTest`) and all CONTEXT.md
  invariants in their new homes.

---

## 4. Platform seam catalog (expect/actual)

| Seam | Android | Desktop JVM | Web (wasm) |
|---|---|---|---|
| Secure credentials | Keystore (security-crypto, unchanged) | OS keychain (keyring lib) | omitted v1 |
| Auth gate | BiometricPrompt | OS prompt (later) / passphrase | omitted v1 |
| Filesystem | SAF/app-dirs (existing) | okio + appdirs | limited/no-op |
| Background work | WorkManager (unchanged) | coroutine supervisors in-process | page-lifetime coroutines |
| Downloads | DownloadWorker (unchanged) | coroutine download manager | n/a v1 |
| Video engine | ExoPlayer/Mpv/LibVlc | **libmpv (S1)** | `<video>` element |
| Image engine | Coil+OkHttp | Coil+OkHttp | Coil+Ktor/wasm |
| API transport | Jellyfin SDK+OkHttp | same | Ktor wasm client |
| Notifications/actions | core:notification | tray/toast equivalents | notifications API (later) |
| Cast | GMS Cast | absent (capability-gated) | absent |

## 5. Stays Android-only, permanently

Widgets & tiles, TV drawer/focus wiring + tv-material (TV flavor), FloatingPlayer
service, Media3 MediaSession service, Cast, baselineprofile modules, WorkManager
sync workers, Play-core bits. These live in `apps/android` and are gated behind
capability flags the shared layer already understands (`EngineCapabilities`,
feature flags).

## 6. iOS guardrails (discipline only, effective immediately)

Enforced in review/CI (detekt rule or custom lint):
1. No `import java.*`, `import android.*`, OkHttp, or `org.jellyfin.sdk` in any
   `commonMain`.
2. All file IO through the filesystem seam (okio), never `java.io.File`.
3. Clock/time via injected abstractions (already house style), dates via
   kotlinx-datetime when touched.
4. Coroutines only; no handler/thread constructs outside platform sets.
Cost today ≈ zero (code already follows these patterns); benefit = adding
iOS later is a targets-list change plus an engine, not a refactor.

## 7. Testing strategy

- Pure logic tests → `commonTest` with existing hand-written fakes (CONTEXT.md
  fake culture pays off: `FakeMediaEngine`, refresher/session tests are plain
  JUnit and move nearly verbatim). mockk survives only in JVM/android tests.
- Robolectric stays for androidMain; add `jvmTest` runs for Room DAOs, stores.
- UI: CMP `ComposeUiTest` v2 APIs (CMP 1.11) for shared screens on desktop;
  existing Android instrumented/UI tests unchanged.
- Each phase's exit includes the module's full test suite green on all
  configured targets.

## 8. Risks

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | libmpv desktop embedding (esp. macOS compositing) | **High** | Spike S1 first; VLCJ fallback proven; macOS could even ship vlc-only initially |
| R2 | Jellyfin SDK absent on wasm/iOS | High | Seam-first C3; scripted DTO generation; iOS later via Swift SDK behind same seam |
| R3 | CMP web Beta churn | Medium | Web is phase-last among new targets; scope cuts predefined |
| R4 | M3-alpha × CMP version skew | Medium | Spike S3 pins matrix before any UI migration |
| R5 | Koin flip regressions across ~100 VMs | Medium | Flip happens per-phase with tests green between phases; runtime DI checks in smoke test |
| R6 | Resource conversion breaking plurals/format args in 9 locales | Medium | Scripted conversion + diff report per locale + matcher tests updated |
| R7 | Parallel-shell drift | Medium | One-phase-max duplication rule (§0); shims deleted same phase |
| R8 | Agent-scale refactors masking semantic drift | Medium | Existing ratchet tests + api-dump style diffs per phase |

## 9. Rough sequencing

Phases 0–V1 are serial (foundations must exist). From V2 onward, the feature
conveyor (V3) can overlap W-phase work with different agents. Elapsed-time
estimate under agent-heavy development: foundations+vertical slice ~4–8 weeks,
player+conveyor ~6–12 weeks, web ~4–8 weeks, hardening ~2–4 weeks. Treat as
order-of-magnitude planning numbers, not commitments.

Note: Do not commit this file