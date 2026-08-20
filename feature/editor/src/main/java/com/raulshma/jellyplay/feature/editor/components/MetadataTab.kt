package com.raulshma.jellyplay.feature.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus
import com.raulshma.jellyplay.feature.editor.EditorUiState
import com.raulshma.jellyplay.feature.editor.EditorViewModel
import com.raulshma.jellyplay.feature.editor.R
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MetadataTab(
    viewModel: EditorViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isLoading) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            LoadingIndicator()
        }
        return
    }

    val mediaType = state.mediaDetail?.item?.mediaType

    val isTv = LocalTvMode.current
    val initialFocus = remember { FocusRequester() }
    RequestOrRestoreFocus(
        focusRequester = if (isTv) initialFocus else null,
        debugKey = "metadata_tab_init",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.editor_section_general),
            initiallyExpanded = true,
            modifier = Modifier.focusRequester(initialFocus),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateField { s -> s.copy(name = it) } },
                label = { Text(stringResource(R.string.editor_field_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.originalTitle,
                onValueChange = { viewModel.updateField { s -> s.copy(originalTitle = it) } },
                label = { Text(stringResource(R.string.editor_field_original_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.sortName,
                onValueChange = { viewModel.updateField { s -> s.copy(sortName = it) } },
                label = { Text(stringResource(R.string.editor_field_sort_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.overview,
                onValueChange = { viewModel.updateField { s -> s.copy(overview = it) } },
                label = { Text(stringResource(R.string.editor_field_overview)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
            )
            if (mediaType == MediaType.MOVIE || mediaType == MediaType.SERIES) {
                OutlinedTextField(
                    value = state.tagline,
                    onValueChange = { viewModel.updateField { s -> s.copy(tagline = it) } },
                    label = { Text(stringResource(R.string.editor_field_tagline)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }

        SectionHeader(title = stringResource(R.string.editor_section_ratings)) {
            val communityInvalid = state.communityRating.isNotEmpty() && state.communityRating.toFloatOrNull() == null
            OutlinedTextField(
                value = state.communityRating,
                onValueChange = { viewModel.updateField { s -> s.copy(communityRating = it) } },
                label = { Text(stringResource(R.string.editor_field_community_rating)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = communityInvalid,
                supportingText = if (communityInvalid) { { Text(stringResource(R.string.editor_field_must_be_number)) } } else null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (mediaType == MediaType.MOVIE) {
                val criticInvalid = state.criticRating.isNotEmpty() && state.criticRating.toFloatOrNull() == null
                OutlinedTextField(
                    value = state.criticRating,
                    onValueChange = { viewModel.updateField { s -> s.copy(criticRating = it) } },
                    label = { Text(stringResource(R.string.editor_field_critic_rating)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = criticInvalid,
                    supportingText = if (criticInvalid) { { Text(stringResource(R.string.editor_field_must_be_number)) } } else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            val parentalRatings = state.editorInfo?.parentalRatingOptions ?: emptyList()
            var officialExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = officialExpanded,
                onExpandedChange = { officialExpanded = it },
            ) {
                OutlinedTextField(
                    value = state.officialRating,
                    onValueChange = { viewModel.updateField { s -> s.copy(officialRating = it) } },
                    label = { Text(stringResource(R.string.editor_field_official_rating)) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = officialExpanded) },
                )
                ExposedDropdownMenu(
                    expanded = officialExpanded,
                    onDismissRequest = { officialExpanded = false },
                ) {
                    parentalRatings.forEach { rating ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(rating.name) },
                            onClick = {
                                viewModel.updateField { s -> s.copy(officialRating = rating.name) }
                                officialExpanded = false
                            },
                        )
                    }
                }
            }

            var customExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = customExpanded,
                onExpandedChange = { customExpanded = it },
            ) {
                OutlinedTextField(
                    value = state.customRating,
                    onValueChange = { viewModel.updateField { s -> s.copy(customRating = it) } },
                    label = { Text(stringResource(R.string.editor_field_custom_rating)) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customExpanded) },
                )
                ExposedDropdownMenu(
                    expanded = customExpanded,
                    onDismissRequest = { customExpanded = false },
                ) {
                    parentalRatings.forEach { rating ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(rating.name) },
                            onClick = {
                                viewModel.updateField { s -> s.copy(customRating = rating.name) }
                                customExpanded = false
                            },
                        )
                    }
                }
            }
        }

        SectionHeader(title = stringResource(R.string.editor_section_dates_numbers)) {
            val yearInvalid = state.productionYear.isNotEmpty() && state.productionYear.toIntOrNull() == null
            OutlinedTextField(
                value = state.productionYear,
                onValueChange = { viewModel.updateField { s -> s.copy(productionYear = it) } },
                label = { Text(stringResource(R.string.editor_field_production_year)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = yearInvalid,
                supportingText = if (yearInvalid) { { Text(stringResource(R.string.editor_field_must_be_number)) } } else null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = state.premiereDate,
                onValueChange = { viewModel.updateField { s -> s.copy(premiereDate = it) } },
                label = { Text(stringResource(R.string.editor_field_premiere_date)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.editor_date_format_hint)) },
            )
            if (mediaType == MediaType.SERIES) {
                OutlinedTextField(
                    value = state.endDate,
                    onValueChange = { viewModel.updateField { s -> s.copy(endDate = it) } },
                    label = { Text(stringResource(R.string.editor_field_end_date)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.editor_date_format_hint)) },
                )
                val runtimeInvalid = state.runtimeMinutes.isNotEmpty() && state.runtimeMinutes.toIntOrNull() == null
                OutlinedTextField(
                    value = state.runtimeMinutes,
                    onValueChange = { viewModel.updateField { s -> s.copy(runtimeMinutes = it) } },
                    label = { Text(stringResource(R.string.editor_field_runtime_minutes)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = runtimeInvalid,
                    supportingText = if (runtimeInvalid) { { Text(stringResource(R.string.editor_field_must_be_number)) } } else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (mediaType == MediaType.EPISODE) {
                val indexInvalid = state.indexNumber.isNotEmpty() && state.indexNumber.toIntOrNull() == null
                OutlinedTextField(
                    value = state.indexNumber,
                    onValueChange = { viewModel.updateField { s -> s.copy(indexNumber = it) } },
                    label = { Text(stringResource(R.string.editor_field_episode_number)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = indexInvalid,
                    supportingText = if (indexInvalid) { { Text(stringResource(R.string.editor_field_must_be_number)) } } else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = state.parentIndexNumber,
                    onValueChange = { viewModel.updateField { s -> s.copy(parentIndexNumber = it) } },
                    label = { Text(stringResource(R.string.editor_field_season_number)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }

        if (mediaType == MediaType.SERIES) {
            SectionHeader(title = stringResource(R.string.editor_section_series_settings)) {
                val statusLabel = remember { mapOf("Continuing" to R.string.editor_status_continuing, "Ended" to R.string.editor_status_ended, "Unreleased" to R.string.editor_status_unreleased) }
                val displayOrderLabel = remember { mapOf("Aired" to R.string.editor_order_aired, "Absolute" to R.string.editor_order_absolute, "DVD" to R.string.editor_order_dvd, "Digital" to R.string.editor_order_digital, "Production" to R.string.editor_order_production) }
                val dayLabel = remember {
                    listOf(
                        "Sunday" to R.string.editor_day_sunday,
                        "Monday" to R.string.editor_day_monday,
                        "Tuesday" to R.string.editor_day_tuesday,
                        "Wednesday" to R.string.editor_day_wednesday,
                        "Thursday" to R.string.editor_day_thursday,
                        "Friday" to R.string.editor_day_friday,
                        "Saturday" to R.string.editor_day_saturday,
                    )
                }
                var statusExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it },
                ) {
                    OutlinedTextField(
                        value = state.status,
                        onValueChange = { viewModel.updateField { s -> s.copy(status = it) } },
                        label = { Text(stringResource(R.string.editor_field_status)) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false },
                    ) {
                        statusLabel.forEach { (status, labelRes) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    viewModel.updateField { s -> s.copy(status = status) }
                                    statusExpanded = false
                                },
                            )
                        }
                    }
                }

                var displayExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = displayExpanded,
                    onExpandedChange = { displayExpanded = it },
                ) {
                    OutlinedTextField(
                        value = state.displayOrder,
                        onValueChange = { viewModel.updateField { s -> s.copy(displayOrder = it) } },
                        label = { Text(stringResource(R.string.editor_field_display_order)) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = displayExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = displayExpanded,
                        onDismissRequest = { displayExpanded = false },
                    ) {
                        displayOrderLabel.forEach { (order, labelRes) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    viewModel.updateField { s -> s.copy(displayOrder = order) }
                                    displayExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.airTime,
                    onValueChange = { viewModel.updateField { s -> s.copy(airTime = it) } },
                    label = { Text(stringResource(R.string.editor_field_air_time)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text(stringResource(R.string.editor_field_air_days), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val dayKeys = dayLabel.map { it.first }
                    dayLabel.forEach { (day, labelRes) ->
                        FilterChip(
                            selected = day in state.airDays,
                            onClick = {
                                val newDays = if (day in state.airDays) {
                                    state.airDays - day
                                } else {
                                    (state.airDays + day).sortedBy { dayKeys.indexOf(it) }
                                }
                                viewModel.updateField { s -> s.copy(airDays = newDays) }
                            },
                            label = { Text(stringResource(labelRes).take(3)) },
                        )
                    }
                }
            }
        }

        SectionHeader(title = stringResource(R.string.editor_section_genres)) {
            EditableChipGroup(
                items = state.genres,
                onAdd = { viewModel.updateField { s -> s.copy(genres = (s.genres + it).sortedBy { g -> g.lowercase() }) } },
                onRemove = { viewModel.updateField { s -> s.copy(genres = s.genres - it) } },
            )
        }

        SectionHeader(title = stringResource(R.string.editor_section_tags)) {
            EditableChipGroup(
                items = state.tags,
                onAdd = { viewModel.updateField { s -> s.copy(tags = (s.tags + it).sortedBy { t -> t.lowercase() }) } },
                onRemove = { viewModel.updateField { s -> s.copy(tags = s.tags - it) } },
            )
        }

        SectionHeader(title = stringResource(R.string.editor_section_studios)) {
            EditableChipGroup(
                items = state.studios,
                onAdd = { viewModel.updateField { s -> s.copy(studios = (s.studios + it).sortedBy { st -> st.lowercase() }) } },
                onRemove = { viewModel.updateField { s -> s.copy(studios = s.studios - it) } },
            )
        }

        SectionHeader(title = stringResource(R.string.editor_section_people)) {
            PeopleEditor(
                people = state.people,
                onAdd = { person -> viewModel.updateField { s -> s.copy(people = s.people + person) } },
                onRemove = { person -> viewModel.updateField { s -> s.copy(people = s.people - person) } },
                onUpdate = { old, new -> viewModel.updateField { s ->
                    s.copy(people = s.people.map { if (it == old) new else it })
                }},
            )
        }

        val externalIds = state.editorInfo?.externalIdInfos ?: emptyList()
        if (externalIds.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.editor_section_external_ids)) {
                externalIds.forEach { extId ->
                    val currentValue = state.providerIds[extId.key] ?: ""
                    OutlinedTextField(
                        value = currentValue,
                        onValueChange = { newValue ->
                            viewModel.updateField { s ->
                                s.copy(providerIds = s.providerIds.toMutableMap().apply {
                                    if (newValue.isBlank()) remove(extId.key)
                                    else put(extId.key, newValue)
                                })
                            }
                        },
                        label = { Text(extId.name) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }

        SectionHeader(title = stringResource(R.string.editor_section_metadata_locking)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.editor_lock_all_metadata), modifier = Modifier.weight(1f))
                Switch(
                    checked = state.lockData,
                    onCheckedChange = { viewModel.updateField { s -> s.copy(lockData = it) } },
                )
            }

            if (!state.lockData) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.editor_lock_individual_fields), style = MaterialTheme.typography.titleSmall)
                val lockableFields = listOf(
                    "Name" to R.string.editor_lock_field_name,
                    "Overview" to R.string.editor_lock_field_overview,
                    "Genres" to R.string.editor_lock_field_genres,
                    "OfficialRating" to R.string.editor_lock_field_parental_rating,
                    "Cast" to R.string.editor_lock_field_people,
                    "ProductionLocations" to R.string.editor_lock_field_production_locations,
                    "Studios" to R.string.editor_lock_field_studios,
                    "Tags" to R.string.editor_lock_field_tags,
                    "Runtime" to R.string.editor_lock_field_runtime,
                )
                lockableFields.forEach { (key, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = key !in state.lockedFields,
                            onCheckedChange = { checked ->
                                viewModel.updateField { s ->
                                    s.copy(
                                        lockedFields = if (checked) {
                                            s.lockedFields - key
                                        } else {
                                            s.lockedFields + key
                                        }
                                    )
                                }
                            },
                        )
                        Text(stringResource(label), modifier = Modifier.focusIndicator().clickable {
                            val checked = key !in state.lockedFields
                            viewModel.updateField { s ->
                                s.copy(
                                    lockedFields = if (!checked) {
                                        s.lockedFields - key
                                    } else {
                                        s.lockedFields + key
                                    }
                                )
                            }
                        })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .focusIndicator()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Tabler.Outline.ChevronUp else Tabler.Outline.ChevronDown,
                contentDescription = if (expanded) stringResource(R.string.editor_action_collapse) else stringResource(R.string.editor_action_expand),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditableChipGroup(
    items: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var newEntry by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(item) },
                    label = { Text(item) },
                    trailingIcon = {
                        Icon(
                            Tabler.Outline.X,
                            contentDescription = stringResource(R.string.editor_action_remove),
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newEntry,
                onValueChange = { newEntry = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.editor_add_new_placeholder)) },
                singleLine = true,
            )
            IconButton(
                onClick = {
                    if (newEntry.isNotBlank()) {
                        onAdd(newEntry.trim())
                        newEntry = ""
                    }
                },
            ) {
                Icon(Tabler.Outline.Plus, contentDescription = stringResource(R.string.editor_action_add))
            }
        }
    }
}

@Composable
private fun PeopleEditor(
    people: List<com.raulshma.jellyplay.core.model.EditorPerson>,
    onAdd: (com.raulshma.jellyplay.core.model.EditorPerson) -> Unit,
    onRemove: (com.raulshma.jellyplay.core.model.EditorPerson) -> Unit,
    onUpdate: (old: com.raulshma.jellyplay.core.model.EditorPerson, new: com.raulshma.jellyplay.core.model.EditorPerson) -> Unit,
) {
    var showPersonDialog by remember { mutableStateOf<com.raulshma.jellyplay.core.model.EditorPerson?>(null) }
    var isNewPerson by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        people.forEach { person ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Tabler.Outline.User,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        person.role?.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.editor_person_role_as_format, stringResource(personTypeLabelRes(person.type)), it) }
                            ?: stringResource(personTypeLabelRes(person.type)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    isNewPerson = false
                    showPersonDialog = person
                }) {
                    Icon(Tabler.Outline.ChevronDown, contentDescription = stringResource(R.string.editor_action_edit))
                }
                IconButton(onClick = { onRemove(person) }) {
                    Icon(Tabler.Outline.Trash, contentDescription = stringResource(R.string.editor_action_remove), tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        androidx.compose.material3.TextButton(onClick = {
            isNewPerson = true
            showPersonDialog = com.raulshma.jellyplay.core.model.EditorPerson()
        }) {
            Icon(Tabler.Outline.Plus, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.editor_add_person))
        }
    }

    showPersonDialog?.let { editingPerson ->
        PersonEditorDialog(
            person = editingPerson,
            isNew = isNewPerson,
            onDismiss = { showPersonDialog = null },
            onConfirm = { person ->
                if (isNewPerson) onAdd(person)
                else onUpdate(editingPerson, person)
                showPersonDialog = null
            },
        )
    }
}

private fun personTypeLabelRes(type: String): Int = when (type) {
    "Actor" -> R.string.editor_person_type_actor
    "Director" -> R.string.editor_person_type_director
    "Writer" -> R.string.editor_person_type_writer
    "Producer" -> R.string.editor_person_type_producer
    "Composer" -> R.string.editor_person_type_composer
    "GuestStar" -> R.string.editor_person_type_guest_star
    "Conductor" -> R.string.editor_person_type_conductor
    "Lyricist" -> R.string.editor_person_type_lyricist
    "Arranger" -> R.string.editor_person_type_arranger
    else -> R.string.editor_person_type_actor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonEditorDialog(
    person: com.raulshma.jellyplay.core.model.EditorPerson,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (com.raulshma.jellyplay.core.model.EditorPerson) -> Unit,
) {
    var name by remember { mutableStateOf(person.name) }
    var role by remember { mutableStateOf(person.role ?: "") }
    var type by remember { mutableStateOf(person.type) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) stringResource(R.string.editor_add_person) else stringResource(R.string.editor_edit_person)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.editor_field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                val personTypes = listOf("Actor", "Director", "Writer", "Producer", "Composer", "GuestStar", "Conductor", "Lyricist", "Arranger")
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it },
                        label = { Text(stringResource(R.string.editor_field_type)) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        personTypes.forEach { pType ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(personTypeLabelRes(pType))) },
                                onClick = { type = pType; typeExpanded = false },
                            )
                        }
                    }
                }
                if (type in listOf("Actor", "GuestStar")) {
                    OutlinedTextField(
                        value = role,
                        onValueChange = { role = it },
                        label = { Text(stringResource(R.string.editor_field_role)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    onConfirm(
                        com.raulshma.jellyplay.core.model.EditorPerson(
                            id = person.id.ifBlank { java.util.UUID.randomUUID().toString() },
                            name = name,
                            role = role.ifBlank { null },
                            type = type,
                            primaryImageTag = person.primaryImageTag,
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_action_cancel)) }
        },
    )
}
