# PiP entry/exit on a physical phone — device pass result (wave 21, PARTIAL)

**Verdict: PARTIAL — PiP ENTRY verified; EXPAND and DISMISS not completed.**
On the Nokia 6.1 Plus (Android 14 / API 34), during video playback in
`PlayerActivity`, pressing HOME entered system picture-in-picture reliably:
the task report flipped to `mode=pinned`, the pinned window rendered at
floating bounds, and the media session kept reporting `state=PLAYING(3)`
throughout — playback continued in PiP. The follow-on step (PiP menu →
fullscreen button → EXPAND, then menu → X → DISMISS) never landed on
device: the wave-21 round was cut short there by directive, and those steps
of the script remain unexercised. `tools/e2e/device-pip-pass.sh` is
committed in full for the next run; it encodes the measured menu geometry
and the retry logic around the two-sided expand-timing race, plus one bug
fix from this round's review (a duplicated unconditional `tap_node "Play"`
after the details/quick-start branch that would have fail-exited the
quick-started-playback path).

The wave-19C residual this lane was meant to close ("PiP entry/exit
needs-device-pass") therefore remains OPEN for the exit half; entry is no
longer in question.

## Headline context: the two launch crashes found on the way

This pass could not even reach playback at first — the same device round
surfaced two P0 crashes, both now fixed and committed (full accounts in
`docs/e2e/device-locale-pass.md`):

1. **nav3 `SavedStateConfiguration` launch crash** (commit `fa4ca3efa`) —
   FATAL `IllegalArgumentException` at first composition
   (`rememberNavBackStack` via `rememberNavigationState`, navigation3
   1.1.5): the common configuration-taking overload requires a
   `SerializersModule` registering every `NavKey` subtype, Android passed
   nothing and fell through to `SavedStateConfiguration.DEFAULT`. Desktop
   was immune because only the desktop shell passes
   `desktopNavSavedStateConfiguration()`. Fix: expect/actual
   `rememberNavBackStackSaveable` — Android uses the library's
   reflection-based overload, JVM/wasm keep the explicit configuration.
2. **compose-resources `.cvr` assets missing from the APK** (commit
   `c6da8ff8a`) — AGP-9 KMP library plugins leave android resources OFF, so
   the APK shipped `Res` accessors with no backing assets;
   `MissingResourceException` on the first string read. Fix:
   `androidResources { enable = true }` across core:ui + the 22 shared
   feature modules (APK now ships 24 modules × 9-locale `.cvr` sets).

## Verified evidence (entry)

From the interrupted wave-21 run (dead agent's partial evidence, files
preserved under `tools/e2e/.results/`):

- Playback established: `pip-01-playing.png` / `pip-02-playing.png`;
  `dumpsys media_session` showed `state=PLAYING(3)` and
  `dumpsys activity activities` showed `PlayerActivity` as the playback
  host.
- HOME keyevent → PiP: `pip-03-pipmode.png` (task `mode=pinned`, floating
  bounds); playback continued while pinned (media session still PLAYING).
- PiP menu interactions probed: `pip-04-aftertap.png`, `pip-05-before.png`,
  `pip-06-menu.png` — the menu's button geometry inside the pinned frame
  was measured here (left+~60/top+~63 fullscreen, right-~60/top+~63 close
  X, ~0.5 s between taps to avoid the double-tap pause toggle and the
  ~2 s menu auto-dismiss); these constants are baked into the script's
  steps 8-9.
- The sign-in → Library → clip → playback chain that precedes PiP in the
  script is itself evidence-logged (`dump-01` … `dump-28`).

What was NOT verified on device: step 8 (menu → fullscreen EXPAND:
pinned task cleared, `PlayerActivity` resumed, playback continued through
the transition) and step 9 (HOME re-entry + menu → X DISMISS:
`PlayerActivity` finished, media session released). No evidence for either
exists in this round; the `pip-07-expanded.png` / `pip-08-pip2.png`
artifacts from the dead agent's manual probing predate the scripted steps
and are not counted as verification.

## The script (kept for the next run)

`tools/e2e/device-pip-pass.sh` drives the whole lane end-to-end: cold
start → connect to the Docker Jellyfin fixture over Wi-Fi LAN → sign in as
the bootstrap `harness` user → play the generated 5-minute
"Harness Pip Clip (2026)" testsrc clip (12 s clips auto-exit PiP on END)
→ HOME enters PiP → menu/fullscreen EXPAND → HOME re-PiP → menu/X DISMISS,
asserting `dumpsys` state at every step and dropping evidence into
`tools/e2e/.results/device-pip/`. Preconditions: unlocked debuggable phone,
healthy `bootstrap-jellyfin.sh` fixture, ffmpeg. Two edits made while
committing it this round: the header no longer claims a verified full-lane
verdict (this doc is the record), and the duplicated `tap_node "Play"`
line after the details/quick-start `fi` was removed — in the quick-start
branch (card tap starts playback directly) there is no details-screen Play
button, so the stray tap would have failed a run that was already playing.

## Residual follow-ups

1. Run the script end-to-end on a device to close the exit half of the
   wave-19C residual (expand + dismiss + session release).
2. The Settings-screen ANR recorded in `docs/e2e/device-locale-pass.md`
   (any locale) — same device round, orthogonal to PiP.
