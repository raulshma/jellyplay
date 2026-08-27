# Desktop Skia startup/memory baseline (wave 12A)

**Status: measuring stick established. No optimization applied in this commit —
deliberately.** The plan's pass criterion for the desktop-perf wave is
instrumentation + an honest baseline; optimization decisions are deferred and
should be argued against THESE numbers, not vibes.

Everything below was produced by `tools/perf/desktop-baseline.sh` on 2026-08-27
against commit branch `wave/12A-desktop-tray-perf`, app image built by
`:apps:desktop:createDistributable` from CMP 1.11.1.

## Instrumentation (what is always on vs gated)

In-code marks live in `DesktopStartupPerf` (`apps/desktop/src/main/kotlin/…/DesktopStartupPerf.kt`):

| Mark | Source | Cost when production boot |
|---|---|---|
| `bootNanos` | First statement of `main()` | one `System.nanoTime()` |
| `koinStartedNanos` | After `startKoin { … }` returns (module list untouched, no reorder) | one atomic store |
| `windowShownNanos` | AWT `WindowListener.windowOpened` on the `ComposeWindow` (authoritative visibility event; listener removes itself) | one atomic store + add/remove listener |
| `firstFrameNanos` | `withFrameNanos` resume right after the Window content's initial composition applies — same composition pass that builds `DesktopAppRoot`'s tree | one suspend + atomic store |

Disk writes, timers, and exits ONLY arm when a `jellyplay.perf.*` system
property is present. A production boot with none of them schedules nothing and
writes nothing:

| Property | Effect |
|---|---|
| `jellyplay.perf.dataDir=<dir>` | `DesktopPaths.resolve()` reroutes data/config/db under `<dir>` so runs never touch real appdata |
| `jellyplay.perf.autoExitSeconds=N` | Daemon timer fires `exitProcess(0)` at N s. **Blunt on purpose**: bypasses graceful teardown (download/audio loops die mid-flight). Measurement-only. It cannot fabricate a crash marker — `DesktopCrashHandler` writes markers solely from the uncaught-exception path |
| `jellyplay.perf.heapSampleSeconds=S` | At S s uptime writes `<dataDir>/data/logs/memory-latest.json` (JVM used/max heap). OS working set is sampled externally by the script via PowerShell keyed to live PIDs |
| `jellyplay.perf.persist=true` | Persist marks without scheduling anything else |

Output contract (one compact JSON line each):

* `<profile>/data/logs/startup-latest.json` →
  `{bootNanos,koinStartedNanos,windowShownNanos,firstFrameNanos,koinStartMs,windowShownMs,firstFrameMs,persistRequested}`
  (nanos absolute monotonic, machine-local; ms deltas vs boot; unrecorded = `null`)
* `<profile>/data/logs/memory-latest.json` →
  `{sampledUptimeMs,usedHeapBytes,maxHeapBytes}`

## Methodology

| Aspect | Value |
|---|---|
| Artifact run | jpackage app-image `apps/desktop/build/compose/binaries/main/app/JellyPlay/JellyPlay.exe` (built once via `./gradlew :apps:desktop:createDistributable`) |
| JVM property injection | `JAVA_TOOL_OPTIONS="-Djellyplay.perf.dataDir=… -Djellyplay.perf.autoExitSeconds=12 -Djellyplay.perf.heapSampleSeconds=8"` exported per launch (inherited through the launcher into the hosted JVM; stderr shows the "Picked up JAVA_TOOL_OPTIONS" banner each run) |
| Profile isolation | Fresh `<LOCALAPPDATA>/JellyPlayPerfBaseline/profile-run-N/data+config+db` per run, deleted after collection |
| Warmup policy | 1 discarded warmup run, then 5 measured runs |
| Auto-exit | `exitProcess(0)` at t=12 s of app uptime (bypasses graceful teardown by design, see above) |
| Working-set sampling | PowerShell `Get-Process -Name JellyPlay` every ~0.75 s wall clock until process death; idle sample = median over WALL-CLOCK TICKS 8–13 after launch — a tick is sleep+PS overhead ≈ ~1 s, so the window is roughly 6–10 s POST-LAUNCH. The app-side heap sample fires at exactly 8 s of APP uptime; the two are close but not equal (~±2 s worst-case skew), which every "~"-labeled output marks rather than hides. Deadline hard-kill uses `taskkill //PID <pid> //F` with only PIDs this run observed — never title matching |
| Concurrency guard | Harness refuses to start while ANY `JellyPlay.exe` already lives, so numbers can never include a stranger's session |

Windows quirk recorded for reproducers — launcher behavior DEPENDS on spawn
path: via PowerShell `Start-Process`, `(Start-Process -PassThru).Id` returns a
~8 MB stub parent that exits within ~1 s while another process carries on;
under this harness's DIRECT BASH EXEC that same launcher shell stays alive
beside the JVM until both exit together. Either way up to TWO processes can be
live (shell + JVM), hence sampling ALL live `JellyPlay.exe` instances and the
combined-vs-single working set labels below.

## Machine

| Field | Value |
|---|---|
| OS | Microsoft Windows 11 Home Single Language build 26200 |
| CPU | Intel(R) Core(TM) Ultra 5 125H (18 logical cores) |
| RAM | 15805 MB |
| Build | Compose Multiplatform 1.11.1 desktop, JDK 17 toolchain, `includeAllModules=true`, **proguard disabled** (see limits) |

## Results (this machine, canonical harness execution)

Per-run table (warmup row shown but excluded from aggregates). Canonical run
executed the exact committed-tree binary:

