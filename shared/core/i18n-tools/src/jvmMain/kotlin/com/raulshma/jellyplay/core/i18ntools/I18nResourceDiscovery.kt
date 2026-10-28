package com.raulshma.jellyplay.core.i18ntools

import java.io.File

/**
 * Locates the repo's guarded i18n resource roots — the one repo walk shared
 * by every consumer of the counted-key grammar: the I18nParityRatchetTest
 * (this module's jvmTest) and the root build's repo-wide
 * propagateStrings/checkStrings tasks, whose `--discover` mode passes the
 * repo root straight through to [discoverResourceRoots].
 */
object I18nResourceDiscovery {

    /**
     * Walks up from [from] to the directory carrying `settings.gradle.kts`
     * — the repo root, wherever the caller started (a Gradle worker's
     * user.dir is the module dir, a JavaExec's is the root project dir).
     */
    fun repoRoot(from: File): File {
        var dir: File? = from.absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        check(dir != null) { "could not locate settings.gradle.kts walking up from $from" }
        return dir
    }

    /**
     * Every resource root to guard under [root]: `composeResources`
     * directories under `shared/` or `apps/` holding a
     * `values/strings.xml` default locale, with `build` output directories
     * pruned from the walk — a new module with resources is guarded
     * automatically, no hand list to forget. (The composeResources root —
     * not the `values` dir itself — is the unit [StringsPropagator.findGaps]
     * consumes: `values/` is the declaration, `values-<lang>/` siblings the
     * artifacts.)
     */
    fun discoverResourceRoots(root: File): List<File> {
        val roots = mutableListOf<File>()
        listOf("shared", "apps").forEach { top ->
            val topDir = File(root, top)
            if (!topDir.isDirectory) return@forEach
            topDir.walkTopDown()
                .onEnter { it.name != "build" }
                .filter { it.isFile && it.path.replace('\\', '/').endsWith("composeResources/values/strings.xml") }
                .forEach { roots += it.parentFile.parentFile }
        }
        return roots
    }
}
