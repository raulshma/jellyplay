package com.raulshma.jellyplay

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.ui.components.JellyPlayPreferenceTheme
import com.raulshma.jellyplay.core.ui.components.rememberPreferenceDarkTheme
import com.raulshma.jellyplay.feature.player.live.LivePlayerScreen
import com.raulshma.jellyplay.feature.player.video.VideoPlayerScreen
import com.raulshma.jellyplay.navigation.playbackhost.PlayerActivityArgs
import com.raulshma.jellyplay.shell.AppLockRedirect
import com.raulshma.jellyplay.shell.AppLockState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.mp.KoinPlatform

/**
 * Dedicated host Activity for fullscreen playback — VOD ([VideoPlayerScreen])
 * and, since wave 19C, Live TV ([LivePlayerScreen]; the host table moved live
 * out of the nav shell so system PiP serves it — see [PlaybackHostRouter]).
 *
 * Introduced so that system Picture-in-Picture floats over the browse UI
 * (architecture): this Activity shares the default `taskAffinity` with
 * [MainActivity], so when it enters PiP the previous task entry — the Compose
 * browse UI in [MainActivity] — is revealed behind the floating window. A
 * single-Activity app cannot reproduce this (Android renders PiP as that
 * Activity's own window content; there is no sibling to show through).
 *
 * The PiP apparatus (param builder, remote actions, lifecycle coordination) is
 * ported from the former single-Activity implementation so the feature set is
 * preserved: RemoteActions (play/pause/skip/next), auto-enter on home, auto-exit
 * on END/ERROR, source-rect hint, aspect-ratio clamp. The pure halves beside
 * this Activity: [PipLifecyclePolicy] owns the ordering machine and param
 * folds, [PipActionSet] owns the remote-action decision tables (the action-set
 * fold and the broadcast id codec); this class keeps only Android wiring.
 * Both hosts feed it
 * through the same legacy `core:data` PipController singleton — VOD's VM via
 * the wave-8C player-video seam, live's VM via the wave-19C player-live seam
 * (SKIP remote actions map to channel zap for live) — so every collector below
 * serves both variants unchanged.
 *
 * Each screen creates its engine fresh via its ViewModel (scoped to this
 * Activity) — there is no cross-Activity engine handoff in the normal
 * open-play-PiP flow.
 *
 * Because the media notification opens this host by class name (bypassing
 * MainActivity), it enforces the app PIN/biometric gate itself (wave 20E):
 * `onCreate`/`onNewIntent` redirect to MainActivity while a lock is
 * configured and the app-scoped AppLockState says locked — see
 * [redirectToLockGateIfNeeded].
 */
class PlayerActivity : FragmentActivity() {

    // PlayerActivity is the SOLE driver of the shared PlayerLifecycleManager
    // single (onPause/onResume/onTopResumed/onStop below). MainActivity
    // deliberately stopped calling it: the singleton has one @Volatile
    // activeCallbacks slot with no owner identity, so a second host pausing
    // here would reach across Activities into this activity's engine. Keep it
    // that way until per-host engine scoping lands (see Plan 01/02).
    // Resolved from the Koin container (wave 8B — Hilt removal).
    private val playerLifecycleManager: PlayerLifecycleManager by lazy { KoinPlatform.getKoin()!!.get() }

    private val pipController: PipController by lazy { KoinPlatform.getKoin()!!.get() }

    private val preferenceProjections: PreferenceProjections by lazy { KoinPlatform.getKoin()!!.get() }

    /**
     * Hoisted launch arguments read from the start/new Intent via
     * [PlayerActivityArgs.fromIntent]. [onNewIntent] (re-selection while this
     * `singleTask` activity is already alive — e.g. picking another item or
     * channel from the browse UI while in PiP) updates this so the Compose
     * tree recomposes with the new args — video re-fires the screen's
     * `LaunchedEffect(itemId)` → `initialize()`; live stops-and-retunes the
     * activity-scoped VM on the channel change (the screen's own
     * changed-channelId branch) — instead of the new extras being silently
     * dropped.
     */
    private val launchArgs = mutableStateOf<PlayerActivityArgs?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // PIN/biometric gate (wave 20E) — FIRST, before any window styling,
        // playback setup or setContent: while a lock is configured and the
        // app is locked, no player UI may compose. The media notification's
        // content intent opens this activity by class name, so this host must
        // enforce the gate itself; redirecting to MainActivity routes the
        // user into the lock screen its own compose gate renders.
        if (redirectToLockGateIfNeeded()) return

