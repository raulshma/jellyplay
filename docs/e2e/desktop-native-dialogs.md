# Desktop native-dialog flows — verification ledger (wave 22F, audit finding F9)

**Verdict: the settings backup export/import dialog flow is now a committed,
re-runnable, green automated lane (3/3 PASS runs on 2026-08-30); the other
wave-20 native-dialog flows stay checklist-only this round, with the shared
dialog mechanics now indirectly evidenced by the automated lane.** The
"manually-verified-only, no headless AWT story" gap wave 20 recorded — and
wave 21's remaining-surface list silently dropped — is closed by an honest
split: what can be gated deterministically IS gated; what would take
pixel-clicking into scrollable lists (with a Factory Reset row one mis-click
away) is a named manual checklist, not a fake green.

> **WAVE-23 UPDATE (2026-09-12): items 3–6 are now a green automated lane
> too — `tools/e2e/desktop-native-dialog-flows-pass.sh` (overallPass:true,
> 2/2 runs this round), with every row/sheet/button reached by REAL Robot
> mouse clicks through the harness-gated `HarnessClickBridge`.** The
> wave-22F "concrete blocker" below is resolved (see the wave-23 section at
> the bottom for the mechanism, the two measured production bugs it
> surfaced, and the per-flow evidence). The wave-22F body below is kept
> verbatim for the history of the boundary decision; the "Deliberate
> boundary" and "Why items 3–6 stayed checklist" sections describe the
> wave-22F state, not the current one.

## What wave 20 left unverified (the F9 backlog)

Wave 20A/20C landed real AWT `FileDialog` flows on desktop and their commit
messages carried the honest cut: *"the native dialog halves (modal AWT
SAVE/LOAD, cancel retention, Desktop.open viewer handoff, live GraphicsLayer
readback) are honestly manual-only — no headless AWT story exists."* The
flows:

1. Settings backup **export** (SAVE dialog, pre-filled `jellyplay-settings.json`)
   and **import** (LOAD dialog) via `DesktopBackupFilePicker` +
   `DesktopSettingsBackupIo`.
2. Editor image picker (`rememberImageFilePicker`, "Choose image") and editor
   subtitle picker (`rememberSubtitleFilePicker`, "Choose subtitle") on
   MetadataEditor.
3. Insights heatmap share (`HeatmapShare.jvm` — GraphicsLayer readback → PNG
   under tmpdir → `Desktop.open`).
4. Player-video subtitle upload picker (`SubtitleUploadPicker.jvm`, LOAD with
   an advisory subtitle-extension filter) + `DesktopVideoPlayerPlatform`
   file reads.

Wave 21D then de-triplicated the dialog shape itself into ONE helper,
`pickAwtFile` (`shared/core/ui/src/jvmMain/.../DesktopFileDialog.kt`) — the
fact that makes the wave-22F lane below evidence for more than settings.

## Automated lane (new): settings backup export + import round trip

`tools/e2e/desktop-native-dialog-pass.sh` — drives the REAL windowed app
with the REAL modal dialogs. Server-free (the backup flow is
local-prefs-only — no sign-in, no Docker fixture, no libmpv needed) and
fast (~30 s app time after the app image exists).

```bash
tools/e2e/desktop-native-dialog-pass.sh          # exit 0 = PASS
AUTO_EXIT_SECONDS=120 tools/e2e/desktop-native-dialog-pass.sh   # slower machines
```

Prerequisites: Git Bash on Windows, an interactive session (the Robot needs
a real display; a locked screen breaks it), and the packaged app image (the
script builds it via `:apps:desktop:createDistributable` if missing).

What one run proves (steps of `DesktopNativeDialogHarness`, gated by
`jellyplay.dialogpass.enabled=true`, report at
`<profile>/data/logs/dialog-harness.json`, dialog screenshots under the
workspace `shots/` dir):

