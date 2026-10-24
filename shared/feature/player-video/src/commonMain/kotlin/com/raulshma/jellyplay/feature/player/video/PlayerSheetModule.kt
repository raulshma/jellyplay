package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.components.HdrOptionsSheet
import com.raulshma.jellyplay.feature.player.video.components.RenderSheet
import com.raulshma.jellyplay.feature.player.video.session.SheetsSessionSlice

/**
 * The player sheet module (the GLOSSARY term): everything one player sheet
 * knows about itself — behind one small interface registered in
 * [PlayerSheetRegistry]. The Home row module's pattern applied to the sheet
 * whose facts restated themselves across the router's 19-branch `when` and
 * the manual `ALL_SHEETS` list (the HDR sheet measured nineteen files in its
 * landing fan).
 *
 * The pilot interface carries exactly what a router branch IS: the sheet key
 * ([sheet]) and the content slot ([Content]) — the branch body moved
 * verbatim, its sheet-local slice collections riding inside [Content] at the
 * exact placement the branches collected them (the placement rule: a slice
 * is collected only while its sheet is open, so the router root never ticks
 * with the stats cadence).
 *
 * NOT covered, on purpose: the sheet's
 * commands (the VM event arms — the glossary's "commands" half migrates when
 * the sheet does) and its slice region ([SessionSlices] stays VM-side). The
 * inbound entry points keep their raw-key lambdas at their call sites
 * (PlayerControls' HDR chip, the gear menu's Rendering entry): the bundle
 * those lambdas ride ([SheetControls]) is not declared here yet, and a
 * module-side declaration today would be ceremony without a consumer. What
 * DID fold is the sheet's address: the two entry lambdas and the HDR→Render
 * cross-sheet link reference the module's [sheet] identity instead of
 * restating the raw constant.
 *
 * Exactly two sheets are implemented so far ([HdrSheetModule],
 * [RenderSheetModule], chosen because they cross-link). The remaining
 * branches stayed hand-written in [PlayerSheetRouter]'s `when` and migrate
 * as touched — the Detail section module precedent
 * ([SubtitleHubSheetModule], its own file).
 */
internal interface PlayerSheetModule {

    /** The sheet this module owns — the router branch's key and the registry's identity. */
    val sheet: PlayerSheet

    /**
     * The content slot: the router branch's body. [scope]
     * bundles the region params the router already holds; a module reads what
     * its sheet needs and keeps its own remembers sheet-local.
     */
    @Composable
    fun Content(scope: PlayerSheetScope)
}

/**
 * The params a sheet's branch renders from — the exact surface
 * [PlayerSheetRouter] receives, bundled so a module's
 * [PlayerSheetModule.Content] slot takes one parameter and reads what its
 * sheet needs (the Detail section render scope's shape).
 */
@Immutable
internal data class PlayerSheetScope(
    val uiState: VideoPlayerUiState,
    val inputs: SheetRouterInputs,
    val viewModel: VideoPlayerViewModel,
    /** The sheets region's session slice (the GLOSSARY "Session slice" seam). */
    val sheets: SheetsSessionSlice,
)

/**
 * One registration per [PlayerSheet]: the migrated module, or the bare key
 * while the router's hand-written branch still owns the content. The
 * migrated factory derives the key from the module — a registration's key
 * half cannot drift from its module's [PlayerSheetModule.sheet].
 */
private class PlayerSheetRegistration private constructor(
    val sheet: PlayerSheet,
    val module: PlayerSheetModule?,
) {
    companion object {
        /** A migrated sheet — [PlayerSheetRegistry.registrationOf]'s migrated arms. */
        fun migrated(module: PlayerSheetModule): PlayerSheetRegistration =
            PlayerSheetRegistration(module.sheet, module)

        /** A not-yet-migrated sheet — the router's hand-written branch owns the content. */
        fun pending(sheet: PlayerSheet): PlayerSheetRegistration =
            PlayerSheetRegistration(sheet, module = null)
    }
}

/**
 * THE player sheet registry: every [PlayerSheet] maps to exactly one
 * registration. Explicit plain-list construction — never
 * `sealedSubclasses`, which needs kotlin-reflect, is not on the runtime
 * classpath, and crashed `PlayerSheetKt`'s `<clinit>` on launch (the
 * constraint the former manual `ALL_SHEETS` comment carried; the registry's
 * explicitness resolves it).
 *
 * A new sheet lands HERE (one `order` row + one `registrationOf` arm — the
 * exhaustive `when` turns a missing registration into a failing compile,
 * not a silent Saver restore gap) plus its module file once its wave comes.
 * PlayerSheetRegistryTest pins the list to the sealed class's declaration
 * order with its own exhaustive `when`.
 */
internal object PlayerSheetRegistry {

