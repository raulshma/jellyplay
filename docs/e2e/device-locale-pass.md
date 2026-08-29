# Compose-resources locale switching on Android — device pass result (wave 21)

**Verdict: PASS.** With per-app locales set through the system
(`cmd locale set-app-locales`), the app's compose-resources strings follow
the locale on a physical device: German and Japanese first-run screens
render fully translated, and English is restored cleanly. Ten of ten script
steps green (`tools/e2e/device-locale-pass.sh`, 2026-08-29, Nokia 6.1 Plus,
Android 14 / API 34). This closes the migration plan's V1a risk
("compose-resources locale switching on Android under the app's
LocaleApplier/per-app-locale flows — needs a device pass") — and it is the
on-device proof of the packaging fix that made it possible: the same strings
crashed with `MissingResourceException` before
`androidResources { enable = true }` shipped the `.cvr` assets (see below).

One honest negative, unrelated to locale correctness: **the Settings screen
ANRs on device in ANY locale** — recorded here because it forced the pass's
assertion surface to move from Settings (the plan's intended surface) to the
first-run auth screen.

## Headline context: the two launch crashes this round fixed

The wave-21 device round set out to verify locale + PiP and found the phone
app could not even start; both P0s are fixed and committed:

1. **nav3 `SavedStateConfiguration` launch crash** (commit `fa4ca3efa`).
   FATAL `IllegalArgumentException` ("You must pass a
   `SavedStateConfiguration.serializersModule` configured to handle NavKey
   open polymorphism", navigation3 1.1.5) on first composition — the common
   `rememberNavBackStack(configuration, ...)` call fell through to
   `SavedStateConfiguration.DEFAULT` because only the desktop shell passes a
   real module (`desktopNavSavedStateConfiguration()`), which is exactly why
   desktop was immune. Fix: the expect/actual `rememberNavBackStackSaveable`
   seam — Android takes the library's reflection overload, JVM/wasm keep the
   explicit-configuration path.
2. **compose-resources assets missing from the APK** (commit `c6da8ff8a`).
   AGP-9 KMP library plugins leave android resources OFF, so no `.cvr` assets
   were packaged and the first `Res` string read threw
   `MissingResourceException`. Fix: `androidResources { enable = true }` in
   core:ui + all 22 shared feature modules. Fresh APK verification this
   round: 216 `.cvr` entries = 24 modules × 9 locales (values, de, es, fr,
   it, ja, ko, pt, zh) under `assets/composeResources/`.

## Method

`tools/e2e/device-locale-pass.sh` drives the real app over adb:

1. install the phone-debug arm64 APK built from the fixed tree, `pm clear`
   (first-run auth screen is the assertion surface — deterministic, no
   server needed, works signed-out);
2. `cmd locale set-app-locales com.raulshma.jellyplay.dev --locales <tag>`
   + verify via `get-app-locales`, `am force-stop` + cold relaunch;
3. `uiautomator dump` and assert known translated strings
   (XML-entity-unescaped, text= or content-desc=);
4. repeat for `de`, `ja`, restore `en`.

Assertions (shared/feature/auth compose-resources):

| locale | string resource | expected |
|--------|-----------------|----------|
| de | `auth_add_server_title` | `Server hinzufügen` |
| de | `auth_no_servers_added_title` | `Keine Server hinzugefügt` |
| ja | `auth_add_server_title` | `サーバーを追加` |
| ja | `auth_no_servers_added_title` | `追加されたサーバーはありません` |
| en (restored) | `auth_add_server_title` | `Add Server` |

## Evidence

- Run of record: `PASS (10 steps)` — dumps in
  `tools/e2e/.results/device-locale/locale-{de,ja,en}.xml`:
  - de (14:35): `Server hinzufügen`, `Keine Server hinzugefügt`,
    `Fügen Sie Ihren Jellyfin-Server hinzu, um zu beginnen`
  - ja (14:36): `サーバーを追加`, `追加されたサーバーはありません`,
    `Jellyfin サーバーを追加して開始してください`
  - en (14:36): `Add Server`, `No servers added`, `Add your Jellyfin server
    to get started`
- Device left clean: per-app locale restored to `[en]`.
- Signed-in UI also follows the locale (earlier in-session evidence from the
  same device: `tools/e2e/.results/dump-35-de-settings.xml` shows the
  side-menu in German — `Einstellungen`, `Offline gehen`,
  `Auf Gerät abspielen`, `Verknüpfungen`).

## Finding: Settings screen ANRs on device (any locale)

The originally intended assertion surface — Settings
(shared/feature/settings, a 1,583-string catalog) — never composes on the
device: opening it from the side-menu leaves the main thread blocked long
enough for the system ANR dialog ("JellyPlay Dev isn't responding", Wait /
Close app). Captured twice, 7 minutes apart, in
`tools/e2e/.results/dump-37-de-settings.xml` and `dump-44-fresh-de.xml`,
in German and English UI states alike — i.e. NOT a translation-size
artifact. Working attribution (from the interrupted first run, stack not
retained): compose-resources' `runBlocking`-backed `stringResource`
resolving the huge string table on the main thread during the settings
screen's first composition. Follow-up worth filing: capture a proper ANR
trace (`adb bugreport`) and either split the settings string reads or move
first resolution off the critical path. The locale verdict above is
unaffected — it is asserted on screens that do compose.

## Script fix made during this run

The dead agent's first scripted run FAILed step 3/11 (`Server hinzufügen`
de) while every other step passed — a script bug, not an app bug:
`dump_text` read a fixed `dump.xml` that `dismiss_overlays` had last
written during the notification-prompt dance, while the assertion's fresh
dump went to `locale-de.xml` (which contained the German strings; the
fail-branch screenshot `locale-de.png` shows the fully rendered German
first-run). Fixed: `dump_text` takes the file to read, the cold-start poll
dumps to the file it polls, and the assertion reads its own per-tag
evidence dump. Rerun after the fix: 10/10 PASS as recorded above.
