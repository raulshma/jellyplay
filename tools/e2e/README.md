# E2E toolset (wave 13)

End-to-end verification tools for the JellyPlay KMP desktop migration. Where
`tools/perf/` measures, `tools/e2e/` gates: each tool here drives a real
artifact of the release pipeline and prints a final `PASS`/`FAIL` verdict
meant to be pasted into gate reports.

## Prerequisites

Toolset-wide (individual tools may need only a subset — see table):

- Windows with Git Bash (`cygpath`, `cmd`, `powershell`, `taskkill`)
- Docker — the Jellyfin server fixture
- ffmpeg — media fixtures
- curl — ad-hoc HTTP probes against the fixture
- Node 18+ — web-verify's checker (sibling tool)
- JDK 17 toolchain via `./gradlew` (repo `gradlew` wrapper)

## Tools

| Tool | What it verifies | Status |
|---|---|---|
| `bootstrap-jellyfin.sh` | Brings up the local Jellyfin fixture (Docker container, wizard-over-API incl. 10.11 CSRF quirks, ffmpeg testsrc media + poster, library scan) and prints user/item credentials | live (coordinator) |
| `msi-boot-pass.sh` | The installed-MSI artifact's payload boots: builds the MSI via `:apps:desktop:packageMsi`, administrative-extracts it (`msiexec /a`, no elevation, no install), checks the extracted layout (`JellyPlay.exe` + `runtime/` + `app/`), then boots the EXTRACTED exe under perf-harness properties and requires a clean self-exit with `windowShownMs >= 0` and zero crash logs | wave 13A — live |
| `desktop-session-pass.sh` | Extended desktop session against a live Jellyfin fixture: in-APP video playback through the whole shared pipeline + Esc/popup-ordering evidence | wave 13B — live |
| `web-verify` | Web (wasm) shell against a live Jellyfin fixture: sign-in, Coil artwork, HtmlVideoEngine playback, via headless-Edge CDP AX-tree driving | wave 13C — live |

## Running bootstrap-jellyfin.sh

```bash
tools/e2e/bootstrap-jellyfin.sh
```

Starts (or restarts) the `jellyplay-e2e` Docker container on port 8096 with
persisted state under `tools/e2e/.state/` (gitignored), runs the first-run
wizard over the API, ensures user `harness` / `harness-e2e-pass`, generates
the 12 s testsrc movie + poster, adds the `E2E Media` library and waits for
the item scan. Idempotent: re-running skips completed stages. 10.11 quirks
handled inside: wizard POSTs need browser-like `Origin`/`Referer` + cookie
jar (CSRF middleware answers origin-less POSTs with 404), and JSON key casing
varies per endpoint.

## Running msi-boot-pass.sh

```bash
tools/e2e/msi-boot-pass.sh
```

Builds `JellyPlay-0.1.1.msi` if absent (several minutes; targeted Gradle
task, no `:app` involved), extracts it to the OS temp dir, boots the
extracted `JellyPlay.exe` once for 30 s, and prints the verdict.

Environment overrides:

| Variable | Default | Meaning |
|---|---|---|
| `JELLYPLAY_VERSION` | `0.1.1` | MSI version (must be numeric `x.y.z`) |
| `MSI` | — | Path to an existing `.msi`; skips the Gradle build |
| `SKIP_BUILD` | `0` | `1` = reuse the expected artifact, never build |
| `AUTO_EXIT_SECONDS` | `30` | App auto-exit delay (deadline is +20 s) |
| `KEEP_EXTRACT` | `0` | `1` = keep the ~300 MB extracted tree even on PASS |

Safety properties: refuses to start while any `JellyPlay.exe` runs; kills by
observed PID only (never window-title patterns); all runtime state lives in
the OS temp dir OUTSIDE the repo (see `.gitignore` here for the defensive
in-repo rules).

## Expected output (tail)

```
==== msi-boot-pass summary ====
verdict:         PASS
msi:             .../apps/desktop/build/compose/binaries/main/msi/JellyPlay-0.1.1.msi (155 MB, version 0.1.1)
extracted files: 42 (app-image ref: 42)
windowShownMs:   2713.816
crash logs:      0
self-exit:       yes (30s auto-exit)
evidence kept:   .../jellyplay-msi-boot-pass/run-<stamp> (boot.out/boot.err, msiexec-extract.log, startup json)
```

Exit code `0` = PASS, non-zero = FAIL (each failure names its stage).

## Running desktop-session-pass.sh (wave 13B)