    /** Registration order = [PlayerSheet] declaration order (the former `ALL_SHEETS`). */
    private val order: List<PlayerSheet> = listOf(
        PlayerSheet.None,
        PlayerSheet.Speed,
        PlayerSheet.Audio,
        PlayerSheet.Chapter,
        PlayerSheet.PlaybackInfo,
        PlayerSheet.AspectRatio,
        PlayerSheet.SubtitleHub,
        PlayerSheet.AVSync,
        PlayerSheet.Decoder,
        PlayerSheet.Episodes,
        PlayerSheet.Queue,
        PlayerSheet.SyncPlay,
        PlayerSheet.Quality,
        PlayerSheet.PlaybackMode,
        PlayerSheet.SleepTimer,
        PlayerSheet.VideoFilter,
        PlayerSheet.Hdr,
        PlayerSheet.Render,
        PlayerSheet.Version,
    )

    /**
     * The registration decision per sheet — the compile-time totality gate.
     * Exhaustive over the sealed class, so a new [PlayerSheet] subtype fails
     * THIS file's compilation until its arm (with the matching [order] row)
     * lands: a new sheet cannot ship unregistered, which is the guarantee
     * the manual list could only promise by review.
     */
    private fun registrationOf(sheet: PlayerSheet): PlayerSheetRegistration = when (sheet) {
        PlayerSheet.None -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.Speed -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.Audio -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.Chapter -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.PlaybackInfo -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.AspectRatio -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.SubtitleHub -> PlayerSheetRegistration.migrated(SubtitleHubSheetModule)
        PlayerSheet.AVSync -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.Decoder -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.Episodes -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.Queue -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.SyncPlay -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.Quality -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.PlaybackMode -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.SleepTimer -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.VideoFilter -> PlayerSheetRegistration.pending(sheet)
        PlayerSheet.Hdr -> PlayerSheetRegistration.migrated(HdrSheetModule)
        PlayerSheet.Render -> PlayerSheetRegistration.migrated(RenderSheetModule)
        PlayerSheet.Version -> PlayerSheetRegistration.pending(sheet)
    }

    private val registrations: List<PlayerSheetRegistration> = order.map { registrationOf(it) }

    /**
     * Every registered sheet, in declaration order — the former manual
     * `ALL_SHEETS`, the enumeration [PlayerSheetSaver] restores against.
     */
    val all: List<PlayerSheet> = registrations.map { it.sheet }

    /** The module owning [sheet], or null while its router branch is hand-written. */
    fun moduleFor(sheet: PlayerSheet): PlayerSheetModule? =
        registrations.firstOrNull { it.sheet == sheet }?.module
}

// ── The wave-1 pilots ───────────────────────────────────────────────────────
// The HDR and Render sheets: they cross-link (the HDR sheet's "Open
// Rendering…" routes to the Render sheet), so migrating them together lets
// the cross-link become a module-to-module reference instead of a router-
// restated constant. The branch bodies moved verbatim from
// VideoPlayerScreenSheets.kt — including the HDR branch's sheet-local
// optimistic-mirror choreography and both branches' sheet-local collection
// placement.

/**
 * The HDR output sheet module (wave 1): the engine's live output facts plus
 * the HDR-specific engine config — mpv passthrough (toggle) or ExoPlayer
 * tone mapping (picker). Opened from the HDR chip in the playback-metadata
 * row; its Routes block cross-links to [RenderSheetModule] (the
 * tone-mapping presets live there).
 */
internal object HdrSheetModule : PlayerSheetModule {

    override val sheet: PlayerSheet = PlayerSheet.Hdr

    /**
     * The engine's live output facts are collected HERE (sheet-local) so the
     * router root doesn't tick with the stats cadence. Both engine picks read
     * through the sheets slice; the toggles keep a sheet-local optimistic
     * mirror (seeded from the pending lens / the global slice, re-seeded when
     * the async store round-trip lands) so the switch reflects the tap
     * immediately without a whole-vm state round trip.
     */
    @Composable
    override fun Content(scope: PlayerSheetScope) {
        val stats by scope.viewModel.videoStats.collectAsStateWithLifecycle()
        val sheets = scope.sheets
        val globalMpv = sheets.globalMpvConfig
        val globalExo = sheets.globalExoConfig
        val seededMpvPass = sheets.render.pending.hdrPassthrough ?: globalMpv.hdrPassthrough
        var mpvPassthrough by remember(seededMpvPass) { mutableStateOf(seededMpvPass) }
        var exoMode by remember(globalExo.hdrToneMapping) { mutableStateOf(globalExo.hdrToneMapping) }
        // The HDR-format pick's optimistic mirror (the persisted-pref twin of
        // the two engine-slice mirrors above — re-seeded when the DataStore
        // round-trip lands). Only the engines that HONOR the posture offer
        // the row: the output policy consumes it on mpv hosts + Exo; VLC and
        // the external hand-off cannot, so they hide it like the other
        // config blocks.
        var hdrFormat by remember(sheets.hdrFormatPreference) { mutableStateOf(sheets.hdrFormatPreference) }
        val isMpv = scope.uiState.preferredPlayerType == PlayerType.MPV
        val isExo = scope.uiState.preferredPlayerType == PlayerType.EXO_PLAYER
        HdrOptionsSheet(
            contentHdrType = scope.uiState.hdrType,
            observedHdrType = stats.videoHdrType,
            playerType = scope.uiState.preferredPlayerType,
            hdrOutputActive = stats.hdrOutputActive,
            hdrOutputNotice = stats.hdrOutputNotice,
            displayHdrTypes = stats.displayHdrTypes,
            mpvHdrPassthrough = mpvPassthrough.takeIf { isMpv },
            onMpvHdrPassthroughChange = { enabled ->
                mpvPassthrough = enabled
                scope.viewModel.onEvent(VideoPlayerUiEvent.SetMpvHdrPassthrough(enabled))
            },
            exoHdrToneMapping = exoMode.takeIf { isExo },
            onExoHdrToneMappingSelect = { mode ->
                exoMode = mode
                scope.viewModel.onEvent(VideoPlayerUiEvent.SetExoHdrToneMapping(mode))
            },
            hdrFormatPreference = hdrFormat.takeIf { isMpv || isExo },
            onHdrFormatPreferenceSelect = { preference ->
                hdrFormat = preference
                scope.sheets.setHdrFormatPreference(preference)
            },
            // The cross-sheet link declares through the sibling module's
            // registered key — the route target is the Render module's
            // identity, not a restated constant.
            onOpenRender = { scope.inputs.onSheetChange(RenderSheetModule.sheet) },
            onDismiss = scope.inputs.dismissSheet,
        )
    }
}

