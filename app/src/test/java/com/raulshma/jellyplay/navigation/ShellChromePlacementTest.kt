package com.raulshma.jellyplay.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [shellChromeBottomClearance] / [miniPlayerNavCoupledOffsetYpx] /
 * [floatingNavBarOffsetYpx] / [overflowMenuInsets] — the phone shell's
 * chrome-clearance placement table, extracted verbatim from PhoneContent's
 * former inline forks. The values ARE the contract (byte-identical to the
 * numbers the forks carried); a change here is a deliberate visual change,
 * not a refactor slip.
 */
class ShellChromePlacementTest {

    // ── bottom clearance: element × layout ──────────────────────────

    @Test
    fun `mini player clears the floating nav bar on phones only`() {
        assertEquals(60.dp, shellChromeBottomClearance(ShellChromeElement.MiniPlayer, isExpanded = false))
        assertEquals(2.dp, shellChromeBottomClearance(ShellChromeElement.MiniPlayer, isExpanded = true))
    }

    @Test
    fun `play on bar clears the floating nav bar on phones, near-flush on rail`() {
        assertEquals(72.dp, shellChromeBottomClearance(ShellChromeElement.PlayOnMiniBar, isExpanded = false))
        assertEquals(8.dp, shellChromeBottomClearance(ShellChromeElement.PlayOnMiniBar, isExpanded = true))
    }

    @Test
    fun `floating nav bar keeps its own breathing room in both layouts`() {
        assertEquals(4.dp, shellChromeBottomClearance(ShellChromeElement.FloatingNavBar, isExpanded = false))
        assertEquals(4.dp, shellChromeBottomClearance(ShellChromeElement.FloatingNavBar, isExpanded = true))
    }

    // ── scroll-coupled offsets ──────────────────────────────────────

    @Test
    fun `mini player rises with the hiding nav bar but never past its height`() {
        // The REAL input domain — what the ShellLayouts call site passes:
        // BottomNavScrollState's raw offsetHeightPx, 0 while visible and
        // NEGATIVE while hiding (animated toward -2 × navHeight fully
        // hidden). navHeight = 100 px below.
        // No scroll: no movement.
        assertEquals(0, miniPlayerNavCoupledOffsetYpx(bottomNavHideOffsetPx = 0f, floatingNavHeightPx = 100f))
        // Half-hidden nav (-0.5H): the player rides up half the bar's height.
        assertEquals(50, miniPlayerNavCoupledOffsetYpx(bottomNavHideOffsetPx = -50f, floatingNavHeightPx = 100f))
        // One bar-height hidden (-1H): the rise reaches exactly the bar's height.
        assertEquals(100, miniPlayerNavCoupledOffsetYpx(bottomNavHideOffsetPx = -100f, floatingNavHeightPx = 100f))
        // Fully hidden (-2H) and mid-flight (-1.5H): the rise CLAMPS at the
        // bar's height — the legacy `(-offset).coerceAtMost(maxOffset)` with
        // maxOffset = +navHeight; the player never travels further than the
        // bar it follows.
        assertEquals(100, miniPlayerNavCoupledOffsetYpx(bottomNavHideOffsetPx = -150f, floatingNavHeightPx = 100f))
        assertEquals(100, miniPlayerNavCoupledOffsetYpx(bottomNavHideOffsetPx = -200f, floatingNavHeightPx = 100f))
        // Show-overshoot (the offset spring settling back to 0 can pass it):
        // the player sinks symmetrically — the clamp never binds there,
        // exactly as the legacy expression behaved.
        assertEquals(-10, miniPlayerNavCoupledOffsetYpx(bottomNavHideOffsetPx = 10f, floatingNavHeightPx = 100f))
    }

    @Test
    fun `nav bar offset is the negated hide offset rounded after negation`() {
        // Same real domain as the mini player: 0 visible, negative hiding.
        assertEquals(0, floatingNavBarOffsetYpx(0f))
        assertEquals(40, floatingNavBarOffsetYpx(-40f))
        // NOT clamped — the bar itself may travel its full 2 × height below
        // the screen (the clamp is ScrollDirectionVisibility's job upstream).
        assertEquals(200, floatingNavBarOffsetYpx(-200f))
    }

    // ── overflow menu insets ────────────────────────────────────────

    @Test
    fun `overflow docks under the status bar beside the rail when expanded`() {
        val insets = overflowMenuInsets(isExpanded = true, statusBarTop = 24.dp)
        assertEquals(12.dp, insets.start)
        assertEquals(32.dp, insets.top) // statusBarTop + 8.dp
        assertEquals(0.dp, insets.end)
        assertEquals(0.dp, insets.bottom)
    }

    @Test
    fun `overflow docks beside the nav toggle when compact`() {
        val insets = overflowMenuInsets(isExpanded = false, statusBarTop = 24.dp)
        assertEquals(0.dp, insets.start)
        assertEquals(0.dp, insets.top)
        assertEquals(16.dp, insets.end)
        assertEquals(4.dp, insets.bottom)
    }
}
