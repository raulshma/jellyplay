package com.raulshma.jellyplay.feature.subtitle.tester

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.player.video.components.SubtitleStyleControls
import com.raulshma.jellyplay.feature.subtitle.tester.components.PreviewTile
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.Res
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_apply
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_discard_cancel
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_discard_confirm
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_discard_message
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_discard_title
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_engine
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_hint_hot
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_hint_libvlc
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_mode_hdr
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_mode_sdr
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_preset
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_reset
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_reloading
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_title
import com.raulshma.jellyplay.feature.subtitle.tester.preview.PreviewEngineHost
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleTesterScreen(
    onBack: () -> Unit,
    viewModel: SubtitleTesterViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeEngine by viewModel.activeEngine.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val host = remember { PreviewEngineHost(context, onSurfaceReady = {}) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // TV: grab initial focus on the first control so the first D-pad press lands
    // in the form instead of nowhere.
    val initialFocusRequester = remember { FocusRequester() }
    RequestOrRestoreFocus(initialFocusRequester, "subtitle_tester_init")

    // SAF font picker — mirrors the player's flow: the picked uri is copied into
    // the shared font cache by the ViewModel; only the local file survives.
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) viewModel.installUserFont(uri)
    }

    LaunchedEffect(activeEngine) {
        val engine = activeEngine
        if (engine != null) host.attach(engine) else host.detach()
    }

    // System/gesture back matches the toolbar back: prompt to discard when
    // there are unsaved edits, otherwise pop. The tester can render inside the
    // bare full-screen container (reached from the player), which — unlike the
    // phone/TV shells — installs no back handler of its own.
    JellyPlayBackHandler(enabled = true) {
        if (state.isDirty) showDiscardDialog = true else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.subtitle_tester_title)) },
                navigationIcon = {
                    TextButton(onClick = {
                        if (state.isDirty) showDiscardDialog = true else onBack()
                    }) { Text("←") }
                },
                actions = {
                    TextButton(onClick = { viewModel.reset() }, enabled = state.isDirty) {
                        Text(stringResource(Res.string.subtitle_tester_reset))
                    }
                    TextButton(onClick = { viewModel.applyAndExit(onBack) }, enabled = state.isDirty) {
                        Text(stringResource(Res.string.subtitle_tester_apply))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Sticky preview region: mode toggle + pickers + preview tile stay
            // pinned at the top so the user sees style changes instantly while
            // scrolling the controls below. Only the style form scrolls.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Mode toggle (SDR / HDR).
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SubtitleStyleMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { viewModel.setMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, SubtitleStyleMode.entries.size),
                            modifier = if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier,
                        ) {
                            Text(stringResource(if (mode == SubtitleStyleMode.SDR) Res.string.subtitle_tester_mode_sdr else Res.string.subtitle_tester_mode_hdr))
                        }
                    }
                }

                EnginePicker(selected = state.previewEngine, onSelect = { viewModel.switchEngine(it) })
                PresetPicker(selected = state.samplePresetId, onSelect = { viewModel.switchPreset(it) })

                PreviewTile(
                    host = host,
                    isApplying = state.isApplying,
                    applyingLabel = stringResource(Res.string.subtitle_tester_reloading),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                EngineHintRow(previewEngine = state.previewEngine)
            }

            // Scrollable controls beneath the pinned preview.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRestorer()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleStyleControls(
                    currentStyle = state.activeWorkingStyle,
                    onStyleChange = { viewModel.updateStyle(it) },
                    capabilities = state.engineCapabilities,
                    onPickFont = { fontPickerLauncher.launch(arrayOf("*/*")) },
                    // The tester always edits the resolved style; the player owns the
                    // override toggle. Reset lives in the top-app-bar, so no chip here.
                    showOverrideToggle = false,
                    onReset = null,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.subtitle_tester_discard_title),
            message = stringResource(Res.string.subtitle_tester_discard_message),
            confirmText = stringResource(Res.string.subtitle_tester_discard_confirm),
            onConfirm = { onBack() },
            onDismiss = { showDiscardDialog = false },
            dismissText = stringResource(Res.string.subtitle_tester_discard_cancel),
            tone = ConfirmTone.NEUTRAL,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnginePicker(selected: PlayerType, onSelect: (PlayerType) -> Unit) {
    val engines = PlayerType.entries.filter { it != PlayerType.EXTERNAL }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.subtitle_tester_engine)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            engines.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name) },
                    onClick = { onSelect(type); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetPicker(selected: String, onSelect: (String) -> Unit) {
    val presets = SampleSubtitlePresets.ALL
    var expanded by remember { mutableStateOf(false) }
    val selectedName = stringResource(SampleSubtitlePresets.byId(selected).displayName)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.subtitle_tester_preset)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(stringResource(preset.displayName)) },
                    onClick = { onSelect(preset.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun EngineHintRow(previewEngine: PlayerType) {
    val text = if (previewEngine == PlayerType.LIBVLC) {
        stringResource(Res.string.subtitle_tester_hint_libvlc)
    } else {
        stringResource(Res.string.subtitle_tester_hint_hot)
    }
    Text(text, modifier = Modifier.padding(horizontal = 16.dp))
}
