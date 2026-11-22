# 0018 — Launcher root is singleTop: the player survives a home-screen relaunch

- **Status:** accepted and implemented (2026-11-22)
- **Scope:** `app/src/main/AndroidManifest.xml` (MainActivity task contract),
  PlayerActivity's lock-gate shell handoff, task-contract pin tests
- **Vocabulary:** ADR 0005 records the old `singleTask` premise as desktop
  context; this ADR owns the client task contract now

## Context

`PlayerActivity` is the dedicated playback host and deliberately shares the
launcher task with `MainActivity` (that is what makes PiP reveal the browse UI
behind the floating window). `MainActivity` was declared `singleTask`, and
Android's documented contract for that mode is: an existing instance is
located in its task and **"meanwhile all of the other activities on top of it
are destroyed"** (tasks-and-back-stack guide). The launcher icon re-delivers
the MAIN/LAUNCHER intent on every tap, so tapping the app icon from the home
screen with the player on top — fullscreen backgrounded or PiP — destroyed
`PlayerActivity` and reused the shell. The shell's Compose back stack still
held the screen playback was started from, so the observable bug was exactly
"the video player is closed and the screen it was started from is shown".
The recents path was unaffected (it moves the task, no launcher intent).

## Decision

1. **MainActivity is `singleTop`, not `singleTask`.** For launch modes other
   than the task-initiating pair, the documented launcher behavior is: "the
   task comes to the foreground and resumes the activity at the top of the
   stack". When MainActivity is itself the top (the usual backgrounded-shell
   case) the launcher intent arrives via `onNewIntent` and classifies as
   `None` — no-op. `android:alwaysRetainTaskState="true"` is added because the
   system's timed (~30 min) task reset would otherwise clear a paused player
   above the root — the same user-visible loss on a longer time scale.
2. **Every in-app shell handoff carries its reuse flags explicitly.** The
   existing senders already did (`NEW_TASK|CLEAR_TOP` or
   `SINGLE_TOP|CLEAR_TOP`: tile, widgets, media/session notifications,
   deeplink dispatcher, shortcut table, the floating-player foreground
   notification); the one path that relied on
   `singleTask` routing — `PlayerActivity.redirectToLockGateIfNeeded` — now
   adds `CLEAR_TOP|SINGLE_TOP`, preserving "reuse the shell underneath and
   drop this finishing player".
3. **PlayerActivity stays `singleTask`.** The media-notification
   PendingIntent must reach a live backgrounded/PiP instance through
   `onNewIntent` instead of stacking a second player; that contract is
   independent of the launcher root and is exercised by
   `PlayerActivityLockRedirectTest`. A delivery to a not-RESUMED instance
   parks its payload (`pendingLaunchArgs`) and publishes only after the
   returning resume passes the gate — the swap must not initialize/retune
   ahead of the lock challenge; an already-RESUMED delivery publishes
   immediately (the fullscreen re-selection path).
4. **The player host folds the shared auto-lock policy too.** Because the
   launcher icon now resumes the player directly, `MainActivity.onResume` no
   longer runs after a player-only background — so `PlayerActivity.onPause`
   feeds the same `AppLockState` background stamp the shell feeds, and the
   return gate (`redirectToLockGateIfNeeded`) runs the same shared fold
   (`AppLockReturn.foldReturnPolicy`, which delegates to
   `AppLockState.onResumed`) before consulting the unlocked flag. A
   return after the configured timer relocks and hands off to the lock gate;
   a return inside the window consumes the stamp and restores playback
   directly (consume-always preserves the no-stale-stamp rule).
5. **The fold is anchored to the actual foreground return.** Intent delivery
   (`onCreate`/`onNewIntent`) only re-checks the current lock state; the
   policy is evaluated at `onResume` — or at a PiP exit that lands already
   RESUMED, the OEM ordering where `onResume` never re-fires — so a
   foregrounding that lands after the timer is still measured at the resume,
   not at the earlier intent. A resume that lands while the window is still
   DOCKED in PiP is not a return either: the PiP-entry stamp stays pending so
   the exit/expand fold still measures the absence (consuming it there would
   let a later expand skip the challenge). Both hosts read the persisted
   policy through one seam (`AppLockReturn.persistedSecurity`, a bounded
   read): when that read times out on a return, the gate fails CLOSED — an
   unlocked holder with a pending stamp is re-locked (the stamp stays pending
   under the challenge the relock raises; the successful `unlock()` that
   answers it consumes the stamp — the authentication resolves the absence,
   so the next return measures a fresh one) instead of restoring playback on
   the stale unlocked flag. The shell's composition applies the same
   persisted-slice rule (`AppLockRedirect.shellGate`): locked + unread
   renders a neutral blocked state, never browse — the preferences seed says
   "no gate configured" until the first emission.
