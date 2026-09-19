# 0004 — PlaybackFocus owns cross-player exclusivity

- **Status:** implemented (2026-09-15 slice 1; 2026-09-16 slice-2
  prerequisites + desktop twin: `FocusArbiter.request` takes per-claimant
  `FocusAudioAttributes` — matrix rows via `PlaybackFocusMatrix.attributesOf`;
  `DesktopFocusArbiter` + `DesktopAudioQueueManagerSurface` give both ports
  their second production adapters; desktop binds `DefaultPlaybackFocus`.
  2026-09-17 music OS-leg migration: `handleAudioFocus` off on BOTH Android
  music players, MUSIC in `osLegClaimants` with the MUSIC attributes row,
  and the enforcement leg — an OS loss on the holder commands its surface
  pause (displaced claimants still pause by observation). Music has no open
  slice; video remains outside the closed world)
- **Scope:** `shared/core/data`, `shared/feature/player-book`, `shared/core/data/androidMain`
- **Supersedes:** nothing (the policy previously did not exist as code)

## Context

JellyPlay has three in-process playback surfaces — music
(`AudioPlaybackManager`), video (the `MediaEngine` stack), and book
read-aloud TTS (`AndroidBookSpeechEngine`). "Who is playing, and who must
pause whom" had NO owner:

- Android music relied on ExoPlayer's built-in audio focus
  (`handleAudioFocus = true`); Android video used a manual focus request via
  `PlayerAudioLifecycle`, gated on two user prefs. Exclusivity was an
  accident of the OS, reached through two mechanisms, and pref-dependent.
- Book read-aloud requested no focus at all — TTS talked over background
  music whenever the OS's arbitration happened not to intervene.
- Desktop ran music and video on separate mpv engines that mixed freely.

## Decision

1. **One module owns the policy.** `PlaybackFocus`
   (`shared/core/data` commonMain, `playback/focus/`) holds the exclusivity
   matrix — a pure, total, pinnable decision table (`PlaybackFocusMatrix`)
   — behind a three-member interface: `claimState`, `acquire`, `release`.
   Slice-1 rulings are **newest-wins** (the newest explicit user action
   takes the floor; a displaced READ_ALOUD claimant pauses via observation
   of `claimState`, never by being commanded), **pause-not-duck** (no duck
   vocabulary exists at slice 1), and **manual-resume** (a claim's release
   never auto-resumes anything it displaced).
2. **Single slot, phase machine.** `FocusClaimState` is
   `Idle / Held(holder) / Suspended(holder, reason)`. OS losses suspend the
   holder (transient and permanent both land in `Suspended`; `Regained` is
   deliberately ignored — a regain never restarts audio). A suspended
   claimant re-acquires on the user's resume. `acquire` dispatches every
   pause directive synchronously before returning `Granted`.
3. **Platform differences live behind two ports.** `FocusArbiter` (the OS
   focus seat: `AndroidFocusArbiter` on Android — one fresh
   `AudioFocusRequest`, AUDIOFOCUS_GAIN, no delayed gain; a scripted fake in
   tests; an in-process twin at desktop slice 2) and `PlaybackSurface`
   (commandable surfaces: `AudioPlaybackManagerSurface` on Android, the
   desktop queue manager at slice 2 — two production adapters make the seam
   real). The reader's speech is NOT a commandable surface: its claimant
   side claims/releases; its victim side is a `claimState` observation.
4. **The playWhenReady guard is load-bearing.** Music's victim pause rides
   `ExoPlayer.pause()`, which clears `playWhenReady` — so when read-aloud
   releases its OS focus and the media3 focus stack re-grants music, built-in
   focus handling sees `playWhenReady == false` and cannot auto-resume.
   Manual resume is therefore enforced at the OS level, not just in-process.
5. **Slice 1 is TTS-over-music.** READ_ALOUD claims take the OS seat
   (`osLegClaimants`); MUSIC claims publish state only (ExoPlayer's built-in
   focus stays music's OS leg until the migration slice, which flips
   `handleAudioFocus` off and adds MUSIC to `osLegClaimants` — a one-line
   change). VIDEO claims are denied (fail-closed closed world): adding video
   is a compiler-forced matrix row plus a surface adapter, not a silent
   behavior change.
6. **Platforms without a binding degrade to vacuous arbitration.**
   `NoopPlaybackFocus` grants everything: where only one sound-maker can
   exist (web; desktop until slice 2; test harnesses), exclusivity is
   trivially true, and denying would break single-player sessions for no
   protective gain.

## Consequences

- `PlayerAudioLifecycle` (video/live) and `ActivePlayerController` (remote
  dispatch) are deliberately untouched — disjoint payloads and consumers.
- The video migration (slice 2) must move video's OS leg into the module OR
  accept dual-mechanism arbitration for video-vs-speech; until then video
  vs. read-aloud overlaps only as much as the OS focus accident allows.
- Desktop slice 2 binds `DefaultPlaybackFocus` with the local arbiter twin
  and a `DesktopAudioQueueManagerSurface`; no caller-side changes.
- User prefs (`pauseOnAudioFocusLoss`, `duckOnTransientFocusLoss`) remain
  video-side inputs. Feeding them into the matrix (duck vocabulary, per-
  surface policies) is additive: the decision vocabulary grows, the
  interface does not.
- Slice-2 checklist addition (2026-09-15 review): `AndroidFocusArbiter`
  hardcodes `USAGE_MEDIA + CONTENT_TYPE_SPEECH` attributes — correct for
  read-aloud, wrong for music/video (OS routing and ducking policy read the
  content type). `FocusArbiter.request(listener)` takes no attributes
  input, so the migration slice must add a per-claimant attributes
  parameter to the port FIRST — otherwise music is forced onto speech
  attributes or onto a second `AudioFocusRequest`, breaking the
  "one AudioFocusRequest owned here" invariant. The "one-line change"
  claim in decision 5 understates this prerequisite.
- Landed-migration addendum (2026-09-17): the migration needed more than
  the one line even past the attributes prerequisite — `handleAudioFocus`
  had to go off on BOTH Android music players (the crossfade secondary's
  built-in GAIN request would focus-loss-pause the primary mid-fade), and
  the holder side needed an enforcement leg (an OS loss now commands the
  suspended holder's surface pause, because only displaced claimants have
  a `claimState` observer).
