# E2E toolset (wave 13)

End-to-end verification tools for the JellyPlay KMP desktop migration. Where
`tools/perf/` measures, `tools/e2e/` gates: each tool here drives a real
artifact of the release pipeline and prints a final `PASS`/`FAIL` verdict
meant to be pasted into gate reports.

## Prerequisites

Toolset-wide (individual tools may need only a subset — see table):

- Windows with Git Bash (`cygpath`, `cmd`, `powershell`, `taskkill`)
- Docker — server-side fixtures for the session tool (sibling tool)
- ffmpeg — media fixtures for the session tool (sibling tool)
- curl — ad-hoc HTTP probes against the fixtures (sibling tools)
- Node 18+ — web-verify's checker (sibling tool)
- JDK 17 toolchain via `./gradlew` (repo `gradlew` wrapper)

## Tools

| Tool | What it verifies | Status |
|---|---|---|
| `msi-boot-pass.sh` | The installed-MSI artifact's payload boots: builds the MSI via `:apps:desktop:packageMsi`, administrative-extracts it (`msiexec /a`, no elevation, no install), checks the extracted layout (`JellyPlay.exe` + `runtime/` + `app/`), then boots the EXTRACTED exe under perf-harness properties and requires a clean self-exit with `windowShownMs >= 0` and zero crash logs | wave 13A — live in this branch |
| `desktop-session-pass.sh` | Extended desktop session against a live Jellyfin fixture (Docker + ffmpeg fixtures) | placeholder — landing on branch wave/13B |
| `web-verify` | Website/web target verification | placeholder — landing on branch wave/13C |

The two placeholder rows describe sibling tools owned by other worktrees;
they are intentionally stubbed here so parallel-branch merges do not conflict
on this table's content.

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