Real-server desktop session pass: verifies IN-APP video playback against a
real Jellyfin inside the REAL windowed app — the whole shared pipeline
(`VideoPlayerScreen` → `VideoPlayerViewModel` → `DesktopMpvPlayerEngineFactory`
→ `MpvDesktopEngine`) — plus the Esc/popup-ordering question wave 9 left open.

```bash
tools/e2e/desktop-session-pass.sh
```

Env overrides: `SERVER_URL` (default `http://localhost:8096`),
`E2E_USERNAME` (`harness`), `E2E_PASSWORD` (`harness-e2e-pass`), `ITEM_NAME`
(`Harness Test Clip`), `AUTO_EXIT_SECONDS` (150). (`USERNAME` is deliberately
not read — on Windows it is the logged-in user's ambient env var and would
silently shadow the default.)

What the script does:

1. Waits for the Jellyfin server (`GET /System/Info/Public` → 200).
2. `AuthenticateByName` → token, then resolves the movie item id via
   `/Items?searchTerm=…&Recursive=true&IncludeItemTypes=Movie`.
3. Builds the packaged app image if missing
   (`./gradlew :apps:desktop:createDistributable`).
4. **Refuses** to start while any `JellyPlay.exe` already runs (screenshots and
   keystrokes must hit only its own instance; kills are PID-only via sampled
   `Get-Process JellyPlay` PIDs — never by window title).
5. Spawns `JellyPlay.exe` with `JAVA_TOOL_OPTIONS` carrying the
   `jellyplay.harness.*` properties (see `DesktopSessionHarness.kt` in
   apps/desktop), an isolated `-Djellyplay.perf.dataDir` profile and
   `-Djna.library.path=<repo>/tools/mpv` (libmpv is per-machine, gitignored).
6. Prints `<profile>/data/logs/session-harness.json`, the screenshot dir and
   the app's harness stdout; exits 0 only when the report says
   `"overallPass":true`.

Report steps (from `DesktopSessionHarness`): `CONFIG`, `LOGIN`, `NAV_READY`,
`SCREENSHOT_HOME`, `PUSH_PLAYER`, `ENGINE_CREATED`, `SCREENSHOT_PLAYER_OPEN`,
`PLAYBACK` (engine reached isPlaying with ≥1 s playhead advance, evidenced by
the `EngineActivityRecorder`), `SCREENSHOT_MID_PLAY`, `SHEET_TRIGGER_SCAN`,
`OVERLAY_SPACE` (injects SPACE and records whether it reached the player —
on the reference machine it did NOT: a player-Box focus gap, honestly
recorded rather than asserted), `ESC_SEQUENCE` (asserts the verified
ordering — the scaffold's back handling pops the player route; a
not-popping run records its finding and FAILS the pass, keeping the tool a
regression gate on the wave-9 answer rather than an open question).

Requires Git Bash on Windows with `cygpath`, `powershell` and `taskkill`
available, and an interactive session (the harness takes real screenshots via
`java.awt.Robot`). Evidence retention: the FAIL path keeps the profile dir
and prints its path; the PASS path keeps it too (temp dir, auto-cleaned by
the OS eventually) — the report JSON path is always printed.

## Running web-verify (wave 13C)

Web-shell verification against a live Jellyfin: builds the wasm development
bundle, stages it with the compose-resources merge, serves it statically,
drives headless Edge over CDP through the accessibility tree (canvas app —
no DOM locators), and asserts the full flow: sign-in → Diagnostics pane →
Coil artwork decoded (`IMAGE_STATE: OK`) → HtmlVideoEngine muted autoplay
reaching `playing=true pos>0` → `DIAG_OVERALL: OK` → zero console
errors/exceptions → screenshot.

```bash
tools/e2e/web-verify.sh
```

Env overrides: `JP_SERVER_URL` (default `http://localhost:8096`),
`JP_USERNAME` (`harness`), `JP_PASSWORD` (`harness-e2e-pass`). (`USERNAME`
is deliberately not read — Windows sets it to the logged-in account and the
sign-in would silently fail against the fixture.) Prerequisites: Node 18+
with `ws` (`npm install` inside `tools/e2e/` — package.json committed),
Edge, and the Node-download governance in `settings.gradle.kts` (webpack
builds work with no repository-mode flips).

One browser `Log.error` per run is expected and not gated: the automatic
`/favicon.ico` 404 against the bare static server. Evidence (result.json +
screenshot) lands in the OS temp dir, outside the repo.
