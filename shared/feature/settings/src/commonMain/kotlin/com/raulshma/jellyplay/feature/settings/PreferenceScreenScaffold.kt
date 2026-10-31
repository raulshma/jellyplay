package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.CenteredBringIntoView
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset

/**
 * The optional advanced-toggle pair the scaffold renders as its first top-bar
 * action. Screens without an advanced tier pass `null` and get no toggle.
 */
@Immutable
internal data class PreferenceAdvancedToggle(
    val showAdvanced: Boolean,
    val onToggle: () -> Unit,
)

/**
 * The optional reset-to-defaults action: the top-bar refresh [IconButton] plus
 * the confirmation dialog it opens. The icon's content description and both
 * dialog strings arrive resolved (callers read them from string resources);
 * [onReset] performs the category reset itself — the scaffold owns only the
 * dialog open/close choreography.
 */
@Immutable
internal data class PreferenceResetAction(
    val iconContentDescription: String,
    val dialogTitle: String,
    val dialogMessage: String,
    val onReset: () -> Unit,
)

/**
 * The optional hidden-settings hint ([HiddenSettingsHint], emitted as the
 * trailing item while advanced settings are hidden): [rowFlags] are the
 * admission flags the shell's derived count evaluates — the same flags the
 * screen's row totals and emission `if`s read, with `showAdvanced` false (the
 * state the count describes) — and [onShowAdvanced] reveals them.
 */
@Immutable
internal data class PreferenceHiddenHint(
    val rowFlags: RowAdmissionFlags,
    val onShowAdvanced: () -> Unit,
)

/**
 * One settings screen group slot — the descriptor of the ONE ordered
 * declaration a screen states for its group sequence
 * ([PreferenceScreenScaffold.groups]). The shell derives everything else from
 * that single declaration: the deep-link scroll resolution (the [idSet]s plus
 * the [SettingsScreenSection] fold), the whole-group advanced gating (an
 * [advancedOnly] slot composes only behind the toggle — the hand-written
 * `if (showAdvanced) { item { … } }` twin the section flag used to require is
 * gone) and the hidden-rows count ([PreferenceHiddenHint] +
 * [hiddenRowsFor]).
 *
 * Payload-bearing AT GROUP GRAIN ONLY: [content] is the group's one render
 * slot, the hand-written group body moved verbatim from the screen's
 * `item { … }` block. Per ADR-0009 the row emission stays hand-written — the
 * [SettingsItemList] totals and emission `if`s inside [content] are unchanged,
 * and the shell never sees row grain (no row-driven loop, no per-row slot
 * registry).
 */
internal class PreferenceScreenGroup(
    /** The group's setting ids — the deep-link scroll identity slot. */
    val idSet: Set<String>,
    /** True when the slot only composes behind the advanced toggle. */
    val advancedOnly: Boolean = false,
    /**
     * The content-count hook: how many of the group's rows the screen emits
     * under [RowAdmissionFlags] — the rows the flags vocabulary cannot
     * express (the playback engine branch's per-engine prefix). The shell's
     * hidden-count derivation differences it across the advanced leg;
     * content-only slots keep the zero default.
     */
    val emittedRows: (RowAdmissionFlags) -> Int = { 0 },
    /** The group's one render slot — the hand-written group body. */
    val content: @Composable (MutableState<PickerState<*>?>) -> Unit = {},
)

/**
 * The declared-group [PreferenceScreenGroup]: the slot's scroll identity and
 * content-count contribution derive from the group's row declaration —
 * [emittedRows] is the hook (how many of the group's rows the screen emits
 * under [RowAdmissionFlags]; the default reads the declared admissions via
 * [rowTotalFor]), and the hidden count the shell derives differences it
 * across the advanced leg. The +1 screen-local terms a group's body adds to
 * its own total cancel in that difference unless they ride the advanced
 * toggle — exactly the rows the hook is for. (The extension shape follows
 * the `asRowGroup`/`asSearchGroup` decoration precedent. [content] defaults
 * to an empty slot — the derivation-only shape the contract-test replicas
 * use; every screen slot passes its hand-written body.)
 */
internal fun SettingsSearchItemGroup.asScreenGroup(
    advancedOnly: Boolean = false,
    emittedRows: (RowAdmissionFlags) -> Int = { flags -> rowTotalFor(this, flags) },
    content: @Composable (MutableState<PickerState<*>?>) -> Unit = {},
): PreferenceScreenGroup = PreferenceScreenGroup(
    idSet = itemIdSet,
    advancedOnly = advancedOnly,
    emittedRows = emittedRows,
    content = content,
)

/**
 * Attaches a group's hand-written body to a derivation-only slot from a
 * screen's hoisted `XScreenSlots` declaration — the screen-side half of the
 * ONE ordered declaration (ADR-0009 amendment). The declaration carries the
 * group ids/order, the whole-group advanced gating and the content-count
 * hook (and is what the contract test's section-7 pins consume); the screen
 * maps each slot to its body inline at the call site, carrying the
 * derivation members over verbatim — re-deriving them here is exactly the
 * stale-replica drift the hoist exists to rule out.
 */
