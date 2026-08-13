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
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.ui.components.AuthChallengeScreen
import com.raulshma.jellyplay.core.ui.components.BlueLightFilterBox
import com.raulshma.jellyplay.core.ui.components.HandModeProvider
import com.raulshma.jellyplay.core.ui.components.rememberPreferenceDarkTheme
import com.raulshma.jellyplay.core.ui.components.colorBlindFilter
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.navigation.JellyPlayApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

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
        // Clear any orientation lock a previous in-nav player (e.g. Live TV) may
        // have written onto this singleTask activity before the process was
        // killed. Because configChanges includes orientation, MainActivity is
        // never recreated, so without this reset a restored task can boot stuck
        // in landscape with no control to unlock it. UNSPECIFIED follows the
        // system auto-rotate setting, matching the fresh-install default.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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

            val hasLockEnabled by remember {
                derivedStateOf { preferences.pinLockEnabled || preferences.biometricLockEnabled }
            }
            val showLockScreen by remember {
                derivedStateOf { hasLockEnabled && !isPinUnlocked.value }
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

    override fun onPause() {
        super.onPause()
        // The video engine is hosted by PlayerActivity now. MainActivity no longer
        // drives the shared PlayerLifecycleManager: doing so contaminated
        // PlayerActivity's engine on app-background / PiP-expand / lock-unlock
        // (the shared singleton's activeCallbacks is PlayerActivity's engine, so
        // a pause here reached across Activities). LiveTV does not use it either
        // (it manages its own engine lifecycle), so there is nothing to pause.
        // Only record backgrounding for the PIN auto-lock timer.
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()

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
