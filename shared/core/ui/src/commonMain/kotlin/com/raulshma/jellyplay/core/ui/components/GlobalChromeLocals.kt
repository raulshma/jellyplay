package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.core.model.BackdropDimLevel
import com.raulshma.jellyplay.core.model.ListDensity
import com.raulshma.jellyplay.core.model.PressEffectStyle

/**
 * The app-wide chrome locals (the "global interface & interaction" appearance
 * rows): one composition local per row, all defaulting to the shipped
 * behavior so a provider that forgets one changes nothing. Provided at the
 * theme root by [JellyPlayPreferenceTheme] from [MainPreferences]; the
 * metered flag is shell-provided (the app shell reads the platform network
 * monitor; desktop counts as never metered).
 */

/** The backdrop-dim multiplier over each backdrop surface's own scrim alpha. */
val LocalBackdropDim: ProvidableCompositionLocal<BackdropDimLevel> =
    compositionLocalOf { BackdropDimLevel.DEFAULT }

/** The confirmation-haptic strength remap consumed by the haptic accessors. */
val LocalHapticsIntensity: ProvidableCompositionLocal<com.raulshma.jellyplay.core.model.HapticsIntensity> =
    compositionLocalOf { com.raulshma.jellyplay.core.model.HapticsIntensity.MEDIUM }

/** The server image-request quality cap in percent, or null for the original. */
val LocalImageQualityCap: ProvidableCompositionLocal<Int?> =
    compositionLocalOf { null }

/** The user's data-saver master (the NETWORK domain's `data_saver_enabled`). */
val LocalImagesDataSaver: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { false }

/**
 * Whether the ACTIVE connection is metered, as a state flow. Shell-provided
 * (the platform network monitor lives in core:data, above this module);
 * defaults to a constant false so desktop — and any shell that doesn't wire
 * the monitor — never throttles.
 */
val LocalNetworkMetered: ProvidableCompositionLocal<StateFlow<Boolean>> =
    compositionLocalOf { MutableStateFlow(false) }

/** The settings/list row density scale. */
val LocalListDensity: ProvidableCompositionLocal<ListDensity> =
    compositionLocalOf { ListDensity.DEFAULT }

/** Flat cards: the card shells drop their elevation/shadow. */
val LocalFlatCards: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { false }

/** The press-feedback style for clickable surfaces. */
val LocalPressEffectStyle: ProvidableCompositionLocal<PressEffectStyle> =
    compositionLocalOf { PressEffectStyle.DEFAULT }

/**
 * Whether imagery should be held back on the current connection: the user's
 * data-saver master ([LocalImagesDataSaver], the network-domain setting the
 * audio bitrate already reads) combined with the shell's metered signal.
 * Both big backdrop surfaces and every [com.raulshma.jellyplay.core.ui.image.MediaImage]
 * call site read this instead of assembling the pair themselves.
 */
@Composable
fun rememberImagesDataSaverActive(): Boolean {
    val saver = LocalImagesDataSaver.current
    val metered by LocalNetworkMetered.current.collectAsState()
    return remember(saver, metered) { saver && metered }
}

/**
 * The [Indication] for the press-effect setting: DEFAULT returns null (keep
 * the theme's Material ripple), SUBTLE the low-alpha tint wash, NONE an
 * effectless factory (press feedback removed entirely — the reduced-motion
 * companion). Null keeps every `clickable(indication = null)`-free call site
 * on LocalIndication's inherited value.
 */
fun pressEffectIndication(style: PressEffectStyle): Indication? = when (style) {
    PressEffectStyle.DEFAULT -> null
    PressEffectStyle.SUBTLE -> SubtleTintIndication
    PressEffectStyle.NONE -> NoIndication
}

/**
 * A minimal press indication: a single low-alpha wash drawn over the pressed
 * node (no ripple, no mask motion). The subtle alternative for users who
 * find the Material ripple loud.
 */
private object SubtleTintIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node =
        SubtleTintNode(interactionSource)

    private class SubtleTintNode(private val interactionSource: InteractionSource) :
        Modifier.Node(), DrawModifierNode {
        private var pressed by mutableStateOf(false)
        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> pressed = true
                        is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (pressed) {
                drawRect(Color.White.copy(alpha = 0.08f))
            }
        }
    }

    override fun equals(other: Any?) = other === this
    override fun hashCode() = javaClass.hashCode()
}

/**
 * The press-effect NONE factory: draws content unchanged, no press state.
 */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node =
        object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() {
                drawContent()
            }
        }

    override fun equals(other: Any?) = other === this
    override fun hashCode() = javaClass.hashCode()
}

/**
 * The pass-through overscroll effect (the overscroll-NONE mode): every
 * delta/fling is simply consumed, no stretch or glow is ever applied.
 */
object DisabledOverscrollEffect : OverscrollEffect {
    override fun applyToScroll(
        scrollDelta: Offset,
        source: NestedScrollSource,
        consume: (Offset) -> Offset,
    ): Offset = consume(scrollDelta)

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        performFling(velocity)
    }

    override val isInProgress: Boolean get() = false

    override val effectModifier: Modifier get() = Modifier
}

/** The [OverscrollEffect] factory the overscroll-NONE mode provides app-wide. */
object DisabledOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = DisabledOverscrollEffect

    override fun equals(other: Any?) = other === this
    override fun hashCode() = javaClass.hashCode()
}