| Step | Proves |
|---|---|
| `CONFIG` | workspace writable, target absent, non-headless AWT |
| `DIALOG_EXPORT_SAVE` | the production `pickAwtFile` SAVE call (same title "Export settings", same `jellyplay-settings.json` prefill, shown **on the EDT** exactly like a click handler) opens the native dialog; the Robot driver clears the name box (Ctrl+A), types the full absolute workspace path, presses Enter; the dialog closes and returns exactly the typed file |
| `EXPORT_VM_WRITES` | the production callback body `SettingsViewModel.exportSettings(uri)` writes the file through `DesktopSettingsBackupIo`'s `file:` URI → JDK stream mapping; asserted from OUTSIDE the process: file exists, non-empty, JSON `schemaVersion == 2`, non-empty slices (measured: 14,901 bytes, 18 slice keys) + VM status "Settings exported successfully" |
| `DIALOG_IMPORT_LOAD` | the LOAD dialog picks the very file the export wrote (same Robot mechanism) |
| `IMPORT_VM_STAGE_CONFIRM` | `importSettings` stages a v2 pending import (not legacy, no version mismatch, dialog-produced URI) and `confirmImport(false)` lands "Settings imported successfully" |
| `DIALOG_CANCEL_ESC` | ESC on the native dialog = cancel: `pickAwtFile` returns null, no callback fires, VM state untouched — the live twin of the wave-21D `pickedAwtFile` cancel unit test |

Status: **PASS 3/3 runs, every dialog on attempt 1** (2026-08-30, Windows 11,
1280x800, JDK 21.0.7 app runtime). Observed mechanics worth knowing for
future flakiness triage (all stamped `t=+…ms` in the app log):

- The native dialog appears ~0.8–2 s after `pickAwtFile` is called; typing a
  ~100-char path takes ~8–9 s at the 40 ms/keystroke cadence; the whole run
  fits in the 90 s auto-exit deadline with room for the 3-attempt retry
  ladder.
- `WFileDialogPeer` never reflects its bounds into the AWT object (measured
  0x0 while showing), so the per-step screenshot falls back to the full
  default screen; the dialog is front-and-center in it (see the shots — the
  production prefill visibly selected in the File name box).
- The AWT `KeyboardFocusManager` reports `focusedWindow=null` while a NATIVE
  dialog owns focus — the driver's focus diag line is therefore informational,
  and correctness is judged by the outcome (dialog dismissed + returned path).

Shared-machine hygiene is the wave-13 set: refuse-to-start while any
`JellyPlay.exe` runs, PID-only `taskkill //PID` teardown of only OUR sampled
PIDs (never window-title patterns), never kill a harness mid-run (the app
self-exits after writing the report), isolated `-Djellyplay.perf.dataDir`
profile, space-free paths enforced because `JAVA_TOOL_OPTIONS` splits on
whitespace.

### Deliberate boundary: the row clicks are NOT automated

The harness calls `pickAwtFile` + the production callback bodies directly
instead of pointer-clicking the Backup & Restore screen's rows. Pixel
clicking a scrollable LazyColumn is the one ingredient the wave-13/14
lessons rule out for a green lane (unstable coordinates across DPI/fonts,
sibling-window focus thieves, and the third row under Export/Import is
**Factory Reset**), and a production app has no compose-test machinery to
resolve row bounds. The row→picker→VM wiring that the click would cover is
three lines (`BackupSettingsScreen.kt`: `launchCreateExport` /
`launchOpenImport` → `exportSettings`/`importSettings`) and stays on the
checklist below. Everything behind the click — the modal SAVE/LOAD/cancel
mechanics, the `file:` URI delivery, the JDK-stream IO, the VM round trip —
is what the lane gates.

## Manual checklist (per flow; statuses dated)

Each item: exact steps on desktop → the observable that counts. The dialog
mechanics themselves (open/type/Enter/cancel) are **indirectly evidenced**
for the FileDialog flows (items 1–4 and 6) by the automated lane, because
they all call the same `pickAwtFile` helper with the same modal shape —
item 5 (heatmap share) goes through `Desktop.open` instead and has no
automated dialog evidence; what remains manual for every item is the
per-flow click wiring and the flow-specific post-condition.

