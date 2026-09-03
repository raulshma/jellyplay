package com.raulshma.jellyplay.desktop

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The E2E harness arming gates (wave 13B session harness, wave 22F native-
 * dialog harness): [DesktopSessionHarness.requested] and
 * [DesktopNativeDialogHarness.requested] are the zero-cost gate that decides
 * whether a normal boot composes anything harness-owned at all.
 *
 * Invariants pinned here:
 *  - Default (property absent) is OFF — the deciding property is read on
 *    every call, so a normal user boot must never arm either harness.
 *  - Exactly the literal "true" (any casing) arms a harness — "1", "yes",
 *    "on", empty and "false" must NOT (JAVA_TOOL_OPTIONS typos must fail
 *    closed, not half-arm a Robot-driving, auto-exiting process).
 *  - Both harnesses share the SAME gate grammar — a runner script setting
 *    one flag shape can rely on the same semantics for both passes.
 *  - The gate reads are pure: reading them leaves the system properties
 *    untouched, so a boot sequence can poll them freely.
 */
class DesktopHarnessGateTest {

    @BeforeTest
    fun clearGateProperties() {
        System.clearProperty(DesktopSessionHarness.PROP_ENABLED)
        System.clearProperty(DesktopNativeDialogHarness.PROP_ENABLED)
    }

    @AfterTest
    fun restoreGateProperties() {
        // Belt and suspenders: never leak a set property into another test
        // class sharing this JVM (an armed harness gate could flip behavior
        // of any code that consults requested()).
        System.clearProperty(DesktopSessionHarness.PROP_ENABLED)
        System.clearProperty(DesktopNativeDialogHarness.PROP_ENABLED)
    }

    @Test
    fun `harnesses are off when the enabled property is unset`() {
        assertFalse(DesktopSessionHarness.requested())
        assertFalse(DesktopNativeDialogHarness.requested())
    }

    @Test
    fun `literal true arms both harnesses regardless of case`() {
        listOf("true", "TRUE", "True", "tRuE").forEach { value ->
            System.setProperty(DesktopSessionHarness.PROP_ENABLED, value)
            System.setProperty(DesktopNativeDialogHarness.PROP_ENABLED, value)
            assertTrue(DesktopSessionHarness.requested(), "session harness must arm on '$value'")
            assertTrue(DesktopNativeDialogHarness.requested(), "dialog harness must arm on '$value'")
        }
    }

    @Test
    fun `anything but literal true fails closed`() {
        listOf(
            "false",
            "1",
            "yes",
            "on",
            " true", // leading space — JAVA_TOOL_OPTIONS value typos must not arm
            "true ",
            "",
        ).forEach { value ->
            System.setProperty(DesktopSessionHarness.PROP_ENABLED, value)
            System.setProperty(DesktopNativeDialogHarness.PROP_ENABLED, value)
            assertFalse(DesktopSessionHarness.requested(), "session harness must stay off on '$value'")
            assertFalse(DesktopNativeDialogHarness.requested(), "dialog harness must stay off on '$value'")
        }
    }

    @Test
    fun `both harnesses share the same gate grammar`() {
        // A runner script (tools/e2e/desktop-session-pass.sh and the
        // native-dialog twin) injects the same JAVA_TOOL_OPTIONS shape into
        // both passes; the two gates must therefore decide identically for
        // every value.
        listOf(null, "true", "TRUE", "false", "1", "").forEach { value ->
            if (value == null) {
                System.clearProperty(DesktopSessionHarness.PROP_ENABLED)
                System.clearProperty(DesktopNativeDialogHarness.PROP_ENABLED)
            } else {
                System.setProperty(DesktopSessionHarness.PROP_ENABLED, value)
                System.setProperty(DesktopNativeDialogHarness.PROP_ENABLED, value)
            }
            assertEquals(
                DesktopSessionHarness.requested(),
                DesktopNativeDialogHarness.requested(),
                "gates must agree on property value '$value'",
            )
        }
    }

    @Test
    fun `reading the gate never mutates the property`() {
        val before = System.getProperty(DesktopSessionHarness.PROP_ENABLED)
        val beforeDialog = System.getProperty(DesktopNativeDialogHarness.PROP_ENABLED)
        DesktopSessionHarness.requested()
        DesktopNativeDialogHarness.requested()
        assertEquals(before, System.getProperty(DesktopSessionHarness.PROP_ENABLED))
        assertEquals(beforeDialog, System.getProperty(DesktopNativeDialogHarness.PROP_ENABLED))
    }
}
