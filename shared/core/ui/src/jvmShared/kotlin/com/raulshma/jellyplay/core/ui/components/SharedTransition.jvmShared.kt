package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * nonWeb actual (android/jvm): reads the androidx navigation3-ui composition
 * local directly. See the expect declaration in SharedTransition.kt for why
 * the indirection exists.
 */
@Composable
actual fun currentNavAnimatedContentScope(): AnimatedContentScope =
    LocalNavAnimatedContentScope.current
