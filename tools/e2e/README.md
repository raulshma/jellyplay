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
- JDK 17 toolchain via `./gradlew` (repo `gradlew` wrapper)

## Tools

| Tool | What it verifies | Status |
|---|---|---|
| `bootstrap-jellyfin.sh` | Brings up the local Jellyfin fixture (Docker container, wizard-over-API incl. 10.11 CSRF quirks, ffmpeg testsrc media + posters, library scan) and prints user/item credentials — 9 movies (8 carrying 2560x1440 posters) | live (coordinator) |
| `msi-boot-pass.sh` | The installed-MSI artifact's payload boots: builds the MSI via `:apps:desktop:packageMsi`, administrative-extracts it (`msiexec /a`, no elevation, no install), checks the extracted layout (`JellyPlay.exe` + `runtime/` + `app/`), then boots the EXTRACTED exe under perf-harness properties and requires a clean self-exit with `windowShownMs >= 0` and zero crash logs | wave 13A — live |
| `desktop-session-pass.sh` | Extended desktop session against a live Jellyfin fixture: in-APP video playback through the whole shared pipeline + Esc/popup-ordering evidence | wave 13B — live |
| `desktop-native-dialog-pass.sh` | Desktop native-dialog flows inside the real windowed app: the AWT FileDialog settings-backup export/import round trip (SAVE with the production prefill, LOAD, ESC cancel) typed into by a Robot driver, asserting the exported file's existence + v2 JSON shape and the VM's status lines — server-free, the audit-F9 lane | wave 22F — live |
| `desktop-native-dialog-flows-pass.sh` | The four remaining native-dialog flows (editor image picker, editor subtitle picker, insights heatmap share, player subtitle upload): real sign-in + route pushes + REAL Robot mouse clicks through the harness-gated `HarnessClickBridge` (`jellyplay.flowpass.*` props), the REAL AWT LOAD dialogs typed by the shared driver, server-side post-conditions (Primary image tag change / subtitle stream deltas / heatmap PNG under the redirected tmpdir) | wave 23 — live |

## Running bootstrap-jellyfin.sh

```bash
tools/e2e/bootstrap-jellyfin.sh
```

Starts (or restarts) the `jellyplay-e2e` Docker container on port 8096 with
persisted state under `tools/e2e/.state/` (gitignored), runs the first-run
wizard over the API, ensures user `harness` / `harness-e2e-pass`, generates
the 12 s testsrc movie + poster PLUS the large-poster library (8 movies with
2560x1440 posters), adds the `E2E Media`
library and waits for the item scan. Idempotent: re-running skips completed
stages (per-item name lookup + byte-compared poster replacement; the scan
itself extracts small primaries from the video pixels which the size check
replaces). 10.11 quirks handled inside: wizard POSTs need browser-like
`Origin`/`Referer` + cookie jar (CSRF middleware answers origin-less POSTs
with 404), JSON key casing varies per endpoint, and the file parser splits
"Name (Year).mp4" into Name + ProductionYear (lookups use display names).
If the config volume rots (wizard 401s before poster repair), wipe
`tools/e2e/.state/config` and re-run.

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

## Running desktop-native-dialog-pass.sh (wave 22F)

Closes audit finding F9: the wave-20 desktop AWT `FileDialog` flows that
landed "manually-verified-only" and then fell off the wave-21
remaining-surface ledger. The settings backup export + import round trip now
has committed, re-runnable evidence: the app runs with the REAL modal
dialogs (`pickAwtFile` shown on the EDT, the production SAVE prefill and
titles) while a `java.awt.Robot` driver thread clears the filename box
(Ctrl+A), types the full absolute path of a pre-created workspace file and
presses Enter (ESC for the cancel leg); the pass asserts the observable
app-side effects from OUTSIDE the dialog machinery — the exported file's
existence + `schemaVersion:2` JSON shape on disk and the
`SettingsViewModel`'s export/import status lines. Server-free by design
(the backup flow is local-prefs-only — no Jellyfin fixture, no libmpv, no
sign-in). Full ledger + what stays manual (the BackupSettingsScreen row
clicks, editor pickers, heatmap share, player subtitle upload) in
`docs/e2e/desktop-native-dialogs.md`.

```bash
tools/e2e/desktop-native-dialog-pass.sh
```

Env overrides: `AUTO_EXIT_SECONDS` (90; the Robot types ~100 chars at
40 ms/keystroke, so each typed dialog costs ~9 s — 90 s covers the whole
run plus the 3-attempt retry ladder). Same hygiene as the session pass:
refuses to start while any `JellyPlay.exe` runs, PID-only `taskkill //PID`
teardown of sampled PIDs, isolated `-Djellyplay.perf.dataDir` profile,
space-free paths enforced (JAVA_TOOL_OPTIONS whitespace split). Report:
`<profile>/data/logs/dialog-harness.json` (`"overallPass":true` gates the
exit code) + dialog screenshots under the workspace `shots/` dir.

## Running desktop-native-dialog-flows-pass.sh (wave 23)

Closes the wave-22F checklist for items 3-6 (editor image picker, editor
subtitle picker, insights heatmap share, player subtitle upload): every
target row/sheet/button is reached by REAL Robot mouse clicks — the screens
annotate their rows with `Modifier.harnessClickTarget(id)` and the
harness-gated `HarnessClickBridge` (armed by `jellyplay.flowpass.enabled`
in Main.kt, zero cost on every normal boot) publishes live window-space
bounds; the harness converts them to screen coordinates (dividing out the
AWT `defaultTransform` scale for HiDPI) and clicks. The AWT LOAD dialogs
are typed by the same driver the wave-22F settings lane uses
(`HarnessDialogDriver`). Post-conditions are SERVER-side: the item's
Primary image tag must change, the subtitle stream count must grow (editor
AND player flows), and the heatmap share must write a PNG under the
redirected `-Djava.io.tmpdir`. It surfaced two real 10.11 wire-contract
bugs in `MetadataApiClientImpl.setItemImage` (raw-binary body → 500;
`image/*` Content-Type → 400) — both measured with curl and fixed.

```bash
tools/e2e/bootstrap-jellyfin.sh               # fixture first (Docker)
tools/e2e/desktop-native-dialog-flows-pass.sh # exit 0 = PASS
```

Env overrides: `AUTO_EXIT_SECONDS` (300), `SERVER_URL`, `E2E_USERNAME`,
`E2E_PASSWORD`, `ITEM_NAME`. Requires the interactive-display + libmpv +
no-concurrent-JellyPlay hygiene of the sibling passes. If shared modules
were just edited, build once with `--no-configuration-cache --no-build-cache`
(the stale configuration-cache VFS can otherwise serve pre-edit classes —
the wave-23 run-5/6/7 lesson). Full ledger: `docs/e2e/desktop-native-dialogs.md`.
