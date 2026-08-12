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
import android.graphics.Rect
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
import com.raulshma.jellyplay.core.data.playback.PipAction
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.ui.components.JellyPlayPreferenceTheme
import com.raulshma.jellyplay.core.ui.components.rememberPreferenceDarkTheme
import com.raulshma.jellyplay.feature.player.video.VideoPlayerScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dedicated host Activity for fullscreen video playback.
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
 * on END/ERROR, source-rect hint, aspect-ratio clamp.
 *
 * The engine is created fresh by [VideoPlayerScreen]'s `VideoPlayerViewModel`
 * (now scoped to this Activity via Hilt) — there is no cross-Activity engine
 * handoff in the normal open-play-PiP flow.
 */
@AndroidEntryPoint
class PlayerActivity : FragmentActivity() {

    @Inject
    lateinit var playerLifecycleManager: PlayerLifecycleManager

    @Inject
    lateinit var pipController: PipController

    @Inject
    lateinit var preferenceProjections: PreferenceProjections

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge so the player draws under the system bars; VideoPlayerScreen
        // owns immersive show/hide and inset handling itself (it was designed for
        // MainActivity's edge-to-edge window). Without this the surface + controls
        // are inset by the status/nav bars in fullscreen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: run {
            Log.w(TAG, "No itemId extra; finishing.")
            finish()
            return
        }
        val mediaSourceId = intent.getStringExtra(EXTRA_MEDIA_SOURCE_ID)
        val startPositionTicks = intent.getLongExtra(EXTRA_START_POSITION_TICKS, 0L)
        val subtitleStreamIndex = intent.getIntExtra(EXTRA_SUBTITLE_STREAM_INDEX, -1)
            .takeIf { it >= 0 }
        val audioStreamIndex = intent.getIntExtra(EXTRA_AUDIO_STREAM_INDEX, -1)
            .takeIf { it >= 0 }

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
            // self-contained.
            var showSubtitleTester by remember { mutableStateOf(false) }
            JellyPlayPreferenceTheme(
                preferences = preferences,
                darkTheme = darkTheme,
            ) {
                Box(Modifier.fillMaxSize()) {
                    VideoPlayerScreen(
                        itemId = itemId,
                        mediaSourceId = mediaSourceId,
                        startPositionTicks = startPositionTicks,
                        subtitleStreamIndex = subtitleStreamIndex,
                        audioStreamIndex = audioStreamIndex,
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

    override fun onDestroy() {
        super.onDestroy()
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
        // gates auto-entry on `!isControlsLocked` so swiping home while
        // the lock overlay is up doesn't yank the user into PiP.
        if (pipController.shouldAutoEnterPip.value && !pipController.isControlsLocked) {
            enterPipMode()
        }
    }

    // Reliability fallback for PiP auto-entry: onUserLeaveHint is not reliably
    // fired on all OEMs/API levels for gesture "slide up to home". When this
    // activity loses the top-resumed position during active playback, enter PiP
    // using the same guard predicate.
    override fun onTopResumedActivityChanged(isTopResumed: Boolean) {
        super.onTopResumedActivityChanged(isTopResumed)
        if (isTopResumed && justExitedPip) {
            justExitedPip = false
            playerLifecycleManager.onActivityResume()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !isTopResumed &&
            !isInPictureInPictureMode &&
            pipController.shouldAutoEnterPip.value &&
            pipController.isPlaying.value &&
            !pipController.isControlsLocked
        ) {
            enterPipMode()
        }
    }

    private var justExitedPip = false

    // Saved window brightness so it can be restored on PiP exit. resets.
    // brightness to the system auto value while in PiP (the in-app brightness
    // gesture is irrelevant in the floating window) and restores it on expand.
    private var savedBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

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
            unregisterPipActionReceiver()
            window.attributes = window.attributes.apply {
                screenBrightness = savedBrightness
            }
            // Leaving PiP fires for BOTH expand-to-fullscreen and dismiss. Do NOT
            // pause here: this callback runs BEFORE onResume on expand, so pausing
            // would freeze playback when the user expands — onResume's resume relies
            // on wasPlayingBeforeActivityPause, which is stale because entering PiP
            // skipped onActivityPause, so it cannot reliably resume. The engine is
            // never paused by us while in PiP, so on expand it just keeps playing.
            // The dismiss case is handled separately: onPause (engine pause unless
            // background audio) + onStop (justExitedPip → notifyPipDismissed). Arm
            // the flag here only so onStop can tell dismiss apart from a plain
            // minimise while still in PiP.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                justExitedPip = true
            }
        }
        pipController.setPipMode(isInPictureInPictureMode)
    }

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode || isScreenOffOrLocked()) {
            playerLifecycleManager.onActivityPause()
        }
    }

    override fun onResume() {
        super.onResume()
        justExitedPip = false
        playerLifecycleManager.onActivityResume()
    }

    override fun onStop() {
        super.onStop()
        if (justExitedPip) {
            // PiP was dismissed (swiped away). If background audio is OFF, close
            // the player via the dismiss path; if ON, leave the engine running.
            justExitedPip = false
            if (!playerLifecycleManager.isBackgroundAudioEnabled) {
                pipController.notifyPipDismissed()
            }
        } else if (isInPictureInPictureMode) {
            // Distinguish screen-lock (pause so audio doesn't leak with bg audio
            // OFF) from app-minimise (keep playing). onStop
            // is the right hook: during PiP the activity is already PAUSED, so
            // onPause can't reliably see the screen-off state; by onStop the
            // keyguard / non-interactive flags have settled. onActivityPause is
            // itself a no-op when backgroundVideoAudioEnabled is ON.
            if (isScreenOffOrLocked()) {
                playerLifecycleManager.onActivityPause()
            }
            // Else: plain minimise while in PiP — intentionally keep playing.
        }
    }

    fun enterPipMode(): Boolean {
        if (!isPipCapable()) return false

        registerPipActionReceiver()

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

    private fun buildPipParams(
        preArm: Boolean,
        includeActions: Boolean,
    ): PictureInPictureParams = PictureInPictureParams.Builder().apply {
        val ratio = pipController.pipAspectRatio.value ?: Rational(16, 9)
        setAspectRatio(clampAspectRatio(ratio))
        pipController.pipSourceRect?.let { src ->
            if (isValidSourceRect(src)) setSourceRectHint(src)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = if (preArm && !isScreenOffOrLocked()) {
                pipController.shouldAutoEnterPip.value && pipController.isPlaying.value
            } else false
            setAutoEnterEnabled(autoEnter)
            setSeamlessResizeEnabled(autoEnter)
        }
        if (includeActions) setActions(buildPipActions())
    }.build()

    private fun isValidSourceRect(src: Rect): Boolean {
        if (src.width() <= 0 || src.height() <= 0) return false
        if (src.left < 0 || src.top < 0) return false
        val w = window.decorView.width
        val h = window.decorView.height
        return w <= 0 || h <= 0 || (src.right <= w && src.bottom <= h)
    }

    private fun applyPipParams(includeActions: Boolean) {
        if (!isPipCapable()) return
        val params = buildPipParams(preArm = !isInPictureInPictureMode, includeActions = includeActions)
        runCatching { setPictureInPictureParams(params) }
            .onFailure { Log.w(TAG, "PiP setPictureInPictureParams failed", it) }
    }

    private fun buildPipActions(): List<RemoteAction> {
        val isPlaying = pipController.isPlaying.value
        val hasNext = pipController.pipHasNext
        val actions = mutableListOf<RemoteAction>()

        actions += pipRemoteAction(
            id = PIP_ACTION_SKIP_BACK,
            icon = android.R.drawable.ic_media_rew,
            title = getString(R.string.pip_rewind),
        )
        actions += pipRemoteAction(
            id = if (isPlaying) PIP_ACTION_PAUSE else PIP_ACTION_PLAY,
            icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            title = if (isPlaying) getString(R.string.media_pause) else getString(R.string.media_play),
        )
        actions += pipRemoteAction(
            id = PIP_ACTION_SKIP_FORWARD,
            icon = android.R.drawable.ic_media_ff,
            title = getString(R.string.pip_forward),
        )
        if (hasNext) {
            actions += pipRemoteAction(
                id = PIP_ACTION_NEXT,
                icon = android.R.drawable.ic_media_next,
                title = getString(R.string.pip_next),
            )
        }
        return actions
    }

    private fun pipRemoteAction(id: Int, icon: Int, title: String): RemoteAction {
        val intent = Intent(PIP_ACTION_BROADCAST).putExtra(PIP_ACTION_EXTRA, id)
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
                if (intent?.action != PIP_ACTION_BROADCAST) return
                val id = intent.getIntExtra(PIP_ACTION_EXTRA, -1)
                val action = when (id) {
                    PIP_ACTION_PLAY -> PipAction.PLAY
                    PIP_ACTION_PAUSE -> PipAction.PAUSE
                    PIP_ACTION_SKIP_FORWARD -> PipAction.SKIP_FORWARD
                    PIP_ACTION_SKIP_BACK -> PipAction.SKIP_BACKWARD
                    PIP_ACTION_NEXT -> PipAction.NEXT
                    else -> return
                }
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
            IntentFilter(PIP_ACTION_BROADCAST),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        pipActionReceiver = receiver
    }

    private fun unregisterPipActionReceiver() {
        pipActionReceiver?.let { runCatching { unregisterReceiver(it) } }
        pipActionReceiver = null
    }

    private fun clampAspectRatio(ratio: Rational): Rational {
        val min = Rational(100, 239)
        val max = Rational(239, 100)
        return when {
            ratio < min -> min
            ratio > max -> max
            else -> ratio
        }
    }

    companion object {
        const val TAG = "PlayerActivity"
        const val PIP_ACTION_BROADCAST = "com.raulshma.jellyplay.PIP_ACTION"
        const val PIP_ACTION_EXTRA = "pip_action_id"
        const val PIP_ACTION_PLAY = 1
        const val PIP_ACTION_PAUSE = 2
        const val PIP_ACTION_SKIP_FORWARD = 3
        const val PIP_ACTION_SKIP_BACK = 4
        const val PIP_ACTION_NEXT = 5

        const val EXTRA_ITEM_ID = "player_item_id"
        const val EXTRA_MEDIA_SOURCE_ID = "player_media_source_id"
        const val EXTRA_START_POSITION_TICKS = "player_start_position_ticks"
        const val EXTRA_SUBTITLE_STREAM_INDEX = "player_subtitle_stream_index"
        const val EXTRA_AUDIO_STREAM_INDEX = "player_audio_stream_index"
    }
}
