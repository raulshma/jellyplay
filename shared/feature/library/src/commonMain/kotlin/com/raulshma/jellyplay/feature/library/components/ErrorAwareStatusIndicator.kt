package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ErrorAwareStatusIndicator(
    status: com.raulshma.jellyplay.core.ui.components.HeaderStatus,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val isError = status is com.raulshma.jellyplay.core.ui.components.HeaderStatus.Error
    val tooltipState = rememberTooltipState(isPersistent = false)
    val scope = rememberCoroutineScope()

    if (isError && errorMessage != null) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = ShapeCache.smooth12,
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            state = tooltipState,
            enableUserInput = true,
            modifier = modifier,
        ) {
            com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                status = status,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { scope.launch { tooltipState.show() } },
                ),
            )
        }
    } else {
        com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
            status = status,
            modifier = modifier,
        )
    }
}