internal fun PreferenceScreenGroup.withContent(
    content: @Composable (MutableState<PickerState<*>?>) -> Unit,
): PreferenceScreenGroup = PreferenceScreenGroup(
    idSet = idSet,
    advancedOnly = advancedOnly,
    emittedRows = emittedRows,
    content = content,
)

/**
 * Pairs a screen's ordered slot declaration with its ordered body list in ONE
 * zip-style call — the whole-list counterpart of the single-slot
 * [PreferenceScreenGroup.withContent]. Reordering a declaration used to
 * silently re-pair the indexed `XScreenSlots[n].withContent { … }` bodies with
 * the wrong derivation members; here the count `check` fails loudly on any
 * length drift and the pairing is positional end-to-end, so a reordered
 * declaration can only move its body with it. The bodies stay the screens'
 * hand-written group bodies, in the same LazyColumn order they always passed.
 */
internal fun List<PreferenceScreenGroup>.withContent(
    bodies: List<@Composable (MutableState<PickerState<*>?>) -> Unit>,
): List<PreferenceScreenGroup> {
    check(size == bodies.size) { "slot/body count mismatch: $size slots vs ${bodies.size} bodies" }
    return mapIndexed { i, slot -> slot.withContent(bodies[i]) }
}

/**
 * The hidden-count derivation over a screen's [PreferenceScreenGroup]
 * declaration — the shell's [PreferenceHiddenHint] count: each slot's
 * [PreferenceScreenGroup.emittedRows] count under the advanced leg forced on
 * minus the count as [RowAdmissionFlags] carries it, summed. The delta shape
 * is what makes it honest (rows hidden for other reasons count nothing —
 * they are hidden under both evaluations). Pure (and internal) so
 * `SettingsCatalogScreenContractTest` can pin it against the same
 * declarations the screens pass.
 */
internal fun hiddenRowsFor(
    groups: List<PreferenceScreenGroup>,
    flags: RowAdmissionFlags,
): Int = groups.sumOf { group ->
    group.emittedRows(flags.copy(showAdvanced = true)) - group.emittedRows(flags)
}

/**
 * The settings sub-screen chassis: the ~35 lines of identical chrome every
 * preference screen hand-copied — background-color state, initial TV focus
 * grab ([TvGrabInitialFocus], tag preserved verbatim per screen), the list
 * state, the optional deep-link highlight-scroll derivation
 * ([rememberHighlightScrollIndex] + [HighlightScrollEffect], active only when
 * a group sequence is declared), the optional advanced toggle and reset
 * action in the top bar, the [JellyPlayScreenScaffold] →
 * [CenteredBringIntoView] → [LazyColumn] stack (`fillMaxSize` + inner padding
 * + [tvFocusRestorer] + focus requester, adaptive start/end/bottom content
 * padding), and the optional [SettingsPickerDialog] host.
 *
 * A screen states its group sequence ONCE, as [groups] — the ordered
 * [PreferenceScreenGroup] declaration the shell derives the scroll
 * resolution, the whole-group advanced gating and the [hiddenHint] count
 * from. The slots stop at GROUP grain: each group's rows stay hand-written
 * inside its [PreferenceScreenGroup.content] (ADR-0009). Screens whose layout
 * deviates (row-item lists, bespoke bodies) pass no [groups] and keep the
 * inline [content] block — the two paths are exclusive.
 *
 * Screens whose chrome deviates (input forms with `imePadding`, directory
 * pages with `verticalArrangement`, bespoke column layouts) stay inline —
 * this chassis is only for the chrome-identical cluster.
 *
 * TV focus contract (do not regress): [focusTag] is passed through to
 * [TvGrabInitialFocus] unchanged, the focus requester attaches to the
 * [LazyColumn] exactly as the hand-copied chrome did, and the LazyColumn
 * modifier chain keeps its original order (`fillMaxSize` → `padding` →
 * `tvFocusRestorer` → `focusRequester`).
 *
 * @param groups the ONE ordered group declaration — scroll ids, advanced
 *   gating and hidden-count derivation all read it. Null on the inline
 *   [content] path (the legacy hand-written `item { … }` blocks).
 * @param hiddenHint when non-null (declaration path only), the shell renders
 *   [HiddenSettingsHint] as the trailing item while advanced settings are
 *   hidden, its count derived from [groups].
 * @param pickerHost when true, the scaffold renders the single root
 *   [SettingsPickerDialog] (including dismissal). Screens with no pickers
 *   pass `false` and get no dialog host at all.
 * @param pickerHostState a screen-owned picker cell handed in place of the
 *   scaffold's private one — for screens that must open a picker from
 *   OUTSIDE the lazy content (a screen-scope effect a lazy item scrolled
 *   out of the viewport could never fire). The scaffold reads it for the
 *   dialog host exactly as it reads its own.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
/** Sentinel default so the exclusive-paths check can use reference equality. */
internal val NoInlineContent: LazyListScope.(MutableState<PickerState<*>?>) -> Unit = { _ -> }

