# E2E tooling

## desktop-session-pass.sh (wave 13B)

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
`OVERLAY_SPACE` (SPACE shows the controls overlay; the playhead freeze proves
the key reached the player), `ESC_SEQUENCE` (single Esc pops the player route
or not — the recorded `finding` answers the ordering question).

Requires Git Bash on Windows with `cygpath`, `powershell` and `taskkill`
available, and an interactive session (the harness takes real screenshots via
`java.awt.Robot`).
