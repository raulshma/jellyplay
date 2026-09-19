# JellyPlay → Kotlin Multiplatform Migration — Status Ledger

Status: **COMPLETE (2026-09-13) — all approved phases landed; outstanding
items are release-mechanics externals and the recorded web-gate scope** ·
Living doc, re-anchored 2026-09-13.

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

- `shared/` tree complete: `core/{concurrency,model,designsystem,datastore,database,network,data,ui,player-contract}`
  + 23 `feature/*` modules; every commonMain expect has actuals; zero stubs
  in shipped surfaces. `:app` builds phone+TV flavors on top of the shared
  modules exclusively.
- Targets: android+jvm everywhere; **wasmJs additionally** on
  core/{model,designsystem,datastore,network,data,ui,player-contract} and
  feature/{requests,calendar,details}. The 20 remaining feature modules are
  jvm/android-only today; 11 of them carry `java.*`-in-commonMain usage that a
  web slice would have to split jvmShared-style first (core:data's wave-15B
  split is the template).
- `apps/desktop`: full shell — nav rail over 15 routes, real mpv engine,
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

**Remaining before the migration is complete (approved completion plan):**

1. ~~**Cutover of the legacy tree**~~ **DONE (2026-09-12, branch
   `chore/legacy-cutover`)**: `:core:ui`, `:core:data`, `:core:notification`
   and `:core:testing` dissolved — every Android-coupled file moved into
   `:shared:core:ui` / `:shared:core:data` `androidMain` under identical
   packages; the Robolectric suites execute in the new AGP-9
   `androidHostTest` lanes; `:app` is the only Android-only module left.
   The v0/v1 settings-backup import sunset lands in v0.11; the typed-key
   prefs migration, the PIN legacy-hash verify and the legacy-download
   container sniffer outlived the cutover and are tracked below as R1/R3.
2. **Phase R — cutover residuals:**
   - R1: ~~legacy-download container-sniffer backfill migration
     `Migration53To54`~~ **DONE (2026-09-12, 5a67802f3)** — lands v0.11;
     the precondition for deleting the sniffer.
   - R2: ~~docs truth pass (README structure/counts, root build exclusion
     list, this ledger)~~ **DONE (2026-09-12, 01894fe54)**.
   - R3: v0.12 removal wave — typed-key prefs fallback, PIN legacy-hash
     verify, sniffer runtime fallback — only after R1 has shipped one
     full release (branch `chore/v0.12-legacy-removals` prepared).
     Accepted consequences: pre-typed-key skip-upgraders lose their
     legacy prefs; never-unlocked-since-PBKDF2 users re-onboard.
3. **Phase D — Room 3 foundation** (unblocks real-DB repositories on
   wasmJs):
   - D0: ~~timeboxed spike on `spike/room3`~~ **GO (2026-09-12)** — all
     three gates passed: toolchain green (room3 3.0.3 on Kotlin
     2.3.21/KSP 2.3.10), tracked schemas 13–54 byte-identical under
     room3 validation, room3-testing MigrationTestHelper works in the
     jvmTest lane. Deviations accepted: `migrate()` is suspend (all 53
     overrides), `@TypeConverter(s)`→`@ColumnTypeConverter(s)`,
     androidx.sqlite 2.6.2→2.7.1, room-ktx dropped.
   - D1: ~~core migration on android+jvm~~ **DONE (2026-09-12, merge
     e833745ec)** — spike promoted wholesale; KSP-arg schema flow kept
     (room3 plugin not applied under AGP-9 KMP).
   - D2: ~~`RoomTransactions.kt` port + its 10 call sites~~ **DONE
     (same merge)** — room3 kept `Transactor`/`useWriterConnection`
     verbatim, so the port was imports-only.
   - D3: ~~wasmJs target on `:shared:core:database` via
     WebWorkerSQLiteDriver~~ **DONE (2026-09-12, f32b1b53c)** — OPFS via
     opfs-sahpool (no COOP/COEP headers; single-tab constraint
     documented); wasmJsBrowserTest roundtrip proves the chain; guard
     test needed no change (platformPrefixes).
   - D4: ~~promote Room-coupled repositories from core:data jvmShared to
     commonMain~~ **DONE (2026-09-13, 398878208)** — 23 types promoted
     (the Room-backed repo slice + managers), new EpochMillisSource/
     ioDispatcher seams, Uuid swap; JVM-heavy machinery deliberately
     stays jvmShared. WebMediaRepositoryNarrow retirement deferred to
     the wave that ports the MediaRepository cluster (D4 left it: the
     promoted slice covers playlists/search-history/seen-state/outbox,
     not MediaRepository).
