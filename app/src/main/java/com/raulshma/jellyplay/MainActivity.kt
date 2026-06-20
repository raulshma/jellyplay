package com.raulshma.jellyplay

import android.Manifest
import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
import android.util.Rational
import androidx.core.view.WindowCompat
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.ui.components.AuthChallengeScreen
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.navigation.JellyPlayApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var playerLifecycleManager: PlayerLifecycleManager

    private val viewModel: MainViewModel by viewModels()

    private var backgroundedAt = 0L
    private var isPinUnlocked = mutableStateOf(false)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Initialize Cast SDK eagerly while the splash screen is still up, so the
        // first composition frame doesn't pay the JNI init cost.
        if (packageManager.hasSystemFeature("com.google.android.gms.cast")) {
            runCatching { com.google.android.gms.cast.framework.CastContext.getSharedInstance(this) }
        }
        splashScreen.setKeepOnScreenCondition { viewModel.isRestoring.value }
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconView = splashScreenView.iconView
            val iconPulse = if (iconView != null) {
                ObjectAnimator.ofPropertyValuesHolder(
                    iconView,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.25f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.25f, 1f),
                ).apply {
                    duration = 650L
                    interpolator = OvershootInterpolator(0.8f)
                    start()
                }
            } else null

            ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f).apply {
                startDelay = 400L
                duration = 500L
                interpolator = DecelerateInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        iconPulse?.cancel()
                        splashScreenView.remove()
                    }
                })
                start()
            }
        }
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

        handleIncomingIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    playerLifecycleManager.shouldAutoEnterPip.collect { shouldAutoEnter ->
                        val params = PictureInPictureParams.Builder()
                            .setAutoEnterEnabled(shouldAutoEnter)
                            .build()
                        setPictureInPictureParams(params)
                    }
                }
            }
        }

        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            var pinError by rememberSaveable { mutableStateOf<String?>(null) }
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
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }

            val isSystemDark = isSystemInDarkTheme()
            val hasLockEnabled by remember {
                derivedStateOf { preferences.pinLockEnabled || preferences.biometricLockEnabled }
            }
            val showLockScreen by remember {
                derivedStateOf { hasLockEnabled && !isPinUnlocked.value }
            }

            val darkTheme by remember {
                derivedStateOf {
                    preferences.synthwaveMode || when (preferences.themeMode) {
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                        ThemeMode.SYSTEM -> isSystemDark
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
                dynamicColor = preferences.dynamicTheming,
                oledMode = preferences.oledMode,
                contrastLevel = preferences.contrastLevel,
                isTv = isTv(),
                performanceMode = preferences.performanceMode,
                accentColorSwatch = preferences.accentColorSwatch,
                colorStyle = preferences.colorStyle,
                synthwaveMode = preferences.synthwaveMode,
                synthwaveAccent = preferences.synthwaveAccent,
                soothingMode = preferences.soothingMode,
                soothingAccent = preferences.soothingAccent,
                monochromeMode = preferences.monochromeMode,
            ) {
                if (showLockScreen) {
                    // Surface the rate-limit lockout to the user when present.
                    // We don't disable the keypad visually here — the click-time
                    // check inside onPinEntered enforces the lockout and surfaces
                    // a "Too many attempts. Try again in X" message. Disabling
                    // the keypad buttons is a future UX polish item.
                    val lockoutState = remember(preferences.pinLockoutUntilEpochMs) {
                        viewModel.preferencesStore.getPinLockoutState()
                    }
                    val now = remember { System.currentTimeMillis() }
                    val lockoutActive = lockoutState.isLockedOut && lockoutState.lockoutUntilEpochMs > now
                    AuthChallengeScreen(
                        title = if (preferences.biometricLockEnabled && preferences.pinHash == null) "Authenticate" else "Enter PIN",
                        subtitle = "Unlock JellyPlay",
                        pinHash = preferences.pinHash,
                        biometricEnabled = preferences.biometricLockEnabled,
                        onPinEntered = { pin ->
                            if (pin.isEmpty()) {
                                isPinUnlocked.value = true
                                pinError = null
                            } else if (preferences.pinHash != null) {
                                // Re-check the lockout at click time: the user
                                // may have triggered it on a previous attempt
                                // since the last composition.
                                val currentLockout = viewModel.preferencesStore.getPinLockoutState()
                                val currentNow = System.currentTimeMillis()
                                if (currentLockout.isLockedOut && currentLockout.lockoutUntilEpochMs > currentNow) {
                                    val remainingMs = currentLockout.lockoutUntilEpochMs - currentNow
                                    pinError = formatLockoutMessage(remainingMs)
                                    return@AuthChallengeScreen
                                }
                                val valid = viewModel.preferencesStore.verifyPin(
                                    pin,
                                    preferences.pinHash,
                                )
                                if (valid) {
                                    isPinUnlocked.value = true
                                    pinError = null
                                    // Reset the failed-attempt counter and clear
                                    // any active lockout, then silently upgrade
                                    // a legacy unsalted-SHA-256 PIN hash to
                                    // PBKDF2 (v2) now that the user has proven
                                    // they know the PIN.
                                    lifecycleScope.launch {
                                        viewModel.preferencesStore.resetPinLockout()
                                        if (viewModel.preferencesStore.pinHashNeedsMigration(preferences.pinHash)) {
                                            viewModel.preferencesStore.upgradePinHashIfLegacy(pin)
                                        }
                                    }
                                } else {
                                    lifecycleScope.launch {
                                        val newState = viewModel.preferencesStore.recordFailedPinAttempt()
                                        pinError = if (newState.isLockedOut) {
                                            formatLockoutMessage(newState.lockoutUntilEpochMs - System.currentTimeMillis())
                                        } else {
                                            "Incorrect PIN"
                                        }
                                    }
                                }
                            }
                        },
                        onErrorClear = { pinError = null },
                        errorMessage = if (lockoutActive) {
                            formatLockoutMessage(lockoutState.lockoutUntilEpochMs - now)
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
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerLifecycleManager.shouldAutoEnterPip.value) {
            enterPipMode()
        }
    }

    private var justExitedPip = false

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
        if (!isInPictureInPictureMode) {
            justExitedPip = true
        }
        playerLifecycleManager.setPipMode(isInPictureInPictureMode)
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
            playerLifecycleManager.notifyPipDismissed()
        }
    }

    fun enterPipMode(aspectRatio: Rational? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return

        val shouldAutoEnter = playerLifecycleManager.shouldAutoEnterPip.value

        val params = PictureInPictureParams.Builder().apply {
            val ratio = aspectRatio ?: Rational(16, 9)
            val clamped = clampAspectRatio(ratio)
            setAspectRatio(clamped)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(shouldAutoEnter)
                setSeamlessResizeEnabled(shouldAutoEnter)
            }
        }.build()

        enterPictureInPictureMode(params)
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
}

/**
 * Formats a PIN-rate-limit lockout duration as a human-readable message.
 * Shows seconds when under a minute, otherwise minutes/hours.
 */
private fun formatLockoutMessage(remainingMs: Long): String {
    if (remainingMs <= 0L) return "Too many attempts. Try again."
    val seconds = (remainingMs + 999L) / 1000L // round up so we never show 0s
    return when {
        seconds < 60 -> "Too many attempts. Try again in ${seconds}s."
        seconds < 3600 -> "Too many attempts. Try again in ${seconds / 60}m."
        else -> "Too many attempts. Try again in ${seconds / 3600}h."
    }
}
