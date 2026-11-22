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
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.raulshma.jellyplay.core.data.playback.AndroidPipController
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.ui.components.JellyPlayPreferenceTheme
import com.raulshma.jellyplay.core.ui.components.rememberPreferenceDarkTheme
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.feature.player.live.LivePlayerScreen
import com.raulshma.jellyplay.feature.player.video.AndroidPlayerKeyBridge
import com.raulshma.jellyplay.feature.player.video.PlayerOrientationHost
import com.raulshma.jellyplay.feature.player.video.PlayerOrientationLock
import com.raulshma.jellyplay.feature.player.video.VideoPlayerScreen
import com.raulshma.jellyplay.navigation.playbackhost.PlayerActivityArgs
import com.raulshma.jellyplay.shell.AppLockRedirect
import com.raulshma.jellyplay.shell.AppLockReturn
import com.raulshma.jellyplay.shell.AppLockState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.mp.KoinPlatform

/**
 * Dedicated host Activity for fullscreen playback — VOD ([VideoPlayerScreen])
 * and Live TV ([LivePlayerScreen]; live is hosted here so system PiP serves
 * it — see [PlaybackHostRouter]).
 *
 * System Picture-in-Picture floats over the browse UI: this Activity shares
 * the default `taskAffinity` with
 * [MainActivity], so when it enters PiP the previous task entry — the Compose
 * browse UI in [MainActivity] — is revealed behind the floating window. A
 * single-Activity app cannot reproduce this (Android renders PiP as that
 * Activity's own window content; there is no sibling to show through).
 *
 * The PiP apparatus (param builder, remote actions, lifecycle coordination)
 * covers the full feature set:
 * RemoteActions (play/pause/skip/next), auto-enter on home, auto-exit
 * on END/ERROR, source-rect hint, aspect-ratio clamp. The pure halves beside
 * this Activity: [PipLifecyclePolicy] owns the ordering machine and param
 * folds, [PipActionSet] owns the remote-action decision tables (the action-set
 * fold and the broadcast id codec), [PipOrientationPolicy] owns the PiP
 * dock/restore orientation stash; this class keeps only Android wiring.
 * Both hosts feed it
 * through the same `core:data` PiP state singleton ([AndroidPipController],
 * the `PipController` port's impl) — VOD's VM and live's VM both resolve the
 * port, live's transport mapping SKIP remote actions to channel zap — so every
 * collector below serves both variants unchanged.
 *
 * Each screen creates its engine fresh via its ViewModel (scoped to this
 * Activity) — there is no cross-Activity engine handoff in the normal
 * open-play-PiP flow.
 *
 * Because the media notification opens this host by class name (bypassing
 * MainActivity), it enforces the app PIN/biometric gate itself: the
 * return gate folds the shared auto-lock policy at the actual foreground
 * return and redirects to MainActivity while a lock is configured and the
 * app-scoped AppLockState says locked — see [redirectToLockGateIfNeeded].
 */
// FragmentActivity for parity with MainActivity: androidx.biometric's prompt
// requires a FragmentActivity host.
class PlayerActivity : FragmentActivity(), PlayerOrientationHost {

    // PlayerActivity is the SOLE driver of the shared PlayerLifecycleManager
    // single (onPause/onResume/onTopResumed/onStop below). The singleton has
    // one @Volatile activeCallbacks slot with no owner identity, so a second
    // host pausing would reach across Activities into this activity's engine;
    // every other host must leave it alone.
    // Resolved from the Koin container.
    private val playerLifecycleManager: PlayerLifecycleManager by lazy { KoinPlatform.getKoin()!!.get() }

    private val pipController: AndroidPipController by lazy { KoinPlatform.getKoin()!!.get() }

    private val preferenceProjections: PreferenceProjections by lazy { KoinPlatform.getKoin()!!.get() }

    // Feeds and folds the shared PIN auto-lock state from this host too: the
    // player can be the task's top activity across a backgrounding
    // (singleTop launcher root), so it records the background stamp in
    // onPause (or PiP entry, when the transition never paused) and the return
    // gate (redirectToLockGateIfNeeded) folds it with the same
    // AppLockState.onResumed call MainActivity.onResume runs — at the
    // returning onResume, not at intent delivery.
    private val appLockState: AppLockState by lazy { KoinPlatform.getKoin()!!.get() }

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
     *
     * Saved to instance state ([onSaveInstanceState], `KEY_ACTIVE_ARGS`): a
     * recreation must restore the ACTIVE payload rather than re-parse the
     * intent — whose extras are the LAST DELIVERED ones (a parked
     * [pendingLaunchArgs] moved them there via `setIntent`) — or onCreate
     * would publish the parked swap before any qualifying return has passed
     * the lock-gate fold.
     *
     * `internal` rather than private so [PlayerActivityLockRedirectTest] can
     * pin the deferred-publish contract around [pendingLaunchArgs].
     */
    internal val launchArgs = mutableStateOf<PlayerActivityArgs?>(null)

    /**
     * Payload parked by an [onNewIntent] that arrived while this activity was
     * not the foreground surface — backgrounded, or docked in PiP (even on
     * the OEMs that hold the lifecycle RESUMED through the whole PiP
     * session, where RESUMED alone cannot mean foreground): intent delivery
     * is not the foreground return, so the swap must wait for a QUALIFYING
     * return to pass the lock-gate fold — a resume outside PiP, or a
     * RESUMED-phase PiP exit ([publishPendingLaunchArgs] carries the
     * contract) — publishing early would initialize/retune the new item or
     * channel in a window the user has not cleared the lock challenge for
     * yet.
     *
     * Saved to instance state ([onSaveInstanceState], `KEY_PENDING_ARGS`) so
     * a recreation re-parks it: the recreated instance's intent IS this
     * payload (`setIntent` ran at delivery), and a parse-from-intent onCreate
     * would publish it before the gate.
     */
    internal var pendingLaunchArgs: PlayerActivityArgs? = null