1. **Settings backup export — row wiring.** Sign in → Settings rail tab →
   drill into the setting whose search id is `backup_export` ("Export
   settings" under Backup & Restore) → click the row. EXPECT the native
   "Export settings" SAVE dialog with `jellyplay-settings.json` pre-filled;
   choose a location → Save. EXPECT "Settings exported successfully" toast +
   the file on disk with `"schemaVersion": 2`. STATUS: dialog + VM verified
   by automation (2026-08-30); the row click itself last exercised manually
   in wave 20C's review round — re-verify on next touch.
2. **Settings backup import — row wiring.** Same screen, "Import settings"
   row → LOAD dialog → pick a previously exported file → EXPECT the
   staged-import confirmation (schema/version summary) → confirm. EXPECT
   "Settings imported successfully" and the preferences applied. STATUS:
   dialog + stage/confirm verified by automation (2026-08-30); row click +
   confirmation sheet — re-verify on next touch.
3. **Editor image picker.** Sign in as admin → MediaDetail of any movie →
   Edit metadata (MetadataEditor — unguarded route since wave 18B) → Images
   tab → upload-from-file row. EXPECT "Choose image" LOAD dialog (advisory
   png/jpg/jpeg/webp/gif/bmp filter — ignored by Windows, per the helper
   KDoc) → pick a local image. EXPECT the Images tab preview renders the
   picked file (coil3 `FileUriFetcher` over the `file:/` URI) and upload
   pushes it to the server. STATUS: unverified end-to-end (blocker below);
   filter/extension handling unit-tested (`DesktopEditorFilePickerTest`).
4. **Editor subtitle picker.** Same screen, Subtitles tab → upload-from-file
   row. EXPECT "Choose subtitle" (unfiltered, matching the Android any-type
   launcher) → pick a local .srt. EXPECT the sheet lists the picked file
   (blank display name falls back to `subtitle.srt`) and upload succeeds.
   STATUS: unverified end-to-end (blocker below).
5. **Insights heatmap share.** Sign in as admin → insights/statistics screen
   → the heatmap share IconButton (visible on desktop since 20C — the
   actual is non-null) → EXPECT a PNG written under `java.io.tmpdir`
   (`watch_progress_heatmap_<millis>.png`, magic bytes = PNG — unit-tested
   in `DesktopHeatmapShareIoTest`) and the OS viewer opening it
   (`Desktop.open`). STATUS: unverified end-to-end (blocker below + one
   genuinely live-only input: the GraphicsLayer→bitmap readback of the
   recorded grid).
6. **Player-video subtitle upload.** Play any item → subtitle sheet → Upload
   tab → select file. EXPECT "Choose subtitle file" LOAD dialog (advisory
   srt/ass/ssa/vtt/sub/idx filter) → pick → the sheet shows name + size
   (`DesktopVideoPlayerPlatform.queryFileSizeBytes` over the `file:` URI) →
   upload sends the bytes to the server (`uploadSubtitle`). STATUS:
   unverified end-to-end (blocker below; gateway length/bytes resolution
   unit-tested in `SubtitleUploadPickerJvmTest`).

### Why items 3–6 stayed checklist this round (the concrete blocker)

All four need real login + server + navigation to a screen the harness
cannot yet reach deterministically: the harness's only navigation primitive
is pushing a `NavKey` onto the scaffold-published back stack (wave-13B
pattern), which reaches VideoPlayer but not MediaDetail → MetadataEditor
drill-in, not the insights screen, and — the same wall as the settings rows
— none of the target rows/sheets are keyboard-reachable, so after navigation
the trigger would again be a pixel click. Extending the harness with
click-target resolution ballooned past this wave's timebox; the settings
lane (no navigation, no clicks needed) was the flow where full automation
fit honestly. Items 3–6 remain open for a future wave that either adds a
compose-semantics-driven click surface (e.g. a harness-gated accessibility
bridge) or accepts screen-specific pixel maps validated per machine.

## Files (wave 22F)

- `apps/desktop/src/main/kotlin/.../DesktopNativeDialogHarness.kt` — the
  gated harness + host (new).
- `apps/desktop/src/main/kotlin/.../DesktopAppRoot.kt` — hosts the harness
  next to the session harness (zero-cost unless `jellyplay.dialogpass.enabled=true`).
- `apps/desktop/src/main/kotlin/.../DesktopSessionHarness.kt` —
  `SessionHarnessReport` gains the defaulted `harness` id so the dialog
  pass reuses the pinned step-ledger JSON shape (`desktop-native-dialog`).
- `apps/desktop/build.gradle.kts` — `implementation(libs.lifecycle.viewmodel)`
  (the harness names `SettingsViewModel` directly; the shell's screens only
  render `settingsSection`, so the supertype was never on the shell
  classpath).
- `tools/e2e/desktop-native-dialog-pass.sh` — the runner (new).
- `tools/e2e/README.md` — toolset table + run instructions.

## Wave 23 (2026-09-12): items 3–6 green — the flows lane

**Verdict: `tools/e2e/desktop-native-dialog-flows-pass.sh` — overallPass
2/2 runs (2026-09-12, Windows 11, 150% display scale, JDK 21.0.7 app
runtime), every flow on attempt 1 in both runs.** Drives the REAL windowed
app against the real Docker Jellyfin fixture (`bootstrap-jellyfin.sh`, the
same one the session/web passes use) with real sign-in, real navigation,
real native dialogs, real Robot mouse clicks on the production rows, and
SERVER-SIDE post-conditions (the strongest observable: what the server
stores changes, not just what the UI shows).

```bash
tools/e2e/bootstrap-jellyfin.sh               # once: fixture on :8096
tools/e2e/desktop-native-dialog-flows-pass.sh # exit 0 = PASS
AUTO_EXIT_SECONDS=420 tools/e2e/desktop-native-dialog-flows-pass.sh
```

Per-flow results (from `<profile>/data/logs/flow-harness.json`,
`harness:"desktop-native-dialog-flows"`, run 2026-09-12):

| Step | Post-condition (server-side unless noted) | Result |
|---|---|---|
| `FLOW3_EDITOR_IMAGE_PICKER` | item Primary `imageTag` changed (`be6b6b98…` → `b1826a78…`) after CLICK Images tab → Upload → Select image → REAL "Choose image" LOAD dialog (Robot types path+Enter) → CLICK Upload-confirm | **PASS** (17.6 s) |
| `FLOW4_EDITOR_SUBTITLE_PICKER` | item subtitle streams 0 → 1 after CLICK Subtitles tab → Upload → Select file → REAL unfiltered "Choose subtitle" LOAD dialog → CLICK language field + type `eng` → CLICK Upload-confirm | **PASS** (15.8 s) |
| `FLOW5_HEATMAP_SHARE` | CLICK the heatmap share IconButton → `watch_progress_heatmap_<millis>.png` written under the redirected `-Djava.io.tmpdir` (10,984 bytes, PNG magic verified headlessly by the pre-existing unit-tested writer) | **PASS** (2.0 s) |
| `FLOW6_PLAYER_SUBTITLE_UPLOAD` | REAL playback first (engine sampled PLAYING), SPACE shows controls, CLICK subtitles trigger → hub Get tab → Upload sub-tab → Select file → REAL "Choose subtitle file" LOAD dialog → language typed → CLICK Upload-confirm → streams 1 → 2 | **PASS** (22.4 s) |

Evidence: 14 per-step screenshots under the workspace `shots/` dir
(editor tabs, upload sheets, picked-file previews, the native dialogs
front-and-center, the playing player, the heatmap), plus the runner's
stdout (`grep JellyPlay.*flowpass` on the app log).

### What resolved the wave-22F blocker (the click-reach fix)

1. **Navigation was never a wall.** All three target screens are
   first-class routes (`Route.MetadataEditor` / `Route.WatchProgressHeatmap`
   / `Route.VideoPlayer`), so the wave-13B primitive — push a `NavKey` onto
   the scaffold-published back stack — reaches all of them. The wave-22F
   note ("reaches VideoPlayer but not MediaDetail → MetadataEditor
   drill-in, not the insights screen") was a timebox cut, not a structural
   fact.
2. **Row clicks: the prescribed "harness-gated accessibility bridge" landed
   as `HarnessClickBridge`** (`shared/core/ui` commonMain — wasm-clean,
   `@Volatile`-flag only, zero cost when disarmed): screens annotate their
   rows with `Modifier.harnessClickTarget(id)`; the modifier factory
   returns `this` untouched unless `jellyplay.flowpass.enabled` armed the
   bridge in Main.kt BEFORE any screen composes. When armed, each row
   publishes its live window-space bounds (+ its own enabled state for the
   gated upload-confirm buttons) on every position pass and removes the
   entry on disposal. The harness converts bounds → screen coordinates —
   **÷ the AWT `defaultTransform` scale (measured 1.5: Compose px vs AWT
   logical px on HiDPI — the run-1/2/3 lesson, caught by the failure
   screenshot showing the editor with the click silently landing off-target)
   — and drives a REAL `java.awt.Robot` mouse click at the row center**, so
   every click exercises the production hit-testing + onClick wiring. No
   per-machine pixel maps: bounds are read live from composition.
   Owner-window resolution handles the material3 `ModalBottomSheet`
   opens-its-own-window case by tracking AWT `WINDOW_OPENED` events (the
   newest visible non-FileDialog window hosts sheet targets; the player's
   in-window sheets resolve to the main window).
3. **The lane paid for itself immediately — two real production bugs, both
   fixed:**
   - **`MetadataApiClientImpl.setItemImage` wire contract (shared/
     core/network, flagged here per the report-first constraint — the
     change is one method):** Jellyfin 10.11's SetItemImage base64-DECODES
     the request body (raw binary body → 500 "One of the identified items
     was in an invalid format") AND requires a CONCRETE Content-Type
     (`image/*` → 400; `image/png` → 204; both measured with curl against
     the fixture). The code sent raw bytes typed `image/*` — the editor
     image upload could never have succeeded against a 10.11 server on ANY
     platform. Fixed: base64-encode + magic-number mime sniff
     (`sniffImageMediaType`, png/jpeg/webp/gif/bmp). Subtitle uploads
     already carried base64 (`UploadSubtitleDto.data`) and needed no change.
   - **Harness-side (apps/desktop, own module):** the stale
     configuration-cache/VFS trap — Gradle served a pre-edit
     `FROM-CACHE`/UP-TO-DATE output for `:shared:core:network` and the app
     image across three runs while the source was already fixed (run-5/6/7
     wrong-diagnosis detour). Fix: build the lane with
     `--no-configuration-cache --no-build-cache` after touching shared
     modules, or clear the project `.gradle/` state; the runner itself
     needs no flags (it only builds the image when missing).
4. **Runner mechanics** (tools/e2e/desktop-native-dialog-flows-pass.sh):
   waits for the fixture, authenticates, resolves the item, generates the
   sample pick files (ffmpeg testsrc2 PNG + minimal SRT) in a space-free
   workspace, redirects `-Djava.io.tmpdir` INTO the workspace so flow 5's
   PNG lands somewhere assertable and disposable, spawns the packaged app
   under `jellyplay.flowpass.*` + isolated profile + libmpv, waits for
   `flow-harness.json` (tasklist-based PID sampling — a hung powershell
   call once stalled the run-1 poll loop past its deadline), restores the
   fixture poster when a previous run's sample.png is still the Primary
   (Jellyfin re-tags only on content change), and PID-only kills OUR
   observed PIDs on timeout.

### Known limits of the lane (honest cut, kept from wave 22F's spirit)

- The lane proves row→dialog→VM→server wiring and the server-side
  post-conditions. It does NOT prove pixel-perfect sheet rendering (the
  confirm button sits at the sheet's bottom edge on a 533-AWT-px window —
  visible in `flow3-picked-preview.png`) or the coil preview decode
  (`MediaImage` renders, but the harness asserts state, not pixels).
- `FLOW4` reports `uploadedName = "?"`: the uploaded stream's title is
  server-assigned and the harness asserts the count delta instead of the
  name (the editor sheet's blank-name fallback to `subtitle.srt` stays
  covered by the unit tests).
- Flow 5's OS-viewer handoff (`Desktop.open`) is intentionally NOT
  asserted — opening a viewer window during an automation run steals
  focus; the PNG-file post-condition is the gate.
- The window-tracking owner resolution assumes at most ONE extra window
  (the open sheet). A future flow with two stacked Compose windows needs
  a z-order rule, not just creation order.
