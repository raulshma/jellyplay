package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing

@Composable
fun <T> defaultSpatialSpec() = MaterialTheme.motionScheme.defaultSpatialSpec<T>()

@Composable
fun <T> fastSpatialSpec() = MaterialTheme.motionScheme.fastSpatialSpec<T>()

@Composable
fun <T> slowSpatialSpec() = MaterialTheme.motionScheme.slowSpatialSpec<T>()

@Composable
fun <T> defaultEffectsSpec() = MaterialTheme.motionScheme.defaultEffectsSpec<T>()

@Composable
fun <T> fastEffectsSpec() = MaterialTheme.motionScheme.fastEffectsSpec<T>()

@Composable
fun <T> slowEffectsSpec() = MaterialTheme.motionScheme.slowEffectsSpec<T>()

@Composable
inline fun <T> lessSpringySpec() = MaterialTheme.motionScheme.defaultSpatialSpec<T>()
