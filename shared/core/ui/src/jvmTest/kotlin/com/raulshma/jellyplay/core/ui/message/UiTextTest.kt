package com.raulshma.jellyplay.core.ui.message

import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * Pins the non-composable half of the [UiText] value model (the `asString`
 * resolution is composition-bound and untested):
 *
 *  - [UiText.Raw] wraps an already-resolved string with data semantics;
 *  - [UiText.Resource] captures the resource AND the format-arg list (arg
 *    order and content are part of its identity);
 *  - `uiTextOf` forwards its vararg tail into [UiText.Resource.args];
 *  - `String.asUiText` always produces a Raw — even when the string equals a
 *    resource's current rendering, the two remain distinct values.
 */
class UiTextTest {

    @Test
    fun raw_carriesTheResolvedString() {
        val raw = UiText.Raw("Server unreachable")

        assertEquals("Server unreachable", raw.value)
    }

    @Test
    fun resource_capturesResAndArgsOrdering() {
        val text = UiText.Resource(Res.string.core_cancel, listOf("a", 2, 3L))

        assertEquals(Res.string.core_cancel, text.res)
        assertEquals(listOf<Any>("a", 2, 3L), text.args)
    }

    @Test
    fun resource_argsArePartOfEquality() {
        val base = UiText.Resource(Res.string.core_cancel, listOf("x"))

        assertEquals(UiText.Resource(Res.string.core_cancel, listOf("x")), base)
        assertNotEquals(base, UiText.Resource(Res.string.core_cancel, listOf("y")))
        assertNotEquals(base, UiText.Resource(Res.string.core_cancel))
    }

    @Test
    fun uiTextOf_forwardsVarargTail() {
        val text = uiTextOf(Res.string.core_cancel, "one", "two")

        assertIs<UiText.Resource>(text)
        assertEquals(listOf<Any>("one", "two"), text.args)
    }

    @Test
    fun uiTextOf_withoutArgs_yieldsEmptyArgList() {
        val text = uiTextOf(Res.string.core_cancel)

        assertEquals(emptyList(), (text as UiText.Resource).args)
    }

    @Test
    fun stringAsUiText_alwaysWrapsAsRaw() {
        val text = "dynamic server message".asUiText()

        assertIs<UiText.Raw>(text)
        assertEquals("dynamic server message", text.value)
    }
}
