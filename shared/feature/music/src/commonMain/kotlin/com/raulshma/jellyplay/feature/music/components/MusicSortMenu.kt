package com.raulshma.jellyplay.feature.music.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.music.collection.MusicSortOption

/**
 * The ONE sort control for the music collections: an anchor button showing the
 * active option's localized label, opening a dropdown over the collection's
 * declared options. Both route families render it — the browse pages wrap it
 * in the end-aligned row above their grid/list, the standalone screens place
 * it in their scaffold actions. The options list comes from
 * `MusicCollectionKind.sortOptions` — never hand-picked at the call site
 * (the declared sort-admission policy).
 */
@Composable
fun MusicSortMenuButton(
    selected: MusicSortOption,
    options: List<MusicSortOption>,
    onSelect: (MusicSortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        val focusState = rememberTvFocusState()
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.then(focusState.focusModifier).tvFocusIndicator(focusState, CircleShape),
        ) {
            Text(
                text = stringResource(selected.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
