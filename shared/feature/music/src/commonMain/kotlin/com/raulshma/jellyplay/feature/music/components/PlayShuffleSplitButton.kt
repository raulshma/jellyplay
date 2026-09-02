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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_play
import com.raulshma.jellyplay.feature.music.generated.resources.music_shuffle

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayShuffleSplitButton(
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
    playFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
) {
    var shuffleChecked by remember { mutableStateOf(false) }
    val playFocusState = rememberTvFocusState()
    val shuffleFocusState = rememberTvFocusState()

    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .height(40.dp)
                    .then(if (playFocusRequester != null) Modifier.focusRequester(playFocusRequester) else Modifier)
                    .focusProperties {
                        up = upFocusRequester ?: FocusRequester.Default
                        down = downFocusRequester ?: FocusRequester.Default
                    }
                    .then(playFocusState.focusModifier)
                    .tvFocusIndicator(playFocusState, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(40.dp),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Tabler.Outline.PlayerPlay,
                    contentDescription = stringResource(Res.string.music_play),
                    modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(40.dp)),
                )
                Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(40.dp)))
                Text(
                    text = stringResource(Res.string.music_play),
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
                modifier = Modifier
                    .height(40.dp)
                    .focusProperties {
                        up = upFocusRequester ?: FocusRequester.Default
                        down = downFocusRequester ?: FocusRequester.Default
                    }
                    .then(shuffleFocusState.focusModifier)
                    .tvFocusIndicator(shuffleFocusState, RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)),
                shapes = SplitButtonDefaults.trailingButtonShapesFor(40.dp),
                contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Tabler.Outline.ArrowsShuffle,
                    contentDescription = stringResource(Res.string.music_shuffle),
                    modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(40.dp)),
                )
            }
        },
    )
}
