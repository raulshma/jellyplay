package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * A customized Material 3 linear wavy progress indicator that globally hides the background track.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JellyPlayLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.linearIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.linearTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
    stopSize: Dp = WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize,
    amplitude: (progress: Float) -> Float = WavyProgressIndicatorDefaults.indicatorAmplitude,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = wavelength,
) {
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = Color.Transparent,
        stroke = stroke,
        trackStroke = trackStroke,
        gapSize = gapSize,
        stopSize = stopSize,
        amplitude = amplitude,
        wavelength = wavelength,
        waveSpeed = waveSpeed,
    )
}

/**
 * Indeterminate version of the customized linear wavy progress indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JellyPlayLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.linearIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.linearTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
    amplitude: Float = 1f,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearIndeterminateWavelength,
    waveSpeed: Dp = wavelength,
) {
    LinearWavyProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = Color.Transparent,
        stroke = stroke,
        trackStroke = trackStroke,
        gapSize = gapSize,
        amplitude = amplitude,
        wavelength = wavelength,
        waveSpeed = waveSpeed,
    )
}

/**
 * A customized Material 3 circular wavy progress indicator that globally hides the background track.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JellyPlayCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.circularIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.circularTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
    amplitude: (progress: Float) -> Float = WavyProgressIndicatorDefaults.indicatorAmplitude,
    wavelength: Dp = WavyProgressIndicatorDefaults.CircularWavelength,
    waveSpeed: Dp = wavelength,
) {
    CircularWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = Color.Transparent,
        stroke = stroke,
        trackStroke = trackStroke,
        gapSize = gapSize,
        amplitude = amplitude,
        wavelength = wavelength,
        waveSpeed = waveSpeed,
    )
}

/**
 * Indeterminate version of the customized circular wavy progress indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JellyPlayCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.circularIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.circularTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
    amplitude: Float = 1f,
    wavelength: Dp = WavyProgressIndicatorDefaults.CircularWavelength,
    waveSpeed: Dp = wavelength,
) {
    CircularWavyProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = Color.Transparent,
        stroke = stroke,
        trackStroke = trackStroke,
        gapSize = gapSize,
        amplitude = amplitude,
        wavelength = wavelength,
        waveSpeed = waveSpeed,
    )
}

/**
 * A customized Material 3 morphing loading indicator that has no background container.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JellyPlayLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LoadingIndicatorDefaults.indicatorColor,
) {
    LoadingIndicator(
        modifier = modifier,
        color = color,
    )
}
