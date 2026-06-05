package com.raulshma.jellyplay

import android.Manifest
import android.animation.ObjectAnimator
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
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
import androidx.compose.runtime.SideEffect
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
        runCatching { com.google.android.gms.cast.framework.CastContext.getSharedInstance(this) }
        splashScreen.setKeepOnScreenCondition { viewModel.isRestoring.value }
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f).apply {
                duration = 400L
                interpolator = DecelerateInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
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

            val hasLockEnabled = preferences.pinLockEnabled || preferences.biometricLockEnabled
            val showLockScreen = hasLockEnabled && !isPinUnlocked.value

            val darkTheme = when (preferences.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val activity = this
            SideEffect {
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
            ) {
                if (showLockScreen) {
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
                                val valid = viewModel.preferencesStore.verifyPin(
                                    pin,
                                    preferences.pinHash,
                                )
                                if (valid) {
                                    isPinUnlocked.value = true
                                    pinError = null
                                } else {
                                    pinError = "Incorrect PIN"
                                }
                            }
                        },
                        onErrorClear = { pinError = null },
                        errorMessage = pinError,
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