    /**
     * The player's D-pad handlers are Compose focus-dependent
     * (`.focusable()` + `onPreviewKeyEvent` on the surface Box), and when no
     * Compose focus target exists — the controls auto-hid and the hide-edge
     * focus handoff lost its race, an overlay unmount orphaned focus, or a
     * View outside Compose took window focus — the default dispatch has no
     * topmost key-input node above the player to fall back to, so every
     * remote key died silently while Back survived (OnBackPressedDispatcher
     * needs no focus). Offer every key the normal dispatch chain did NOT
     * consume to the composing player screen's own handler
     * ([AndroidPlayerKeyBridge]; the screens install their exact D-pad/keyboard
     * arm sets while up). A consumed key never reaches here, so there is no
     * double handling; a declined key keeps the system's default `false`
     * behavior. Both hosts (VOD and live) are covered by this single seam.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (super.dispatchKeyEvent(event)) return true
        return AndroidPlayerKeyBridge.deliver(
            androidx.compose.ui.input.key.KeyEvent(event),
        )
    }

    /**
     * One physical BACK press, one back decision. On API 33+ (manifest
     * `enableOnBackInvokedCallback="true"`) WITH a back callback actually
     * registered (the composing player screen's JellyPlayBackHandler ladder)
     * the framework already ran that ladder when this press's KEYUP crossed
     * the Pre-IME stage ([android.view.ViewRootImpl] → OnBackInvokedInput →
     * the ladder); this legacy key-path entry (`Activity.onKeyUp` BACK
     * tracking → `onBackPressed`) would run the SAME ladder a second time on
     * the identical press. The second run reads the first run's own effect as
     * new state — controls it just hid, a sheet it just closed — and falls
     * through to `onBack()`: one press hides the controls AND exits the player
     * (the "BACK exits with controls visible" failure mode).
     *
     * The registration test matters: when NO callback is registered — the
     * live TV screen composes no JellyPlayBackHandler (its D-pad BACK arm IS
     * the ladder), and it is also down in mid-swap/transient windows — the
     * predictive pipeline runs nothing, so early-returning here would kill
     * BACK entirely in exactly those windows (the D-pad arm/sink has already
     * declined). Falling through lets the dispatcher's no-callback fallback
     * run: the activity finishes. Android <33 has
     * no platform back callback at all: this legacy path IS the dispatch and
     * must super through.
     */
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            onBackPressedDispatcher.hasEnabledCallbacks()
        ) {
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // PIN/biometric gate — FIRST, before any window styling,
        // playback setup or setContent: while a lock is configured and the
        // app is locked, no player UI may compose. The media notification's
        // content intent opens this activity by class name, so this host must
        // enforce the gate itself; redirecting to MainActivity routes the
        // user into the lock screen its own compose gate renders. Intent
        // delivery only re-checks the CURRENT lock state — the auto-lock fold
        // waits for onResume (the genuine return), so a foregrounding that
        // lands past the timer is still measured at the resume, before the
        // first frame draws.
        if (redirectToLockGateIfNeeded(evaluateReturnPolicy = false)) return

        // Edge-to-edge so the player draws under the system bars; VideoPlayerScreen
        // owns immersive show/hide and inset handling itself (it was designed for
        // MainActivity's edge-to-edge window). Without this the surface + controls
        // are inset by the status/nav bars in fullscreen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Restore the orientation intent (+ docked stash) saved by
        // onSaveInstanceState BEFORE Compose composes: the screen's orientation
        // effect then records onto a restored intent instead of a null one, and
        // an instance recreated while docked re-docks from the saved stash
        // (restoreOrientationState; see also onDestroy's finishing-only reset).
        restoreOrientationState(savedInstanceState)

        // A recreation must NOT re-derive the payloads from the intent: the
        // intent is the LAST DELIVERED one — a pre-return onNewIntent parked
        // its payload after setIntent moved it here — so parsing it now would
        // publish the parked swap from onCreate, before any qualifying return
        // has passed the lock-gate fold. The saved payloads restore the parked
        // contract verbatim instead: the active item stays active and the
        // parked item stays parked (publishPendingLaunchArgs still owns the
        // publication). Falls through to the intent parse only when nothing
        // was saved — the fresh-start path, where the intent IS the launch.
        val restoredActive = savedInstanceState?.restoreArgs(KEY_ACTIVE_ARGS)
        val restoredPending = savedInstanceState?.restoreArgs(KEY_PENDING_ARGS)
        launchArgs.value = restoredActive ?: PlayerActivityArgs.fromIntent(intent) ?: run {
            Log.w(TAG, "No itemId extra; finishing.")
            finish()
            return
        }
        if (restoredPending != null) {
            pendingLaunchArgs = restoredPending
        }

        setContent {
            // The TV-mode composition local: MainActivity's shell provides it
            // (MainContent), but this host composes the players DIRECTLY —
            // without this provider LocalTvMode.current reads false on every TV
            // device here, so the players run their PHONE branches: the TV
            // D-pad arms, the TV focus grab/key sink
            // and the back ladder's "TV: hide controls on back" step stay
            // dead code, and BACK always exits (the "back exits with
            // controls visible" bug) because `isTv && showControls` can
            // never hold.
            val isTvDevice = isTv()
            androidx.compose.runtime.CompositionLocalProvider(
                LocalTvMode provides isTvDevice,
            ) {
                // Theme the player with the same preference-driven stack as
                // MainActivity (accent/OLED/contrast/performance-locals/hand-mode/
                // blue-light/colorblind) via the shared wrapper. PreferenceProjections
                // is the same @Singleton MainViewModel reads, so the player tracks the
                // user's appearance settings identically.
                val preferences by preferenceProjections.mainPreferences
                    .collectAsStateWithLifecycle()
                val darkTheme = rememberPreferenceDarkTheme(preferences)
                // Subtitle tester overlays the player (keeps the video engine alive
                // underneath) rather than navigating away to it. The tester builds
                // its own preview engine, so it is self-contained. Video-only —
                // live has no subtitle side-loading.
                var showSubtitleTester by remember { mutableStateOf(false) }
                // Hoisted launch args so onNewIntent (re-selection while this
                // singleTask activity is alive, e.g. picking another item while in
                // PiP) can swap the payload without recreating the Activity. The
                // variant branch recomposes the matching screen with the new args.
                val args = launchArgs.value ?: return@CompositionLocalProvider
                JellyPlayPreferenceTheme(
                    preferences = preferences,
                    darkTheme = darkTheme,
                    // The form-factor flag must ride along or the theme's
                    // density normalization (the TV UI scale) stays inert
                    // here and the player's chrome renders at 100% while the
                    // main shell honors the user's scale setting.
                    isTv = isTvDevice,
                ) {
                    when (args) {
                        is PlayerActivityArgs.Video -> Box(Modifier.fillMaxSize()) {
                            VideoPlayerScreen(
                                itemId = args.itemId,
                                mediaSourceId = args.mediaSourceId,
                                startPositionTicks = args.startPositionTicks,
                                subtitleStreamIndex = args.subtitleStreamIndex,
                                audioStreamIndex = args.audioStreamIndex,
                                forceStream = args.forceStream,
                                onBack = { finish() },
                                onEnterPip = { enterPipMode() },
                                onOpenSubtitleTester = { showSubtitleTester = true },
                                // The tester composes ABOVE the player and owns
                                // the window while up: gates the player's TV
                                // focus-grab re-assert (would steal focus from
                                // the tester's controls) and its key sink
                                // (tester-declined keys would act on the
                                // player underneath).
                                isOverlayScreenActive = showSubtitleTester,
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
        }

        // `playbackPreferences` is lazily shared (WhileSubscribed) and its
        // only other in-player subscriber — the PiP params collector below —
        // runs just on S+ with system PiP. Warm it on every API level so
        // shouldAutoEnterPipNow() reads persisted state, not the default,
        // after a cold start straight into the player.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferenceProjections.playbackPreferences.collect {
                    // No-op — subscription only keeps the shared flow warm.
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            // One collector drives param application for both the pre-arm path
            // (not yet in PiP: setAutoEnterEnabled + aspect + source rect, no
            // actions) and the in-PiP refresh path (resolution/track swap while
            // already in PiP: actions + aspect). The combined value is only a
            // CHANGE TRIGGER — the emitted list carries the raw inputs so any
            // of them flipping (including the auto-PiP preference, mapped to
            // the single field so unrelated playback-pref writes don't re-fire)
            // re-applies params; buildPipParams re-derives the effective gate
            // fresh via shouldAutoEnterPipNow(), so no gate is computed here.
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    combine(
                        pipController.shouldAutoEnterPip,
                        pipController.pipAspectRatio,
                        pipController.isPlaying,
                        preferenceProjections.playbackPreferences
                            .map { it.autoEnterPip }
                            .distinctUntilChanged(),
                    ) { shouldAutoEnter, aspect, isPlaying, autoPipEnabled ->
                        listOf(shouldAutoEnter, aspect, isPlaying, autoPipEnabled)
                    }
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
        // redirect as onCreate. The auto-lock fold deliberately waits for
        // the returning onResume: intent delivery is not the return, and
        // consuming the background stamp here would let a foregrounding that
        // lands past the timer start playback unchallenged.
        if (redirectToLockGateIfNeeded(evaluateReturnPolicy = false)) return
        // Swap the payload without recreating this singleTask Activity:
        // updating launchArgs recomposes the matching screen with the new
        // args. This is the path that handles "play another media/channel
        // while in PiP" — video re-fires its LaunchedEffect(itemId) →
        // initialize(); live's screen sees the changed channelId and
        // stop-and-retunes the activity-scoped VM (its `initialized` latch
        // would otherwise no-op and keep the old channel playing). Without
        // any of this the live instance expands out of PiP but the new extras
        // are dropped and the old media keeps playing.
        //
        // Publish only when this delivery lands in the FOREGROUND —
        // fullscreen AND already RESUMED (a re-selection with the player on
        // top). RESUMED alone is not foreground: OEMs that keep this
        // activity RESUMED through the whole PiP session deliver intents
        // while docked, and that delivery parks the payload exactly like a
        // backgrounded one — the return is a qualifying one (the following
        // onResume outside PiP, or a RESUMED-phase PiP exit) whose fold may
        // relock and redirect, and the swap must not initialize/retune ahead
        // of that gate.
        val args = PlayerActivityArgs.fromIntent(intent) ?: return
        if (!isInPictureInPictureMode &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            launchArgs.value = args
        } else {
            pendingLaunchArgs = args
        }
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
        // Finishing-only: a CONFIGURATION recreation must not clobber the
        // request — the new instance restores the saved intent
        // (restoreOrientationState) and re-locks from its own effect; an
        // unconditional write here raced that restore (issue #186 RC-3).
        if (isFinishing) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            orientationIntent = null
            pipOrientationState = PipOrientationPolicy.State()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Re-assert the player's orientation intent: some OEMs reset the
        // window request during the expand rotation (or an unrelated config
        // ripple — uiMode, keyboard) while the request and the intent have
        // already diverged. The equality guard makes this a no-op whenever
        // the request already matches, so it cannot loop with the system's
        // own application of the request. Never while docked: PiP windows
        // are system-letterboxed and must stay orientation-free (Issue #11).
        if (isInPictureInPictureMode) return
        val intent = orientationIntent ?: return
        val requested = orientationLockConstant(intent)
        if (requestedOrientation != requested) {
            requestedOrientation = requested
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // The intent (RC-3: a plain field dies with the instance) plus the
        // dock/stash pair when the activity is docked — restoreOrientationState
        // rebuilds both into the new instance BEFORE Compose records over them.
        outState.putString(KEY_ORIENTATION_INTENT, orientationIntent?.name)
        if (pipOrientationState.docked) {
            outState.putBoolean(KEY_PIP_DOCKED, true)
            outState.putString(KEY_PIP_STASHED_INTENT, pipOrientationState.stashedIntent?.name)
        }
        // The launch payloads ride along (plain fields die with the instance):
        // the active one, and any payload a pre-return onNewIntent parked —
        // that parked contract must survive a recreation instead of onCreate
        // re-publishing it from the updated intent (see the onCreate restore).
        launchArgs.value?.let { outState.putBundle(KEY_ACTIVE_ARGS, it.savedExtras()) }
        pendingLaunchArgs?.let { outState.putBundle(KEY_PENDING_ARGS, it.savedExtras()) }
    }

    /**
     * The args as an extras [Bundle] — built through
     * [PlayerActivityArgs.buildIntent] so the ONE launch contract owns the
     * field set; no second hand-fold of the extras exists.
     */
    private fun PlayerActivityArgs.savedExtras(): Bundle =
        buildIntent(this@PlayerActivity).extras ?: Bundle.EMPTY

    /**
     * The args saved under [key] by [onSaveInstanceState], parsed back through
     * the same [PlayerActivityArgs.fromIntent] adapter; a missing or corrupt
     * entry degrades to null (no parking, no publication).
     */
    private fun Bundle.restoreArgs(key: String): PlayerActivityArgs? =
        getBundle(key)?.let { PlayerActivityArgs.fromIntent(Intent().replaceExtras(it)) }

    /**
     * onCreate-side twin of [onSaveInstanceState] — runs before `setContent`
     * so the first composition's orientation effect records ONTO the restored
     * intent. Two landing shapes: the recreate-in-PiP path (OEM recreations
     * mid-transition) re-docks from the saved stash and keeps the window
     * orientation-free; a recreation that already landed outside PiP applies
     * the stash once and clears the dock state (the expand raced the
     * recreate).
     */
    private fun restoreOrientationState(savedInstanceState: Bundle?) {
        val saved = savedInstanceState ?: return
        orientationIntent = saved.getString(KEY_ORIENTATION_INTENT)?.toPlayerOrientationLockOrNull()
        if (!saved.getBoolean(KEY_PIP_DOCKED)) return
        val stashed = saved.getString(KEY_PIP_STASHED_INTENT)?.toPlayerOrientationLockOrNull()
        pipOrientationState = PipOrientationPolicy.State(stashedIntent = stashed, docked = true)
        if (isInPictureInPictureMode) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            stashed?.let { requestedOrientation = orientationLockConstant(it) }
            pipOrientationState = PipOrientationPolicy.State()
        }
    }

    // ── Orientation vocabulary mapping ─────────────────────────────────────
    // The PlayerOrientationLock ⇄ ActivityInfo constant folds the player
    // screen's window seam carries privately; the Activity needs its own pair
    // for the dock/restore writes (restoreOnPlayerExit's seam instance is a
    // composition artifact, not reachable from lifecycle callbacks).

    /** The `ActivityInfo` screen-orientation constant [mode] requests. */
    private fun orientationLockConstant(mode: PlayerOrientationLock): Int = when (mode) {
        PlayerOrientationLock.SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        PlayerOrientationLock.SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        PlayerOrientationLock.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        PlayerOrientationLock.LOCKED_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        PlayerOrientationLock.LOCKED_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        PlayerOrientationLock.TV_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        PlayerOrientationLock.USER -> ActivityInfo.SCREEN_ORIENTATION_USER
        PlayerOrientationLock.USER_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        PlayerOrientationLock.UNSPECIFIED -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    /** The [PlayerOrientationLock] this raw screen-orientation constant maps to; unknown constants → null. */
    private fun Int.toPlayerOrientationLockOrNull(): PlayerOrientationLock? = when (this) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> PlayerOrientationLock.SENSOR_LANDSCAPE
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> PlayerOrientationLock.SENSOR_PORTRAIT
        ActivityInfo.SCREEN_ORIENTATION_SENSOR -> PlayerOrientationLock.SENSOR
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> PlayerOrientationLock.LOCKED_LANDSCAPE
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> PlayerOrientationLock.LOCKED_PORTRAIT
        ActivityInfo.SCREEN_ORIENTATION_USER -> PlayerOrientationLock.USER
        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE -> PlayerOrientationLock.USER_LANDSCAPE
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> PlayerOrientationLock.UNSPECIFIED
        else -> null
    }

    /** Saved-state name → lock; a stale or corrupt name degrades to null, never a throw. */
    private fun String.toPlayerOrientationLockOrNull(): PlayerOrientationLock? =
        PlayerOrientationLock.entries.firstOrNull { it.name == this }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-enter guard lives in PipLifecyclePolicy.userLeaveAutoEnter:
        // `!isControlsLocked` so swiping home while the lock overlay is up
        // doesn't yank the user into PiP, and `!isScreenOffOrLocked()` because
        // several OEMs fire this callback for the power button too.
        // Deliberately no isPlaying term — the fallback below adds it.
        // shouldAutoEnterPipNow() folds the user's auto-PiP preference
        // with the engine-armed flag: off means Home/recents
        // simply backgrounds the app.
        if (PipLifecyclePolicy.onUserLeaveHint(
                shouldAutoEnter = shouldAutoEnterPipNow(),
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
    // guards).
    override fun onTopResumedActivityChanged(isTopResumed: Boolean) {
        super.onTopResumedActivityChanged(isTopResumed)
        val decision = PipLifecyclePolicy.onTopResumedChanged(
            isTopResumed = isTopResumed,
            inPip = isInPictureInPictureMode,
            apiSupportsAutoEnter = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            shouldAutoEnter = shouldAutoEnterPipNow(),
            isPlaying = pipController.isPlaying.value,
            controlsLocked = pipController.isControlsLocked,
            // Same lock guard as onUserLeaveHint: the keyguard stealing the
            // top-resumed position is not a "leave" worth auto-PiP for.
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

    // `internal` so PlayerActivityLockRedirectTest can pin the dismiss
    // protocol's arm state (the discharge-pins premise assertions), the same
    // contract-visibility precedent as launchArgs/pendingLaunchArgs.
    internal var justExitedPip = false

    // Saved window brightness so it can be restored on PiP exit. resets.
    // brightness to the system auto value while in PiP (the in-app brightness
    // gesture is irrelevant in the floating window) and restores it on expand.
    private var savedBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    // The player's SEMANTIC orientation intent (PlayerOrientationHost): what
    // the composing screen wants, recorded by VideoPlayerScreen's orientation
    // effect (before its 400 ms settle) and the rotate toggle — never inferred
    // from the window request, which can legitimately read UNSPECIFIED (cast,
    // TV, or an entry racing the settle). Null = no concrete intent.
    private var orientationIntent: PlayerOrientationLock? = null

    // The PiP dock/restore machine over that intent (PipOrientationPolicy —
    // its KDoc carries the full protocol): entry stashes the intent once and
    // docks (idempotent), expand alone restores, dismiss clears, and the
    // stash rides onSaveInstanceState so an OEM recreation during the PiP
    // transition still expands to the pre-PiP orientation (issue #186: the
    // old window-request stash was overwritten with UNSPECIFIED by repeat
    // callbacks and did not survive recreation). Docked, the activity never
    // holds a fixed-orientation request — the Issue-#11 fixed-rotation
    // conflict that the WM "resolves" by CLOSING the activity — because the
    // window request is written UNSPECIFIED once on dock and every
    // lockOrientation write is suppressed while isInPictureInPictureMode.
    private var pipOrientationState = PipOrientationPolicy.State()

    /**
     * [PlayerOrientationHost]: the screen's semantic intent landed. Outside
     * PiP this replaces the intent ([PlayerOrientationLock.UNSPECIFIED]
     * clears it). While docked the pre-PiP stash wins — a mid-PiP record
     * (cast connecting, a pref flip reached through Settings) must not
     * rewrite what expand will restore — EXCEPT when no stash exists at all:
     * a fresh activity created directly into PiP has no pre-PiP state to
     * preserve, so its first concrete intent becomes the stash expand
     * restores. Recording never writes a window request.
     */
    override fun onPlayerOrientationIntentChanged(intent: PlayerOrientationLock) {
        if (!isInPictureInPictureMode) {
            orientationIntent = intent.takeUnless { it == PlayerOrientationLock.UNSPECIFIED }
            return
        }
        if (pipOrientationState.stashedIntent == null &&
            intent != PlayerOrientationLock.UNSPECIFIED
        ) {
            orientationIntent = intent
            pipOrientationState = pipOrientationState.copy(stashedIntent = intent)
        }
    }

    // ── PIN/biometric lock gate ─────────────────────────────────
    //
    // Closes the media-notification bypass: the video MediaSession pins its
    // session-activity PendingIntent to this class BY NAME
    // (AndroidMediaSessionController), so a notification tap while the app is
    // locked would otherwise reach full playback without ever hitting
    // MainActivity's lock gate. At a genuine foreground return the gate folds
    // the shared auto-lock-on-return policy; it then — when a gate is
    // configured and the app-scoped AppLockState says locked — hands off to
    // MainActivity, whose own gate renders AuthChallengeScreen, and finishes
    // before composing anything.
    //
    // Note: this is the APP lock, not the in-player "screen lock" overlay
    // (usePinForPlayerLock / SlideToUnlockOverlay inside VideoPlayerScreen) —
    // that feature locks player controls; this one gates entry to the app.
    /**
     * Redirects to MainActivity's lock gate when a PIN/biometric lock is
     * configured and the app is locked. With [evaluateReturnPolicy] set, this
     * call marks a genuine foreground RETURN — `onResume` outside PiP, or a
     * PiP exit that lands already RESUMED — and first folds the shared
     * auto-lock-on-return policy ([AppLockReturn.foldReturnPolicy], the exact
     * fold MainActivity.onResume runs, fed by the same background stamp both
     * hosts record from `onPause`). This gate is the one return path that
     * skips MainActivity
     * (launcher icon resuming the backgrounded player, a media-notification
     * tap on a live instance, a PiP expand), so a return after the configured
     * timer elapsed relative to the last backgrounding must relock here and
     * fall through to the redirect; a return inside the window consumes the
     * stamp and restores playback directly.
     *
     * Intent delivery (`onCreate` / `onNewIntent`) passes
     * [evaluateReturnPolicy] `false`: those moments only re-check the CURRENT
     * lock state, because the foregrounding can land later than the delivery
     * (cold-start init, keyguard ordering) and the timer must be measured at
     * the actual resume.
     *
     * Returns `true` when the redirect fired — callers must return immediately
     * so no player UI, PiP collector or window styling runs (finishing in
     * `onCreate` precedes
     * `onUserLeaveHint`/`onTopResumedActivityChanged`/`STARTED`-gated
     * collectors, so no PiP path can trigger on the redirect).
     */
    private fun redirectToLockGateIfNeeded(evaluateReturnPolicy: Boolean): Boolean {
        val koin = KoinPlatform.getKoin() ?: return false
        val lockState = koin.get<AppLockState>()
        // An intent delivery (evaluateReturnPolicy false) with the holder
        // already unlocked can neither fold nor redirect — the predicate
        // below is `gateConfigured && !unlocked` for any gateConfigured —
        // so return BEFORE the persisted-security read: delivery must never
        // block main on runBlocking while unlocked. The same skip covers a
        // genuine return whose holder is unlocked with NO pending stamp:
        // the fold is then provably a no-op for either read outcome
        // (shouldRelock and the fail-closed arm both require a stamp) and
        // shouldRedirect can never fire while unlocked, so the bounded read
        // would be pure main-thread latency. A genuine return with a
        // pending stamp must NOT skip: the fold may relock the unlocked
        // holder off the expired stamp.
        if (lockState.unlocked.value &&
            (!evaluateReturnPolicy || !lockState.hasPendingBackground)
        ) {
            return false
        }
        val securityStore = koin.get<SecurityStore>()
        // Stamp the entry moment BEFORE the bounded read: the fold measures
        // the absence against this instant, so the read's latency (up to the
        // full timeout) must not count as time away.
        val nowMs = SystemClock.elapsedRealtime()
        // The gate predicate must read the PERSISTED security slice, not the
        // `.security` StateFlow seed: on a cold process (recents-restore of a
        // task topped by this activity) the Eagerly-collected upstream may
        // not have emitted yet and the seed's defaults would read as "no gate
        // configured", re-opening the bypass in exactly the coldest case.
        // AppLockReturn.persistedSecurity is the bounded read both hosts
        // share (the exact one MainActivity.onResume folds with): it
        // suspends until DataStore's initial read lands — warm-process
        // callers return from the cached snapshot without disk IO — under a
        // timeout so a pathological read can never wedge cold start (timeout
        // handling: fail CLOSED, below). The Application's IO prewarm
        // hydrates this same persisted slice off main at process start, so in
        // the common case the read below is an instant memory replay — the
        // runBlocking + timeout stays exactly as the fail-closed safety net
        // for the cold-start race the prewarm cannot fully close (this
        // activity can beat the prewarm coroutine to the CPU).
        val persisted = runBlocking { AppLockReturn.persistedSecurity(securityStore) }
        // Fail CLOSED on a timed-out read:
        // falling back to the `.security` StateFlow seed would re-open the
        // cold-start race this read exists to close whenever the read is
        // merely slow. Treating "unknown" as gate-configured still lets an
        // UNLOCKED holder through (that user authenticated this process);
        // a locked one lands on the lock screen. Residual (equivalent on
        // both hosts): a CORRUPT prefs read inside
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
        // Fold the shared auto-lock policy BEFORE consulting the unlocked
        // flag — the persisted slice's timer + gate configuration are the
        // same values (and the same fold) MainActivity.onResume runs. The
        // fold consumes the background stamp unconditionally, so a short
        // return neither relocks nor leaves a stale stamp behind; on a
        // timed-out read it fails closed instead (AppLockReturn owns that
        // policy — see its foldReturnPolicy KDoc).
        if (evaluateReturnPolicy) {
            AppLockReturn.foldReturnPolicy(
                lockState = lockState,
                persisted = persisted,
                nowMs = nowMs,
            )
        }
        if (lockState.unlocked.value) return false
        if (!AppLockRedirect.shouldRedirect(gateConfigured = gateConfigured, unlocked = lockState.unlocked.value)) {
            return false
        }
        // Plain launch intent (playback args deliberately NOT forwarded —
        // after unlocking, the user lands on the browse UI exactly like a
        // cold app start). MainActivity is the task's singleTop root on the
        // same default taskAffinity as this activity, so CLEAR_TOP|SINGLE_TOP
        // routes to the existing instance underneath this finishing one
        // (popping it rather than stacking a second shell above) or
        // cold-creates it on the same task; either way its own gate shows the
        // lock screen (the shared fold above already re-locked when the timer
        // fired, and its own auto-lock-on-resume fold still covers the shell
        // paths).
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        lockGateRedirected = true
        finish()
        return true
    }

    /**
     * Set the first time [redirectToLockGateIfNeeded] fired — the onResume /
     * PiP-expand re-checks must not launch a
     * SECOND MainActivity handoff for the same redirect: after onCreate or
     * onNewIntent redirects, the activity still walks its lifecycle through
     * onResume while finishing.
     */
    private var lockGateRedirected = false

    /**
     * Publishes the payload parked by a pre-return [onNewIntent] and clears
     * it. Called only after a qualifying return has PASSED the gate: the
     * [onResume] outside PiP (a docked resume is not a return — the gate fold
     * is skipped there and the payload must stay parked with it), and a
     * RESUMED-phase PiP exit for the OEM orderings that expand without a
     * later onResume. A redirecting return finishes before reaching either
     * call, so the payload never publishes under the lock challenge.
     */
    private fun publishPendingLaunchArgs() {
        pendingLaunchArgs?.let {
            launchArgs.value = it
            pendingLaunchArgs = null
        }
    }

    /**
     * Evaluates a pending auto-lock absence at a TEARDOWN PiP dismissal. A
     * dismissal is not a foreground return (ADR 0018, Decision 5 — the exit
     * fold is skipped and the PiP-entry stamp stays pending), but a teardown
     * dismissal is also the END of the absence it represents: the shell
     * beneath a docked window has been RESUMED since entry — its own docked
     * resume never folds, by that same decision — and finishing this activity
     * delivers no new MainActivity.onResume. Without this fold the stamp
     * would never be evaluated: browse would sit interactive and unlocked
     * behind an expired auto-lock timer with no challenge left to pass. A
     * fold that relocks flips the app-scoped holder, and the shell's compose
     * gate renders the challenge without needing any shell lifecycle event;
     * the stamp is consumed here (a following shell fold finds nothing
     * pending).
     *
     * The two call sites are the teardown dismissal's discharge points: the
     * below-STARTED exit callback (onStop already ran; no keep-alive — the
     * activity finishes right after), and the armed [onStop] discharge (the
     * STARTED dismissal ordering; an expand's resume clears the arm first, so
     * an arm reaching onStop means the window was dismissed). A keep-alive
     * dismissal is deliberately NOT folded: that session continues under the
     * shell — background audio — and a future genuine return (notification
     * relaunch, app re-entry) still folds the stamp. RESUMED-phase exits fold
     * as genuine returns at the exit gate; a redirect that already fired left
     * the holder locked, which the compose gate covers.
     */
    private fun foldPendingAbsenceAtDismissalTeardown() {
        if (lockGateRedirected) return
        if (!appLockState.hasPendingBackground) return
        val koin = KoinPlatform.getKoin() ?: return
        // Stamp the fold moment BEFORE the bounded read — the same discipline
        // as the other gates: the read's latency (up to the full timeout) must
        // not count as time away.
        val nowMs = SystemClock.elapsedRealtime()
        val persisted = runBlocking { AppLockReturn.persistedSecurity(koin.get<SecurityStore>()) }
        AppLockReturn.foldReturnPolicy(
            lockState = appLockState,
            persisted = persisted,
            nowMs = nowMs,
        )
    }

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
            // PiP entry backgrounds the fullscreen surface. The standard
            // callback ordering paused the activity first — onPause already
            // recorded the shared AppLockState stamp — but the OEMs that keep
            // this activity RESUMED through the whole PiP session never pause
            // it, and without a stamp an expand after the auto-lock timer
            // elapsed could not relock. Recording only when none is pending
            // keeps the standard ordering from duplicating its own pause
            // stamp (that pending stamp IS this same transition and is folded
            // shortly after — by the revealed shell's resume, or by the exit
            // gate).
            appLockState.onBackgroundedIfNonePending(SystemClock.elapsedRealtime())
            // Entry touches none of the dismiss machinery (registration, bar
            // show and brightness stash below are execution, not policy) and
            // none of the PiP policy's state — no PiP policy call here.
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
            // Same fold for the orientation intent (PipOrientationPolicy — see
            // pipOrientationState): stash the player's semantic intent once and
            // dock, then drop the window request so the floating task never sits
            // in the fixed-rotation conflict the WM "resolves" by closing the
            // activity. Restored on exit below. onEntered is idempotent
            // (State.docked): the repeat callbacks OEMs fire read the
            // already-reset window request WITHOUT overwriting the first stash —
            // the pre-screening window-request stash was the primary #186
            // failure.
            when (val decision = PipOrientationPolicy.onEntered(
                state = pipOrientationState,
                desiredIntent = orientationIntent,
                currentRequested = requestedOrientation.toPlayerOrientationLockOrNull(),
            )) {
                is PipOrientationPolicy.Decision.Dock -> {
                    pipOrientationState = pipOrientationState.copy(
                        stashedIntent = decision.stash,
                        docked = true,
                    )
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                // Repeated callback: the first stash stands.
                else -> {}
            }
        } else {
            // PiP-expand gate re-check: some
            // OEMs keep the activity RESUMED through the whole PiP session,
            // so the onResume re-check may not re-fire on expand — consult
            // the gate here too (idempotent via lockGateRedirected; the
            // dismiss paths are already finishing or about to, and a locked
            // holder being dismissed should still not expand to fullscreen
            // playback unchallenged). The auto-lock fold runs only when this
            // exit genuinely landed the activity in the foreground (RESUMED):
            // a STARTED callback is not a return — the expand-before-onResume
            // ordering leaves the fold to onResume, and a keep-alive dismissal
            // keeps the stamp pending for the next true return, so the
            // background-audio absence is still measured. On a passing
            // RESUMED exit the same moment also publishes a parked payload:
            // some OEMs expand without a later onResume, so waiting for the
            // resume alone would strand the swap.
            val exitIsReturn = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!lockGateRedirected && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                val redirected = redirectToLockGateIfNeeded(evaluateReturnPolicy = exitIsReturn)
                if (!redirected && exitIsReturn) publishPendingLaunchArgs()
            }
            unregisterPipActionReceiver()
            window.attributes = window.attributes.apply {
                screenBrightness = savedBrightness
            }
            // The lifecycle phase at callback time — computed once, consumed
            // by BOTH folds below (orientation restore and the dismiss/expand
            // protocol; PipLifecyclePolicy's KDoc carries the OEM-ordering
            // spec). The orientation policy mirrors the enum (it stays pure of
            // the lifecycle policy's Decision shape), so the value maps 1:1.
            val lifecyclePhase = when {
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ->
                    PipLifecyclePolicy.Phase.RESUMED
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ->
                    PipLifecyclePolicy.Phase.STARTED_NOT_RESUMED
                else -> PipLifecyclePolicy.Phase.BELOW_STARTED
            }
            val phase = when (lifecyclePhase) {
                PipLifecyclePolicy.Phase.RESUMED -> PipOrientationPolicy.Phase.RESUMED
                PipLifecyclePolicy.Phase.STARTED_NOT_RESUMED ->
                    PipOrientationPolicy.Phase.STARTED_NOT_RESUMED
                PipLifecyclePolicy.Phase.BELOW_STARTED -> PipOrientationPolicy.Phase.BELOW_STARTED
            }
            // Re-apply the player's orientation intent on expand (stashed on
            // entry; VideoPlayerScreen's lock effect only runs on composition,
            // not on PiP exit). RESTORE only: the expand path re-asserts the
            // pre-PiP intent; a STARTED_NOT_RESUMED callback (the OEM ordering
            // where expand lands before onResume, or a keep-alive dismissal)
            // keeps the stash for onResume to apply, and a dismiss
            // (BELOW_STARTED) clears WITHOUT writing — restoring on a dismiss
            // would rotate the hidden activity / task mid-transition.
            when (val orientationDecision = PipOrientationPolicy.onExited(pipOrientationState, phase)) {
                is PipOrientationPolicy.Decision.Restore -> {
                    requestedOrientation = orientationLockConstant(orientationDecision.intent)
                    pipOrientationState = PipOrientationPolicy.State()
                }
                is PipOrientationPolicy.Decision.Clear -> pipOrientationState = PipOrientationPolicy.State()
                else -> {}
            }
            // Leaving PiP fires for BOTH expand-to-fullscreen and dismiss; the
            // two are distinguished by lifecycle state at this callback and
            // the whole dismiss/expand fold (arm justExitedPip vs finish
            // directly vs keep-alive no-op — including the OEM ordering where
            // this callback fires before onStop, and the background-audio /
            // keyguard branches) lives in PipLifecyclePolicy.pipExited — its
            // KDoc carries the full OEM-ordering spec. The fresh keep-alive
            // read is hoisted so the decision and the teardown-dismissal fold
            // below consume the SAME read (one host judgment per exit).
            val keepAliveAtExit = keepPlayerAliveAfterPipDismiss()
            val decision = PipLifecyclePolicy.pipExited(
                phase = lifecyclePhase,
                keepPlayerAlive = keepAliveAtExit,
                isFinishing = isFinishing,
                justExitedPip = justExitedPip,
            )
            justExitedPip = decision.justExitedPip
            // A below-STARTED exit callback with no keep-alive IS the teardown
            // dismissal (onStop already ran; the activity never resumes): the
            // END of the docked absence — fold the pending stamp now, before
            // the shell is revealed with no further lifecycle event (see
            // foldPendingAbsenceAtDismissalTeardown). The STARTED dismissal
            // ordering folds at its onStop discharge (the arm reaching onStop
            // is the dismissal confirmation — an expand's resume clears the
            // arm first), so a STARTED exit itself stays fold-free: the
            // expand-before-onResume ordering must leave the stamp for the
            // following resume (ADR 0018, Decision 5).
            if (lifecyclePhase == PipLifecyclePolicy.Phase.BELOW_STARTED && !keepAliveAtExit) {
                foldPendingAbsenceAtDismissalTeardown()
            }
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
        // Feed the shared PIN auto-lock background stamp — the same
        // AppLockState backgrounding MainActivity.onPause records. This host
        // can stay the task's top activity across a backgrounding (singleTop
        // launcher root), so a return that skips MainActivity must find the
        // absence stamped here; the return gate's fold
        // (redirectToLockGateIfNeeded) consumes it. Recording only when none
        // is pending: a pause that follows a PiP-entry stamp (the
        // dismissal-time pause of a session that stayed RESUMED through PiP)
        // is a later transition inside the same absence, and resetting the
        // clock would let the time spent watching the PiP window escape the
        // auto-lock timer.
        appLockState.onBackgroundedIfNonePending(SystemClock.elapsedRealtime())
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
        // Gate re-check on every resume: the
        // gate otherwise runs only at intent-delivery moments (onCreate /
        // onNewIntent), but a live PiP window survives MainActivity's
        // auto-lock — expanding it returns here with the holder already
        // locked and no intent delivered. This resume is also the genuine
        // foreground RETURN that folds the shared auto-lock policy, so an
        // intent whose foregrounding lands past the timer is measured HERE,
        // not at its delivery. A resume that lands while still DOCKED in PiP
        // (`isInPictureInPictureMode`) is not a foreground return — the
        // PiP-entry stamp stays pending so the exit/expand fold still
        // measures the absence; consuming it here would let an expand after
        // the timer slip through unchallenged. Warm-process reads replay the
        // in-memory snapshot (no disk IO). Early-return on a fresh redirect
        // so the player lifecycle hook (and its DI graph) never runs while
        // locked.
        // A redirect that already fired (this activity is finishing) must
        // return before ANYTHING below — the parked-payload publish most of
        // all: a payload parked before the redirect would otherwise publish
        // on this finishing pass and initialize/retune the swap while the
        // lock challenge the redirect put up is still showing.
        if (lockGateRedirected) return
        if (redirectToLockGateIfNeeded(evaluateReturnPolicy = !isInPictureInPictureMode)) {
            return
        }
        // A payload parked by a pre-resume onNewIntent publishes only now
        // that a QUALIFYING return has passed the gate — a redirect above
        // returned (and finished) before reaching this line, and a resume
        // that lands while still DOCKED in PiP is not the foreground return
        // (the gate fold above was skipped for the same reason): the payload
        // stays parked so the swap cannot initialize/retune under the lock
        // challenge. The RESUMED-phase PiP exit publishes for the OEM
        // orderings that never resume the activity on expand.
        if (!isInPictureInPictureMode) {
            publishPendingLaunchArgs()
        }
        // Genuine expand: clear the dismiss arm + resume the player lifecycle
        // (PipLifecyclePolicy.onResume).
        val decision = PipLifecyclePolicy.onResume(justExitedPip)
        justExitedPip = decision.justExitedPip
        if (decision.action == PipLifecyclePolicy.Action.Resume) {
            playerLifecycleManager.onActivityResume()
        }
        // Orientation re-assert for the OEMs where onPipModeChanged(false)
        // fired BEFORE the activity reported RESUMED: that callback kept the
        // stash (PipOrientationPolicy.Decision.Keep), so this resume — the
        // first moment the activity is genuinely foreground — applies it.
        // A resume while still in PiP, or with nothing stashed, is a no-op.
        when (val orientationDecision = PipOrientationPolicy.onResumed(
            state = pipOrientationState,
            inPip = isInPictureInPictureMode,
        )) {
            is PipOrientationPolicy.Decision.Restore -> {
                requestedOrientation = orientationLockConstant(orientationDecision.intent)
                pipOrientationState = PipOrientationPolicy.State()
            }
            else -> {}
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
        val armedDismissal = justExitedPip
        val keepAliveAtStop = keepPlayerAliveAfterPipDismiss()
        val decision = PipLifecyclePolicy.onStop(
            inPip = isInPictureInPictureMode,
            screenOffOrLocked = isScreenOffOrLocked(),
            keepPlayerAlive = keepAliveAtStop,
            isFinishing = isFinishing,
            justExitedPip = justExitedPip,
        )
        justExitedPip = decision.justExitedPip
        // The armed stop IS the teardown-dismissal discharge (the expand's
        // resume clears the arm before any stop, so an arm reaching onStop
        // means the PiP window was dismissed): the END of the docked absence —
        // fold the pending stamp before the shell is revealed with no further
        // lifecycle event (see foldPendingAbsenceAtDismissalTeardown). Skipped
        // ONLY for the policy's keep-alive survival arm (keepAlive && !isFinishing
        // → None: a keyguard/screen-off landing between the exit callback and
        // this stop means the session CONTINUES under the shell — background
        // audio — and the absence is NOT over; the stamp stays pending for the
        // next genuine return, exactly like the keep-alive dismissal that never
        // armed — folding there would consume it and strand the shell unlocked
        // past the timer). An already-finishing stop (isFinishing: the finish
        // came from elsewhere, so the policy returned None only to avoid a
        // double finish) still discharges — the activity is dying either way.
        if (armedDismissal && (!keepAliveAtStop || isFinishing)) {
            foldPendingAbsenceAtDismissalTeardown()
        }
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

    /**
     * The user's auto-PiP preference folded with the engine-armed
     * flag — the single gate all three auto-entry paths consume:
     * `onUserLeaveHint`, the top-resumed-loss fallback, and the pre-arm
     * `setAutoEnterEnabled` value in [buildPipParams]. Off means leaving the
     * app backgrounds it normally (the recents-switch behaviour); manual PiP
     * entry via the controls button (`enterPipMode()`) is deliberately
     * ungated. `playbackPreferences` is kept warm while the activity is
     * started (see onCreate), so this reads the persisted toggle at each
     * callback rather than the projection's default.
     */
    private fun shouldAutoEnterPipNow(): Boolean =
        pipController.shouldAutoEnterPip.value &&
            preferenceProjections.playbackPreferences.value.autoEnterPip

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
     * setting is on); a lock/screen-off inside the dismissal transition takes
     * the finish path.
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
                    shouldAutoEnter = shouldAutoEnterPipNow(),
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

        // Saved-state keys for the orientation intent / PiP dock stash
        // (see onSaveInstanceState / restoreOrientationState). The enum's
        // .name travels — stable across releases, parsed defensively.
        private const val KEY_ORIENTATION_INTENT = "player_orientation_intent"
        private const val KEY_PIP_DOCKED = "pip_orientation_docked"
        private const val KEY_PIP_STASHED_INTENT = "pip_orientation_stash"

        // Saved-state keys for the launch payloads (active + parked; extras
        // Bundles built/parsed by PlayerActivityArgs — see the onCreate
        // restore and onSaveInstanceState).
        private const val KEY_ACTIVE_ARGS = "player_active_launch_args"
        private const val KEY_PENDING_ARGS = "player_pending_launch_args"

        // Launch extras live in PlayerActivityArgs — the single build/parse
        // adapter for this activity's intent contract.
    }
}
