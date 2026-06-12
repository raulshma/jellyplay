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
