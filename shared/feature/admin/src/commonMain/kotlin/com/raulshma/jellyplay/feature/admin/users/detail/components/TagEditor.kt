package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_add
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_add_tag

/**
 * Tag chip editor with autocomplete suggestions (from the server's existing
 * tags) plus free-text entry. A brand-new tag not seen by the server can be
 * typed and added. Existing tags render as dismissible [InputChip]s.
 *
 * @param label section heading.
 * @param tags currently-selected tags.
 * @param suggestions server-known tags for autocomplete; already-selected tags
 *  are filtered out of the suggestion dropdown.
 * @param onChange invoked with the new full tag list.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TagEditor(
    label: String,
    tags: List<String>,
    suggestions: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }

    // Renders as section CONTENT (no own card/title/horizontal padding — the
    // surrounding UserEditSection supplies those). The [label] is kept for
    // accessibility/state-description only.
    Column(modifier = modifier.fillMaxWidth()) {
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(tag) },
                        trailingIcon = {
                            androidx.compose.material3.IconButton(
                                onClick = { onChange(tags - tag) },
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Text(
                                    "✕",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        },
                        colors = InputChipDefaults.inputChipColors(),
                    )
                }
            }
        }
        val filtered = remember(draft, suggestions, tags) {
            if (draft.isBlank()) emptyList()
            else suggestions
                .filter { it !in tags && it.contains(draft, ignoreCase = true) }
                .take(8)
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(Res.string.admin_add_tag)) },
            singleLine = true,
            trailingIcon = {
                if (draft.isNotBlank()) {
                    androidx.compose.material3.TextButton(onClick = {
                        val v = draft.trim()
                        if (v.isNotEmpty() && v !in tags) onChange(tags + v)
                        draft = ""
                    }) { Text(stringResource(Res.string.admin_add)) }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        if (filtered.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                filtered.forEach { s ->
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            if (s !in tags) onChange(tags + s)
                            draft = ""
                        },
                        label = { Text(s) },
                    )
                }
            }
        }
    }
}
