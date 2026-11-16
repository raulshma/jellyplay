package com.raulshma.jellyplay.feature.player.video.engine.mpv

/**
 * One surface-created event's verdict.
 */
sealed interface MpvSurfaceReattachDecision {
    /**
     * Hand the event to `BaseMPVView.surfaceCreated` untouched: the library
     * attaches the surface and, iff it holds a recorded path, issues its
     * one-shot initial loadfile. Both the pre-`playFile` attach (path null →
     * attach + the lib's own vo restore) and the armed initial load ride
     * this arm.
     */
    data object LibraryInitialLoad : MpvSurfaceReattachDecision

    /**
     * Mid-session re-attach: attach the new surface DIRECTLY — a loadfile
     * here is the issue-#220 bug (session restart: position loss + the
     * fresh-decode wedge on the re-attached surface). Applying this decision
     * also restores the video output the lib's destroy half tore down (the
     * runtime `vo` write — see the engine's reattach arm).
     *
     * @property rePresentFrame the session is paused — the torn-down video
     *   output will present nothing until a frame is pushed, so the host must
     *   fire the frame-exact re-present seek to the cached position. False
     *   while playing: mpv re-presents on its own.
     */
    data class Reattach(val rePresentFrame: Boolean) : MpvSurfaceReattachDecision
}

/**
 * Decides what the Android MPV view must do when the Compose surface
 * (re)creates.
 *
 * The defects this owns (issue #220: pause → lock → unlock → black video;
 * plus its restart twin) live in the `is.xyz.mpv.BaseMPVView` binding
 * (mpv-android-lib), whose `surfaceCreated` has two arms (bytecode-verified):
 * an ARMED ONE-SHOT `loadfile` for its recorded path — the sole initial-load
 * issuer (`playFile` only records the path; the v0.11.8 attach-race fix
 * depends on this) — and a no-reload arm that restores the video output. Two
 * failure shapes hang off it:
 *
 * - RESTART: `playFile` records the path, but no surface event may ever
 *   consume the one-shot — the surface usually ALREADY exists when `load()`
 *   arms it (and the load-verification bridge issues its loadfile directly),
 *   so the FIRST later surface re-create (screen lock/unlock, app switch)
 *   hits the loadfile arm and RESTARTS the file: the paused position is lost
 *   (playback resumes at the item's original `start` position) and the fresh
 *   decode pipeline on the re-attached surface is the known AImageReader
 *   wedge class (`acquireLatestImage failed`, the v0.11.8 signature).
 *
 * - BLACK: `surfaceDestroyed` writes the runtime property `vo=null`, and a
 *   re-attach that restores no `vo` leaves the video output DOWN forever —
 *   `VO: [null]`, `h264_mediacodec: Both surface and native_window are NULL`
 *   — the playhead advances and audio plays over a black screen
 *   (device-proven on the POCO F1). And even with the VO back, a PAUSED
 *   session pushes no frame on its own after the teardown, so the new
 *   surface stays black until something forces a frame.
 *
 * The policy's rule is therefore split by session epoch:
 *
 * - INITIAL-LOAD WINDOW (no file has ever started on this handle, or a
 *   `playFile` is armed and its start-file has not arrived yet): delegate to
 *   the library's `surfaceCreated`. Its one-shot attach + loadfile is the
 *   correct — and only — initial-load issuer, and the pre-`playFile` attach
 *   (path still null → attach + its vo restore) preserves the v0.11.8 race
 *   semantics the load-verification bridge repairs.
 *
 * - MID-SESSION (a file has started and no new `playFile` is pending): NEVER
 *   reload. Attach the new surface directly and restore the video output the
 *   lib's destroy half tore down (the host applies this — the runtime `vo`
 *   write with the ACTIVE driver stashed at destroy); when the session is
 *   paused, force a frame-exact re-present (an absolute seek to the cached
 *   position — the paused frame re-decodes and the re-created video output
 *   presents it; inaudible, since audio is paused). While playing, mpv's
 *   play loop re-presents on its own once the VO is back.
 *
 * This class owns only WHEN and WHICH; the engine applies the decision (the
 * JNI attaches, the seek kick, the debug logs). Pure and dependency-free so
 * the jvmTest lane pins the rule — the reload-on-reattach shape must stay
 * unwritable ([MpvSurfaceReattachDecision] has no reload arm by construction).
 */
class MpvSurfaceReattachPolicy {

    /**
     * Decides one surface-created event.
     *
     * @param awaitingInitialStartFile a `playFile` is armed on this handle
     *   and its `MPV_EVENT_START_FILE` has not arrived yet — the library's
     *   one-shot loadfile is still pending and must not be bypassed.
     * @param hasEverStartedFile at least one file has fully started on this
     *   mpv handle since it was created (any `MPV_EVENT_START_FILE` ever).
     * @param paused the session holds a presentable PAUSED frame at the
     *   moment of re-attach (mid-file `READY` pause — the host gates out
     *   ENDED/BUFFERING/IDLE/ERROR before calling; see the engine's kick
     *   gate for the per-state reasoning) — such a re-attach needs the
     *   explicit re-present kick, because a paused mpv pushes no frame on
     *   its own after the lib's `vo=null` teardown.
     */
    fun onSurfaceCreated(
        awaitingInitialStartFile: Boolean,
        hasEverStartedFile: Boolean,
        paused: Boolean,
    ): MpvSurfaceReattachDecision =
        if (awaitingInitialStartFile || !hasEverStartedFile) {
            MpvSurfaceReattachDecision.LibraryInitialLoad
        } else {
            MpvSurfaceReattachDecision.Reattach(rePresentFrame = paused)
        }
}
