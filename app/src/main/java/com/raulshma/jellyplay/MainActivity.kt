package com.raulshma.jellyplay

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.ui.components.PinLockScreen
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.navigation.JellyPlayApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerLifecycleManager: PlayerLifecycleManager

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.isRestoring.value }
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

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
            var isPinUnlocked by rememberSaveable { mutableStateOf(false) }
            var pinError by rememberSaveable { mutableStateOf<String?>(null) }

            val showPinLock = preferences.pinLockEnabled &&
                preferences.pinHash != null &&
                !isPinUnlocked

            JellyPlayTheme(
                dynamicColor = preferences.dynamicTheming && !preferences.kidsModeEnabled,
                kidsMode = preferences.kidsModeEnabled,
                isTv = isTv(),
            ) {
                if (showPinLock) {
                    PinLockScreen(
                        onPinEntered = { pin ->
                            val valid = viewModel.preferencesStore.verifyPin(
                                pin,
                                preferences.pinHash,
                            )
                            if (valid) {
                                isPinUnlocked = true
                                pinError = null
                            } else {
                                pinError = "Incorrect PIN"
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
