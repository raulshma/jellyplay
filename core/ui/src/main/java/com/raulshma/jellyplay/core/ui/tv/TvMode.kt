package com.raulshma.jellyplay.core.ui.tv

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

fun Context.isTv(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)

@Deprecated(
    message = "Use LocalTvMode.current instead of isTvDevice()",
    replaceWith = ReplaceWith("LocalTvMode.current"),
    level = DeprecationLevel.WARNING,
)
@Composable
fun isTvDevice(): Boolean = LocalContext.current.isTv()

val LocalTvMode = compositionLocalOf { false }

val LocalTvTypography = compositionLocalOf<androidx.compose.material3.Typography?> { null }

/**
 * Expands the TV navigation drawer from within screen content. Provided by the
 * TV app scaffold ([com.raulshma.jellyplay.navigation.TvNavigationDrawer]) around its
 * content slot; a no-op default everywhere else (including phone).
 *
 * Screens attach this to leftward focus exits at their left edge so D-pad Left
 * reliably expands the drawer even when the geometric focus search comes back
 * without a target (e.g. the selected rail entry is recycled out of the lazy
 * drawer column and can't take focus).
 */
val LocalTvDrawerOpener = compositionLocalOf<() -> Unit> { {} }

@Composable
fun TvScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (topBar != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                topBar()
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        } else {
            content()
        }
    }
}
