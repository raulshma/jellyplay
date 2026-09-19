package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * web actual: the JetBrains navigation3-ui fork (substituted for google's
 * artifact on wasmJs) declares the same LocalNavAnimatedContentScope. See the
 * expect declaration in SharedTransition.kt for why the indirection exists.
 */
@Composable
actual fun currentNavAnimatedContentScope(): AnimatedContentScope =
    LocalNavAnimatedContentScope.current