4. **Phase W — web breadth** (3 routes → full app). Per-module checklist:
   wasmJs target → java.*-in-commonMain split (kotlinx-datetime /
   kotlin.io.encoding / okio; core:data's wave-15B split is the template)
   → repositories reachable from `dataWasmModule` → apps/web dep + Koin +
   route + URL parse → KoinModuleRegistrationGuardTest allowlist → CI
   wasm lane → browser smoke.
   - W1: ~~leaf modules — arrqueue, shortcuts, onboarding, auth~~
     **DONE (2026-09-12/13, 465f884bf + 98a8c6a59)** — all four compile
     for wasm (auth needed the add-server failure-classifier seam);
     arrqueue + onboarding ROUTED on web (entries + Koin + guard-test
     allowlist); shortcuts + auth target-only this wave (no wasm
     AuthRepository binding; grid routes mostly non-wasm).
   - W2: ~~small splits — newsletter, editor, search, library,
     player-book~~ **DONE (2026-09-13, 053c6d78b)** — all five compile
     for wasm. Seams: NewsletterDateLabels, EditorSubtitleStore,
     QuickDownloadActions (screens gate the download CTA on
     isSupported — hidden, not Failed-toasting on web), player-book
     honest-degradation actuals. Not routed on web yet (orchestrator
     integration pending).
   - W3: ~~data work — livetv, insights, settings, player-audio~~
     **DONE (2026-09-13, d989b64b8 + 8de9767fc)** — all four compile for
     wasm (livetv's java.time cluster ported with locale-preserving
     expect/actual formatters; insights' Kotlin surface moved to
     jvmShared, commonMain = composeResources; settings gained the
     SettingsBackupIo seam + a wasmJs-scoped aboutlibraries force;
     player-audio gates downloads via the AudioTrackDownloads seam).
     Routed on web: the ArrSettings slice (settings root needs a wasm
     AuthRepository binding — recorded as the follow-up that would
     unlock it); player-audio unrouted (four playback ctor seams lack
     wasm bindings).
   - W4: ~~hard — music, home, downloads, syncplay, admin, player-live~~
     **DONE (2026-09-13, b198d356f)** — all six compile for wasm behind
     honest fail-closed seams (wasm actuals never fabricate success);
     admin = insights precedent (whole surface jvmShared; promotion
     scope recorded in its build KDoc); player-live's media3 screen
     stays Android-only, recorded in build KDoc.
   - W5: ~~last — player-video, subtitle-tester, shell~~ **CLOSED
     (2026-09-13, dcb93fd74)** — shell compiles for wasm (target only;
     web keeps WebAppRoot). **player-video + subtitle-tester stay
     jvm/android by recorded decision**: five core:data jvmShared types
     are constructor-injected into the playback hubs
     (PlaybackSession/PlayerSessionManager/VideoPlayerViewModel, pinned
     by 87 jvmTest files), plus a ~39-ref java.io.File surface and ~25
     expects — hub surgery, not bounded seams, and in-shell web playback
     is excluded by the locked best-effort decision. Web playback
     remains the verified `HtmlVideoEngine` diagnostics path; unlocking
     the two modules is a scoped follow-up (seam the five hub
     dependencies, split the File/trickplay surface, write ~25 wasm
     actuals).
   - Follow-up (from wave E's production fix): setItemImage now always
     sends the base64 body Jellyfin 10.11 requires; servers older than
     10.11 whose ImageSaver predates base64 decoding will fail image
     upload. Deliberate cut (no server-version detection available at
     the call site) — revisit if pre-10.11 server support matters.
5. ~~**Phase E — e2e tail**~~ **DONE (2026-09-13, c4b7739c4 +
   5876f5ffe)** — harness click-reach solved (flow-pass harness + Robot
   driver); native-dialog flows 3–6 PASS 2/2 with server-side
   post-conditions; the lane found and fixed a real production bug
   (MetadataApiClientImpl.setItemImage violated Jellyfin 10.11's wire
   contract on every platform). PiP expand/dismiss stays OPEN — no
   device attached this session (ledger notes it). Web route-smoke
   extension scoped (one parseE2eBootRoute branch + one step block).
6. ~~**Phase X — release engineering**~~ **DONE (2026-09-13, 5d6a43a7e)**
   — auto-update ADR client implemented (channel flag replaces the
   999999.0.0 sentinel; manual check + open release page; dev builds
   suppressed). Outstanding EXTERNALS, documented in the ADR: signing
   certs (Authenticode/notarization placeholders + exact commands),
   macOS/Linux real-machine passes (checklist in ADR), attaching
   desktop installers to stable releases (release.yml wiring).
7. **iOS**: remains excluded (deliberate; commonMain purity is the only
   standing pre-investment).

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
