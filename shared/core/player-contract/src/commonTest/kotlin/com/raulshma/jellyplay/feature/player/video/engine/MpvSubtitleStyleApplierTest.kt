package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the ONE subtitle-style write choreography both mpv engines run
 * ([MpvSubtitleStyleApplier]) through a fake [MpvPropertySurface] recording
 * every typed write — the four former per-engine bodies (Android init
 * options / Android runtime properties / desktop runtime) previously drifted
 * here, most notably the desktop writing the user's font size as an absolute
 * `sub-font-size` instead of the reference-pin + multiplicative `sub-scale`
 * discipline and omitting `sub-font` / `sub-margin-y` entirely.
 */
class MpvSubtitleStyleApplierTest {

    /** One recorded surface write, with the write kind the choreography chose. */
    private sealed interface Write {
        data class Opt(val name: String, val value: String) : Write
        data class Str(val name: String, val value: String) : Write
        data class Dbl(val name: String, val value: Double) : Write
        data class Num(val name: String, val value: Int) : Write
        data class Flag(val name: String, val value: Boolean) : Write
    }

    private class FakeSurface : MpvPropertySurface {
        val writes = mutableListOf<Write>()

        override fun setOptionString(name: String, value: String) {
            writes += Write.Opt(name, value)
        }

        override fun setPropertyString(name: String, value: String) {
            writes += Write.Str(name, value)
        }

        override fun setPropertyDouble(name: String, value: Double) {
            writes += Write.Dbl(name, value)
        }

        override fun setPropertyInt(name: String, value: Int) {
            writes += Write.Num(name, value)
        }

        override fun setPropertyBoolean(name: String, value: Boolean) {
            writes += Write.Flag(name, value)
        }
    }

    private val customStyle = SubtitleStyle(
        applyCustomStyle = true,
        fontSize = 36,
        fontFamilyName = "Roboto",
        verticalPosition = 0.1f,
        edgeType = SubtitleEdgeType.OUTLINE,
        borderWidth = 3.5f,
        bold = true,
    )

    @Test
    fun runtime_customStyle_fullTypedChoreography() {
        val surface = FakeSurface()
        MpvSubtitleStyleApplier.apply(
            surface = surface,
            style = customStyle,
            phase = MpvSubtitleStylePhase.RUNTIME,
            ownedKeys = emptySet(),
            fallbackFontFamily = "BundledSans",
            subtitleDelayMs = -250L,
        )
        assertEquals<List<Write>>(
            listOf(
                // App-owned visibility flag first.
                Write.Flag("sub-visibility", true),
                // The mapping's custom string pairs.
                Write.Str("sub-color", "#FFFFFFFF"),
                Write.Str("sub-back-color", "#00000000"),
                Write.Str("sub-border-color", "#FF000000"),
                Write.Str("sub-shadow-color", "#FF000000"),
                Write.Str("sub-border-style", "outline-and-shadow"),
                Write.Str("sub-ass-override", "scale"),
                Write.Str("sub-ass-justify", "yes"),
                Write.Str("sub-bold", "yes"),
                Write.Str("sub-italic", "no"),
                // Typed custom magnitudes (border/shadow BEFORE font/scale).
                Write.Dbl("sub-border-size", 3.5),
                Write.Dbl("sub-shadow-offset", 0.0),
                // User family wins over the bundled fallback.
                Write.Str("sub-font", "Roboto"),
                Write.Dbl("sub-scale", 1.5), // 36 / 24 — the multiplicative rule
                // Shared tail: reference-pinned size, bottom-up pos, zero margin.
                Write.Dbl("sub-font-size", 55.0),
                Write.Num("sub-pos", 90), // 100 - (0.1 * 100)
                Write.Num("sub-margin-y", 0),
                Write.Dbl("sub-delay", -0.25),
            ),
            surface.writes,
        )
    }

    @Test
    fun runtime_defaultStyle_resetChoreographyWithFlagTypedJustify() {
        val surface = FakeSurface()
        MpvSubtitleStyleApplier.apply(
            surface = surface,
            style = SubtitleStyle(applyCustomStyle = false, verticalPosition = 0.2f),
            phase = MpvSubtitleStylePhase.RUNTIME,
            ownedKeys = emptySet(),
            fallbackFontFamily = "BundledSans",
            subtitleDelayMs = 0L,
        )
        assertEquals<List<Write>>(
            listOf(
                Write.Flag("sub-visibility", true),
                // The mapping's native-default reset pairs — sub-ass-justify is
                // flag-typed on mpv, so it rides the boolean setter.
                Write.Str("sub-color", "#FFFFFFFF"),
                Write.Str("sub-back-color", "#00000000"),
                Write.Str("sub-border-color", "#FF000000"),
                Write.Str("sub-shadow-color", "#FF000000"),
                Write.Str("sub-border-style", "outline-and-shadow"),
                Write.Str("sub-ass-override", "no"),
                Write.Flag("sub-ass-justify", false),
                Write.Str("sub-bold", "no"),
                Write.Str("sub-italic", "no"),
                // Fallback font (no user family on the default branch), then
                // the default magnitudes.
                Write.Str("sub-font", "BundledSans"),
                Write.Dbl("sub-border-size", 3.0),
                Write.Dbl("sub-shadow-offset", 0.0),
                Write.Dbl("sub-scale", 1.0),
                Write.Dbl("sub-font-size", 55.0),
                Write.Num("sub-pos", 80), // 100 - (0.2 * 100)
                Write.Num("sub-margin-y", 0),
                Write.Dbl("sub-delay", 0.0),
            ),
            surface.writes,
        )
    }

