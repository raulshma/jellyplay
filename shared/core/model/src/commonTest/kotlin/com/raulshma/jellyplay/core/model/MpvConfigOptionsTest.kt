package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.Test

class MpvConfigOptionsTest {

    @Test
    fun `empty text yields no options`() {
        assertEquals(emptyList<MpvOption>(), parseMpvConfigOptions(""))
    }

    @Test
    fun `blank lines and comments are ignored`() {
        val text = """
            # this is a comment
               # indented comment

            scale=lanczos
        """.trimIndent()
        assertEquals(listOf(MpvOption("scale", "lanczos")), parseMpvConfigOptions(text))
    }

    @Test
    fun `key value pairs preserve order and trim whitespace`() {
        val text = """
            scale=ewa_lanczossharp
              tscale = mitchell
            tone-mapping=bt.2390
        """.trimIndent()
        assertEquals(
            listOf(
                MpvOption("scale", "ewa_lanczossharp"),
                MpvOption("tscale", "mitchell"),
                MpvOption("tone-mapping", "bt.2390"),
            ),
            parseMpvConfigOptions(text),
        )
    }

    @Test
    fun `bare key is treated as boolean flag yes`() {
        assertEquals(listOf(MpvOption("deband", "yes")), parseMpvConfigOptions("deband"))
    }

    @Test
    fun `empty value is preserved for list-reset semantics`() {
        assertEquals(listOf(MpvOption("vf", "")), parseMpvConfigOptions("vf="))
    }

    @Test
    fun `value containing equals sign keeps it`() {
        assertEquals(listOf(MpvOption("vf", "lavfi=[scale=cuda]")), parseMpvConfigOptions("vf=lavfi=[scale=cuda]"))
    }

    @Test
    fun `line with only equals and key is dropped`() {
        assertEquals(emptyList<MpvOption>(), parseMpvConfigOptions("=value"))
    }
}
