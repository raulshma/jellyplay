package com.raulshma.jellyplay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
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
import androidx.core.view.WindowCompat
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.cast.withCastDiskReadsPermitted
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.ui.components.AuthChallengeScreen
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.ui.util.LocalNetworkAccess
import com.raulshma.jellyplay.core.ui.components.JellyPlayPreferenceTheme
import com.raulshma.jellyplay.core.ui.components.rememberPreferenceDarkTheme
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.di.KoinViewModelFactory
import com.raulshma.jellyplay.navigation.JellyPlayApp
import com.raulshma.jellyplay.shell.AppLockRedirect
import com.raulshma.jellyplay.shell.AppLockState
import com.raulshma.jellyplay.shell.PinGateController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels { KoinViewModelFactory }

    // Cross-cutting shell infrastructure, resolved from the Koin container
    // (wave 8B — Hilt removal) instead of re-exported through MainViewModel —
    // the ViewModel exposes only the signals it owns plus the coordinator
    // seam. Memoizing lazies preserve the old deferred field-inject timing.
    private val userMessageBus: UserMessageBus by lazy { KoinPlatform.getKoin()!!.get() }
    private val pinRateLimiter: PinRateLimiter by lazy { KoinPlatform.getKoin()!!.get() }
    private val securityStore: SecurityStore by lazy { KoinPlatform.getKoin()!!.get() }
    private val networkMonitor: NetworkMonitor by lazy { KoinPlatform.getKoin()!!.get() }
    private val remoteNavigationBridge: RemoteNavigationBridge by lazy { KoinPlatform.getKoin()!!.get() }
    private val remoteControlReceiver: RemoteControlReceiver by lazy { KoinPlatform.getKoin()!!.get() }
    // Lazy deferral is load-bearing: the playback engine (AudioPlaybackManager
    // and its 14-dep graph) stays unbuilt for auth/onboarding-only sessions —
    // resolved only inside ShellInfra's authenticated branch (JellyPlayApp).
    private val audioPlaybackManagerLazy: kotlin.Lazy<AudioPlaybackManager> =
        lazy { KoinPlatform.getKoin()!!.get() }

    // App-scoped lock flag (wave 20E): hoisted off the former compose-local
    // `isPinUnlocked` mutableStateOf so PlayerActivity's redirect check (the
    // media-notification class-name-PendingIntent bypass fix) reads the SAME
    // flag this gate renders. Same resolution pattern as the shell infra
    // above; same default (locked) and the same call sites flip it — this is
    // a state-home move, not a behavior change (one deliberate delta: the
    // flag now survives this activity's recreate() — pre-Android-13 language
    // change — instead of re-locking mid-session; see AppLockState KDoc).
    private val appLockState: AppLockState by lazy { KoinPlatform.getKoin()!!.get() }

    // The PIN-attempt command fold (click-time lockout re-check, off-main
    // verify, failure/success accounting) — composed here from the same Koin
    // singletons the former inline `onPinEntered` body reached directly.
    private val pinGateController: PinGateController by lazy {
        PinGateController(
            rateLimiter = pinRateLimiter,
            appLockState = appLockState,
            verifyPin = { pin -> securityStore.verifyPinOffMainThread(pin) },
        )
    }

    // Flipped by the cast-init coroutine kicked off in onCreate just before
    // setContent (STA-6): whether the one-time CastContext initialization
    // succeeded. Held as plain mutableStateOf — NOT remember-cached at the
    // read site — so the hidden media-route button in setContent composes the
    // moment it flips true (a frame or two after the first composition), and
    // its setUpMediaRouteButton resolves the cached CastContext instead of
    // re-triggering the Dynamite dex load.
    private var castPreinitialized by mutableStateOf(false)

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
        // Clear any orientation lock a previous in-nav player (e.g. Live TV) may
        // have written onto this singleTask activity before the process was
        // killed. Because configChanges includes orientation, MainActivity is
        // never recreated, so without this reset a restored task can boot stuck
        // in landscape with no control to unlock it. UNSPECIFIED follows the
        // system auto-rotate setting, matching the fresh-install default.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        // The one-time CastContext initialization (Dynamite dex load +
        // hasSystemFeature binder call) used to run synchronously here, before
        // setContent, putting both straight into TTID — the splash cannot
        // release until onCreate + the first composition finish. It now runs
        // in the coroutine kicked off just before setContent below (STA-6);
        // every later getSharedInstance caller (the hidden route button
        // there, CastManager, GoogleCastStrategy) still hits the cached
        // singleton.
        splashScreen.setKeepOnScreenCondition { viewModel.sessionCoordinator.isRestoring.value }
        // No custom setOnExitAnimationListener: the system default splash exit
        // is a clean cross-fade to the first composed frame. A manual listener
        // holds the splash view alive across an alpha fade, and because the
        // starting window's background is the splash color, the fade revealed
        // that color through the outgoing splash — producing a visible "splash
        // flashes back in" artifact after Home had already rendered. Letting
        // the system remove the splash the instant the gate releases avoids it.
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
        if (LocalNetworkAccess.enforced &&
            !LocalNetworkAccess.isGranted(this)
        ) {
            requestLocalNetworkPermissionLauncher.launch(
                LocalNetworkAccess.PERMISSION
            )
        }

        handleIncomingIntent(intent)

        // Bundled once here so the shell host's five cross-cutting services
        // travel to JellyPlayApp → MainContent as one value; the
        // AudioPlaybackManager stays lazy inside it (resolved only in the
        // authenticated branch).
        val shellInfra = com.raulshma.jellyplay.shell.ShellInfra(
            userMessageBus = userMessageBus,
            networkStatus = networkMonitor.networkStatus,
            audioPlaybackManagerLazy = audioPlaybackManagerLazy,
            remoteNavigationBridge = remoteNavigationBridge,
            remoteControlReceiver = remoteControlReceiver,
        )

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

        // STA-6: the CastContext init, kicked off here so it runs CONCURRENTLY
        // with setContent instead of serially before it — onCreate (and thus
        // TTID) no longer pays the Dynamite dex load + hasSystemFeature binder
        // call. Still on Main (getSharedInstance is main-thread-only, the API
        // has no off-thread variant), but on Dispatchers.Main — NOT
        // Main.immediate, which would execute this body inline inside onCreate
        // itself (there are no suspension points before the CastContext call)
        // and reproduce today's serial cost — the posted task interleaves with
        // the first composed frames, and castPreinitialized flips the hidden
        // 1dp route button in one or two frames. The dex reads stay wrapped in
        // withCastDiskReadsPermitted: the StrictMode permit is thread-scoped
        // and the coroutine still runs on Main, so debug builds keep
        // permitting exactly those third-party reads (release installs no
        // policy either way).
        lifecycleScope.launch(Dispatchers.Main) {
            castPreinitialized = packageManager.hasSystemFeature("com.google.android.gms.cast") &&
                runCatchingRethrowingCancellation {
                    withCastDiskReadsPermitted {
                        com.google.android.gms.cast.framework.CastContext.getSharedInstance(this@MainActivity)
                    }
                }.isSuccess
        }
        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            // The unlocked flag lives in the app-scoped holder (wave 20E) —
            // collected here so the gate below recomposes exactly like the
            // former compose-local isPinUnlocked state did.
            val pinUnlocked by appLockState.unlocked.collectAsStateWithLifecycle()
                var pinError by rememberSaveable { mutableStateOf<String?>(null) }
                var pinVerifying by rememberSaveable { mutableStateOf(false) }
            val context = androidx.compose.ui.platform.LocalContext.current

            // Hidden Cast media-route button, composed only once the onCreate
            // cast-init coroutine (kicked off just before setContent, STA-6)
            // reports success — so setUpMediaRouteButton resolves the cached
            // singleton and does no dex I/O. Reading castPreinitialized
            // directly (no remember {}) subscribes this call site, so the
            // button appears on the recomposition that lands the flip, a
            // frame or two after the first composition. When Cast is
            // unavailable (or not yet initialized) the branch is skipped
            // outright, matching the old fallback plain View (nothing
            // references the invisible button).
            if (castPreinitialized) {
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
            }

            val hasLockEnabled by remember {
                derivedStateOf {
                    AppLockRedirect.isGateConfigured(
                        pinLockEnabled = preferences.pinLockEnabled,
                        biometricLockEnabled = preferences.biometricLockEnabled,
                    )
                }
            }
            val showLockScreen by remember {
                derivedStateOf { hasLockEnabled && !pinUnlocked }
            }

            // Preference-driven dark-theme derivation (DARK/LIGHT/SYSTEM/SCHEDULED
            // + synthwave override) is shared with PlayerActivity via
            // rememberPreferenceDarkTheme so both hosts flip theme identically.
            val darkTheme = rememberPreferenceDarkTheme(preferences)

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

            JellyPlayPreferenceTheme(
                preferences = preferences,
                darkTheme = darkTheme,
                isTv = isTv(),
            ) {
                if (showLockScreen) {
                    // Surface the rate-limit lockout to the user when present.
                    val context = LocalContext.current
                    val lockoutState = remember(preferences.pinLockoutUntilEpochMs) {
                        pinRateLimiter.getPinLockoutState()
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
                            // Both paths fold through the controller — it owns
                            // the click-time lockout re-check and the
                            // failure/success accounting.
                            if (pin.isEmpty()) {
                                // The empty-PIN shortcut shows no spinner (the old
                                // inline path unlocked synchronously);
                                // lifecycleScope's Main.immediate dispatch runs it
                                // in this frame.
                                lifecycleScope.launch {
                                    pinGateController.submit(pin, onUnlocked = { pinError = null })
                                }
                            } else if (preferences.pinHash != null && !pinVerifying) {
                                pinVerifying = true
                                lifecycleScope.launch {
                                    when (val outcome = pinGateController.submit(pin, onUnlocked = { pinError = null })) {
                                        PinGateController.PinSubmitOutcome.Unlocked -> Unit
                                        PinGateController.PinSubmitOutcome.Incorrect ->
                                            pinError = context.getString(R.string.pin_incorrect)
                                        is PinGateController.PinSubmitOutcome.LockedOut ->
                                            pinError = PinGateController
                                                .lockoutMessage(outcome.remainingMs)
                                                .resolve(context)
                                    }
                                    pinVerifying = false
                                }
                            }
                        },
                        onErrorClear = { pinError = null },
                        errorMessage = if (lockoutActive) {
                            PinGateController.lockoutMessage(lockoutState.lockoutUntilEpochMs - now)
                                .resolve(context)
                        } else {
                            pinError
                        },
                    )
                } else {
                    JellyPlayApp(
                        viewModel = viewModel,
                        infra = shellInfra,
                    )
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

    override fun onPause() {
        super.onPause()
        // The video engine is hosted by PlayerActivity now. MainActivity no longer
        // drives the shared PlayerLifecycleManager: doing so contaminated
        // PlayerActivity's engine on app-background / PiP-expand / lock-unlock
        // (the shared singleton's activeCallbacks is PlayerActivity's engine, so
        // a pause here reached across Activities). LiveTV does not use it either
        // (it manages its own engine lifecycle), so there is nothing to pause.
        // Only record backgrounding for the PIN auto-lock timer — the stamp
        // itself now lives in AppLockState (the lock family), not on this field.
        appLockState.onBackgrounded(System.currentTimeMillis())
    }

    override fun onResume() {
        super.onResume()

        // The auto-lock-on-resume policy (gate configured + timer elapsed while
        // backgrounded → relock) is folded into AppLockState.onResumed via
        // AppLockRedirect.shouldRelock; this adapter only feeds it the same
        // signals it always read at this point (current preferences + the
        // resume-moment wall clock). onResumed consumes the background stamp
        // unconditionally, exactly like the former `backgroundedAt = 0L` did.
        val prefs = viewModel.preferences.value
        appLockState.onResumed(
            nowMs = System.currentTimeMillis(),
            gateConfigured = AppLockRedirect.isGateConfigured(
                pinLockEnabled = prefs.pinLockEnabled,
                biometricLockEnabled = prefs.biometricLockEnabled,
            ),
            autoLockTimerMs = prefs.autoLockTimerMs,
        )
    }

    override fun onStop() {
        super.onStop()
        // No engine lifecycle here. The video engine lives in PlayerActivity
        // (which owns its own PiP + pause/resume handling); LiveTV manages
        // itself. MainActivity can no longer enter PiP, so the former PiP-dismiss
        // branches were dead and are removed along with the cross-Activity
        // playerLifecycleManager contamination.
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

/**
 * String-side twin of [PinGateController.lockoutMessage] — resolves the pure
 * fold's buckets against the app resources (same `pin_lockout_*` strings the
 * former in-activity formatter produced).
 */
private fun PinGateController.LockoutMessage.resolve(context: Context): String = when (this) {
    is PinGateController.LockoutMessage.Now -> context.getString(R.string.pin_lockout_now)
    is PinGateController.LockoutMessage.Seconds -> context.getString(R.string.pin_lockout_seconds, seconds)
    is PinGateController.LockoutMessage.Minutes -> context.getString(R.string.pin_lockout_minutes, minutes)
    is PinGateController.LockoutMessage.Hours -> context.getString(R.string.pin_lockout_hours, hours)
    is PinGateController.LockoutMessage.HoursMinutes -> context.getString(R.string.pin_lockout_hours_minutes, hours, minutes)
}
