package jellyplay.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.GradleException
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

/**
 * Packaging convention plugin for the desktop shell (:apps:desktop) — the
 * release-engineering mechanics relocated out of that 797-line build script
 * (which keeps plugins, dependencies, the navigation3 fork substitution, and
 * the compose.desktop/nativeDistributions config):
 *
 *  - registers `fetchBundledLibmpv` ([FetchBundledLibmpvTask]) — the pinned,
 *    sha256-verified Windows libmpv download — and wires it into `run`,
 *    `test` (jna.library.path), `prepareAppResources`, and every
 *    `package…`/`createDistributable…` task (exactly the module script's
 *    former wiring; the task self-gates by OS and dll presence, so
 *    non-Windows lanes no-op);
 *  - registers `writeDesktopBuildInfo` ([WriteDesktopBuildInfoTask]) and
 *    feeds its output directory into the main resources (processResources
 *    depends on it implicitly through the srcDir wiring — same mechanism as
 *    before, the bare task-output provider is what makes the implicit edge);
 *  - publishes the [DesktopPackagingExtension] (`jellyplayDesktopPackaging`):
 *    the numeric packageVersion grammar (a non-conforming -PjellyplayVersion
 *    fails CONFIGURATION here, eagerly, exactly as the module script did),
 *    the display version, the release channel, and the macOS 0.x.y -> 1.x.y
 *    bundle-version shift.
 *
 * Task NAMES are byte-compatible with the old script on purpose: CI release
 * lanes and the README reference `fetchBundledLibmpv`, and the release.yml /
 * kmp-release.yml comments reference `WriteDesktopBuildInfoTask`.
 *
 * Applied AFTER org.jetbrains.kotlin.jvm + the Compose plugin in the
 * module's plugins block (the sourceSets wiring needs the java/Kotlin
 * source sets; the task matching is name-based and order-independent).
 */
class DesktopPackagingPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // x86_64 build of libmpv (GPL-2.0-or-later — fine to bundle in this
        // GPLv3 app; see packaging/mpv-third-party-notice.txt) from the
        // mpv-winbuild project, mirrored on GitHub by dyphire because the
        // canonical SourceForge downloads rotate mirrors and have been
        // observed serving byte-unstable responses. Both URLs and sha256s
        // are pinned; bump the build inside the URLs (and re-hash the
        // archives) to move to a newer mpv — deliberately: the engine's
        // property semantics and the real-engine test expectations were
        // live-probed against this v0.41-era build, and an Aug-2026 git
        // build already showed `vf` readback drift in MpvDesktopEngineVideoTest.
        // The dev archive carries only libmpv-2.dll; the PLAYER archive from
        // the same build supplies lua51.dll, which this libmpv links
        // dynamically (verified: the dll fails to load with "module not
        // found" without it). -Pjellyplay.mpvDevUrl / -Pjellyplay.mpvPlayerUrl
        // override for experiments.
        val libmpvToolsDir = project.rootProject.layout.projectDirectory.dir("tools/mpv")
        val libmpvDevArchiveUrl = project.providers.gradleProperty("jellyplay.mpvDevUrl").orElse(
            "https://github.com/dyphire/mpv-winbuild/releases/download/mpv_own-2025-09-30/" +
                "mpv-dev-x86_64-20250930-git-05656cd.7z",
        )
        val libmpvDevArchiveSha256 =
            "0f952998dac3ca767d9e859580f7ed49c387f410bce17e90921f00435f1b96b4"
        val libmpvPlayerArchiveUrl = project.providers.gradleProperty("jellyplay.mpvPlayerUrl").orElse(
            "https://github.com/dyphire/mpv-winbuild/releases/download/mpv_own-2025-09-30/" +
                "mpv-x86_64-20250930-git-05656cd.7z",
        )
        val libmpvPlayerArchiveSha256 =
            "bbcba68f0be265b8bd341a0b75da18000c37bf030d341c69adaf8404940c251b"

        val fetchBundledLibmpv = project.tasks.register(
            "fetchBundledLibmpv",
            FetchBundledLibmpvTask::class.java,
        ) {
            devArchiveUrl.set(libmpvDevArchiveUrl)
            devArchiveSha256.set(libmpvDevArchiveSha256)
            playerArchiveUrl.set(libmpvPlayerArchiveUrl)
            playerArchiveSha256.set(libmpvPlayerArchiveSha256)
            noticeFile.set(
                project.layout.projectDirectory.file("packaging/mpv-third-party-notice.txt"),
            )
            dllFile.set(libmpvToolsDir.file("libmpv-2.dll"))
            resourcesDir.set(libmpvToolsDir.dir("appResources/windows-x64"))
        }

        // Dev runs resolve libmpv from tools/mpv — JNA also keeps searching
        // PATH and MPV_LIBRARY wins over everything (MpvLib.load checks it
        // first). Lazy matching because the Compose plugin registers its run
        // task (a JavaExec subclass) after this plugin applies.
        project.tasks.matching { it.name == "run" }.configureEach {
            dependsOn(fetchBundledLibmpv)
            (this as? JavaExec)?.systemProperty(
                "jna.library.path",
                libmpvToolsDir.asFile.absolutePath,
            )
        }

        // The Compose plugin's prepareAppResources (Sync) reads the fetch
        // task's resourcesDir output (the appResourcesRootDir input lives in
        // the module's nativeDistributions block); without this declaration
        // Gradle 9 task-graph validation fails whenever the Sync actually
        // re-executes (e.g. after tools/mpv was cleaned) instead of being
        // up-to-date.
        project.tasks.matching { it.name == "prepareAppResources" }.configureEach {
            dependsOn(fetchBundledLibmpv)
        }

        // Every packaging flavor (and the no-installer app image) must carry
        // the dll; the fetch task self-gates by OS and dll presence, so
        // non-Windows lanes no-op.
        project.tasks.matching { task: Task ->
            task.name.startsWith("package") || task.name.startsWith("createDistributable")
        }.configureEach {
            dependsOn(fetchBundledLibmpv)
        }

        // libmpv for MpvDesktopEngine's tests: the fetch task materializes
        // it on Windows (manual drop-in into tools/mpv also works); without
        // it the engine tests skip.
        project.tasks.named("test", Test::class.java) {
            useJUnitPlatform()
            dependsOn(fetchBundledLibmpv)
            systemProperty(
                "jna.library.path",
                libmpvToolsDir.asFile.absolutePath,
            )
        }

        // ── version grammar + release-channel derivation ──────────────────
        // packageVersion is release-driven: pass -PjellyplayVersion=x.y.z on
        // the release lane (CI desktop-package job does exactly that).
        // jpackage demands a strictly numeric major.minor.build triple —
        // Windows MSI's ProductVersion rejects non-numeric segments outright
        // ("1.2.3-rc1" fails msiexec validation; deb/dmg are more lenient
        // but we keep one version grammar), so a git-describe-style fallback
        // would poison every package, not just msi. Anything non-conforming
        // passed explicitly FAILS configuration rather than silently falling
        // back — a packaged 0.1.0 shipped while believing it was 2.0.0 is
        // the failure mode this guard exists for.
        val requested = project.findProperty("jellyplayVersion")?.toString()
        val numericTriple = Regex("""\d+\.\d+\.\d+""")
        val packageVersion = when {
            requested == null || requested.isBlank() -> "0.1.0" // dev-machine fallback
            numericTriple.matches(requested) -> requested
            else -> throw GradleException(
                "-PjellyplayVersion='$requested' is not a numeric x.y.z; jpackage/msi " +
                    "requires digits-only major.minor.build (e.g. -PjellyplayVersion=1.2.3)",
            )
        }

        // The release CHANNEL rides a separate display version:
        // packageVersion above must stay digits-only (jpackage/msi), so
        // alpha tags like "0.11.0-alpha.1" pass as -PjellyplayVersionName
        // and land in the classpath properties file the About screen's
        // DesktopAppMetaProvider reads. Defaults to the numeric package
        // version; when no file exists (settings-module tests, IDE runs
        // before first processResources) the provider falls back to "dev".
        val displayVersion = project.providers.gradleProperty("jellyplayVersionName")
            .orElse(packageVersion)

        // "release" only when -PjellyplayVersion was EXPLICITLY passed — i.e.
        // a CI release lane produced this build. The "0.1.0" dev-machine
        // fallback (and any bare `gradlew run`/IDE build) is channel=dev, and
        // the update check then reports "up to date" BY CONSTRUCTION (see
        // DesktopPackagingExtension.releaseChannel / docs/adr/
        // desktop-auto-update.md) — this replaces the old 999999.0.0
        // sentinel's job with an explicit, generated flag instead of a fake
        // version number.
        val releaseChannel = project.providers.gradleProperty("jellyplayVersion")
            .map { "release" }
            .orElse("dev")

        val extension = project.extensions.create(
            "jellyplayDesktopPackaging",
            DesktopPackagingExtension::class.java,
        )
        extension.packageVersion.set(packageVersion)
        extension.displayVersion.set(displayVersion)
        extension.releaseChannel.set(releaseChannel)
        extension.macOsBundleVersion.set(
            project.provider {
                val versionParts = packageVersion.split('.')
                if (versionParts.first() == "0") {
                    "1." + versionParts.drop(1).joinToString(".")
                } else {
                    packageVersion
                }
            },
        )

        val writeDesktopBuildInfo = project.tasks.register(
            "writeDesktopBuildInfo",
            WriteDesktopBuildInfoTask::class.java,
        ) {
            versionName.set(displayVersion)
            channel.set(releaseChannel)
            outputFile.set(project.layout.buildDirectory.file("generated/build-info/desktop-build.properties"))
        }

        // Wiring the srcDir through the task-output provider (not the bare
        // directory) is what makes processResources depend on
        // writeDesktopBuildInfo implicitly.
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            project.extensions.getByType(SourceSetContainer::class.java).named("main") {
                resources.srcDir(writeDesktopBuildInfo.map { it.outputFile.get().asFile.parentFile })
            }
        }
    }
}
