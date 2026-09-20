package com.raulshma.jellyplay.feature.player.video

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the DECLARED divergence the [EffectsCommandCore] KDoc encodes: which
 * `Order` each adapter constructs with. The core's own suite proves each
 * order is honored as a contract; only a pin on the adapter's CHOICE catches
 * a silent flip. The video flip is also behaviorally pinned by
 * `VideoEffectsControllerTest.syncConfig_rebuildsFromThePostUpdateState`;
 * the audio flip is behavior-neutral today (the persist leg reads the
 * manager's post-apply `.value` under either order), so this source pin is
 * its only guard. Comments are stripped before matching so KDoc mentions of
 * the order names can't satisfy it.
 */
class EffectsAdapterOrderContractTest {

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        assertTrue(File(dir, "settings.gradle.kts").isFile, "repo root not found from ${System.getProperty("user.dir")}")
        return dir
    }

    private fun strippedSource(relativePath: String): String {
        val file = File(repoRoot(), relativePath)
        assertTrue(file.isFile, "missing source file ${file.absolutePath}")
        return file.readText(Charsets.UTF_8)
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//.*"), "")
    }

    @Test
    fun `audio adapter declares APPLY_FIRST`() {
        val source = strippedSource(
            "shared/feature/player-audio/src/commonMain/kotlin/" +
                "com/raulshma/jellyplay/feature/player/audio/AudioEffectsController.kt",
        )
        assertTrue(
            "EffectsCommandCore.Order.APPLY_FIRST" in source,
            "AudioEffectsController must construct its EffectsCommandCore with Order.APPLY_FIRST",
        )
        assertTrue(
            "EffectsCommandCore.Order.STATE_FIRST" !in source,
            "AudioEffectsController must not adopt Order.STATE_FIRST",
        )
    }

    @Test
    fun `video adapter declares STATE_FIRST`() {
        val source = strippedSource(
            "shared/feature/player-video/src/commonMain/kotlin/" +
                "com/raulshma/jellyplay/feature/player/video/VideoEffectsController.kt",
        )
        assertTrue(
            "EffectsCommandCore.Order.STATE_FIRST" in source,
            "VideoEffectsController must construct its EffectsCommandCore with Order.STATE_FIRST",
        )
        assertTrue(
            "EffectsCommandCore.Order.APPLY_FIRST" !in source,
            "VideoEffectsController must not adopt Order.APPLY_FIRST",
        )
    }
}
