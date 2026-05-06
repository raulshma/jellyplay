package com.raulshma.jellyplay

import android.os.Bundle
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.ui.components.PinLockScreen
import com.raulshma.jellyplay.navigation.JellyPlayApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        val viewModel: MainViewModel by viewModels()
        splashScreen.setKeepOnScreenCondition { viewModel.isRestoring.value }
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
}
