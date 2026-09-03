package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the pure focus-restore helpers shared by every TV screen (the
 * `remember*`/`LaunchedEffect` factories are composition-bound and untested):
 *
 *  - [FocusRequester.tryRequestFocus] never throws for callers: on current
 *    Compose (1.11) requesting focus before attachment is a safe no-op rather
 *    than the historic IllegalStateException, so the helper reports TRUE —
 *    the retry loops still converge because an attached attempt grabs focus.
 *    (The `false` path survives only for runtimes that still throw.)
 *  - [Modifier.ifElse] is `then(if (condition) a else b)` — with the default
 *    else branch it must return the SAME modifier instance (no-op chains must
 *    not allocate a new modifier), and with a concrete else branch it composes
 *    that branch.
 *  - [RowColumn] defaults to the "-1, -1" sentinel meaning "nothing focused
 *    yet" and keeps row/column in its data identity.
 */
class FocusRestorerHelpersTest {

    @Test
    fun tryRequestFocus_onUnattachedRequester_returnsTrueWithoutThrowing() {
        val requester = FocusRequester()

        assertTrue(requester.tryRequestFocus("test"))
    }

    @Test
    fun ifElse_falseWithDefaultElse_returnsSameInstance() {
        val base = Modifier
        val decorated = Modifier.focusRequester(FocusRequester())

        assertSame(base, base.ifElse(condition = false, ifTrueModifier = decorated))
    }

    @Test
    fun ifElse_true_composesTheTrueModifier() {
        val base = Modifier
        val decorated = Modifier.focusRequester(FocusRequester())

        val result = base.ifElse(condition = true, ifTrueModifier = decorated)

        assertNotSame(base, result, "the true branch must be folded in")
        assertTrue(result != base)
    }

    @Test
    fun ifElse_falseWithExplicitElse_composesTheElseModifier() {
        val base = Modifier
        val ifTrue = Modifier.focusRequester(FocusRequester())
        val ifFalse = Modifier.focusRequester(FocusRequester())

        val result = base.ifElse(condition = false, ifTrueModifier = ifTrue, ifFalseModifier = ifFalse)

        assertNotSame(base, result)
    }

    @Test
    fun rowColumn_defaultsToUnfocusedSentinel() {
        val none = RowColumn()

        assertEquals(-1, none.row)
        assertEquals(-1, none.column)
    }

    @Test
    fun rowColumn_isAValuePair() {
        assertEquals(RowColumn(row = 2, column = 5), RowColumn(row = 2, column = 5))
        assertEquals(2 to 5, RowColumn(row = 2, column = 5).let { it.row to it.column })
    }
}