    @Test
    fun init_customStyle_allOptionStringWrites_andNoVisibilityFlag() {
        val surface = FakeSurface()
        MpvSubtitleStyleApplier.apply(
            surface = surface,
            style = customStyle,
            phase = MpvSubtitleStylePhase.INIT,
            ownedKeys = emptySet(),
            fallbackFontFamily = "BundledSans",
            subtitleDelayMs = 1500L,
        )
        assertEquals<List<Write>>(
            listOf(
                Write.Opt("sub-color", "#FFFFFFFF"),
                Write.Opt("sub-back-color", "#00000000"),
                Write.Opt("sub-border-color", "#FF000000"),
                Write.Opt("sub-shadow-color", "#FF000000"),
                Write.Opt("sub-border-style", "outline-and-shadow"),
                Write.Opt("sub-ass-override", "scale"),
                Write.Opt("sub-ass-justify", "yes"),
                Write.Opt("sub-bold", "yes"),
                Write.Opt("sub-italic", "no"),
                // Init custom: font + scale only (no typed numerics — the
                // runtime path owns border/shadow).
                Write.Opt("sub-font", "Roboto"),
                Write.Opt("sub-scale", "1.5"),
                // Tail as plain strings, reference-pin included.
                Write.Opt("sub-font-size", "55"),
                Write.Opt("sub-pos", "90"),
                Write.Opt("sub-margin-y", "0"),
                Write.Opt("sub-delay", "1.5"),
            ),
            surface.writes,
        )
    }

    @Test
    fun init_defaultStyle_initSubsetOnly() {
        val surface = FakeSurface()
        MpvSubtitleStyleApplier.apply(
            surface = surface,
            style = SubtitleStyle(applyCustomStyle = false),
            phase = MpvSubtitleStylePhase.INIT,
            ownedKeys = emptySet(),
            fallbackFontFamily = null,
            subtitleDelayMs = 0L,
        )
        assertEquals<List<Write>>(
            listOf(
                // defaultInitEntries: the ass-override/bold/italic subset only
                // (no colors — those ride runtime property writes).
                Write.Opt("sub-ass-override", "no"),
                Write.Opt("sub-bold", "no"),
                Write.Opt("sub-italic", "no"),
                // No bundled fallback at init on a fontless host → sans-serif.
                Write.Opt("sub-font", "sans-serif"),
                Write.Opt("sub-scale", "1.0"),
                Write.Opt("sub-font-size", "55"),
                Write.Opt("sub-pos", "95"), // 100 - (0.05 * 100), the model default
                Write.Opt("sub-margin-y", "0"),
                Write.Opt("sub-delay", "0.0"),
            ),
            surface.writes,
        )
    }

    @Test
    fun ownedKeys_skipEveryMatchingWrite_butNeverTheAppOwnedPair() {
        val surface = FakeSurface()
        MpvSubtitleStyleApplier.apply(
            surface = surface,
            style = customStyle,
            phase = MpvSubtitleStylePhase.RUNTIME,
            ownedKeys = setOf("sub-font", "sub-scale", "sub-pos", "sub-bold"),
            fallbackFontFamily = "BundledSans",
            subtitleDelayMs = 0L,
        )
        val written = surface.writes.map { write ->
            when (write) {
                is Write.Opt -> write.name
                is Write.Str -> write.name
                is Write.Dbl -> write.name
                is Write.Num -> write.name
                is Write.Flag -> write.name
            }
        }.toSet()
        // User-owned styling keys are never written — the pair list is
        // filtered AND the scalar writes gate.
        assertFalse("sub-font" in written)
        assertFalse("sub-scale" in written)
        assertFalse("sub-pos" in written)
        assertFalse("sub-bold" in written)
        // sub-visibility and sub-delay stay app-owned regardless.
        assertTrue(surface.writes.contains(Write.Flag("sub-visibility", true)))
        assertTrue(surface.writes.contains(Write.Dbl("sub-delay", 0.0)))
    }

    @Test
    fun fontSizeDiscipline_referencePinAndMultiplicativeScaleForAnySize() {
        // The recorded drift this applier fixes (desktop parity): the user's
        // size NEVER lands on sub-font-size directly — sub-font-size is always
        // the libass reference (55) and the size rides sub-scale = size / 24.
        listOf(12, 24, 36, 55, 72).forEach { size ->
            val surface = FakeSurface()
            MpvSubtitleStyleApplier.apply(
                surface = surface,
                style = SubtitleStyle(applyCustomStyle = true, fontSize = size),
                phase = MpvSubtitleStylePhase.RUNTIME,
                ownedKeys = emptySet(),
                fallbackFontFamily = null,
                subtitleDelayMs = 0L,
            )
            assertTrue(surface.writes.contains(Write.Dbl("sub-font-size", 55.0)), "size=$size")
            assertTrue(
                surface.writes.contains(Write.Dbl("sub-scale", size / 24.0)),
                "size=$size",
            )
        }
    }
}
