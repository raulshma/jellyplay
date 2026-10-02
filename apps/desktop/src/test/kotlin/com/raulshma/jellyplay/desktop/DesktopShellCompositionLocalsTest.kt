package com.raulshma.jellyplay.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Shell composition-locals ratchet (the KoinModuleRegistrationGuardTest
 * source-text idiom, applied to the CompositionLocalProvider block):
 * composition-local provisioning is runtime-only wiring — a local the
 * desktop shell forgets to provide compiles fine and fails at first read,
 * and a read with no default crashes outright.
 *
 * DesktopNavScaffold is the ONE place the desktop shell provides its
 * shell-relevant locals, so this guard reads that source and asserts each
 * expected `Local… provides` mention exists — "desktop forgot a local"
 * becomes loud here instead of silent until first navigation. The expected
 * set is deliberately minimal and hand-documented: add a line only when the
 * shell genuinely grows a provided local (LocalSurpriseOnLaunch is
 * deliberately ABSENT — its declaration's inert never-armed default is the
 * contract for that one, since desktop has no launcher-shortcut seam).
 */
class DesktopShellCompositionLocalsTest {

    /** The single desktop shell file whose CompositionLocalProvider block this guards. */
    private val scaffoldFile =
        "apps/desktop/src/main/kotlin/com/raulshma/jellyplay/desktop/DesktopNavScaffold.kt"

    /**
     * The shell-relevant locals desktop MUST provide — the shared screens
     * read each unconditionally (network banner, server-health banner,
     * pull-to-refresh, adaptive layout, UI environment, one-shot messages).
     */
    private val expectedProvidedLocals = listOf(
        "LocalNetworkStatus",
        "LocalServerHealth",
        "LocalPullToRefreshRegistry",
        "LocalAdaptiveInfo",
        "LocalJellyPlayUi",
        "LocalUserMessageBus",
    )

    @Test
    fun `desktop scaffold provides every shell-relevant composition local`() {
        val text = stripComments(scaffold().readText())
        val missing = expectedProvidedLocals.filter { "$it provides" !in text }
        assertEquals(
            emptyList(),
            missing,
            "DesktopNavScaffold no longer provides $missing — a shell local the shared " +
                "screens read unconditionally went missing (crash/no-op at first read). " +
                "Restore the provide or update this guard's expected set deliberately.",
        )
    }

    @Test
    fun `desktop scaffold does not re-provide the inert-default surprise local`() {
        // LocalSurpriseOnLaunch's declaration owns an inert never-armed
        // default (its KDoc contract) precisely so shells without the
        // launcher-shortcut seam skip provisioning — desktop must not grow a
        // dummy provider again.
        val text = stripComments(scaffold().readText())
        assertFalse(
            "LocalSurpriseOnLaunch provides" in text,
            "desktop re-provides LocalSurpriseOnLaunch — the inert default already covers " +
                "the unconditional HomeHeroController read; delete the dummy wiring.",
        )
    }

    /** Walks up from the test working dir (may be apps/desktop/) to settings.gradle.kts. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    private fun scaffold(): File {
        val file = repoRoot().resolve(scaffoldFile)
        assertTrue(file.isFile, "missing $scaffoldFile — repo layout changed?")
        return file
    }

    /** Kotlin block comments NEST; loop until stable, then drop line comments. */
    private fun stripComments(text: String): String {
        var stripped = text
        while (true) {
            val next = stripped.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            if (next == stripped) break
            stripped = next
        }
        return stripped.replace(Regex("//[^\n]*"), "")
    }
}