/**
 * The two content paths are exclusive: a screen either declares [groups]
 * (its [content] slot stays at the [NoInlineContent] default) or hands the
 * hand-written inline [content] block (and no [groups]). Pure + internal so
 * the contract test pins the full truth table at unit speed — the identity
 * leg (a groups declaration beside the sentinel default) is exactly the
 * leg the v0.11.6 regression got backwards.
 */
internal fun preferencePathsAreExclusive(
    groups: List<PreferenceScreenGroup>?,
    content: LazyListScope.(MutableState<PickerState<*>?>) -> Unit,
): Boolean = groups == null || content === NoInlineContent

@Composable
internal fun PreferenceScreenScaffold(
    title: String,
    onBack: () -> Unit,
    focusTag: String,
    highlightSettingId: String? = null,
    groups: List<PreferenceScreenGroup>? = null,
    advancedToggle: PreferenceAdvancedToggle? = null,
    hiddenHint: PreferenceHiddenHint? = null,
    reset: PreferenceResetAction? = null,
    pickerHost: Boolean = false,
    pickerHostState: MutableState<PickerState<*>?>? = null,
    actions: @Composable () -> Unit = {},
    highlightGroups: List<Set<String>>? = null,
    adjustForAdvanced: (groupIndex: Int) -> Int = { it },
    content: LazyListScope.(activePicker: MutableState<PickerState<*>?>) -> Unit = NoInlineContent,
) {
    check(preferencePathsAreExclusive(groups, content)) {
        "PreferenceScreenScaffold: pass groups or content, not both — the paths are exclusive"
    }
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = focusTag,
    )

    val showAdvanced = advancedToggle?.showAdvanced ?: false
    val declaredSections = groups?.map { SettingsScreenSection(it.idSet, it.advancedOnly) }
    val effectiveHighlightGroups = declaredSections?.map { it.idSet } ?: highlightGroups
    val effectiveAdjust = if (declaredSections != null) {
        deriveAdjustForAdvanced(declaredSections, showAdvanced)
    } else {
        adjustForAdvanced
    }

    val scrollState = rememberLazyListState()
    if (effectiveHighlightGroups != null) {
        val scrollIndex = rememberHighlightScrollIndex(
            highlightSettingId,
            effectiveHighlightGroups,
            effectiveAdjust,
        )
        HighlightScrollEffect(scrollState, scrollIndex)
    }

    val activePicker = pickerHostState ?: remember { mutableStateOf<PickerState<*>?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    JellyPlayScreenScaffold(
        title = title,
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            advancedToggle?.let { advanced ->
                AdvancedSettingsToggleButton(
                    showAdvanced = advanced.showAdvanced,
                    onToggle = advanced.onToggle,
                )
            }
            if (reset != null) {
                IconButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.focusIndicator(CircleShape),
                ) {
                    Icon(
                        Tabler.Outline.Refresh,
                        contentDescription = reset.iconContentDescription,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            actions()
        },
    ) { innerPadding ->
        CenteredBringIntoView {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .tvFocusRestorer()
                    .focusRequester(focusRequester),
                contentPadding = PaddingValues(
                    start = adaptiveInfo.contentPadding(isTv),
                    end = adaptiveInfo.contentPadding(isTv),
                    bottom = adaptiveInfo.bottomPadding(isTv),
                ),
            ) {
                if (groups != null) {
                    // The declaration path: one slot per group, in declaration
                    // order — the whole-group advanced gate the shell owns (no
                    // hand-written `if (showAdvanced)` twin to drift).
                    groups.forEach { group ->
                        if (!group.advancedOnly || showAdvanced) {
                            item { group.content(activePicker) }
                        }
                    }
                    if (hiddenHint != null && !showAdvanced) {
                        item {
                            HiddenSettingsHint(
                                hiddenCount = hiddenRowsFor(groups, hiddenHint.rowFlags),
                                onShowAdvanced = hiddenHint.onShowAdvanced,
                            )
                        }
                    }
                } else {
                    content(activePicker)
                }
            }
        }
    }

    if (reset != null && showResetDialog) {
        ConfirmDialog(
            title = reset.dialogTitle,
            message = reset.dialogMessage,
            confirmText = stringResource(Res.string.settings_reset),
            onConfirm = {
                reset.onReset()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
            dismissText = stringResource(Res.string.settings_cancel),
        )
    }

    if (pickerHost) {
        SettingsPickerDialog(
            state = activePicker.value,
            onDismiss = { activePicker.value = null },
        )
    }
}
