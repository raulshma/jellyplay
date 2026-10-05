package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowsHorizontal
import com.composables.icons.tabler.outline.Backspace
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.HandClick
import com.composables.icons.tabler.outline.HandFinger
import com.composables.icons.tabler.outline.HandMove
import com.composables.icons.tabler.outline.Keyboard
import com.composables.icons.tabler.outline.Maximize
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.groupedItemContainerColor
import com.raulshma.jellyplay.core.designsystem.theme.hairlineBorderColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusDefaults
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerBinding
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.bindableToDiscreteRow
import com.raulshma.jellyplay.core.ui.animation.SwipeActionBox
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.components.GlassFilterChip
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.core.ui.model.verticalSwipeLabel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_search_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_add_shortcut
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_add_shortcut_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_count
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_modified_suffix
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_preset_label
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_reset_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_reset_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_row_delete
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_row_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_search_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_search_hint
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_section_keyboard
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_section_mouse
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_section_touch
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_section_tv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** How long the capture-collision glow stays lit before clearing itself. */
private const val COLLISION_FLASH_CLEAR_MS = 2_600L

/**
 * The player input-binding editor (issue #171 generalized): one row per
 * bindable input, each carrying its action (the picker) and its enabled
 * flag (the switch; the picker's "Unbound" arms it off without erasing the
 * action). Sections group the surfaces: touch, mouse & scroll, keyboard,
 * TV remote — each collapsible, its header summary (count · modified)
 * staying truthful while a search filters the bodies.
 *
 * On top of the row list: a search field over pattern/action labels, the
 * GestureMode preset chips (the same atomic preset the playback settings
 * row writes), swipe-to-reset on rows that drifted from the parameterized
 * defaults, swipe-to-delete on user-captured rows, and an add-shortcut row
 * whose capture dialog binds any modifier combo over a catalog key.
 * Everything the list renders is derived by the pure [InputBindingsFilter]
 * from one [InputBindingsUiState]; the item plan below is built once and
 * drives BOTH the emission and the highlight-scroll groups, and the
 * capture-collision path lifts the owning section out of its collapsed
 * set and clears the query before flashing — so the flash always lands on
 * the row it names, wherever collapse/search had hidden it.
 */
