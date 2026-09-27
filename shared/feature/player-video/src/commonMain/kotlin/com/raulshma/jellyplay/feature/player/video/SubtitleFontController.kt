package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the user-font install + the direct engine re-apply of the current
 * subtitle style, extracted from [VideoPlayerViewModel] (the
 * [SubtitlePreviewController] shape). The style EDIT choreography — mirror
 * write, engine-config sync, global persist with the per-item offset
 * preserved — stays in [SubtitleStyleController] (A7); this controller is
 * the font-facing sibling beside it: it turns a picked font file into a
 * style edit, and re-applies the current style to the engine's native
 * subtitle surface on demand (the zoom-safe strategy's visual-style path).
 *
 * Never references the ui state bag: the style mirror is read through the
 * [getStyleMirror] seam and every edit flows through [editStyle] (the VM
 * routes both onto its mirror and [SubtitleStyleController]), so the
 * god-count ratchet is unmoved.
 */
internal class SubtitleFontController(
    private val scope: CoroutineScope,
    private val fontProvider: FontProvider,
    private val getEngine: () -> MediaEngine?,
    /** Live read of the current style mirror (post-suspension reads stay live). */
    private val getStyleMirror: () -> SubtitleStyle,
    /** The style edit funnel (routes into [SubtitleStyleController.setStyle]). */
    private val editStyle: (SubtitleStyle) -> Unit,
) {

    /**
     * Installs a user-picked font (from a SAF `OpenDocument` pick) via
     * [FontProvider.installUserFont], then applies the resulting family
     * name/path as a style edit through [editStyle] (mirror write + engine
     * sync + global persist with the per-item offset preserved).
     *
     * No-op if the copy/parse fails (FontProvider returns null), leaving the
     * bundled fallback font in place. (The uri stringifies at the API
     * boundary — Android hands a SAF Uri's string form, desktop a file URI.)
     */
    fun installUserFont(uri: String) {
        scope.launch {
            val installed = fontProvider.installUserFont(uri) ?: return@launch
            editStyle(
                getStyleMirror().copy(
                    fontFamilyPath = installed.file.absolutePath,
                    fontFamilyName = installed.familyName,
                )
            )
        }
    }

    /**
     * Re-applies the CURRENT style mirror to the engine's own native
     * subtitle surface ([MediaEngine.applySubtitleStyle]) — the
     * visual-style-only path the zoom-safe strategy drives. No-op when no
     * engine is bound.
     */
    fun applySubtitleStyle() {
        val engine = getEngine() ?: return
        engine.applySubtitleStyle(getStyleMirror())
    }
}
