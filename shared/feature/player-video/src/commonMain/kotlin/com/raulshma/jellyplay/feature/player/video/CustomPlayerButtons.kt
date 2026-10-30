package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * The user-authored custom on-screen player buttons (issue #27), parsed from
 * the `mpvCustomButtons` preference (a JSON list). mpv-only — the layer that
 * renders them gates on the active engine.
 *
 * JSON schema (one object per button):
 *
 * ```json
 * [
 *   {
 *     "label": "+30s",
 *     "tap":        {"type": "seek", "seconds": 30},
 *     "longPress":  {"type": "toggleFilter", "name": "sharpen"},
 *     "position": "right",
 *     "order": 1
 *   }
 * ]
 * ```
 *
 *  - `label` (required, non-blank) — the caption rendered on the button; no
 *    app strings are involved (the user authors their own labels).
 *  - `tap` / `longPress` (optional each, at least one required) — the
 *    [CustomButtonAction] payloads below.
 *  - `position` — `"left"` | `"right"` (default `right`); which screen edge
 *    the button's stack hugs.
 *  - `order` — optional sort key (default: array position); lower first.
 *
 * Actions (`"type"` discriminator):
 *  - `{"type":"seek","seconds":N}` — relative seek through the screen's
 *    routed seek funnel (SyncPlay → cast → local). Non-zero finite N.
 *  - `{"type":"toggleFilter","name":"<mpv filter>"}` — mpv `vf toggle <name>`
 *    through the engine command seam.
 *  - `{"type":"command","cmd":"<raw mpv command>"}` — split on whitespace and
 *    executed through the engine command seam.
 *  - `{"type":"sheet","sheet":"speed"|"audio"|"subtitle"|"quality"|"chapters"|"aspect"}`
 *    — opens the matching player sheet through the screen's sheet funnel.
 *  - `{"type":"customButton","id":N}` — dispatches button N's TAP action
 *    (array position in the parsed, capped list; the self-alias idiom for
 *    re-using one button's action elsewhere). Out-of-range ids and reference
 *    loops no-op at execution time.
 *
 * Parse policy (defensive by design — the input is free-form user JSON):
 * invalid JSON, a non-object entry, a missing/blank label, a button with no
 * actions, or an UNKNOWN/invalid action type drops THAT BUTTON ONLY; the
 * parser never throws. Valid buttons are capped at [MAX_BUTTONS] (first wins)
 * and sorted by `order` (array position breaking ties).
 */
@Immutable
enum class CustomButtonSide {
    LEFT,
    RIGHT,
}

/** One button action — the sealed execution vocabulary of the schema above. */
@Immutable
sealed interface CustomButtonAction {
    /** Relative seek by [seconds] (negative = backward) via the routed seek funnel. */
    data class Seek(val seconds: Double) : CustomButtonAction

    /** Toggles an mpv video filter by name (`vf toggle <filterName>`). */
    data class ToggleFilter(val filterName: String) : CustomButtonAction

    /** A raw mpv command line (split on whitespace at execution). */
    data class Command(val command: String) : CustomButtonAction

    /** Opens one of the player sheets. */
    data class OpenSheet(val sheet: PlayerSheet) : CustomButtonAction

    /** Dispatches the tap action of the button at parsed-list position [id]. */
    data class ButtonRef(val id: Int) : CustomButtonAction
}

/** One parsed custom button (see [CustomPlayerButtons] for the schema). */
@Immutable
data class CustomPlayerButton(
    val label: String,
    val tap: CustomButtonAction?,
    val longPress: CustomButtonAction?,
    /** Which screen edge the button's stack hugs. Default RIGHT. */
    val side: CustomButtonSide,
    /** Sort key within the side's stack (lower first). */
    val order: Int,
)

/**
 * The pure parser for the `mpvCustomButtons` preference. Pure and
 * side-effect-free so the schema is jvmTest-pinned without an engine or
 * Compose.
 */
object CustomPlayerButtons {

    /** The rendered-button cap — the on-screen stack must stay bounded. */
    const val MAX_BUTTONS = 6

    /**
     * The mpv command line a filter action translates to. `vf toggle` appends
     * the filter when absent and removes it when present (mpv >= 0.34); a
     * failed/unsupported toggle surfaces as the engine seam's `false` and is
     * silently ignored by the layer.
     */
    fun mpvToggleFilterCommand(filterName: String): String = "vf toggle $filterName"

    /** Lenient: unknown JSON keys are ignored; malformed values drop the button. */
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class ButtonDef(
        val label: String? = null,
        val tap: ActionDef? = null,
        val longPress: ActionDef? = null,
        val position: String? = null,
        val order: Int? = null,
    )

    @Serializable
    private data class ActionDef(
        val type: String? = null,
        val seconds: Double? = null,
        val name: String? = null,
        val cmd: String? = null,
        val sheet: String? = null,
        val id: Int? = null,
    )

    /**
     * Parses [jsonText] into the capped, ordered button list. Empty/blank
     * text and structurally invalid JSON return an empty list; per-entry
     * failures drop only that entry (never throw).
     */
    fun parse(jsonText: String): List<CustomPlayerButton> {
        if (jsonText.isBlank()) return emptyList()
        val array = try {
            json.parseToJsonElement(jsonText)
        } catch (_: Exception) {
            return emptyList()
        }
        val elements = (array as? List<kotlinx.serialization.json.JsonElement>) ?: return emptyList()
        val parsed = elements.mapIndexedNotNull { index, element ->
            try {
                decodeButton(element, index)
            } catch (_: Exception) {
                null
            }
        }
        return parsed.take(MAX_BUTTONS).withIndex()
            .sortedWith(compareBy({ it.value.order }, { it.index }))
            .map { it.value }
    }

    /** One entry → one button, or null when the entry drops (any failure shape). */
    private fun decodeButton(element: kotlinx.serialization.json.JsonElement, arrayIndex: Int): CustomPlayerButton? {
        val def = json.decodeFromJsonElement<ButtonDef>(element)
        val label = def.label?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        // An UNKNOWN/invalid action anywhere on the button drops the whole
        // button (toAction returns null for it) — the schema's skip policy.
        val tapAction = def.tap?.let { action -> action.toAction() ?: return null }
        val longPressAction = def.longPress?.let { action -> action.toAction() ?: return null }
        if (tapAction == null && longPressAction == null) return null
        val side = when (def.position?.lowercase()) {
            "left" -> CustomButtonSide.LEFT
            else -> CustomButtonSide.RIGHT
        }
        return CustomPlayerButton(
            label = label,
            tap = tapAction,
            longPress = longPressAction,
            side = side,
            // No explicit order = the array position (the schema's default),
            // so unordered buttons keep their declared sequence.
            order = def.order ?: arrayIndex,
        )
    }

    /** [ActionDef] → [CustomButtonAction], or null when the action is unknown/invalid. */
    private fun ActionDef.toAction(): CustomButtonAction? = when (type?.lowercase()) {
        "seek" -> seconds?.takeIf { it.isFinite() && it != 0.0 }?.let(CustomButtonAction::Seek)
        "togglefilter" -> name?.trim().takeUnless { it.isNullOrEmpty() }?.let(CustomButtonAction::ToggleFilter)
        "command" -> cmd?.trim().takeUnless { it.isNullOrEmpty() }?.let(CustomButtonAction::Command)
        "sheet" -> sheet?.lowercase()?.let(::sheetFor)?.let(CustomButtonAction::OpenSheet)
        "custombutton" -> id?.takeIf { it >= 0 }?.let(CustomButtonAction::ButtonRef)
        else -> null
    }

    /** The schema's sheet name → [PlayerSheet] table; unknown names drop the button. */
    private fun sheetFor(name: String): PlayerSheet? = when (name) {
        "speed" -> PlayerSheet.Speed
        "audio" -> PlayerSheet.Audio
        "subtitle" -> PlayerSheet.SubtitleHub
        "quality" -> PlayerSheet.Quality
        "chapters" -> PlayerSheet.Chapter
        "aspect" -> PlayerSheet.AspectRatio
        else -> null
    }
}
