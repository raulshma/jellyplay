@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ParentalRatingOption
import com.raulshma.jellyplay.core.model.UnratedItemOption
import com.raulshma.jellyplay.feature.admin.R

/**
 * Parental Control tab: max-rating dropdown (score + subScore), block-unrated
 * multi-select, allowed/blocked tag editors, and access schedules. Schedule
 * editing is hidden for administrators (web parity).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ParentalControlTab(
    policy: ManagedUserPolicy,
    parentalRatings: List<ParentalRatingOption>,
    tags: List<String>,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        UserEditSection(
            title = stringResource(R.string.admin_max_parental_rating),
            description = stringResource(R.string.admin_max_parental_rating_desc),
        ) {
            MaxParentalRatingField(
                policy = policy,
                parentalRatings = parentalRatings,
                onPolicyChange = onPolicyChange,
            )
        }
        UserEditSection(
            title = stringResource(R.string.admin_block_unrated),
            description = stringResource(R.string.admin_block_unrated_desc),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                UnratedItemOption.entries.forEach { item ->
                    FilterChip(
                        selected = item in policy.blockUnratedItems,
                        onClick = {
                            val next = if (item in policy.blockUnratedItems) {
                                policy.blockUnratedItems - item
                            } else {
                                policy.blockUnratedItems + item
                            }
                            onPolicyChange(policy.copy(blockUnratedItems = next))
                        },
                        label = { Text(item.unratedLabel()) },
                    )
                }
            }
        }
        UserEditSection(
            title = stringResource(R.string.admin_allow_tags),
            description = stringResource(R.string.admin_allow_tags_desc),
        ) {
            TagEditor(
                label = stringResource(R.string.admin_allow_tags),
                tags = policy.allowedTags,
                suggestions = tags,
                onChange = { onPolicyChange(policy.copy(allowedTags = it)) },
            )
        }
        UserEditSection(
            title = stringResource(R.string.admin_block_tags),
            description = stringResource(R.string.admin_block_tags_desc),
        ) {
            TagEditor(
                label = stringResource(R.string.admin_block_tags),
                tags = policy.blockedTags,
                suggestions = tags,
                onChange = { onPolicyChange(policy.copy(blockedTags = it)) },
            )
        }
        if (!policy.isAdministrator) {
            AccessScheduleSection(
                schedules = policy.accessSchedules,
                onChange = { onPolicyChange(policy.copy(accessSchedules = it)) },
            )
        }
    }
}

@Composable
private fun MaxParentalRatingField(
    policy: ManagedUserPolicy,
    parentalRatings: List<ParentalRatingOption>,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    // "No limit" synthesized client-side (null score) + all server ratings.
    val options = remember(parentalRatings) {
        listOf(ParentalRatingOption("No limit", null, null)) + parentalRatings
    }
    val selected = options.firstOrNull { o ->
        o.score == policy.maxParentalRating && o.subScore == policy.maxParentalSubRating
    } ?: options.first() // "No limit"
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = if (selected.name == "No limit") stringResource(R.string.admin_no_limit) else selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.admin_rating)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (option.name == "No limit") stringResource(R.string.admin_no_limit) else option.name) },
                    onClick = {
                        onPolicyChange(
                            policy.copy(
                                maxParentalRating = option.score,
                                maxParentalSubRating = option.subScore,
                            ),
                        )
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun UnratedItemOption.unratedLabel() = when (this) {
    UnratedItemOption.BOOK -> "Books"
    UnratedItemOption.CHANNEL_CONTENT -> "Channels"
    UnratedItemOption.LIVE_TV_CHANNEL -> "Live TV"
    UnratedItemOption.MOVIE -> "Movies"
    UnratedItemOption.MUSIC -> "Music"
    UnratedItemOption.TRAILER -> "Trailers"
    UnratedItemOption.SERIES -> "Shows"
}