| run | koinStartMs | windowShownMs | firstFrameMs | idleWS_MB(post-launch-WS-window) | maxWS_MB | usedHeap_MB |
|---|---|---|---|---|---|---|
| warmup | 1410.460 | 9250.938 | 9251.168 | 299.9 | 310.8 | 31.7 |
| 1 | 540.441 | 2779.853 | 2779.905 | 324.0 | 334.5 | 56.1 |
| 2 | 702.350 | 3855.301 | 3855.511 | 329.9 | 325.1 | 56.1 |
| 3 | 537.852 | 2546.917 | 2546.967 | 325.3 | 336.8 | 56.0 |
| 4 | 761.359 | 4734.667 | 4734.808 | 309.0 | 301.2 | 36.0 |
| 5 | 535.700 | 2713.816 | 2713.866 | 330.3 | 341.9 | 50.0 |

**Aggregates over 5 measured runs** (min / median / max; warmup excluded).
Regenerated after review round 1: the previous aggregation pipeline dropped
measured run #1 and published 4-sample stats — every cell below was recomputed
as a true 5-sample statistic; the per-run rows above are unchanged.

| field | min / median / max |
|---|---|
| koinStart (ms) | 535.700 / 540.441 / 761.359 |
| windowShown (ms) | 2546.917 / 2779.853 / 4734.667 |
| firstFrame (ms) | 2546.967 / 2779.905 / 4734.808 |
| idle combined working set (MB, launcher+app; WS ticks ≈ 8–13 post-launch — NOT app-t=8s) | 309.0 / 325.300 / 330.3 |
| largest single-process working set to exit (MB) | 301.2 / 334.500 / 341.9 |
| JVM used heap @8s of app uptime (MB) | 36.0 / 56.000 / 56.1 |

### Read-through (description, not prescription)

* Koin graph construction is the most stable segment measured (~0.54–0.76 s
  canonical; other same-day invocations saw up to ~1.15 s under load).
* Between Koin completion and window visibility lies another multi-second
  segment (compose/Skia init plus whatever warms outside our marks); it is
  where future profiling effort would land IF anyone decides startup work is
  warranted.
* `firstFrame − windowShown` stayed ≤ ~0.25 ms in EVERY recorded run of every
  harness execution — first composition applies essentially at visibility; no
  wasted frames between.
* Idle footprint ≈ peak footprint (~300–340 MB working set): memory at rest is
  dominated by mapped/native Skia + runtime image, not live Java objects
  (JVM heap only ~26–56 MB used at rest).

## Limits & honesty section

Read these before quoting any number above:

1. **Single machine, consumer Windows laptop, wall-clock measurement.**
   `windowShown` aggregates landed anywhere from ~2.8 s to ~5.8 s across three
   same-day harness executions minutes apart as background desktop load
   changed (two of those three ran via the pre-review aggregator that dropped
   measured run #1, i.e. their medians describe a 4-run subset — direction of
   the noise conclusion is unaffected). Treat ms numbers as ballpark, not
   contract; only same-session A/B comparisons (before/after one specific
   change, interleaved on the same machine) are meaningful when re-measuring.
2. **Distribution build has proguard/R8 disabled** (`buildTypes.release.proguard
   isEnabled = false`). Startup numbers therefore describe an UNMINIFIED
   classpath — i.e. they are ceiling-favorable (pessimistic for startup);
   enabling shrinking later may shift these, which is exactly why the baseline
   exists before such decisions.
3. **Memory semantics**: "idle combined WS" sums the ~8 MB launcher stub plus
   the app JVM (two processes, see methodology); "largest single-process" is
   one process. They are different quantities on purpose — do not subtract them.
   JVM heap-at-rest is what managed allocations actually hold (a few tens of
   MB — 36–56 MB used across this session's runs); the rest of the working set
   is mapped runtime/Skia/JIT machinery.
4. **firstFrame granularity**: recorded when the frame clock delivers its first
   frame after root-content initial composition (which includes
   `DesktopAppRoot`) — not from inside `DesktopAppRoot`. Empirically identical
   to window-shown here (≤0.45 ms), but a truly painted-pixel hook would need
   deeper plumbing; not worth it while delta measures zero.
5. **Auto-exit is measurement-only** and kills without graceful teardown by
   design (documented in code + printed at runtime whenever armed).
6. **Tray UX: partially covered programmatically, visuals still uneyeballed.**
   Wave 13A extracted the tray handlers into `DesktopTrayActions` with unit
   cover for the programmatically reachable halves — Quit's delegation
   (callback fires exactly once) and Show's null-window path (no-throw
   no-op). NOT covered by any automated test, and still needing a one-time
   manual eyeball on a developer machine: tray rendering, tooltip text, the
   VISUAL restore/focus of a live window (a real `ComposeWindow` cannot be
   constructed headless), and click-through Quit in a live session. Code
   path compiles, the packaged jar carries the icon bytes, and automated
   boots produced zero exceptions/fatal/uncaught lines — that is not a
   substitute for the eyeball. Closing the main window remains a full quit
   BY DESIGN (no hide-to-tray).
7. Raw evidence (per-run logs + JSON copies + CSV) is kept OUTSIDE the repo in
   `%LOCALAPPDATA%\JellyPlayPerfBaseline\results-*` by the harness.

## Reproducing

```bash
./gradlew :apps:desktop:compileKotlin      # gate 1
./gradlew :apps:desktop:test               # gate 2
./gradlew :apps:desktop:createDistributable # builds the app image once
tools/perf/desktop-baseline.sh 5           # 1 warmup + 5 measured launches
```

The script prints the machine block, per-run table, and aggregates ready to
paste into this file, and leaves everything under
`%LOCALAPPDATA%\JellyPlayPerfBaseline\results-<timestamp>\`.
