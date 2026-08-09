package com.raulshma.jellyplay

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.util.Rational
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.playback.PipAction
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.ui.components.AuthChallengeScreen
import com.raulshma.jellyplay.core.ui.components.BlueLightFilterBox
import com.raulshma.jellyplay.core.ui.components.HandModeProvider
import com.raulshma.jellyplay.core.ui.components.colorBlindFilter
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.navigation.JellyPlayApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var playerLifecycleManager: PlayerLifecycleManager
    @Inject lateinit var pipController: PipController

    private val viewModel: MainViewModel by viewModels()

    private var backgroundedAt = 0L
    private var isPinUnlocked = mutableStateOf(false)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Android 17+ blocks local network access (LAN Jellyfin servers + SSDP/DLNA
    // discovery) unless ACCESS_LOCAL_NETWORK is granted. Requested once on cold
    // start so returning users — who never see the Add Server screen — still get
    // prompted. On grant, the self-healing WebSocket/health monitor reconnect on
    // their own. Mirrors the notification permission launcher above.
    private val requestLocalNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Initialize Cast SDK off the main thread. The MediaRouteButton is set
        // up lazily inside setContent's AndroidView.factory and
        // CastButtonFactory.setUpMediaRouteButton resolves CastContext lazily,
        // so deferring the JNI/Play-Services binding here no longer blocks the
        // main thread before setContent (i.e. while the splash is still being
        // evaluated). The original "pay it during the splash" intent is
        // preserved — the splash stays up via setKeepOnScreenCondition until
        // session restore completes, overlapping the Cast init.
        if (packageManager.hasSystemFeature("com.google.android.gms.cast")) {
            lifecycleScope.launch {
                runCatching { com.google.android.gms.cast.framework.CastContext.getSharedInstance(this@MainActivity) }
            }
        }
        splashScreen.setKeepOnScreenCondition { viewModel.isRestoring.value }
        // No custom setOnExitAnimationListener: the system default splash exit
        // is a clean cross-fade to the first composed frame. A manual listener
        // holds the splash view alive across an alpha fade, and because the
        // starting window's background is the splash color, the fade revealed
        // that color through the outgoing splash — producing a visible "splash
        // flashes back in" artifact after Home had already rendered. Letting
        // the system remove the splash the instant the gate releases avoids it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            window.navigationBarColor = android.graphics.Color.parseColor("#66000000") // Translucent black
        }
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Android 17+: request local network access so LAN servers and discovery
        // work for returning users on cold start. The session restore in
        // MainViewModel runs concurrently; on denial, connections to LAN hosts
        // simply fail fast (and recover once the permission is later granted).
        if (com.raulshma.jellyplay.core.network.LocalNetworkAccess.enforced &&
            !com.raulshma.jellyplay.core.network.LocalNetworkAccess.isGranted(this)
        ) {
            requestLocalNetworkPermissionLauncher.launch(
                com.raulshma.jellyplay.core.network.LocalNetworkAccess.PERMISSION
            )
        }

        handleIncomingIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            // One collector drives param application for both the pre-arm path
            // (not yet in PiP: setAutoEnterEnabled + aspect + source rect, no
            // actions) and the in-PiP refresh path (resolution/track swap while
            // already in PiP: actions + aspect). Branching here avoids a second
            // aspect collector that would double-apply params on a track change.
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    combine(
                        pipController.shouldAutoEnterPip,
                        pipController.pipAspectRatio,
                    ) { shouldAutoEnter, aspect -> shouldAutoEnter to aspect }
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
                    pipController.isPlaying.collect {
                        refreshPipActions()
                    }
                }
            }
            // Auto-exit: when the ViewModel signals END/ERROR in PiP, reuse the
            // existing dismiss path (pause + navigate back) so no new exit
            // plumbing is needed.
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

        // Pre-Android 13 per-app language: observe the saved language and apply
        // it on cold start, then recreate when the user changes it at runtime.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            var localeApplied = false
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    viewModel.preferences.collect { prefs ->
                        if (localeApplied) return@collect
                        val current = com.raulshma.jellyplay.core.ui.components.LocaleApplier
                            .currentLanguageTag(this@MainActivity)
                        if (prefs.appLanguage != current) {
                            localeApplied = true
                            com.raulshma.jellyplay.core.ui.components.LocaleApplier
                                .apply(this@MainActivity, prefs.appLanguage)
                            recreate()
                        }
                    }
                }
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    com.raulshma.jellyplay.core.ui.components.LocaleApplier.recreateSignal.collect {
                        recreate()
                    }
                }
            }
        }

        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
                var pinError by rememberSaveable { mutableStateOf<String?>(null) }
                var pinVerifying by rememberSaveable { mutableStateOf(false) }
            val context = androidx.compose.ui.platform.LocalContext.current

            Box(
                modifier = Modifier
                    .size(1.dp)
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        try {
                            androidx.mediarouter.app.MediaRouteButton(ctx).also {
                                it.visibility = View.INVISIBLE
                                com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(ctx, it)
                            }
                        } catch (_: Exception) {
                            View(ctx)
                        }
                    },
                    modifier = Modifier.size(1.dp),
                )
            }

            androidx.compose.runtime.LaunchedEffect(viewModel) {
                viewModel.globalMessage.collect { msg ->
                    viewModel.userMessageBus.info(msg)
                }
            }

            val isSystemDark = isSystemInDarkTheme()
            val hasLockEnabled by remember {
                derivedStateOf { preferences.pinLockEnabled || preferences.biometricLockEnabled }
            }
            val showLockScreen by remember {
                derivedStateOf { hasLockEnabled && !isPinUnlocked.value }
            }

            var themeClockTick by remember { mutableLongStateOf(0L) }
            LaunchedEffect(preferences.themeMode) {
                if (preferences.themeMode == ThemeMode.SCHEDULED) {
                    while (true) {
                        kotlinx.coroutines.delay(60_000L)
                        themeClockTick = System.currentTimeMillis()
                    }
                }
            }

            val darkTheme by remember {
                derivedStateOf {
                    @Suppress("UnusedExpression") themeClockTick // time-based input
                    preferences.synthwaveMode || when (preferences.themeMode) {
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                        ThemeMode.SYSTEM -> isSystemDark
                        ThemeMode.SCHEDULED -> {
                            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                            val start = preferences.scheduledThemeStartHour
                            val end = preferences.scheduledThemeEndHour
                            if (start > end) {
                                hour >= start || hour < end
                            } else {
                                hour >= start && hour < end
                            }
                        }
                    }
                }
            }

            val activity = this
            LaunchedEffect(darkTheme) {
                activity.enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
            }

            JellyPlayTheme(
                darkTheme = darkTheme,
                dynamicColor = preferences.theme.dynamicTheming,
                oledMode = preferences.theme.oledMode,
                contrastLevel = preferences.contrastLevel,
                isTv = isTv(),
                performanceMode = preferences.performanceMode,
                reduceMotion = preferences.reduceMotionEnabled,
                accentColorSwatch = preferences.theme.accentColorSwatch,
                colorStyle = preferences.theme.colorStyle,
                synthwaveMode = preferences.synthwaveMode,
                synthwaveAccent = preferences.synthwaveAccent,
                soothingMode = preferences.soothingMode,
                soothingAccent = preferences.soothingAccent,
                monochromeMode = preferences.monochromeMode,
                appFontScale = preferences.appFontScale,
            ) {
                // Provide the motion/performance flags to the whole UI subtree in one place.
                // JellyPlayTheme already uses these to pick its MotionScheme; providing them as
                // CompositionLocals lets non-scheme animations (infinite loops, bespoke effects)
                // honor both "Performance Mode" and "Reduce Motion" via LocalReducedMotion.
                androidx.compose.runtime.CompositionLocalProvider(
                    com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode provides preferences.performanceMode,
                    com.raulshma.jellyplay.core.ui.components.LocalReduceMotionEnabled provides preferences.reduceMotionEnabled,
                    com.raulshma.jellyplay.core.ui.components.LocalReducedMotion provides
                        (preferences.performanceMode || preferences.reduceMotionEnabled),
                ) {
                HandModeProvider(mode = preferences.handMode) {
                    BlueLightFilterBox(
                        enabled = preferences.blueLightFilterEnabled,
                        strength = preferences.blueLightFilterStrength,
                    ) {
                        Box(
                            modifier = Modifier.colorBlindFilter(preferences.colorBlindMode),
                        ) {
                            if (showLockScreen) {
                                // Surface the rate-limit lockout to the user when present.
                                val context = LocalContext.current
                                val lockoutState = remember(preferences.pinLockoutUntilEpochMs) {
                                    viewModel.pinRateLimiter.getPinLockoutState()
                                }
                                val now = remember { System.currentTimeMillis() }
                                val lockoutActive = lockoutState.isLockedOut && lockoutState.lockoutUntilEpochMs > now
                                AuthChallengeScreen(
                                    title = if (preferences.biometricLockEnabled && preferences.pinHash == null) stringResource(R.string.auth_title_biometric) else stringResource(R.string.auth_title_pin),
                                    subtitle = stringResource(R.string.auth_subtitle),
                                    pinHash = preferences.pinHash,
                                    biometricEnabled = preferences.biometricLockEnabled,
                                    enabled = !lockoutActive && !pinVerifying,
                                    verifying = pinVerifying,
                                    onPinEntered = { pin ->
                                        if (pin.isEmpty()) {
                                            isPinUnlocked.value = true
                                            pinError = null
                                        } else if (preferences.pinHash != null) {
                                            if (pinVerifying) return@AuthChallengeScreen
                                            // Re-check the lockout at click time: the user
                                            // may have triggered it on a previous attempt
                                            // since the last composition. This counter read
                                            // is cheap and stays on the caller thread.
                                            val currentLockout = viewModel.pinRateLimiter.getPinLockoutState()
                                            val currentNow = System.currentTimeMillis()
                                            if (currentLockout.isLockedOut && currentLockout.lockoutUntilEpochMs > currentNow) {
                                                val remainingMs = currentLockout.lockoutUntilEpochMs - currentNow
                                                pinError = formatLockoutMessage(context, remainingMs)
                                                return@AuthChallengeScreen
                                            }
                                            // PBKDF2 verification is deliberately slow, so run
                                            // it off the main thread; the success/failure
                                            // accounting and optional hash upgrade follow it.
                                            pinVerifying = true
                                            lifecycleScope.launch {
                                                val valid = viewModel.securityStore.verifyPinOffMainThread(pin)
                                                if (valid) {
                                                    isPinUnlocked.value = true
                                                    pinError = null
                                                    viewModel.pinRateLimiter.resetPinLockout()
                                                } else {
                                                    val newState = viewModel.pinRateLimiter.recordFailedPinAttempt()
                                                    pinError = if (newState.isLockedOut) {
                                                        formatLockoutMessage(context, newState.lockoutUntilEpochMs - System.currentTimeMillis())
                                                    } else {
                                                        context.getString(R.string.pin_incorrect)
                                                    }
                                                }
                                                pinVerifying = false
                                            }
                                        }
                                    },
                                    onErrorClear = { pinError = null },
                                    errorMessage = if (lockoutActive) {
                                        formatLockoutMessage(context, lockoutState.lockoutUntilEpochMs - now)
                                    } else {
                                        pinError
                                    },
                                )
                            } else {
                                JellyPlayApp(viewModel = viewModel)
                            }
                        }
                    }
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val isShortcutAction = action.startsWith("com.raulshma.jellyplay.action.")
        if (isShortcutAction) {
            viewModel.handleShortcutIntent(intent)
            return
        }
        if (action == Intent.ACTION_VIEW && intent.data != null) {
            viewModel.handleDeepLink(intent)
        } else if (action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                viewModel.handleSharedText(sharedText)
            }
        } else if (action == Intent.ACTION_SEARCH || action == "android.search.action.GLOBAL_SEARCH") {
            val query = intent.getStringExtra(android.app.SearchManager.QUERY)
            if (!query.isNullOrBlank()) {
                viewModel.handleSearchQuery(query)
            }
        } else if (action == Intent.ACTION_ASSIST) {
            // ACTION_ASSIST uses hidden extras (android.intent.extra.ASSIST_INPUT); fall back to
            // SearchManager.QUERY for some launchers.
            val query = intent.getStringExtra("android.intent.extra.ASSIST_INPUT")
                ?: intent.getStringExtra(android.app.SearchManager.QUERY)
            if (!query.isNullOrBlank()) {
                viewModel.handleSearchQuery(query)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipController.shouldAutoEnterPip.value) {
            enterPipMode()
        }
    }

    // Reliability fallback for PiP auto-entry: onUserLeaveHint is not
    // reliably fired on all OEMs/API levels for gesture "slide up to home". When this
    // activity loses the top-resumed position during active playback (i.e. the user
    // navigated away), enter PiP using the same guard predicate. Guarded so it never
    // triggers when paused/stopped.
    override fun onTopResumedActivityChanged(isTopResumed: Boolean) {
        super.onTopResumedActivityChanged(isTopResumed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !isTopResumed &&
            !isInPictureInPictureMode &&
            pipController.shouldAutoEnterPip.value &&
            pipController.isPlaying.value
        ) {
            enterPipMode()
        }
    }

    private var justExitedPip = false

    // ── PiP remote actions ──
    // A BroadcastReceiver registered while in PiP, fed by RemoteAction PendingIntents
    // so the PiP window exposes play/pause, skip ±, and next controls.
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
            // Register the receiver + attach actions on EVERY PiP entry, including
            // auto-entry (home-press while playing) which bypasses enterPipMode()
            // and would otherwise leave the remote actions dead.
            registerPipActionReceiver()
            refreshPipActions()
        } else {
            justExitedPip = true
            unregisterPipActionReceiver()
        }
        pipController.setPipMode(isInPictureInPictureMode)
    }

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode) {
            playerLifecycleManager.onActivityPause()
            backgroundedAt = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        justExitedPip = false
        playerLifecycleManager.onActivityResume()

        if (backgroundedAt > 0L) {
            val prefs = viewModel.preferences.value
            if ((prefs.pinLockEnabled || prefs.biometricLockEnabled) && prefs.autoLockTimerMs > 0L) {
                val elapsed = System.currentTimeMillis() - backgroundedAt
                if (elapsed >= prefs.autoLockTimerMs) {
                    isPinUnlocked.value = false
                }
            }
            backgroundedAt = 0L
        }
    }

    override fun onStop() {
        super.onStop()
        if (justExitedPip) {
            justExitedPip = false
            pipController.notifyPipDismissed()
        }
    }

    fun enterPipMode() {
        if (!isPipCapable()) return

        registerPipActionReceiver()

        val params = buildPipParams(preArm = false, includeActions = true)
        // Wrap the system call: some OEMs/ROMs throw IllegalArgumentException on
        // out-of-range aspect ratios or malformed source rects even after our
        // clamping. Fail open (no entry) rather than crashing — VLC's defense.
        runCatching { enterPictureInPictureMode(params) }
            .onFailure { Log.w(TAG, "PiP enter failed", it) }
    }

    private fun isPipCapable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * Builds [PictureInPictureParams] from the live [PipController] state: the
     * video aspect ratio (from server streams, clamped to the legal range), the
     * source-rect hint (from the surface's window bounds, for a smooth enter
     * animation), seamless resize, auto-enter flag, and remote actions.
     *
     * @param preArm `true` for the pre-arm path (not yet in PiP): sets
     *  `setAutoEnterEnabled`/`setSeamlessResizeEnabled` from the controller so
     *  the system can auto-enter on a home gesture; omits actions (only
     *  rendered once in PiP). `false` for an explicit enter or an in-PiP
     *  refresh, where actions are attached.
     * @param includeActions whether to attach the play/pause/skip/next
     *  RemoteActions. Omitted during pre-arm; attached on enter and refresh.
     */
    private fun buildPipParams(
        preArm: Boolean,
        includeActions: Boolean,
    ): PictureInPictureParams = PictureInPictureParams.Builder().apply {
        val ratio = pipController.pipAspectRatio.value ?: Rational(16, 9)
        setAspectRatio(clampAspectRatio(ratio))
        // Source-rect hint: the video surface's window bounds. Gives the system
        // the crop source for a seamless enter animation (Jellyfin's pattern).
        // Only set when the surface is laid out and within the window; a hint
        // outside the window bounds is ignored or can throw on some ROMs.
        pipController.pipSourceRect?.let { src ->
            if (isValidSourceRect(src)) setSourceRectHint(src)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = if (preArm) pipController.shouldAutoEnterPip.value else false
            setAutoEnterEnabled(autoEnter)
            setSeamlessResizeEnabled(autoEnter)
        }
        if (includeActions) setActions(buildPipActions())
    }.build()

    /**
     * A source-rect hint is valid only when it has non-zero area and is fully
     * within the visible window bounds (off-screen edges can cause the system to
     * drop the hint or throw).
     */
    private fun isValidSourceRect(src: Rect): Boolean {
        if (src.width() <= 0 || src.height() <= 0) return false
        if (src.left < 0 || src.top < 0) return false
        val w = window.decorView.width
        val h = window.decorView.height
        return w <= 0 || h <= 0 || (src.right <= w && src.bottom <= h)
    }

    /**
     * Re-applies the current PiP params via [setPictureInPictureParams]. Used
     * both for pre-arming (when auto-enter/aspect change) and for live updates
     * while in PiP. Guarded against [IllegalArgumentException].
     */
    private fun applyPipParams(includeActions: Boolean) {
        if (!isPipCapable()) return
        val params = buildPipParams(preArm = !isInPictureInPictureMode, includeActions = includeActions)
        runCatching { setPictureInPictureParams(params) }
            .onFailure { Log.w(TAG, "PiP setPictureInPictureParams failed", it) }
    }

    /**
     * Builds the [RemoteAction] list shown in the PiP window. The play/pause icon
     * reflects the current playback state so the toggle stays in sync.
     */
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

    /**
     * Refreshes the PiP action icons (notably the play/pause toggle) when the
     * playback state changes while in PiP. No-op outside PiP.
     */
    fun refreshPipActions() {
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
                    // A stale/unregistered transport silently drops PiP actions.
                    // Log so it's diagnosable instead of dead PIP buttons.
                    Log.w(TAG, "PiP action $action dropped: pipTransport is null")
                } else {
                    transport.handle(action)
                }
                // The play/pause toggle changes the icon — refresh immediately.
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

    private companion object {
        const val TAG = "MainActivity"
        const val PIP_ACTION_BROADCAST = "com.raulshma.jellyplay.PIP_ACTION"
        const val PIP_ACTION_EXTRA = "pip_action_id"
        const val PIP_ACTION_PLAY = 1
        const val PIP_ACTION_PAUSE = 2
        const val PIP_ACTION_SKIP_FORWARD = 3
        const val PIP_ACTION_SKIP_BACK = 4
        const val PIP_ACTION_NEXT = 5
    }
}

/**
 * Formats a PIN-rate-limit lockout duration as a human-readable message.
 * Shows seconds when under a minute, otherwise minutes/hours.
 */
private fun formatLockoutMessage(context: Context, remainingMs: Long): String {
    if (remainingMs <= 0L) return context.getString(R.string.pin_lockout_now)
    val seconds = (remainingMs + 999L) / 1000L // round up so we never show 0s
    return when {
        seconds < 60 -> context.getString(R.string.pin_lockout_seconds, seconds)
        seconds < 3600 -> context.getString(R.string.pin_lockout_minutes, seconds / 60)
        seconds < 86400 -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            if (m == 0L) context.getString(R.string.pin_lockout_hours, h)
            else context.getString(R.string.pin_lockout_hours_minutes, h, m)
        }
        else -> context.getString(R.string.pin_lockout_hours, seconds / 3600)
    }
}
