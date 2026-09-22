package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronUp
import com.composables.icons.tabler.outline.Filter
import com.composables.icons.tabler.outline.ListNumbers
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Music
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Subtitles
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.Typography
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.LanguageRule
import com.raulshma.jellyplay.core.model.LanguageRuleSet
import com.raulshma.jellyplay.core.model.RuleContentType
import com.raulshma.jellyplay.core.model.SubtitleTrackMode
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lang_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_editor_add_language
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_editor_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_editor_move_down
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_editor_move_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_editor_remove
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rule_any_language
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rule_applies_to
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rule_audio_language
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rule_delete
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rule_edit
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rule_subtitle_language
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rule_subtitle_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rule_title_pattern
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rule_title_pattern_helper
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rules
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rules_add
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rules_cap

/**
 * The maximum number of advanced [LanguageRule]s the editor accepts — the cap
 * keeps the JSON blob and the restore-time rule scan bounded (plan: "~10").
 */
internal const val TRACK_RULES_MAX = 10

/**
 * The ordered-language editor: the current ordered preference with
 * move-up/move-down/remove controls per entry and an "add language" picker.
 * v1 keeps it deliberately minimal — no drag handles, no free-text codes; the
 * pickers reuse the screen-wide language catalogue
 * ([languages], ISO-639-2 codes normalised by the existing matchers).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderedLanguagesEditorSheet(
    title: String,
    ordered: List<String>,
    onDismiss: () -> Unit,
    onChange: (List<String>) -> Unit,
) {
    var activePicker by remember { mutableStateOf<PickerState<String>?>(null) }
    val addLanguageTitle = stringResource(Res.string.settings_track_editor_add_language)

    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(title = title, icon = Tabler.Outline.ListNumbers)
            if (ordered.isEmpty()) {
                Text(
                    stringResource(Res.string.settings_track_editor_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(
                        max = with(LocalDensity.current) {
                            LocalWindowInfo.current.containerSize.height.toDp() * 0.35f
                        },
                    ),
                ) {
                    itemsIndexed(ordered, key = { _, code -> code }) { index, code ->
                        val shape = expressiveListShape(index, ordered.size)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$index · ${languageNameByCode[code] ?: code}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                enabled = index > 0,
                                onClick = { onChange(ordered.moved(index, up = true)) },
                            ) {
                                Icon(
                                    Tabler.Outline.ChevronUp,
                                    contentDescription = stringResource(Res.string.settings_track_editor_move_up),
                                )
                            }
                            IconButton(
                                enabled = index < ordered.lastIndex,
                                onClick = { onChange(ordered.moved(index, up = false)) },
                            ) {
                                Icon(
                                    Tabler.Outline.ChevronDown,
                                    contentDescription = stringResource(Res.string.settings_track_editor_move_down),
                                )
                            }
                            IconButton(onClick = { onChange(ordered - code) }) {
                                Icon(
                                    Tabler.Outline.X,
                                    contentDescription = stringResource(Res.string.settings_track_editor_remove),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        activePicker = PickerState.List(
                            title = addLanguageTitle,
                            items = languages.mapNotNull { it.first }.filter { it !in ordered },
                            label = { code -> languageNameByCode[code] ?: code },
                            isSelected = { false },
                            onSelect = { code -> onChange(ordered + code) },
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Tabler.Outline.Plus, contentDescription = null)
                Spacer(Modifier.padding(start = 12.dp))
                Text(
                    addLanguageTitle,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }

    SettingsPickerDialog(state = activePicker, onDismiss = { activePicker = null })
}

/**
 * One-slot reorder shared by the language list and the rules editor: the
 * element at [index] swaps with its neighbour ([up] or down). The move
 * buttons' `enabled` guards already clamp [index], so no bounds check here.
 */
private fun <T> List<T>.moved(index: Int, up: Boolean): List<T> = toMutableList().apply {
    add(index, removeAt(if (up) index - 1 else index + 1))
}

