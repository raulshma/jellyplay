package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.model.SubtitleColor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [subtitleColorToCompose] — the SubtitleColor→Compose mapping that stayed
 * in core/ui for onboarding's pickers (its former file-mate, the preview
 * composable, moved to feature/settings; this mapping's consumer did not).
 * Covers every enum constant so a new SubtitleColor can't silently render a
 * `when`-exhaustiveness crash.
 */
class SubtitleColorMapTest {

    @Test
    fun coversEveryEnumConstant() {
        assertEquals(Color.White, subtitleColorToCompose(SubtitleColor.WHITE))
        assertEquals(Color.Yellow, subtitleColorToCompose(SubtitleColor.YELLOW))
        assertEquals(Color.Green, subtitleColorToCompose(SubtitleColor.GREEN))
        assertEquals(Color.Cyan, subtitleColorToCompose(SubtitleColor.CYAN))
        assertEquals(Color.Red, subtitleColorToCompose(SubtitleColor.RED))
        assertEquals(Color.Black, subtitleColorToCompose(SubtitleColor.BLACK))
        assertEquals(Color.Blue, subtitleColorToCompose(SubtitleColor.BLUE))
    }
}
