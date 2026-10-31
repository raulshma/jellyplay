# Settings sub-screens crash-on-open — device pass result (v0.11.6, FIXED)

**Result: PASS on the fixed build; the v0.11.6 artifact goes RED on exactly
the six declaration-path screens.** `tools/e2e/device-settings-smoke-pass.sh`
is committed as the standing release gate for the crash-on-open class.

## The regression (2026-10-31)

The v0.11.6 build (commit `82830c432` had landed in it) crashed the app when
tapping **Home Screen**, **Appearance**, **Playback** (and, as the device
walk later confirmed, **Language & Subtitles**, **Downloads & Storage**,
**Security**) on the Settings screen. Every other settings entry point
worked.

### Root cause — an inverted exclusivity check

Commit `82830c432` ("refactor(settings): accept the GROUP-grain slot
registry", new in v0.11.6) introduced a guard in
`shared/feature/settings/.../PreferenceScreenScaffold.kt` meant to keep the
chassis's two content paths exclusive:

```kotlin
// AS IT LANDED (inverted — crashes the LEGAL path, waves the ILLEGAL one through):
check(groups == null || content !== NoInlineContent) { "…paths are exclusive" }
```

`check` throws when the condition is FALSE, i.e. when `groups != null &&
content === NoInlineContent` — which is exactly the state every
declaration-path screen passes (`groups = …`, no `content`, so the parameter
default sentinel rides in). Six screens hit it at first composition:

```
java.lang.IllegalStateException: PreferenceScreenScaffold: pass groups or
content, not both — the paths are exclusive
```

while the illegal both-paths state the guard exists to catch (`groups` plus a
hand-written inline block) evaluated TRUE and would have been waved through.
The six inline-content screens (Controls, Discover Rows, Home Layout Presets,
Audio Player, Backup & Restore, Notifications) never touch the check and
worked. Debug builds crash identically — nothing release-specific — the
drill-in path simply wasn't exercised on either variant in testing.

The call-site audit (all 12 `PreferenceScreenScaffold(` sites) found **zero**
sites passing both paths: the corrected check cannot false-fire anywhere.

### The fix

`PreferenceScreenScaffold.kt`: the predicate extracted as a pure internal
function with the branches corrected, and the sentinel promoted to internal
so the truth table is unit-testable:

```kotlin
internal fun preferencePathsAreExclusive(
    groups: List<PreferenceScreenGroup>?,
    content: LazyListScope.(MutableState<PickerState<*>?>) -> Unit,
): Boolean = groups == null || content === NoInlineContent
```

`shared/feature/settings/src/jvmTest/.../PreferenceScreenPathsExclusiveTest.kt`
pins the truth table — demonstrated RED against the inverted logic as it
landed (the declaration-path leg fails: expected true, got false — the
crash itself; the both-paths leg fails the other way) and GREEN against
the fix.

### Device verification (POCO F1 / lineage, Android 15, arm64)

`tools/e2e/device-settings-smoke-pass.sh` against the exact CI-signed
`jellyplay-v0.11.6-phone-arm64-v8a.apk` (SKIP_INSTALL=1): RED with
PROCESS DIED / fresh crash-buffer entries on all six declaration-path
screens, PASS on Notifications + Audio Player — matching the audit
prediction line for line. Against the fixed build (local
`assemblePhoneRelease`, R8 on, debug-keystore-signed for the local
install): **verdict PASS** — every walked screen opens, renders, and
returns to the root without a crash.

## Standing gate

Run before publishing a release (manual today — GitHub runners have no
physical device; the repo's other device passes run the same way):

```bash
tools/e2e/device-settings-smoke-pass.sh          # build + install + walk
SKIP_INSTALL=1 tools/e2e/device-settings-smoke-pass.sh   # reuse installed app
```

Preconditions: one adb device (pin with `ANDROID_SERIAL`), swipe-unlocked,
and the release package ALREADY SIGNED IN — the pass does not automate
first-run login, and a signature-mismatch reinstall wipes the login (the
script says so and exits; sign in once, re-run with SKIP_INSTALL=1).
Evidence (dumps + screencaps per step) lands in
`tools/e2e/.results/device-settings-smoke/` (gitignored).

## Harness lessons recorded on the way (do not re-learn them)

- `uiautomator dump` intermittently fails right after a transition and
  LEAVES THE PREVIOUS DUMP FILE in place — a stale non-empty read is worse
  than an empty one. The pass removes the file before every dump, retries,
  and accepts only a real `<hierarchy>`.
- The crash buffer (`logcat -b crash`) is a RING buffer — on a crash-heavy
  walk, line-count deltas blame the wrong step. The pass clears the buffer
  per step instead (`logcat -c -b crash`).
- UI text carries a unicode ellipsis (`Search settings…`) — exact-quote
  matching misses it; pick markers without ellipses (the pass pins on
  "Power User Mode").
- Compose screens here expose their text to uiautomator fine once settled —
  dumps taken mid-transition are the unreliable ones, not the mechanism.