/**
 * The advanced-rules editor: one card per [LanguageRule] (declared order
 * = priority order, so cards carry move-up/move-down too), each editable
 * (applies-to picker, title-pattern field, language pickers, subtitle-mode
 * picker) and deletable, plus an add row capped at [TRACK_RULES_MAX] rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackRulesEditorSheet(
    rules: LanguageRuleSet,
    onDismiss: () -> Unit,
    onChange: (LanguageRuleSet) -> Unit,
) {
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }
    var editingRuleId by remember { mutableStateOf<String?>(null) }
    val anyLanguageLabel = stringResource(Res.string.settings_track_rule_any_language)

    fun updateRules(updated: List<LanguageRule>) = onChange(rules.copy(rules = updated))

    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(title = stringResource(Res.string.settings_track_rules), icon = Tabler.Outline.Filter)
            if (rules.rules.isEmpty()) {
                Text(
                    stringResource(Res.string.settings_track_editor_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.heightIn(
                    max = with(LocalDensity.current) {
                        LocalWindowInfo.current.containerSize.height.toDp() * 0.35f
                    },
                ),
            ) {
                itemsIndexed(rules.rules, key = { _, rule -> rule.id }) { index, rule ->
                    val shape = expressiveListShape(index, rules.rules.size)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { editingRuleId = rule.id }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                ruleSummarised(rule, anyLanguageLabel),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${rule.appliesTo.displayName}" +
                                    (rule.titlePattern?.takeIf { it.isNotBlank() }?.let { " · /$it/" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            enabled = index > 0,
                            onClick = { updateRules(rules.rules.moved(index, up = true)) },
                        ) {
                            Icon(
                                Tabler.Outline.ChevronUp,
                                contentDescription = stringResource(Res.string.settings_track_editor_move_up),
                            )
                        }
                        IconButton(
                            enabled = index < rules.rules.lastIndex,
                            onClick = { updateRules(rules.rules.moved(index, up = false)) },
                        ) {
                            Icon(
                                Tabler.Outline.ChevronDown,
                                contentDescription = stringResource(Res.string.settings_track_editor_move_down),
                            )
                        }
                        IconButton(onClick = { updateRules(rules.rules - rule) }) {
                            Icon(
                                Tabler.Outline.Trash,
                                contentDescription = stringResource(Res.string.settings_track_rule_delete),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.padding(top = 8.dp))
            val capReached = rules.rules.size >= TRACK_RULES_MAX
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !capReached) {
                        // Collision-free id: smallest "rule-N" suffix not in use,
                        // so delete-then-add can never produce a duplicate
                        // LazyColumn key.
                        var n = rules.rules.size + 1
                        while (rules.rules.any { it.id == "rule-$n" }) n++
                        val newRule = LanguageRule(id = "rule-$n")
                        updateRules(rules.rules + newRule)
                        editingRuleId = newRule.id
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Tabler.Outline.Plus, contentDescription = null)
                Spacer(Modifier.padding(start = 12.dp))
                Text(
                    if (capReached) {
                        stringResource(Res.string.settings_track_rules_cap, TRACK_RULES_MAX)
                    } else {
                        stringResource(Res.string.settings_track_rules_add)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (capReached) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    // The per-rule editor: pickers ride the shared dispatcher; the title
    // pattern rides the text sheet. Editing targets the rule by id so a
    // reorder mid-edit can never write to the wrong card.
    editingRuleId?.let { id ->
        val rule = rules.rules.firstOrNull { it.id == id }
        if (rule == null) {
            editingRuleId = null
        } else {
            TrackRuleEditSheet(
                rule = rule,
                onDismiss = { editingRuleId = null },
                onSave = { updated ->
                    updateRules(rules.rules.map { if (it.id == id) updated else it })
                    editingRuleId = null
                },
            )
        }
    }

    SettingsPickerDialog(state = activePicker, onDismiss = { activePicker = null })
}

/** "eng → deu, full dialogue" — one line per card. */
private fun ruleSummarised(rule: LanguageRule, anyLanguage: String): String {
    val audio = rule.audioLanguages.firstOrNull() ?: anyLanguage
    val subtitle = rule.subtitleLanguages.firstOrNull() ?: anyLanguage
    return "$audio → $subtitle · ${rule.subtitleMode.displayName}"
}

