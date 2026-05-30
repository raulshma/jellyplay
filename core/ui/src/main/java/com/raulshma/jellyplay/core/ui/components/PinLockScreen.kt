package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.animateColorAsState
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.animation.defaultSpatialSpec
import com.raulshma.jellyplay.core.ui.animation.fastEffectsSpec
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import com.raulshma.jellyplay.core.designsystem.theme.ComponentStyles

@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
fun PinLockScreen(
    title: String = "Enter PIN",
    subtitle: String = "Unlock JellyPlay",
    onPinEntered: (String) -> Unit,
    onErrorClear: () -> Unit = {},
    errorMessage: String? = null,
) {
    var pin by remember { mutableStateOf("") }
    val maxDigits = 4

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = contentPad),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(maxDigits) { index ->
                val filled = index < pin.length
                val interactionSource = remember { MutableInteractionSource() }
                val styleState = rememberUpdatedStyleState(interactionSource)
                
                Box(
                    modifier = Modifier
                        .styleable(
                            styleState,
                            if (filled) ComponentStyles.pinDotFilledStyle else ComponentStyles.pinDotStyle
                        )
                )
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(48.dp))

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "backspace"),
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterHorizontally),
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(modifier = Modifier.size(72.dp))
                        "backspace" -> {
                            val interactionSource = remember { MutableInteractionSource() }
                            val styleState = rememberUpdatedStyleState(interactionSource)

                            Box(
                                modifier = Modifier
                                    .styleable(styleState, ComponentStyles.pinKeyStyle)
                                    .tvFocusable().clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                    ) {
                                        if (pin.isNotEmpty()) {
                                            pin = pin.dropLast(1)
                                            onErrorClear()
                                        }
                                    }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.Backspace,
                                    contentDescription = "Backspace",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        else -> {
                            PinKeyButton(
                                key = key,
                                isPressed = false,
                                onClick = {
                                    if (pin.length < maxDigits) {
                                        pin += key
                                        onErrorClear()
                                        if (pin.length == maxDigits) {
                                            onPinEntered(pin)
                                            pin = ""
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
private fun PinKeyButton(
    key: String,
    isPressed: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource)

    Box(
        modifier = Modifier
            .styleable(styleState, ComponentStyles.pinKeyStyle)
            .tvFocusable().clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