@Composable
fun InputBindingsScreen(
    onBack: () -> Unit,
    viewModel: InputBindingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState

    var query by rememberSaveable { mutableStateOf("") }
    var collapsedSections by rememberSaveable { mutableStateOf(emptySet<InputBindingsSection>()) }
    var showCaptureDialog by rememberSaveable { mutableStateOf(false) }
    var highlightId by rememberSaveable { mutableStateOf<String?>(null) }
    // A captured NEW row awaiting its action picker. Not saveable — a
    // process death between capture and picker just leaves an unbound row
    // the user edits like any other.
    var pendingNewRow by remember { mutableStateOf<PlayerBinding?>(null) }
    // Screen-owned picker cell (handed to the scaffold): a capture commits
    // while the list sits scrolled down at the add-shortcut row, and the
    // pending picker must open from SCREEN scope — a lazy item scrolled
    // out of the viewport is not composed, so the effect would only fire
    // later, when the header scrolled back into view.
    val pickerHostState = remember { mutableStateOf<PickerState<*>?>(null) }

    // Baseline = what Reset-all would restore under the current behavior
    // flags — "unmodified" and "what reset restores" can never disagree.
    val defaultsById = remember(
        state.gestureMode,
        state.holdSpeedEnabled,
        state.doubleTapHoldSeekEnabled,
    ) {
        PlayerInputDefaults.defaultBindingsById(
            gestureMode = state.gestureMode,
            holdSpeedEnabled = state.holdSpeedEnabled,
            doubleTapHoldSeekEnabled = state.doubleTapHoldSeekEnabled,
        )
    }

    // Picker/search labels resolve in composable scope (the sheet's label
    // lambda runs outside composition — the orientation-row pattern). Row
    // titles are per-BINDING, not per-pattern: a rebound vertical-swipe
    // half must title with its rebound action, not the side's factory
    // word (InputLabelSeam's verticalSwipeLabel contract), and the search
    // corpus is built from the same titles + subtitles the rows render.
    val actionLabels = PlayerAction.entries.associateWith { it.localizedDisplayName() }
    val corpusById = state.map.bindings.associate { binding ->
        binding.id to (bindingTitle(binding) + " " + actionLabels.getValue(binding.action))
    }
    val model = InputBindingsFilter.build(
        bindings = state.map.bindings,
        defaultsById = defaultsById,
        query = query,
        corpusById = corpusById,
    )

    val sections = listOf(
        SectionSpec(InputBindingsSection.TOUCH, model.touch, Res.string.settings_input_bindings_section_touch),
        SectionSpec(InputBindingsSection.MOUSE, model.mouse, Res.string.settings_input_bindings_section_mouse),
        SectionSpec(InputBindingsSection.KEYBOARD, model.keyboard, Res.string.settings_input_bindings_section_keyboard),
        SectionSpec(InputBindingsSection.TV, model.tv, Res.string.settings_input_bindings_section_tv),
    )
    val queryActive = query.isNotBlank()
    val items = remember(sections, queryActive, collapsedSections) {
        buildInputBindingsLayout(sections, queryActive, collapsedSections)
    }

    // The capture-collision flash: the scaffold's highlight path scrolls to
    // the row, then the glow clears itself.
    LaunchedEffect(highlightId) {
        if (highlightId != null) {
            kotlinx.coroutines.delay(COLLISION_FLASH_CLEAR_MS)
            highlightId = null
        }
    }

    PendingPickerEffect(
        pending = pendingNewRow,
        clearPending = { pendingNewRow = null },
        actionLabels = actionLabels,
        viewModel = viewModel,
        activePicker = pickerHostState,
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_input_bindings_title),
        onBack = onBack,
        focusTag = "input_bindings_init",
        pickerHost = true,
        pickerHostState = pickerHostState,
        reset = PreferenceResetAction(
            iconContentDescription = stringResource(Res.string.settings_input_bindings_reset),
            dialogTitle = stringResource(Res.string.settings_input_bindings_reset_title),
            dialogMessage = stringResource(Res.string.settings_input_bindings_reset_message),
            onReset = viewModel::resetToDefaults,
        ),
        highlightSettingId = highlightId,
        highlightGroups = items.map { setOf(it.key) },
    ) { activePicker ->
        items(items, key = { it.key }, contentType = { it.contentType }) { item ->
            // The 4dp inter-row gap SettingsGroup gives its rows — this
            // screen emits rows as bare lazy items, so it carries the same
            // spacing itself.
            Box(
                modifier = Modifier
                    .animateItem(placementSpec = lazyItemPlacementSpec())
                    .padding(bottom = 4.dp),
            ) {
                when (item) {
                    is InputBindingsItem.Header -> {
                        InputBindingsHeader(
                            query = query,
                            onQueryChange = { query = it },
                            gestureMode = state.gestureMode,
                            onGestureMode = viewModel::setGestureMode,
                        )
                    }

                    is InputBindingsItem.SectionHeader -> SectionHeaderRow(
                        title = stringResource(item.titleRes),
                        section = item.section,
                        collapsed = item.sectionKey in collapsedSections && !queryActive,
                        onToggle = {
                            collapsedSections = if (item.sectionKey in collapsedSections) {
                                collapsedSections - item.sectionKey
                            } else {
                                collapsedSections + item.sectionKey
                            }
                        },
                    )

                    is InputBindingsItem.Row -> BindingRow(
                        row = item.row,
                        actionLabels = actionLabels,
                        highlighted = item.row.binding.id == highlightId,
                        viewModel = viewModel,
                        activePicker = activePicker,
                    )

                    is InputBindingsItem.AddShortcut -> SettingListItem(
                        icon = Tabler.Outline.Plus,
                        title = stringResource(Res.string.settings_input_bindings_add_shortcut),
                        subtitle = stringResource(Res.string.settings_input_bindings_add_shortcut_subtitle),
                        onClick = { showCaptureDialog = true },
                    )

                    is InputBindingsItem.EmptyResult -> Text(
                        text = stringResource(Res.string.settings_input_bindings_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            }
        }
    }

    if (showCaptureDialog) {
        KeyCaptureDialog(
            onCaptured = { pattern ->
                showCaptureDialog = false
                val result = viewModel.addKeyBinding(pattern)
                if (result.created) {
                    pendingNewRow = PlayerBinding(
                        id = result.bindingId,
                        pattern = pattern,
                        action = PlayerAction.NONE,
                        enabled = true,
                    )
                } else {
                    // The row owning the captured pattern may sit in a
                    // collapsed section or behind the active query — lift
                    // both before flashing, or the highlight would target
                    // a row outside the item plan: no scroll, no flash, no
                    // feedback at all.
                    collapsedSections = collapsedSections - InputBindingsFilter.sectionOf(pattern)
                    if (query.isNotBlank()) query = ""
                    highlightId = result.bindingId
                }
            },
            onDismiss = { showCaptureDialog = false },
        )
    }
}

// ── Layout plan ───────────────────────────────────────────────────────────

private data class SectionSpec(
    val key: InputBindingsSection,
    val model: InputBindingsSectionModel,
    val titleRes: StringResource,
)

/**
 * One emitted LazyColumn item. The full ordered plan is computed BEFORE the
 * scaffold and reused as the highlight-scroll groups, so an id's item index
 * in the plan IS its index in the list — capture collisions flash the row
 * they land on, wherever collapse/search moved it.
 */
private sealed interface InputBindingsItem {
    val key: String
    val contentType: String

    data object Header : InputBindingsItem {
        override val key get() = "header"
        override val contentType get() = "header"
    }

    data class SectionHeader(
        val sectionKey: InputBindingsSection,
        val section: InputBindingsSectionModel,
        val titleRes: StringResource,
    ) : InputBindingsItem {
        override val key get() = "section.${sectionKey.name}"
        override val contentType get() = "section_header"
    }

    data class Row(val row: InputBindingsRowModel) : InputBindingsItem {
        override val key get() = row.binding.id
        override val contentType get() = "binding_row"
    }

    data object AddShortcut : InputBindingsItem {
        override val key get() = "add_shortcut"
        override val contentType get() = "add_shortcut"
    }

    data object EmptyResult : InputBindingsItem {
        override val key get() = "empty_result"
        override val contentType get() = "empty_result"
    }
}

private fun buildInputBindingsLayout(
    sections: List<SectionSpec>,
    queryActive: Boolean,
    collapsedSections: Set<InputBindingsSection>,
): List<InputBindingsItem> = buildList {
    add(InputBindingsItem.Header)
    var anyRow = false
    for (spec in sections) {
        if (queryActive && spec.model.rows.isEmpty()) continue
        anyRow = anyRow || spec.model.rows.isNotEmpty()
        add(InputBindingsItem.SectionHeader(spec.key, spec.model, spec.titleRes))
        // While searching, every section renders flat regardless of its
        // collapsed state — the query is the collapse override.
        if (!queryActive && spec.key in collapsedSections) continue
        for (row in spec.model.rows) add(InputBindingsItem.Row(row))
        if (spec.key == InputBindingsSection.KEYBOARD) add(InputBindingsItem.AddShortcut)
    }
    if (queryActive && !anyRow) add(InputBindingsItem.EmptyResult)
}

// ── Header: search + gesture-mode preset chips ───────────────────────────

@Composable
private fun InputBindingsHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    gestureMode: GestureMode,
    onGestureMode: (GestureMode) -> Unit,
) {
    val gestureModeLabels = GestureMode.entries.associateWith { it.localizedDisplayName() }
    // Same glass pill the settings screen shows as its top header search —
    // CircleShape over the grouped container color, hairline border, 18dp
    // magnifier — with the TV focus border/glow the settings search applies.
    val isTv = LocalTvMode.current
    var isSearchFocused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = groupedItemContainerColor(darkAlpha = 0.4f),
            border = BorderStroke(
                width = if (isSearchFocused && isTv) TvFocusDefaults.BorderWidth else 1.dp,
                color = if (isSearchFocused && isTv) {
                    MaterialTheme.colorScheme.primary
                } else {
                    hairlineBorderColor()
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .then(
                    if (isSearchFocused && isTv) {
                        Modifier.shadow(
                            elevation = TvFocusDefaults.GlowElevation,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = MaterialTheme.colorScheme.primary
                                .copy(alpha = TvFocusDefaults.GlowAmbientAlpha),
                            spotColor = MaterialTheme.colorScheme.primary
                                .copy(alpha = TvFocusDefaults.GlowSpotAlpha),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Tabler.Outline.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.settings_input_bindings_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusEvent { isSearchFocused = it.isFocused },
                    )
                }
                if (query.isNotEmpty()) {
                    val clearSearchCd = stringResource(Res.string.settings_clear_search_cd)
                    Icon(
                        imageVector = Tabler.Outline.X,
                        contentDescription = clearSearchCd,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") }
                            .padding(2.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(Res.string.settings_input_bindings_preset_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (mode in GestureMode.entries) {
                GlassFilterChip(
                    label = gestureModeLabels.getValue(mode),
                    selected = gestureMode == mode,
                    onClick = { onGestureMode(mode) },
                )
            }
        }
    }
}

// ── Collapsible section header with an unfiltered count summary ──────────

@Composable
private fun SectionHeaderRow(
    title: String,
    section: InputBindingsSectionModel,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 180f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "section_chevron",
    )
    val countTemplate = stringResource(Res.string.settings_input_bindings_count, section.totalCount)
    val summary = if (section.modifiedCount > 0) {
        countTemplate + " " + stringResource(
            Res.string.settings_input_bindings_modified_suffix,
            section.modifiedCount,
        )
    } else {
        countTemplate
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusIndicator(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Tabler.Outline.ChevronDown,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 4.dp)
                .rotate(chevronRotation),
        )
    }
}

// ── One binding row ──────────────────────────────────────────────────────

/**
 * The row title for ONE persisted binding. Vertical-swipe halves carry the
 * ACTION in their label ("Swipe up · volume"), so a rebound row must title
 * with its rebound action — the binding-aware [verticalSwipeLabel], the
 * exact contract the pre-editor rows rendered — never the side's factory
 * word that pattern-level [InputPattern.localizedDisplayName] fills in.
 * Every other pattern's label is action-free.
 */
@Composable
private fun bindingTitle(binding: PlayerBinding): String = when (val pattern = binding.pattern) {
    is InputPattern.VerticalSwipe -> verticalSwipeLabel(pattern.side, binding.action)
    else -> pattern.localizedDisplayName()
}

/**
 * The action picker a binding row (or a fresh capture) opens: the discrete
 * actions only — the continuous gesture class would be a dead binding on
 * any rebound (see [com.raulshma.jellyplay.core.model.bindableToDiscreteRow])
 * — plus NONE, the explicit erase.
 */
private fun discreteActionPicker(
    title: String,
    actionLabels: Map<PlayerAction, String>,
    selected: PlayerAction,
    onSelect: (PlayerAction) -> Unit,
): PickerState<PlayerAction> = PickerState.List(
    title = title,
    items = PlayerAction.entries.filter { it == PlayerAction.NONE || it.bindableToDiscreteRow },
    label = { actionLabels.getValue(it) },
    isSelected = { it == selected },
    onSelect = onSelect,
)

/**
 * One binding row: title = the input, subtitle = the action ("Unbound" is
 * the picker's explicit erase), row click = the action picker, trailing
 * switch = the enabled flag. Swipe reveals the row-level action: delete on
 * user-captured rows, reset-to-default on rows that drifted from the
 * parameterized defaults. Unmodified default rows have no swipe action —
 * they render bare so the drag never steals scroll.
 */
@Composable
private fun BindingRow(
    row: InputBindingsRowModel,
    actionLabels: Map<PlayerAction, String>,
    highlighted: Boolean,
    viewModel: InputBindingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
    val binding = row.binding
    val title = bindingTitle(binding)
    val subtitle = actionLabels.getValue(binding.action)
    val deleteLabel = stringResource(Res.string.settings_input_bindings_row_delete)
    val resetLabel = stringResource(Res.string.settings_input_bindings_row_reset)

    val rowContent: @Composable () -> Unit = {
        SettingToggleItem(
            icon = patternIcon(binding.pattern),
            title = title,
            subtitle = subtitle,
            checked = binding.enabled,
            highlighted = highlighted,
            onCheckedChange = { enabled -> viewModel.setBindingEnabled(binding.id, enabled) },
            onClick = {
                activePicker.value = discreteActionPicker(
                    title = title,
                    actionLabels = actionLabels,
                    selected = binding.action,
                    onSelect = { action -> viewModel.setBindingAction(binding.id, action) },
                )
            },
        )
    }
    when {
        row.deletable -> SwipeActionBox(
            onAction = { viewModel.removeBinding(binding.id) },
            actionContentDescription = deleteLabel,
            modifier = Modifier.fillMaxWidth(),
        ) { rowContent() }

        row.modified -> SwipeActionBox(
            onAction = { viewModel.resetBinding(binding.id) },
            actionIcon = Tabler.Outline.Refresh,
            actionContentDescription = resetLabel,
            actionColor = MaterialTheme.colorScheme.secondaryContainer,
            actionIconTint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) { rowContent() }

        else -> rowContent()
    }
}

/**
 * Opens the action picker for a just-captured row — one composition AFTER
 * the capture, so the pattern's title resolves in composable scope (the
 * commit callback is not one). The picker preselects Unbound; picking an
 * action binds the fresh row.
 */
@Composable
private fun PendingPickerEffect(
    pending: PlayerBinding?,
    clearPending: () -> Unit,
    actionLabels: Map<PlayerAction, String>,
    viewModel: InputBindingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
    val title = pending?.let { bindingTitle(it) }
    LaunchedEffect(pending, title) {
        if (pending == null || title == null) return@LaunchedEffect
        clearPending()
        activePicker.value = discreteActionPicker(
            title = title,
            actionLabels = actionLabels,
            selected = PlayerAction.NONE,
            onSelect = { action -> viewModel.setBindingAction(pending.id, action) },
        )
    }
}

/** Per-pattern-family icon — the scan aid the old per-section icon lacked. */
private fun patternIcon(pattern: InputPattern): ImageVector = when (pattern) {
    InputPattern.Tap -> Tabler.Outline.HandClick
    is InputPattern.DoubleTap, is InputPattern.DoubleTapHold -> Tabler.Outline.HandFinger
    InputPattern.LongPress -> Tabler.Outline.HandMove
    is InputPattern.VerticalSwipe,
    InputPattern.HorizontalSwipe,
    is InputPattern.EdgeSwipe,
    -> Tabler.Outline.ArrowsHorizontal

    InputPattern.Pinch -> Tabler.Outline.Maximize
    is InputPattern.Wheel -> Tabler.Outline.Settings
    is InputPattern.Key -> Tabler.Outline.Keyboard
    is InputPattern.DPad -> Tabler.Outline.DeviceTv
}
