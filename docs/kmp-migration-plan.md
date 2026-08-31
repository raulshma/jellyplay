# JellyPlay → Kotlin Multiplatform Migration — Status Ledger

Status: **In progress — Phase X (cutover & hardening) remains** · Living doc,
re-anchored 2026-08-31.

This file re-anchors the ~50 references across build scripts, KDocs and CI
that point at `docs/kmp-migration-plan.md`. The original 385-line draft plan
(intentionally marked "do not commit") lives in git history at
`c092be235~1:docs/kmp-migration-plan.md` — its decision tables, framework
inventory and risk register are the authoritative record of *why*; this
ledger tracks *where the migration stands*.

## Locked decisions (unchanged from the draft)

| Decision | Choice |
|---|---|
| Platform priority | Desktop first (Win/mac/Linux, one JVM target), then Web (wasm), iOS later (not started) |
| Web scope | Working 3-route client today; full-app best-effort playback accepted as future scope |
| DI framework | Koin (Hilt extinct repo-wide) |
| Desktop playback | libmpv behind the shared `MediaEngine` contract (`MpvDesktopEngine`) |
| Offline downloads | Desktop coroutine download manager (no WorkManager) |
| Migration shape | Parallel `shared/` shell, strangler-fig cutover, Android shipped throughout |
| Execution | Wave-numbered incremental merges; every wave exits with all lanes green |

## Where things stand (measured 2026-08-31, wave 22f + follow-ups)

**Done:**

- `shared/` tree complete: `core/{model,designsystem,datastore,database,network,data,ui,player-contract}`
  + 24 `feature/*` modules; every commonMain expect has actuals; zero stubs
  in shipped surfaces. `:app` builds phone+TV flavors on top of the shared
  modules exclusively.
- Targets: android+jvm everywhere; **wasmJs additionally** on
  core/{model,designsystem,datastore,network,data,ui,player-contract} and
  feature/{requests,calendar,details}. The 21 remaining feature modules carry
  documented `java.*`-in-commonMain usage that a web slice would have to split
  jvmShared-style first (core:data's wave-15B split is the template).
- `apps/desktop`: full shell — nav rail over 17 routes, real mpv engine,
  download manager, audio queue manager, tray/menus, packaging in CI on all
  three OSes. Gaps: Live TV channel player + subtitle tester dead-ended,
  video route gated to Windows HWND / software-surface OSes, File→Refresh
  historically unwired.
- `apps/web`: real Ktor + Coil3 stack (W.1/W.4 landed), connect/sign-in flow,
  Requests / Upcoming Calendar / Seerr Detail routes, `HtmlVideoEngine`
  verified in-browser via diagnostics.
- Verification: `.github/workflows/kmp-build.yml` compiles every target on
  3 OSes, runs every shared `jvmTest`, app phone/tv unit tests, the APK
  compose-resources guard (216 entries = 24 modules × 9 locales) and desktop
  packaging. **Pushes to `kmp-alpha` run it** (added 2026-08-31 — its absence
  is how three test regressions landed unverified during the v0.10.6 merges).
- e2e ledgers under `docs/e2e/`: desktop native dialogs (3/3 PASS),
  device-locale pass 10/10, web cache-eviction and input-dead-region closed.

**Remaining before the migration is complete (Phase X):**

1. **Cutover of the legacy tree**: `core:data` / `core:ui` are re-export
   shims + Android-coupled halves; ~16 shared→legacy edges in `androidMain`
   are marked "dies at Phase X". Each file that can move into `shared/`
   moves; the truly Android-native remainder (cast, MediaSessionService,
   workers, widgets, notification) is re-homed as plain `androidMain`
   actuals and the shim modules dissolve.
2. **Web breadth**: 3 routes → full app requires the wasm target roll-out
   above; Room-coupled repositories are the first blocker per module.
3. **iOS**: no target work started (deliberate; commonMain purity is the only
   standing pre-investment).
4. **e2e checklist-only items**: native-dialog flows 3–6 (editor pickers,
   heatmap share, subtitle upload, row-click wiring) and the PiP
   expand/dismiss device rerun.
5. **Release engineering**: desktop is "a preview, not a release" per README
   — macOS/Linux untested beyond CI packaging, auto-update ADR pending
   implementation.

## Verification quick reference

```bash
./gradlew :apps:desktop:run                    # desktop app (libmpv required)
./gradlew :apps:web:compileKotlinWasmJs        # wasm compile gate
./gradlew :app:testPhoneDebugUnitTest :app:testTvDebugUnitTest
./gradlew :app:verifyPhoneDebugComposeResources
# Every shared module's jvmTest + desktop tests (the CI matrix):
./gradlew $(find shared -name build.gradle.kts -not -path '*/build/*' \
  | sed 's|/build.gradle.kts||' | tr '/' ':' | sed 's|^|:|; s|$|:jvmTest|') \
  :apps:desktop:test
```