        // Edge-to-edge so the player draws under the system bars; VideoPlayerScreen
        // owns immersive show/hide and inset handling itself (it was designed for
        // MainActivity's edge-to-edge window). Without this the surface + controls
        // are inset by the status/nav bars in fullscreen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        launchArgs.value = PlayerActivityArgs.fromIntent(intent) ?: run {
            Log.w(TAG, "No itemId extra; finishing.")
            finish()
            return
        }

        setContent {
            // Theme the player with the same preference-driven stack as
            // MainActivity (accent/OLED/contrast/performance-locals/hand-mode/
            // blue-light/colorblind) via the shared wrapper. PreferenceProjections
            // is the same @Singleton MainViewModel reads, so the player tracks the
            // user's appearance settings identically.
            val preferences by preferenceProjections.mainPreferences
                .collectAsStateWithLifecycle()
            val darkTheme = rememberPreferenceDarkTheme(preferences)
            // Subtitle tester overlays the player (keeps the video engine alive
            // underneath) rather than navigating away, mirroring the old nav-push
            // behaviour. The tester builds its own preview engine, so it is
            // self-contained. Video-only — live has no subtitle side-loading.
            var showSubtitleTester by remember { mutableStateOf(false) }
            // Hoisted launch args so onNewIntent (re-selection while this
            // singleTask activity is alive, e.g. picking another item while in
            // PiP) can swap the payload without recreating the Activity. The
            // variant branch recomposes the matching screen with the new args.
            val args = launchArgs.value ?: return@setContent
            JellyPlayPreferenceTheme(
                preferences = preferences,
                darkTheme = darkTheme,
            ) {
                when (args) {
                    is PlayerActivityArgs.Video -> Box(Modifier.fillMaxSize()) {
                        VideoPlayerScreen(
                            itemId = args.itemId,
                            mediaSourceId = args.mediaSourceId,
                            startPositionTicks = args.startPositionTicks,
                            subtitleStreamIndex = args.subtitleStreamIndex,
                            audioStreamIndex = args.audioStreamIndex,
                            onBack = { finish() },
                            onEnterPip = { enterPipMode() },
                            onOpenSubtitleTester = { showSubtitleTester = true },
                        )
                        if (showSubtitleTester) {
                            com.raulshma.jellyplay.feature.subtitle.tester.SubtitleTesterScreen(
                                onBack = { showSubtitleTester = false },
                            )
                        }
                    }
                    is PlayerActivityArgs.Live -> LivePlayerScreen(
                        channelId = args.channelId,
                        channelName = args.channelName,
                        audioStreamIndex = args.audioStreamIndex,
                        subtitleStreamIndex = args.subtitleStreamIndex,
                        onBack = { finish() },
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            // One collector drives param application for both the pre-arm path
            // (not yet in PiP: setAutoEnterEnabled + aspect + source rect, no
            // actions) and the in-PiP refresh path (resolution/track swap while
            // already in PiP: actions + aspect).
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    combine(
                        pipController.shouldAutoEnterPip,
                        pipController.pipAspectRatio,
                        pipController.isPlaying,
                    ) { shouldAutoEnter, aspect, isPlaying -> Triple(shouldAutoEnter, aspect, isPlaying) }
                        .distinctUntilChanged()
                        .collect {
                            if (isInPictureInPictureMode) {
                                applyPipParams(includeActions = true)
                            } else {
                                applyPipParams(includeActions = false)
                            }
                        }
                }
            }
            // Keep the PiP play/pause action icon in sync with playback state.
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    pipController.isPlaying.collect { refreshPipActions() }
                }
            }
            // Auto-exit: when the ViewModel signals END/ERROR in PiP, reuse the
            // existing dismiss path so no new exit plumbing is needed.
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    pipController.autoExitPip.collect { exit ->
                        if (exit && isInPictureInPictureMode) {
                            pipController.consumeAutoExitPip()
                            pipController.notifyPipDismissed()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Lock gate on the live-instance path too: this activity is
        // `singleTask`, so a media-notification tap while a PlayerActivity
        // instance is alive (e.g. backgrounded/PiP playback) routes here —
        // onCreate never runs and would otherwise not re-check. Same
        // redirect as onCreate.
        if (redirectToLockGateIfNeeded()) return
        // Swap the payload without recreating this singleTask Activity:
        // updating launchArgs recomposes the matching screen with the new
        // args. This is the path that handles "play another media/channel
        // while in PiP" — video re-fires its LaunchedEffect(itemId) →
        // initialize(); live's screen sees the changed channelId and
        // stop-and-retunes the activity-scoped VM (its `initialized` latch
        // would otherwise no-op and keep the old channel playing). Without
        // any of this the live instance expands out of PiP but the new extras
        // are dropped and the old media keeps playing.
        PlayerActivityArgs.fromIntent(intent)?.let { launchArgs.value = it }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Safety net for the paths that never reach onPipModeChanged(false):
        // a destroy while still in PiP (recreate / OEM callback orderings) or a
        // cancelled entry. Without this the action receiver stays registered on
        // a dead activity and the framework logs IntentReceiverLeaked.
        unregisterPipActionReceiver()
        // Drop any orientation lock the player applied so the browse host
        // (MainActivity, UNSPECIFIED) resumes into a clean, system-controlled
        // orientation. VideoPlayerScreen.onDispose skips its orientation restore
        // when the activity is finishing (its `!isFinishing` guard), so without
        // this the activity exits while still requesting landscape — leaving the
        // device rotated and the browse UI stuck in landscape after playback on
        // devices with system auto-rotate off (no in-app rotate on browse).
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-enter guard lives in PipLifecyclePolicy.userLeaveAutoEnter:
        // `!isControlsLocked` so swiping home while the lock overlay is up
        // doesn't yank the user into PiP, and `!isScreenOffOrLocked()` because
        // several OEMs fire this callback for the power button too (issue
        // #145). Deliberately no isPlaying term — the fallback below adds it.
        if (PipLifecyclePolicy.onUserLeaveHint(
                shouldAutoEnter = pipController.shouldAutoEnterPip.value,
                controlsLocked = pipController.isControlsLocked,
                screenOffOrLocked = isScreenOffOrLocked(),
            ) == PipLifecyclePolicy.Action.EnterPip
        ) {
            enterPipMode()
        }
    }

    // Reliability fallback for PiP auto-entry: onUserLeaveHint is not reliably
    // fired on all OEMs/API levels for gesture "slide up to home". When this
    // activity loses the top-resumed position during active playback, enter PiP
    // — PipLifecyclePolicy.topResumedLossAutoEnter is the same guard predicate
    // PLUS the isPlaying term (the documented divergence between the two
    // hand-copied guards).
    override fun onTopResumedActivityChanged(isTopResumed: Boolean) {
        super.onTopResumedActivityChanged(isTopResumed)
        val decision = PipLifecyclePolicy.onTopResumedChanged(
            isTopResumed = isTopResumed,
            inPip = isInPictureInPictureMode,
            apiSupportsAutoEnter = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            shouldAutoEnter = pipController.shouldAutoEnterPip.value,
            isPlaying = pipController.isPlaying.value,
            controlsLocked = pipController.isControlsLocked,
            // Same lock guard as onUserLeaveHint: the keyguard stealing the
            // top-resumed position is not a "leave" worth auto-PiP for
            // (issue #145).
            screenOffOrLocked = isScreenOffOrLocked(),
            justExitedPip = justExitedPip,
        )
        justExitedPip = decision.justExitedPip
        when (decision.action) {
            PipLifecyclePolicy.Action.Resume -> playerLifecycleManager.onActivityResume()
            PipLifecyclePolicy.Action.EnterPip -> enterPipMode()
            else -> {}
        }
    }

    private var justExitedPip = false

    // Saved window brightness so it can be restored on PiP exit. resets.
    // brightness to the system auto value while in PiP (the in-app brightness
    // gesture is irrelevant in the floating window) and restores it on expand.
    private var savedBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    // ── PIN/biometric lock gate (wave 20E) ─────────────────────────────────
    //
    // Closes the media-notification bypass: the video MediaSession pins its
    // session-activity PendingIntent to this class BY NAME
    // (AndroidMediaSessionController), so a notification tap while the app is
    // locked used to reach full playback (video since the dedicated-host
    // split, live TV too since wave 19C) without ever hitting MainActivity's
    // lock gate. When a gate is configured and the app-scoped AppLockState
    // says locked, this host hands off to MainActivity — whose own gate then
    // renders AuthChallengeScreen — and finishes before composing anything.
    //
    // Note: this is the APP lock, not the in-player "screen lock" overlay
    // (usePinForPlayerLock / SlideToUnlockOverlay inside VideoPlayerScreen) —
    // that feature locks player controls; this one gates entry to the app.
    /**
     * Redirects to MainActivity's lock gate when a PIN/biometric lock is
     * configured and the app is locked. Returns `true` when the redirect
     * fired — callers must return immediately so no player UI, PiP collector
     * or window styling runs (finishing in `onCreate` precedes
     * `onUserLeaveHint`/`onTopResumedActivityChanged`/`STARTED`-gated
     * collectors, so no PiP path can trigger on the redirect).
     */
    private fun redirectToLockGateIfNeeded(): Boolean {
        val koin = KoinPlatform.getKoin() ?: return false
        val lockState = koin.get<AppLockState>()
        val securityStore = koin.get<SecurityStore>()
        // The gate predicate must read the PERSISTED security slice, not the
        // `.security` StateFlow seed: on a cold process (recents-restore of a
        // task topped by this activity) the Eagerly-collected upstream may
        // not have emitted yet and the seed's defaults would read as "no gate
        // configured", re-opening the bypass in exactly the coldest case.
        // firstPersistedSecurity() suspends until DataStore's initial read
        // lands — warm-process callers return from the cached snapshot
        // without disk IO — bounded by a timeout so a pathological read can
        // never wedge cold start (timeout handling: fail CLOSED, see below).
        // STA-8 (2026-09 perf audit): the Application's IO prewarm hydrates
        // this same persisted slice off main at process start, so in the
        // common case the read below is an instant memory replay — the
        // runBlocking + timeout stays exactly as the fail-closed safety net
        // for the cold-start race the prewarm cannot fully close (this
        // activity can beat the prewarm coroutine to the CPU).
        val persisted = runBlocking {
            withTimeoutOrNull(APP_LOCK_GATE_READ_TIMEOUT_MS) {
                securityStore.firstPersistedSecurity()
            }
        }
        // Fail CLOSED on a timed-out read (reviewer D, wave 20 fix round):
        // falling back to the `.security` StateFlow seed would re-open the
        // cold-start race this read exists to close whenever the read is
        // merely slow. Treating "unknown" as gate-configured still lets an
        // UNLOCKED holder through (that user authenticated this process);
        // a locked one lands on the lock screen. Residual (pre-existing and
        // equivalent on both hosts): a CORRUPT prefs read inside
        // SecurityStore degrades to emptyPreferences → no gate — MainActivity's
        // own lock screen reads the same store and shows no gate either.
        val gateConfigured = if (persisted != null) {
            AppLockRedirect.isGateConfigured(
                pinLockEnabled = persisted.pinLockEnabled,
                biometricLockEnabled = persisted.biometricLockEnabled,
            )
        } else {
            true
        }
        if (!AppLockRedirect.shouldRedirect(gateConfigured = gateConfigured, unlocked = lockState.unlocked.value)) {
            return false
        }
        // Plain launch intent (playback args deliberately NOT forwarded —
        // after unlocking, the user lands on the browse UI exactly like a
        // cold app start). MainActivity is singleTask on the same default
        // taskAffinity as this activity, so this routes to the existing
        // instance underneath this finishing one, or cold-creates it; either
        // way its own gate shows the lock screen (and its auto-lock-on-resume
        // check re-locks first if the timer elapsed).
        startActivity(Intent(this, MainActivity::class.java))
        lockGateRedirected = true
        finish()
        return true
    }

    /**
     * Set the first time [redirectToLockGateIfNeeded] fired — the onResume /
     * PiP-expand re-checks (reviewer D, wave 20 fix round) must not launch a
     * SECOND MainActivity handoff for the same redirect: after onCreate or
     * onNewIntent redirects, the activity still walks its lifecycle through
     * onResume while finishing.
     */
    private var lockGateRedirected = false

    // ── PiP remote actions ──
    private var pipActionReceiver: BroadcastReceiver? = null

    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        onPipModeChanged(isInPictureInPictureMode)
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        onPipModeChanged(isInPictureInPictureMode)
    }

    private fun onPipModeChanged(isInPictureInPictureMode: Boolean) {
        if (isInPictureInPictureMode) {
            // Entry touches none of the dismiss machinery (registration, bar
            // show and brightness stash below are execution, not policy) and
            // none of the policy's state — no policy call here.
            registerPipActionReceiver()
            refreshPipActions()
            // Exit immersive mode on PiP entry so the system's gesture-nav
            // handle anchors at its correct (bottom) position. Entering PiP
            // from a fully-immersive window leaves the handle floating
            // mid-screen until the next layout pass (the "minimize + reopen
            // fixes it" symptom): the activity never released the hidden state,
            // so the framework has no stable inset anchor during the transition.
            // Showing the bars here prompts an immediate relayout. Immersive is
            // restored on PiP exit by VideoPlayerScreen's isInPipMode effect,
            // which re-hides system bars once !isInPipMode. Covers both the
            // manual enterPipMode() path and system auto-entry via
            // setAutoEnterEnabled (which bypasses enterPipMode entirely).
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
            // Reset window brightness to the system auto value while in PiP: the
            // in-app brightness gesture has no meaning in the floating window, and
            // a stale override would persist after expand. Restore on exit.
            savedBrightness = window.attributes.screenBrightness
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        } else {
            // PiP-expand gate re-check (reviewer D, wave 20 fix round): some
            // OEMs keep the activity RESUMED through the whole PiP session,
            // so the onResume re-check may not re-fire on expand — consult
            // the gate here too (idempotent via lockGateRedirected; the
            // dismiss paths are already finishing or about to, and a locked
            // holder being dismissed should still not expand to fullscreen
            // playback unchallenged).
            if (!lockGateRedirected && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                redirectToLockGateIfNeeded()
            }
            unregisterPipActionReceiver()
            window.attributes = window.attributes.apply {
                screenBrightness = savedBrightness
            }
            // Leaving PiP fires for BOTH expand-to-fullscreen and dismiss; the
            // two are distinguished by lifecycle state at this callback and
            // the whole dismiss/expand fold (arm justExitedPip vs finish
            // directly vs keep-alive no-op — including the OEM ordering where
            // this callback fires before onStop, and the background-audio /
            // keyguard branches) lives in PipLifecyclePolicy.pipExited — its
            // KDoc carries the full OEM-ordering spec.
            val decision = PipLifecyclePolicy.pipExited(
                phase = when {
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ->
                        PipLifecyclePolicy.Phase.RESUMED
                    lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ->
                        PipLifecyclePolicy.Phase.STARTED_NOT_RESUMED
                    else -> PipLifecyclePolicy.Phase.BELOW_STARTED
                },
                keepPlayerAlive = keepPlayerAliveAfterPipDismiss(),
                isFinishing = isFinishing,
                justExitedPip = justExitedPip,
            )
            justExitedPip = decision.justExitedPip
            if (decision.action == PipLifecyclePolicy.Action.Finish) finish()
        }
        pipController.setPipMode(isInPictureInPictureMode)
    }

    override fun finish() {
        // Release the immersive hidden-bars state before the window tears down.
        // Without this, finishing a fully-immersive player leaves the system
        // gesture-nav handle floating mid-screen on the returning window until
        // the next layout pass (the "minimize + reopen fixes it" symptom) — same
        // class of bug as PiP entry, which releases the hidden state in
        // onPipModeChanged above. VideoPlayerScreen's onDispose also tries to
        // show(), but its restore branches are guarded by !isFinishing, so a real
        // back-close (activity finishing) skips it and tears down still-immersive.
        // Skipped during PiP since that path shows the bars itself and the
        // activity is not dying.
        if (!isInPictureInPictureMode) {
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
        super.finish()
    }

    override fun onPause() {
        super.onPause()
        // PipLifecyclePolicy.onPause: pause unless this is a minimise into PiP
        // with an interactive screen (that minimise keeps playing).
        if (PipLifecyclePolicy.onPause(
                inPip = isInPictureInPictureMode,
                screenOffOrLocked = isScreenOffOrLocked(),
            ) == PipLifecyclePolicy.Action.Pause
        ) {
            playerLifecycleManager.onActivityPause()
        }
    }

    override fun onResume() {
        super.onResume()
        // Gate re-check on every resume (reviewer D, wave 20 fix round): the
        // gate otherwise runs only at intent-delivery moments (onCreate /
        // onNewIntent), but a live PiP window survives MainActivity's
        // auto-lock — expanding it returns here with the holder already
        // locked and no intent delivered. Warm-process reads replay the
        // in-memory snapshot (no disk IO). lockGateRedirected skips the pass
        // where onCreate/onNewIntent already redirected and this activity is
        // merely walking its finishing lifecycle. Early-return on a fresh
        // redirect so the player lifecycle hook (and its DI graph) never
        // runs while locked.
        if (!lockGateRedirected && redirectToLockGateIfNeeded()) return
        // Genuine expand: clear the dismiss arm + resume the player lifecycle
        // (PipLifecyclePolicy.onResume).
        val decision = PipLifecyclePolicy.onResume(justExitedPip)
        justExitedPip = decision.justExitedPip
        if (decision.action == PipLifecyclePolicy.Action.Resume) {
            playerLifecycleManager.onActivityResume()
        }
    }

    override fun onStop() {
        super.onStop()
        // The discharge point of the dismiss protocol (PipLifecyclePolicy —
        // class KDoc has the OEM-ordering spec): an armed justExitedPip
        // finishes (the keep-alive re-check covers a keyguard landing between
        // the callback and this stop); an unarmed stop while in PiP pauses
        // only for screen-lock/keyguard so audio doesn't leak with background
        // audio OFF — by onStop the keyguard / non-interactive flags have
        // settled, and onActivityPause is itself a no-op when background
        // audio is ON. A plain minimise while in PiP intentionally keeps
        // playing.
        val decision = PipLifecyclePolicy.onStop(
            inPip = isInPictureInPictureMode,
            screenOffOrLocked = isScreenOffOrLocked(),
            keepPlayerAlive = keepPlayerAliveAfterPipDismiss(),
            isFinishing = isFinishing,
            justExitedPip = justExitedPip,
        )
        justExitedPip = decision.justExitedPip
        when (decision.action) {
            PipLifecyclePolicy.Action.Finish -> finish()
            PipLifecyclePolicy.Action.Pause -> playerLifecycleManager.onActivityPause()
            else -> {}
        }
    }

    fun enterPipMode(): Boolean {
        if (!isPipCapable()) return false

        // No receiver registration here: if the system cancels the entry
        // (e.g. another window steals top position) onPipModeChanged(true)
        // never fires and an eager registration would leak. onPipModeChanged
        // registers on confirmed entry — early enough, since the RemoteActions
        // only exist inside the PiP window that the mode change precedes.

        val params = buildPipParams(preArm = false, includeActions = true)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode(params)
            } else false
        }.onFailure { Log.w(TAG, "PiP enter failed", it) }
            .getOrDefault(false)
    }

    private fun isPipCapable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** True when the keyguard is showing or the screen is off (non-interactive). */
    private fun isScreenOffOrLocked(): Boolean {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        return keyguardManager?.isKeyguardLocked == true || powerManager?.isInteractive == false
    }

    /**
     * True when a PiP dismissal should close only the window and keep playing:
     * background video audio is enabled and the screen is interactive. The
     * activity survives (stopped) with its ViewModel, engine and media session
     * intact, so audio continues under the revealed MainActivity and the
     * now-playing notification stays up. Mirrors the fullscreen→home minimise
     * semantics of [PlayerLifecycleManager.onActivityPause] (a no-op when the
     * setting is on); a lock/screen-off inside the dismissal transition keeps
     * the old finish behaviour.
     */
    private fun keepPlayerAliveAfterPipDismiss(): Boolean =
        playerLifecycleManager.isBackgroundAudioEnabled && !isScreenOffOrLocked()

    private fun buildPipParams(
        preArm: Boolean,
        includeActions: Boolean,
    ): PictureInPictureParams = PictureInPictureParams.Builder().apply {
        val ratio = pipController.pipAspectRatio.value ?: Rational(16, 9)
        val clamped = PipLifecyclePolicy.clampAspectRatio(ratio.numerator, ratio.denominator)
        setAspectRatio(Rational(clamped.first, clamped.second))
        pipController.pipSourceRect?.let { src ->
            if (PipLifecyclePolicy.isValidSourceRect(
                    left = src.left,
                    top = src.top,
                    right = src.right,
                    bottom = src.bottom,
                    windowWidth = window.decorView.width,
                    windowHeight = window.decorView.height,
                )
            ) {
                setSourceRectHint(src)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // PipLifecyclePolicy.systemAutoEnterEnabled — deliberately no
            // controls-lock term (that system-side flag never gated on it).
            val autoEnter = if (preArm) {
                PipLifecyclePolicy.systemAutoEnterEnabled(
                    shouldAutoEnter = pipController.shouldAutoEnterPip.value,
                    isPlaying = pipController.isPlaying.value,
                    screenOffOrLocked = isScreenOffOrLocked(),
                )
            } else false
            setAutoEnterEnabled(autoEnter)
            setSeamlessResizeEnabled(autoEnter)
        }
        if (includeActions) setActions(buildPipActions())
    }.build()

    private fun applyPipParams(includeActions: Boolean) {
        if (!isPipCapable()) return
        val params = buildPipParams(preArm = !isInPictureInPictureMode, includeActions = includeActions)
        runCatching { setPictureInPictureParams(params) }
            .onFailure { Log.w(TAG, "PiP setPictureInPictureParams failed", it) }
    }

    private fun buildPipActions(): List<RemoteAction> =
        PipActionSet.actionSpecs(
            isPlaying = pipController.isPlaying.value,
            hasNext = pipController.pipHasNext,
        ).map { spec ->
            pipRemoteAction(
                id = PipActionSet.idFor(spec.action),
                icon = spec.iconRes,
                title = getString(spec.titleRes),
            )
        }

    private fun pipRemoteAction(id: Int, icon: Int, title: String): RemoteAction {
        val intent = Intent(PipActionSet.PIP_ACTION_BROADCAST).putExtra(PipActionSet.PIP_ACTION_EXTRA, id)
        val pi = PendingIntent.getBroadcast(
            this,
            id,
            intent.setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(
            Icon.createWithResource(this, icon),
            title,
            title,
            pi,
        )
    }

    private fun refreshPipActions() {
        if (!isInPictureInPictureMode) return
        applyPipParams(includeActions = true)
    }

    private fun registerPipActionReceiver() {
        if (pipActionReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != PipActionSet.PIP_ACTION_BROADCAST) return
                val action = PipActionSet.actionForId(intent.getIntExtra(PipActionSet.PIP_ACTION_EXTRA, -1))
                    ?: return
                val transport = pipController.pipTransport
                if (transport == null) {
                    Log.w(TAG, "PiP action $action dropped: pipTransport is null")
                } else {
                    transport.handle(action)
                }
                refreshPipActions()
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(PipActionSet.PIP_ACTION_BROADCAST),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        pipActionReceiver = receiver
    }

    private fun unregisterPipActionReceiver() {
        pipActionReceiver?.let { runCatching { unregisterReceiver(it) } }
        pipActionReceiver = null
    }

    companion object {
        const val TAG = "PlayerActivity"

        // Upper bound for the persisted-security-slice read in the lock-gate
        // check (see redirectToLockGateIfNeeded) — a pathological DataStore
        // read must never wedge cold start; on timeout the check falls back
        // to the current Eagerly-collected StateFlow value.
        const val APP_LOCK_GATE_READ_TIMEOUT_MS = 1_000L

        // Launch extras live in PlayerActivityArgs — the single build/parse
        // adapter for this activity's intent contract.
    }
}
