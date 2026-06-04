package com.raulshma.jellyplay.feature.music.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayShuffleSplitButton(
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shuffleChecked by remember { mutableStateOf(false) }

    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onPlayClick,
                modifier = modifier.height(40.dp),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(40.dp),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Tabler.Outline.PlayerPlay,
                    contentDescription = "Play",
                    modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(40.dp)),
                )
                Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(40.dp)))
                Text(
                    text = "Play",
                    style = ButtonDefaults.textStyleFor(40.dp),
                )
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = shuffleChecked,
                onCheckedChange = {
                    shuffleChecked = it
                    if (it) onShuffleClick()
                },
                modifier = modifier.height(40.dp),
                shapes = SplitButtonDefaults.trailingButtonShapesFor(40.dp),
                contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Tabler.Outline.ArrowsShuffle,
                    contentDescription = "Shuffle",
                    modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(40.dp)),
                )
            }
        },
    )
}