/**
 * The mpv render surface module (wave 1): shader pack / tone mapping /
 * render quality. The session render state is the sheet's source of truth;
 * picks apply to the running engine immediately and (with the toggle on)
 * persist to the series row. Opened from the gear menu's Rendering entry
 * (mpv engines only) and from [HdrSheetModule]'s Routes block.
 */
internal object RenderSheetModule : PlayerSheetModule {

    override val sheet: PlayerSheet = PlayerSheet.Render

    /**
     * The "save for this series" toggle is the sheet's router-scoped state:
     * it rides [SheetRouterInputs] (the same per-sheet-intent channel as the
     * SubtitleHub's reset-first flag) so it survives sheet close/reopen
     * exactly as the former router-held remember did.
     */
    @Composable
    override fun Content(scope: PlayerSheetScope) {
        // mpv render surface. The session render state is the sheet's source
        // of truth; picks apply to the running engine immediately and (with
        // the toggle on) persist to the series row.
        val sheets = scope.sheets
        val sessionRender = sheets.render
        val global = sheets.globalMpvConfig
        val effective = sessionRender.effectiveMpvConfig(global)
        val active = sessionRender.override
        // The persisted render prefs read the uiPrefs slice (the
        // VideoPlayerStore projection) and write through the session's pref
        // commands — the aggregate fanout's render-pref diff then rebuilds
        // the engine config so the pick reaches the running engine.
        val uiPrefs = scope.uiState.uiPrefs
        RenderSheet(
            shaderPack = active?.shaderPack ?: global.shaderPack,
            toneMapping = active?.toneMapping ?: global.toneMapping,
            // The effective quality (the session lens may hold a pick the
            // DataStore round-trip hasn't mirrored into the aggregate yet).
            renderQuality = effective.renderQuality,
            toneMappingCurve = uiPrefs.mpvToneMappingCurve,
            debandMode = uiPrefs.mpvDebandMode,
            motionInterpolation = uiPrefs.mpvMotionInterpolation,
            hasStoredOverride = active != null,
            canSaveForSeries = scope.uiState.media.seriesId != null || scope.inputs.itemId != null,
            saveForSeries = scope.inputs.saveRenderForSeries,
            onShaderPackSelect = { scope.viewModel.onEvent(VideoPlayerUiEvent.SetRenderShaderPack(it, scope.inputs.saveRenderForSeries)) },
            onToneMappingSelect = { scope.viewModel.onEvent(VideoPlayerUiEvent.SetRenderToneMapping(it, scope.inputs.saveRenderForSeries)) },
            onRenderQualitySelect = { scope.viewModel.onEvent(VideoPlayerUiEvent.SetRenderQuality(it)) },
            onToneMappingCurveSelect = { scope.viewModel.playbackSession.uiCommands.setMpvToneMappingCurve(it) },
            onDebandModeSelect = { scope.viewModel.playbackSession.uiCommands.setMpvDebandMode(it) },
            onMotionInterpolationChange = { scope.viewModel.playbackSession.uiCommands.setMpvMotionInterpolation(it) },
            onSaveForSeriesChange = { scope.inputs.onSaveRenderForSeriesChange(it) },
            onInherit = {
                scope.viewModel.onEvent(VideoPlayerUiEvent.ClearRenderOverride)
                scope.inputs.onSheetChange(PlayerSheet.None)
            },
            onDismiss = scope.inputs.dismissSheet,
        )
    }
}