6. **PiP entry records the stamp when the transition never paused.** The
   OEMs that keep the activity RESUMED through the whole PiP session deliver
   no entry `onPause`; `onPipModeChanged(true)` records the background stamp
   itself when none is pending (on the standard pause-first ordering the
   pending stamp IS the same transition and is not duplicated), so an expand
   after the timer elapsed still lands on the lock gate. The pause path
   records with the same none-pending guard
   (`AppLockState.onBackgroundedIfNonePending`): a later transition inside
   one absence — e.g. the dismissal-time pause of such a session — cannot
   reset the absence clock, so the time spent watching the PiP window still
   counts and the stamp survives for the next genuine return.

### Rejected alternatives

- **Launcher trampoline activity** that finishes over the existing task:
  a second activity plus task-identity/recents risk, and it must special-case
  the cold-start root. The launchMode attribute is the documented fix.
- **Distinct `taskAffinity` for PlayerActivity**: breaks PiP-over-browse
  (the shell is revealed only because it is the same task) and adds a second
  recents card.
- **Replaying the player from MainActivity.onNewIntent**: the engine and all
  in-memory state are already gone by then, and the restart flashes.

## Consequences

- Tapping the app icon with the player up returns to the player (fullscreen
  or PiP), with the shell state underneath untouched.
- Deep links / ACTION_SEND still arrive in the app's task in practice —
  browsers and the system resolver launch with `NEW_TASK`, and callerless
  starts (PendingIntents, shortcut launches) get `NEW_TASK` forced. The
  residual is a misbehaving third-party sender that omits `NEW_TASK`, which
  would place the shell in the caller's task; accepted.
- A deep link into the shell while the player is alive now stacks the shell
  over it instead of destroying playback; back returns to the player.
- The PIN auto-lock timer now applies to returns that skip the shell: the
  player records the backgrounding and its gate folds the shared policy at
  the returning resume, so launcher relaunches, notification taps, and PiP
  expands after the configured timer land on the lock gate; returns inside
  the window restore playback directly. The immediate gate is unchanged: a
  locked `AppLockState` still redirects out of `PlayerActivity` on
  create/new-intent/resume. A PiP dismissal is not a return and its exit
  callback does not fold — an already-locked holder still hands off on the
  dismissal gate — but its TEARDOWN finish (no keep-alive: the window closes
  and the activity finishes) evaluates a pending absence at the discharge
  (the below-STARTED exit callback, or the armed onStop finish): the shell
  beneath a docked window has been RESUMED since entry, and finishing the
  player delivers no new shell `onResume`, so without this the expired stamp
  would never be evaluated and browse would sit interactive past the timer
  with no challenge. A keep-alive dismissal (the session continues under the
  shell) keeps the stamp pending for the next genuine return, including a
  later expand.
- A recreation of `PlayerActivity` re-delivers the LAST intent — for a
  parked `pendingLaunchArgs` that IS the parked payload (`setIntent` ran at
  delivery) — so both launch payloads ride `onSaveInstanceState` and the
  recreated `onCreate` restores active-over-intent and re-parks the parked
  payload; publication stays owned by the qualifying-return folds.
- The shell gate decides from the PERSISTED security slice, so a cold
  start's first composition is the fail-closed blocked surface until that
  read answers (the startup prewarm usually makes it an in-memory replay).
  For a user with no lock configured this is first-frame latency with no
  security benefit, accepted because the gate cannot know "no lock" before
  the persisted read answers and deciding from the preferences seed is the
  flash-of-browse bug this decision closes. The splash holds until the gate
  renders a usable surface (the same pair the fully-drawn report waits on),
  so the neutral surface is not user-visible in the common case; a stalled
  read retries under `AppLockReturn.READ_TIMEOUT_MS`/`READ_RETRY_MS` rather
  than wedging the shell.
- `MainActivityTaskContractTest` pins MainActivity's launch mode +
  `alwaysRetainTaskState` and PlayerActivity's `singleTask`;
  `PlayerActivityLockRedirectTest.assertShellHandoff` pins the handoff flags.