/** One [LanguageRule]'s editor: pickers per axis + the title-pattern text sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackRuleEditSheet(
    rule: LanguageRule,
    onDismiss: () -> Unit,
    onSave: (LanguageRule) -> Unit,
) {
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }
    val anyLanguageLabel = stringResource(Res.string.settings_track_rule_any_language)
    // Pre-resolved strings: picker construction happens inside non-composable
    // onClick lambdas, so every stringResource call must happen here.
    val appliesToTitle = stringResource(Res.string.settings_track_rule_applies_to)
    val patternTitle = stringResource(Res.string.settings_track_rule_title_pattern)
    val patternHelper = stringResource(Res.string.settings_track_rule_title_pattern_helper)
    val audioLangTitle = stringResource(Res.string.settings_track_rule_audio_language)
    val subtitleLangTitle = stringResource(Res.string.settings_track_rule_subtitle_language)
    val subtitleModeTitle = stringResource(Res.string.settings_track_rule_subtitle_mode)
    val editTitle = stringResource(Res.string.settings_track_rule_edit)
    val languageCodes = languages.mapNotNull { it.first }

    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(title = editTitle, icon = Tabler.Outline.Filter)
            SettingListItem(
                icon = Tabler.Outline.Movie,
                title = appliesToTitle,
                subtitle = "",
                trailingText = rule.appliesTo.displayName,
                onClick = {
                    activePicker = PickerState.List(
                        title = appliesToTitle,
                        items = RuleContentType.entries,
                        label = { it.displayName },
                        isSelected = { it == rule.appliesTo },
                        onSelect = { value -> onSave(rule.copy(appliesTo = value)) },
                    )
                },
            )
            SettingListItem(
                icon = Tabler.Outline.Typography,
                title = patternTitle,
                subtitle = patternHelper,
                trailingText = rule.titlePattern?.takeIf { it.isNotBlank() } ?: "—",
                onClick = {
                    activePicker = PickerState.Text(
                        title = patternTitle,
                        initialText = rule.titlePattern.orEmpty(),
                        helperText = patternHelper,
                        onSave = { pattern -> onSave(rule.copy(titlePattern = pattern.trim().takeIf { it.isNotEmpty() })) },
                    )
                },
            )
            SettingListItem(
                icon = Tabler.Outline.Music,
                title = audioLangTitle,
                subtitle = "",
                trailingText = rule.audioLanguages.firstOrNull()?.let { languageNameByCode[it] ?: it }
                    ?: anyLanguageLabel,
                onClick = {
                    activePicker = PickerState.List(
                        title = audioLangTitle,
                        items = listOf<String?>(null) + languageCodes,
                        label = { code ->
                            code?.let { languageNameByCode[it] ?: it } ?: anyLanguageLabel
                        },
                        isSelected = { it == rule.audioLanguages.firstOrNull() },
                        onSelect = { code ->
                            onSave(rule.copy(audioLanguages = listOfNotNull(code)))
                        },
                    )
                },
            )
            SettingListItem(
                icon = Tabler.Outline.Subtitles,
                title = subtitleLangTitle,
                subtitle = "",
                trailingText = rule.subtitleLanguages.firstOrNull()?.let { languageNameByCode[it] ?: it }
                    ?: anyLanguageLabel,
                onClick = {
                    activePicker = PickerState.List(
                        title = subtitleLangTitle,
                        items = listOf<String?>(null) + languageCodes,
                        label = { code ->
                            code?.let { languageNameByCode[it] ?: it } ?: anyLanguageLabel
                        },
                        isSelected = { it == rule.subtitleLanguages.firstOrNull() },
                        onSelect = { code ->
                            onSave(rule.copy(subtitleLanguages = listOfNotNull(code)))
                        },
                    )
                },
            )
            SettingListItem(
                icon = Tabler.Outline.Filter,
                title = subtitleModeTitle,
                subtitle = "",
                trailingText = rule.subtitleMode.displayName,
                onClick = {
                    activePicker = PickerState.List(
                        title = subtitleModeTitle,
                        items = SubtitleTrackMode.entries,
                        label = { it.displayName },
                        isSelected = { it == rule.subtitleMode },
                        onSelect = { mode -> onSave(rule.copy(subtitleMode = mode)) },
                    )
                },
            )
        }
    }

    SettingsPickerDialog(state = activePicker, onDismiss = { activePicker = null })
}
