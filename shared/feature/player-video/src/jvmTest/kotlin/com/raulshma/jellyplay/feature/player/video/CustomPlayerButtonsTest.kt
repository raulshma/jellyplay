package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The [CustomPlayerButtons.parse] schema table (issue #27): the sealed action
 * vocabulary, the never-throw skip policy (invalid JSON / unknown types drop
 * only the offending button), the 6-button cap, and the order/side defaults.
 */
class CustomPlayerButtonsTest {

    private fun button(
        label: String = "B",
        tap: String? = null,
        longPress: String? = null,
        position: String? = null,
        order: Int? = null,
    ): String {
        val actions = listOfNotNull(
            tap?.let { """"tap": $it""" },
            longPress?.let { """"longPress": $it""" },
        ).joinToString(",")
        val extras = listOfNotNull(
            position?.let { """"position": "$it"""" },
            order?.let { """"order": $it""" },
        ).joinToString(",")
        return """{"label": "$label"${
            if (actions.isNotEmpty()) ", $actions" else ""
        }${
            if (extras.isNotEmpty()) ", $extras" else ""
        }}"""
    }

    private fun array(vararg buttons: String) = "[${buttons.joinToString(",")}]"

    // ── The action vocabulary ───────────────────────────────────────────────

    @Test
    fun `tap seek action parses`() {
        val parsed = CustomPlayerButtons.parse(array(button(tap = """{"type":"seek","seconds":30}""")))
        assertEquals(CustomButtonAction.Seek(30.0), parsed.single().tap)
    }

    @Test
    fun `toggle filter and raw command actions parse`() {
        val parsed = CustomPlayerButtons.parse(
            array(
                button(label = "Sharp", tap = """{"type":"toggleFilter","name":"sharpen"}"""),
                button(label = "Cmd", tap = """{"type":"command","cmd":"no-osd cycle pause"}"""),
            ),
        )
        assertEquals(CustomButtonAction.ToggleFilter("sharpen"), parsed[0].tap)
        assertEquals(CustomButtonAction.Command("no-osd cycle pause"), parsed[1].tap)
    }

    @Test
    fun `filter action maps to the vf toggle command`() {
        assertEquals(
            "vf toggle sharpen",
            CustomPlayerButtons.mpvToggleFilterCommand("sharpen"),
        )
    }

    @Test
    fun `sheet actions map onto the player sheets`() {
        val cases = mapOf(
            "speed" to PlayerSheet.Speed,
            "audio" to PlayerSheet.Audio,
            "subtitle" to PlayerSheet.SubtitleHub,
            "quality" to PlayerSheet.Quality,
            "chapters" to PlayerSheet.Chapter,
            "aspect" to PlayerSheet.AspectRatio,
        )
        val parsed = CustomPlayerButtons.parse(
            array(*cases.keys.map { button(tap = """{"type":"sheet","sheet":"$it"}""") }.toTypedArray()),
        )
        assertEquals(cases.values.toList(), parsed.map { (it.tap as CustomButtonAction.OpenSheet).sheet })
    }

    @Test
    fun `customButton ref parses`() {
        val parsed = CustomPlayerButtons.parse(array(button(tap = """{"type":"customButton","id":1}""")))
        assertEquals(CustomButtonAction.ButtonRef(1), parsed.single().tap)
    }

    // ── The skip policy ─────────────────────────────────────────────────────

    @Test
    fun `long-press-only button parses with null tap`() {
        val parsed = CustomPlayerButtons.parse(
            array(button(longPress = """{"type":"seek","seconds":-10}""")),
        )
        val only = parsed.single()
        assertEquals(null, only.tap)
        assertEquals(CustomButtonAction.Seek(-10.0), only.longPress)
    }

    @Test
    fun `unknown action type drops the whole button`() {
        val parsed = CustomPlayerButtons.parse(
            array(
                button(label = "Good", tap = """{"type":"seek","seconds":5}"""),
                button(label = "Bad", tap = """{"type":"teleport","seconds":5}"""),
                button(label = "Good2", tap = """{"type":"seek","seconds":7}"""),
            ),
        )
        assertEquals(listOf("Good", "Good2"), parsed.map { it.label })
    }

    @Test
    fun `invalid action payloads drop the button`() {
        val parsed = CustomPlayerButtons.parse(
            array(
                button(label = "Zero seek", tap = """{"type":"seek","seconds":0}"""),
                button(label = "NaN seek", tap = """{"type":"seek","seconds":"abc"}"""),
                button(label = "Blank filter", tap = """{"type":"toggleFilter","name":"  "}"""),
                button(label = "Blank cmd", tap = """{"type":"command","cmd":""}"""),
                button(label = "Unknown sheet", tap = """{"type":"sheet","sheet":" EQUALIZER "}"""),
                button(label = "Negative ref", tap = """{"type":"customButton","id":-1}"""),
                button(label = "No actions"),
                button(label = "Keeper", tap = """{"type":"seek","seconds":1}"""),
            ),
        )
        assertEquals(listOf("Keeper"), parsed.map { it.label })
    }

    @Test
    fun `blank or missing label drops the button`() {
        val parsed = CustomPlayerButtons.parse(
            array(
                button(label = "  ", tap = """{"type":"seek","seconds":5}"""),
                """{"tap": {"type":"seek","seconds":5}}""",
                button(label = "Ok", tap = """{"type":"seek","seconds":5}"""),
            ),
        )
        assertEquals(listOf("Ok"), parsed.map { it.label })
    }

    @Test
    fun `invalid json never throws and returns empty`() {
        assertTrue(CustomPlayerButtons.parse("").isEmpty())
        assertTrue(CustomPlayerButtons.parse("   ").isEmpty())
        assertTrue(CustomPlayerButtons.parse("not json").isEmpty())
        assertTrue(CustomPlayerButtons.parse("""{"label": "not an array"}""").isEmpty())
        // A single malformed entry among valid ones drops only itself.
        val parsed = CustomPlayerButtons.parse(
            """[${button(label = "Ok", tap = """{"type":"seek","seconds":5}""")}, {"label": 42}]""",
        )
        assertEquals(listOf("Ok"), parsed.map { it.label })
    }

    @Test
    fun `unknown json fields are ignored`() {
        val parsed = CustomPlayerButtons.parse(
            """[{"label": "Ok", "tap": {"type": "seek", "seconds": 5, "speed": 3}, "color": "red"}]""",
        )
        assertEquals(1, parsed.size)
    }

    // ── Cap, order, side defaults ───────────────────────────────────────────

    @Test
    fun `cap enforcement keeps the first six valid buttons`() {
        val json = array(*(1..9).map { i -> button(label = "B$i", tap = """{"type":"seek","seconds":$i}""") }.toTypedArray())
        val parsed = CustomPlayerButtons.parse(json)
        assertEquals(CustomPlayerButtons.MAX_BUTTONS, parsed.size)
        assertEquals((1..6).map { i -> "B$i" }, parsed.map { it.label })
        // A mid-list invalid button does not earn the cap an extra survivor:
        // the cap counts VALID buttons only.
        val withDud = array(
            *(1..5).map { i -> button(label = "B$i", tap = """{"type":"seek","seconds":$i}""") }.toTypedArray()
                + button(label = "Dud", tap = """{"type":"wat"}""")
                + (7..9).map { i -> button(label = "B$i", tap = """{"type":"seek","seconds":$i}""") },
        )
        val parsed2 = CustomPlayerButtons.parse(withDud)
        assertEquals(6, parsed2.size)
        assertEquals(listOf("B1", "B2", "B3", "B4", "B5", "B7"), parsed2.map { it.label })
    }

    @Test
    fun `side defaults right and honours left`() {
        val parsed = CustomPlayerButtons.parse(
            array(
                button(tap = """{"type":"seek","seconds":1}"""),
                button(position = "left", tap = """{"type":"seek","seconds":2}"""),
                button(position = "LEFT", tap = """{"type":"seek","seconds":3}"""),
                button(position = "diagonal", tap = """{"type":"seek","seconds":4}"""),
            ),
        )
        assertEquals(
            listOf(
                CustomButtonSide.RIGHT,
                CustomButtonSide.LEFT,
                CustomButtonSide.LEFT,
                CustomButtonSide.RIGHT,
            ),
            parsed.map { it.side },
        )
    }

    @Test
    fun `order sorts explicitly ordered buttons first and interleaves unordered ones at array position`() {
        // The no-order default IS the array position, so unordered buttons
        // interleave with the ordered ones by declared sequence.
        val parsed = CustomPlayerButtons.parse(
            array(
                button(label = "C", order = 3, tap = """{"type":"seek","seconds":1}"""),
                button(label = "A", order = 1, tap = """{"type":"seek","seconds":1}"""),
                button(label = "U1", tap = """{"type":"seek","seconds":1}"""),
                button(label = "B", order = 2, tap = """{"type":"seek","seconds":1}"""),
                button(label = "U2", tap = """{"type":"seek","seconds":1}"""),
            ),
        )
        assertEquals(listOf("A", "U1", "B", "C", "U2"), parsed.map { it.label })
    }
}
