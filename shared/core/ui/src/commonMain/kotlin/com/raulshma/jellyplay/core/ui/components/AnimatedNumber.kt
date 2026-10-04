package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** animation-core ships no Long converter; exact to 2^24 — counter territory. */
private val LongVectorConverter: TwoWayConverter<Long, AnimationVector1D> =
    TwoWayConverter(
        convertToVector = { AnimationVector1D(it.toFloat()) },
        convertFromVector = { it.value.toLong() },
    )

/**
 * Numeric text that counts toward its target with the theme's default effects
 * spec — the [com.raulshma.jellyplay.core.designsystem.theme.ReducedMotionScheme]
 * resolves that spec to a snap, so reduced-motion users see the final value
 * immediately. Use for dashboard/statistics counters, not for high-frequency
 * values (progress bytes update fast enough on their own).
 */
@Composable
fun AnimatedNumber(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = Color.Unspecified,
    formatter: (Long) -> String = { it.toString() },
) {
    // No animateLongAsState in animation-core; ride a local Long converter.
    val animated by animateValueAsState(
        targetValue = value,
        typeConverter = LongVectorConverter,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "animatedNumberLong",
    )
    Text(
        text = formatter(animated),
        style = style,
        color = color,
        modifier = modifier,
    )
}
