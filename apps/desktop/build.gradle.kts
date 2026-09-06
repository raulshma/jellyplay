import org.jetbrains.compose.desktop.application.dsl.TargetFormat
// NOTE: java.awt/java.io must be IMPORTED here rather than written as inline
// FQNs — this project applies the JVM plugin, so the identifier `java`
// collides with the `java { }` extension accessor in script scope and every
// `java.<pkg>…` chain fails script compilation ("Unresolved reference 'awt'").
import org.gradle.api.tasks.JavaExec
import java.awt.Color
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import javax.imageio.ImageIO

plugins {
    id("org.jetbrains.kotlin.jvm") // version inherited from the plugin classpath
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

// Desktop shell (docs/kmp-migration-plan.md §Phase V1b): Compose Window + tray
// + menubar + shortcuts over the shared core stack. Feature modules land here
// one conveyor step at a time (§V1c/V3).
dependencies {
    implementation(compose.desktop.currentOs)

    implementation(project(":shared:core:model"))
    implementation(project(":shared:core:designsystem"))
    implementation(project(":shared:core:ui"))
    implementation(project(":shared:core:datastore"))
    implementation(project(":shared:core:database"))
    implementation(project(":shared:core:network"))
    implementation(project(":shared:core:data"))
    // MediaEngine contract for the desktop player engine (Phase V2).
    implementation(project(":shared:core:player-contract"))

    // V3 feature conveyor: search — LIVE since the Phase X desktop nav v1
    // (DesktopAppRoot's NavDisplay renders searchSection as the start tab).
    implementation(project(":shared:feature:search"))

    // …library, second conveyor item — LIVE like search; PhotoExport's
    // desktop actual is the desktop-inert half (isSupported=false gates the
    // share buttons).
    implementation(project(":shared:feature:library"))

    // …music, third conveyor item — fully LIVE since wave 9B real audio:
    // browse since Wave wC, and play/enqueue/instant-mix drive real playback
    // through the DefaultAudioQueueFacade over DesktopAudioQueueManager.
    implementation(project(":shared:feature:music"))

    // …livetv, fourth conveyor item — LIVE since the MediaRepository
    // cluster flip; liveTvSection renders in the rail.
    implementation(project(":shared:feature:livetv"))

    // …downloads, fifth conveyor item — fully live since the cluster flip
    // (series downloads included); downloadsSection renders in the rail.
    implementation(project(":shared:feature:downloads"))

    // …syncplay, sixth conveyor item — LIVE since the cluster flip;
    // syncPlaySection renders in the rail.
    implementation(project(":shared:feature:syncplay"))

    // …settings, seventh conveyor item — LIVE since the admin repositories'
    // Koin flip (Wave wB): nav v1+ renders settingsSection in the rail (with
    // the desktop platform actuals; Desktop's update-check row since Wave xB).
    implementation(project(":shared:feature:settings"))
    // Wave 22F dialog pass: the harness names SettingsViewModel directly (the
    // shell's own screens only render settingsSection, so the ViewModel
    // supertype was never on the shell classpath before).
    implementation(libs.lifecycle.viewmodel)

    // …admin, eighth conveyor item — LIVE since the same flip:
    // AdminRepository + AdminStatisticsRepository are Koin singles in
    // dataJvmModule on both platforms, and nav v1+ renders adminSection in
    // the rail (gated by the desktop admin-status state).
    implementation(project(":shared:feature:admin"))



    // …editor, ninth conveyor item — LIVE since the wave 18B store
    // promotion: desktopDataModule binds the real StreamingSubtitleStore and
    // DesktopAppRoot renders editorSection (the details screen's edit push,
    // admin-gated like Android).
    implementation(project(":shared:feature:editor"))

    // …calendar, conveyor feature — LIVE and wired in nav v1
    // (calendarSection in the rail; all three VM ctor deps resolve).
    implementation(project(":shared:feature:calendar"))


    // …requests, eleventh conveyor item — LIVE and wired in nav v1
    // (requestsSection in the rail; fully Koin-native ctor graph).
    implementation(project(":shared:feature:requests"))
    implementation(project(":shared:feature:shortcuts"))


    // …newsletter, conveyor item after requests — LIVE since the cluster
    // flip and wired in nav v1 (newsletterSection in the rail).
    implementation(project(":shared:feature:newsletter"))


    // …insights, conveyor feature — LIVE since the cluster flip and wired
    // in nav v1 (insightsSection in the rail; the share seam's null actual
    // keeps the share button hidden).
    implementation(project(":shared:feature:insights"))
    implementation(project(":shared:feature:arrqueue"))


    // …onboarding, conveyor feature — fully live: nav v1 registers
    // onboardingSection (reachable from Shortcuts and the settings "rerun
    // setup" row), and since wave 21B the shell gates first run —
    // DesktopNavScaffold pushes Route.Onboarding once per authenticated
    // session while the persisted onboarding_completed flag is unset
    // (Android's JellyPlayApp gate order and pref; completion flows through
    // the shared OnboardingViewModel, so the gate never re-fires).
    implementation(project(":shared:feature:onboarding"))
    implementation(project(":shared:feature:details"))


    // …auth, Phase X cutover (feature-conveyor transform): LIVE since wave
    // 19A unified sign-in — the signed-out gate (DesktopSignedOutAuthHost)
    // and the signed-in settings drill-ins (DesktopAppRoot's authSection
    // entries) both instantiate these ViewModels; the whole ctor graph is
    // Koin-native here.
    implementation(project(":shared:feature:auth"))


    // …home, Phase X cutover feature (the desktop landing screen) — LIVE
    // since the wave 8B desktop wiring: the four WorkManager/widget-backed
    // HomeViewModel ctor deps resolve to the no-op desktop defs in
    // desktopDataModule, and DesktopAppRoot wires homeSection in the rail.
    implementation(project(":shared:feature:home"))


    // …player-live, conveyor feature (wave 7B) — module compiles and
    // playerLiveModule is registered LATENT: the player screen + engine
    // factory/audio/renderer seams are Android-only (androidMain) and
    // Route.LiveTvChannelPlayer stays guarded in DesktopAppRoot, so
    // nothing constructs the live-player VM on desktop. The jvm target
    // exists for the shared ViewModel's jvmTest suite.
    implementation(project(":shared:feature:player-live"))

    // …player-audio, wave 7A conveyor (legacy :feature:player:audio deleted):
    // LIVE since wave 9B real audio — desktopPlayerModule provides the four
    // playback/cast ctor deps (DesktopAudioQueueManager implements
    // AudioQueueManager + AudioPlayerEngine over an audio-only MpvDesktop
    // Engine; state-only DesktopAudioEffectsManager; never-connected
    // DesktopAudioPlayerCast), and DesktopAppRoot registers
    // audioPlayerSection (Route.AudioPlayer + Route.Ambient) so music track
    // clicks open the now-playing screen.
    implementation(project(":shared:feature:player-audio"))

    // …player-video, wave 8C conveyor slice → wave 9A playback LIVE on
    // Windows: the ViewModel/session cluster is commonMain and
    // desktop-resolvable (desktopPlayerVideoModule registers the VM + no-op
    // seam actuals, jvmMain), DesktopAppRoot registers Route.VideoPlayer for
    // Windows with the commonMain screen's SwingPanel/HWND surface, and
    // desktopPlayerModule binds PlayerEngineFactory to a per-session
    // MpvDesktopEngine carrying that surface's HWND. Other OSes keep
    // Route.VideoPlayer dead-end-guarded.
    implementation(project(":shared:feature:player-video"))

    // Shared shell graph (shared appSections): one entryProvider behind both
    // shells — DesktopAppRoot supplies the desktop ShellHostHooks and the
    // conditional VideoPlayer registration; the dead-end guard derives from
    // the same graph's registration ledger.
    implementation(project(":shared:feature:shell"))


    // Desktop libmpv binding (MpvDesktopEngine, Phase V2): JNA loads
    // mpv-2.dll / libmpv.so / libmpv.dylib at runtime; wave 9A also resolves
    // the surface HWND through Native.getComponentPointer (shared
    // player-video's jvmMain declares its own implementation-scoped jna edge).
    implementation(libs.jna)

    // Swing Main dispatcher (SyncPlayPlaybackCore constructs on
    // Dispatchers.Main.immediate; plain JVM has none without this).
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
    // koinInject() for shell-level services (AuthRepository in the sign-in
    // gate); feature screens bring their own koin-compose-viewmodel edges.
    implementation(libs.koin.compose)
    // NavKey is public API surface of shared/core/ui's navigation helpers;
    // NavDisplay + entryDecorator wiring need the -ui artifact alongside.
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    // NavKey polymorphic registration enumerates Route's sealed leaves.
    implementation(kotlin("reflect"))

    // Image pipeline: coil3 desktop engine + OkHttp network fetcher.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)

    // Rail icons (same tabler set the shared feature screens use).
    implementation(libs.tabler.icons.outline)

    // Desktop audio queue-semantics suite: deterministic virtual-time
    // scheduling (UnconfinedTestDispatcher drives every manager effect to
    // completion inline; the ticker/reporter delay() cadences become virtual
    // time advanced explicitly per test). The real-engine WAV suite keeps
    // wall clocks — mpv runs its own threads — but no polling races there.
    testImplementation(libs.coroutines.test)
    testImplementation(kotlin("test"))
}

// ── Bundled libmpv (out-of-the-box desktop playback) ────────────────────────
// Desktop video/audio playback goes through libmpv via JNA, and requiring
// every user to install mpv by hand (or set MPV_LIBRARY) is a non-starter —
// a missing dll used to surface as a silent black player screen. The Windows
// dll is instead FETCHED at build time from the pinned mpv-winbuild dev
// package, verified by sha256, and shipped inside the packaged app image
// (appResourcesRootDir below), so playback works with zero user setup.
//
// Three consumers share the one copy in the gitignored tools/mpv/:
//  * the `run` task points jna.library.path there for dev runs,
//  * the `test` task does the same for the real-engine suites,
//  * the packaging tasks copy it (plus the third-party notice) into the
//    app-resources dir that jpackage installs next to the jars — Main.kt
//    reads compose.application.resources.dir and re-points JNA at it.
//
// Extraction needs a 7-Zip binary: every distributor ships the dev package
// as .7z (LZMA2+BCJ2, beyond pure-Java readers), while Windows dev boxes and
// GitHub's windows runners all carry one. Machines without it keep the
// pre-existing manual escape hatch — drop libmpv-2.dll into tools/mpv/.
val libmpvToolsDir = rootProject.layout.projectDirectory.dir("tools/mpv")

// x86_64 build of libmpv (GPL-2.0-or-later — fine to bundle in this GPLv3
// app; see packaging/mpv-third-party-notice.txt) from the mpv-winbuild
// project, mirrored on GitHub by dyphire because the canonical SourceForge
// downloads rotate mirrors and have been observed serving byte-unstable
// responses. Both URLs and sha256s are pinned; bump the build inside the
// URLs (and re-hash the archives) to move to a newer mpv — deliberately: the
// engine's property semantics and the real-engine test expectations were
// live-probed against this v0.41-era build (wave 17B), and an Aug-2026 git
// build already showed `vf` readback drift in MpvDesktopEngineVideoTest.
// The dev archive carries only libmpv-2.dll; the PLAYER archive from the
// same build supplies lua51.dll, which this libmpv links dynamically
// (verified: the dll fails to load with "module not found" without it).
// -Pjellyplay.mpvDevUrl / -Pjellyplay.mpvPlayerUrl override for experiments.
val libmpvDevArchiveUrl = providers.gradleProperty("jellyplay.mpvDevUrl").orElse(
    "https://github.com/dyphire/mpv-winbuild/releases/download/mpv_own-2025-09-30/" +
        "mpv-dev-x86_64-20250930-git-05656cd.7z",
)
val libmpvDevArchiveSha256 =
    "0f952998dac3ca767d9e859580f7ed49c387f410bce17e90921f00435f1b96b4"
val libmpvPlayerArchiveUrl = providers.gradleProperty("jellyplay.mpvPlayerUrl").orElse(
    "https://github.com/dyphire/mpv-winbuild/releases/download/mpv_own-2025-09-30/" +
        "mpv-x86_64-20250930-git-05656cd.7z",
)
val libmpvPlayerArchiveSha256 =
    "bbcba68f0be265b8bd341a0b75da18000c37bf030d341c69adaf8404940c251b"

// Declared as a proper task class (not a doLast lambda) for the same
// configuration-cache reason as GeneratePackagingIconsTask below.
abstract class FetchBundledLibmpvTask : DefaultTask() {
    @get:Input abstract val devArchiveUrl: Property<String>
    @get:Input abstract val devArchiveSha256: Property<String>
    @get:Input abstract val playerArchiveUrl: Property<String>
    @get:Input abstract val playerArchiveSha256: Property<String>
    @get:InputFile abstract val noticeFile: RegularFileProperty
    @get:OutputFile abstract val dllFile: RegularFileProperty
    @get:OutputDirectory abstract val resourcesDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val dll = dllFile.get().asFile
        if (dll.isFile && dll.length() > 0L) {
            // Manual drop-in (the pre-bundling workflow) still wins — never
            // overwrite what a developer placed there on purpose.
            syncResources(dll)
            return
        }
        if (!System.getProperty("os.name", "").lowercase().startsWith("windows")) {
            // The bundle only carries the Windows dll; Linux/macOS desktop
            // builds load the system libmpv.so/.dylib through JNA's normal
            // search path and a PE dll could never load there anyway.
            logger.lifecycle("Bundled libmpv is Windows-only; nothing to fetch on this OS")
            return
        }

        val sevenZip = findSevenZip() ?: throw GradleException(
            "7-Zip not found (libmpv ships as .7z). Install 7-Zip from https://7-zip.org, " +
                "or manually place libmpv-2.dll into ${dll.parentFile} (see README §Desktop playback).",
        )

        // Download+verify as ONE retryable unit: this machine's network stack
        // intermittently delivers corrupt bytes through both the JVM client
        // and curl (observed as a different wrong sha256 per attempt), so a
        // single-shot download that passes a length check cannot be trusted —
        // only the pinned hash can.
        fun fetchArchive(url: String, expectedSha256: String, targetName: String): File {
            val archive = File(temporaryDir, targetName)
            for (attempt in 1..3) {
                archive.delete()
                archive.parentFile.mkdirs()
                logger.lifecycle("Downloading {} (attempt {})", url, attempt)
                download(archive, url)
                if (archive.isFile && archive.length() > 0L && sha256Hex(archive) == expectedSha256) {
                    return archive
                }
                logger.lifecycle("Download attempt {} produced bad bytes; retrying", attempt)
            }
            archive.delete()
            throw GradleException(
                "Could not obtain $url after 3 attempts (expected sha256 $expectedSha256) " +
                    "— flaky network or a changed upstream artifact",
            )
        }

        fun extract(archive: File, vararg entryNames: String): List<File> {
            val extractDir = File(temporaryDir, "extracted-${archive.nameWithoutExtension}")
            extractDir.deleteRecursively()
            extractDir.mkdirs()
            val extraction = ProcessBuilder(
                sevenZip.absolutePath, "e", "-y",
                "-o${extractDir.absolutePath}", archive.absolutePath, *entryNames,
            ).redirectErrorStream(true).start()
            val extractionLog = extraction.inputStream.bufferedReader().readText()
            val extracted = entryNames.map { File(extractDir, it) }
            if (extraction.waitFor() != 0 || extracted.any { !it.isFile }) {
                throw GradleException(
                    "7-Zip could not extract ${entryNames.toList()} from ${archive.name} " +
                        "(exit ${extraction.exitValue()}):\n$extractionLog",
                )
            }
            return extracted
        }

        val devArchive = fetchArchive(devArchiveUrl.get(), devArchiveSha256.get(), "mpv-dev.7z")
        val playerArchive = fetchArchive(playerArchiveUrl.get(), playerArchiveSha256.get(), "mpv-player.7z")
        val (libmpvDll) = extract(devArchive, "libmpv-2.dll")
        val (luaDll) = extract(playerArchive, "lua51.dll")

        dll.parentFile.mkdirs()
        libmpvDll.copyTo(dll, overwrite = true)
        luaDll.copyTo(File(dll.parentFile, "lua51.dll"), overwrite = true)
        logger.lifecycle(
            "Bundled libmpv-2.dll ({} MiB) + lua51.dll ready at {}",
            dll.length() / (1024 * 1024),
            dll.parentFile,
        )
        syncResources(dll)
    }

    /** Mirrors the dlls + license notice into the app-resources packaging dir. */
    private fun syncResources(dll: File) {
        val resources = resourcesDir.get().asFile.apply { mkdirs() }
        dll.copyTo(File(resources, "libmpv-2.dll"), overwrite = true)
        File(dll.parentFile, "lua51.dll").takeIf { it.isFile }
            ?.copyTo(File(resources, "lua51.dll"), overwrite = true)
        noticeFile.get().asFile.copyTo(File(resources, "THIRD-PARTY-NOTICE-mpv.txt"), overwrite = true)
    }

    /**
     * Two-step download. The JVM's HttpClient first (with redirects — GitHub
     * release assets 302 to a CDN host, and the JVM default of NEVER saves
     * the empty 3xx body); when the JVM stack is blocked outright —
     * per-process firewalls that exempt native binaries only, TLS
     * interception scoped the same way — curl takes over. curl.exe ships
     * with Windows 10+ and every CI runner, so the fallback is dependable.
     */
    private fun download(target: File, url: String) {
        try {
            val response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
                .send(
                    HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofFile(target.toPath()),
                )
            // ofFile writes the body whatever the status is — an HTTP error
            // page would otherwise pass a non-empty check and fail later as
            // a sha256 mismatch with no hint of the real cause.
            if (response.statusCode() == 200 && target.isFile && target.length() > 0L) return
            logger.lifecycle(
                "JVM HttpClient got status {} ({} bytes); falling back to curl",
                response.statusCode(),
                target.length(),
            )
        } catch (e: Exception) {
            logger.lifecycle("JVM HttpClient failed ({}); falling back to curl", e.message)
        }
        target.delete()
        val curl = findOnPath("curl")
            ?: throw GradleException("Neither the JVM HttpClient nor curl could fetch $url")
        logger.lifecycle("Downloading via curl at {}", curl.absolutePath)
        val fetch = ProcessBuilder(
            curl.absolutePath, "-fsSL", "-o", target.absolutePath, url,
        ).redirectErrorStream(true).start()
        val fetchLog = fetch.inputStream.bufferedReader().readText()
        if (fetch.waitFor() != 0) {
            target.delete()
            throw GradleException("curl could not fetch $url (exit ${fetch.exitValue()}):\n$fetchLog")
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** First executable named [names] on PATH, or null. */
    private fun findOnPath(vararg names: String): File? {
        for (name in names) {
            val lookup = ProcessBuilder("where", name).redirectErrorStream(true).start()
            val hits = lookup.inputStream.bufferedReader().readText()
            if (lookup.waitFor() == 0) {
                hits.lineSequence()
                    .mapNotNull { it.trim().takeIf { it.isNotEmpty() } }
                    .map(::File)
                    .firstOrNull { it.isFile }
                    ?.let { return it }
            }
        }
        return null
    }

    private fun findSevenZip(): File? =
        findOnPath("7z", "7za")
            ?: listOf(
                File("C:\\Program Files\\7-Zip\\7z.exe"),
                File("C:\\Program Files (x86)\\7-Zip\\7z.exe"),
            ).firstOrNull { it.isFile }
}

val fetchBundledLibmpv = tasks.register<FetchBundledLibmpvTask>("fetchBundledLibmpv") {
    devArchiveUrl.set(libmpvDevArchiveUrl)
    devArchiveSha256.set(libmpvDevArchiveSha256)
    playerArchiveUrl.set(libmpvPlayerArchiveUrl)
    playerArchiveSha256.set(libmpvPlayerArchiveSha256)
    noticeFile.set(layout.projectDirectory.file("packaging/mpv-third-party-notice.txt"))
    dllFile.set(libmpvToolsDir.file("libmpv-2.dll"))
    resourcesDir.set(libmpvToolsDir.dir("appResources/windows-x64"))
}

// Dev runs resolve libmpv from tools/mpv — JNA also keeps searching PATH and
// MPV_LIBRARY wins over everything (MpvLib.load checks it first). Lazy
// matching because the Compose plugin registers its run task (a JavaExec
// subclass) after this script block evaluates.
tasks.matching { it.name == "run" }.configureEach {
    dependsOn(fetchBundledLibmpv)
    (this as? JavaExec)?.systemProperty("jna.library.path", libmpvToolsDir.asFile.absolutePath)
}

// The Compose plugin's prepareAppResources (Sync) reads the fetch task's
// resourcesDir output (appResourcesRootDir below); without this declaration
// Gradle 9 task-graph validation fails whenever the Sync actually re-executes
// (e.g. after tools/mpv was cleaned) instead of being up-to-date.
tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(fetchBundledLibmpv)
}

// Every packaging flavor (and the no-installer app image) must carry the dll;
// the fetch task self-gates by OS and dll presence, so non-Windows lanes no-op.
tasks.matching {
    it.name.startsWith("package") || it.name.startsWith("createDistributable")
}.configureEach {
    dependsOn(fetchBundledLibmpv)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // libmpv for MpvDesktopEngine's tests: the fetch task above materializes
    // it on Windows (manual drop-in into tools/mpv also works); without it
    // the engine tests skip.
    dependsOn(fetchBundledLibmpv)
    systemProperty(
        "jna.library.path",
        rootProject.layout.projectDirectory.dir("tools/mpv").asFile.absolutePath,
    )
}

// google's androidx.navigation3:navigation3-ui publishes only an API-stub for
// JVM ("jvmstubs") — NavDisplay dies at runtime with
// "Implemented only in JetBrains fork". The real desktop implementation is
// the JetBrains fork coordinate; the fork's POM depends on the google runtime
// artifact, so only the -ui module is swapped (1.1.5 requests collapse onto
// the fork's 1.1.1 — same androidx.navigation3.ui package, ABI-stable surface).
// Shared feature modules' jvm artifacts carry the google -ui dep transitively,
// hence the graph-wide substitution instead of swapping our own direct edge.
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.navigation3:navigation3-ui"))
            .using(module(libs.jb.navigation3.ui.get().toString()))
            .because("google navigation3-ui is jvm-stubbed; desktop NavDisplay needs the JetBrains fork")
    }
}

// ── Wave 10A release engineering ────────────────────────────────────────────
// packageVersion is release-driven: pass -PjellyplayVersion=x.y.z on the
// release lane (CI desktop-package job does exactly that). jpackage demands
// a strictly numeric major.minor.build triple — Windows MSI's ProductVersion
// rejects non-numeric segments outright ("1.2.3-rc1" fails msiexec validation;
// deb/dmg are more lenient but we keep one version grammar), so a
// git-describe-style fallback would poison every package, not just msi.
// Anything non-conforming passed explicitly FAILS here rather than silently
// falling back — a packaged 0.1.0 shipped while believing it was 2.0.0 is the
// failure mode this guard exists for.
val jellyplayPackageVersion = run {
    val requested = project.findProperty("jellyplayVersion")?.toString()
    val numericTriple = Regex("""\d+\.\d+\.\d+""")
    when {
        requested == null || requested.isBlank() -> "0.1.0" // dev-machine fallback
        numericTriple.matches(requested) -> requested
        else -> throw GradleException(
            "-PjellyplayVersion='$requested' is not a numeric x.y.z; jpackage/msi " +
                "requires digits-only major.minor.build (e.g. -PjellyplayVersion=1.2.3)"
        )
    }
}

// The release CHANNEL rides a separate display version: packageVersion above
// must stay digits-only (jpackage/msi), so alpha tags like "0.11.0-alpha.1"
// pass as -PjellyplayVersionName and land in a classpath properties file that
// the About screen's DesktopAppMetaProvider reads. Defaults to the numeric
// package version; when no file exists (settings-module tests, IDE runs
// before first processResources) the provider falls back to "dev".
val jellyplayDisplayVersion = providers.gradleProperty("jellyplayVersionName")
    .orElse(jellyplayPackageVersion)

// Same config-cache-safe declared-task pattern as GeneratePackagingIconsTask
// below: a doLast lambda capturing script-scope vals fails serialization.
abstract class WriteDesktopBuildInfoTask : DefaultTask() {
    @get:Input abstract val versionName: Property<String>
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun write() {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            # Generated by WriteDesktopBuildInfoTask; do not edit. Release lanes
            # pass -PjellyplayVersionName (e.g. 0.11.0-alpha.1).
            versionName=${versionName.get()}
            """.trimIndent() + "\n",
        )
    }
}

val writeDesktopBuildInfo = tasks.register<WriteDesktopBuildInfoTask>("writeDesktopBuildInfo") {
    versionName.set(jellyplayDisplayVersion)
    outputFile.set(layout.buildDirectory.file("generated/build-info/desktop-build.properties"))
}

// Wiring the srcDir through the task-output provider (not the bare directory)
// is what makes processResources depend on writeDesktopBuildInfo implicitly.
sourceSets.named("main") {
    resources.srcDir(writeDesktopBuildInfo.map { it.outputFile.get().asFile.parentFile })
}

/** The module-local icon/asset directory the generator task below writes. */
val packagingIconsDir = layout.projectDirectory.dir("packaging/icons")

// Draws the placeholder brand assets into packaging/icons/ — committed as real
// files so ordinary builds never need to re-run this task. One draw routine,
// three containers:
//  * JellyPlay-linux.png   flat 512×512 PNG (freedesktop icon spec minimum)
//  * JellyPlay.ico         multi-size ICO; every entry embeds a PNG (Vista+)
//  * JellyPlay.icns        ic07/ic08/ic09/ic10 PNG sub-images (128..1024)
// All bytes are assembled by hand (javax.imageio PNG encoder + plain header
// writing) — no binary blobs checked in unread, no external downloads.
// Declared as a proper task class because a script-level doLast lambda that
// touches top-level vals captures the whole script object, which Gradle's
// configuration cache (enabled repo-wide) refuses to serialize.
abstract class GeneratePackagingIconsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outDir = outputDir.get().asFile.apply { mkdirs() }

        fun draw(size: Int): BufferedImage =
            BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { img ->
                val g = img.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                // Background: Jellyfin-dark navy #000B25.
                g.color = Color(0x00, 0x0B, 0x25)
                g.fillRect(0, 0, size, size)
                // Play triangle in Jellyfin blue #00A4DC.
                val x = { f: Double -> (size * f).toInt() }
                val triangle = Polygon(
                    intArrayOf(x(0.38), x(0.38), x(0.78)),
                    intArrayOf(x(0.26), x(0.74), x(0.50)),
                    3,
                )
                g.color = Color(0x00, 0xA4, 0xDC)
                g.fillPolygon(triangle)
                g.dispose()
            }

        fun pngBytes(size: Int): ByteArray =
            ByteArrayOutputStream().use { buffer ->
                ImageIO.write(draw(size), "png", buffer)
                buffer.toByteArray()
            }

        fun Int.toLittleEndianBytes(width: Int): ByteArray =
            ByteArray(width) { i -> ((this shr (8 * i)) and 0xFF).toByte() }
        fun Int.toBigEndianBytes(width: Int): ByteArray =
            ByteArray(width) { i -> ((this shr (8 * (width - 1 - i))) and 0xFF).toByte() }

        // ICO container: 6-byte ICONDIR + one 16-byte ICONDIRENTRY per image,
        // then raw embedded PNGs. widthByte 0 encodes 256 per the spec.
        fun writeIco(file: File) {
            val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
            val images = sizes.map { it to pngBytes(it) }
            val data = ByteArrayOutputStream().use { out ->
                out.write(byteArrayOf(0, 0)); out.write(byteArrayOf(1, 0)) // reserved=0, type=icon
                out.write(sizes.size.toLittleEndianBytes(2))
                var offset = 6 + 16 * sizes.size
                for ((size, png) in images) {
                    out.write(if (size >= 256) 0 else size)
                    out.write(if (size >= 256) 0 else size)
                    out.write(byteArrayOf(0, 0))                        // palette count, reserved
                    out.write(1.toLittleEndianBytes(2))                 // color planes
                    out.write(32.toLittleEndianBytes(2))                // bits per pixel
                    out.write(png.size.toLittleEndianBytes(4))          // bytes of resource data
                    out.write(offset.toLittleEndianBytes(4))
                    offset += png.size
                }
                images.forEach { (_, png) -> out.write(png) }
                out.toByteArray()
            }
            file.writeBytes(data)
        }

        // ICNS container: 'icns' magic + total length (big-endian), then
        // entries of [type, entryLength(incl. 8-byte entry header), png].
        fun writeIcns(file: File) {
            val entries = listOf("ic07" to 128, "ic08" to 256, "ic09" to 512, "ic10" to 1024)
                .map { (type, size) -> type to pngBytes(size) }
            val totalLength = 8 + entries.sumOf { (_, png) -> 8 + png.size }
            val data = ByteArrayOutputStream().use { out ->
                out.write("icns".toByteArray(Charsets.US_ASCII))
                out.write(totalLength.toBigEndianBytes(4))
                entries.forEach { (type, png) ->
                    out.write(type.toByteArray(Charsets.US_ASCII))
                    out.write((8 + png.size).toBigEndianBytes(4))
                    out.write(png)
                }
                out.toByteArray()
            }
            file.writeBytes(data)
        }

        ImageIO.write(draw(512), "png", outDir.resolve("JellyPlay-linux.png"))
        writeIco(outDir.resolve("JellyPlay.ico"))
        writeIcns(outDir.resolve("JellyPlay.icns"))
    }
}

val generatePackagingIcons = tasks.register<GeneratePackagingIconsTask>("generatePackagingIcons") {
    outputDir.set(packagingIconsDir)
}

compose.desktop {
    application {
        mainClass = "com.raulshma.jellyplay.desktop.MainKt"

        buildTypes.release.proguard {
            // Ship unsigned minimal packaging for V1; hardening is §Phase X.
            isEnabled = false
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg)
            packageName = "JellyPlay"
            packageVersion = jellyplayPackageVersion
            description = "JellyPlay — Jellyfin client for desktop"
            vendor = "JellyPlay"

            // Explicit module enumeration instead of includeAllModules:
            // the compose plugin auto-runs jdeps over the app jars and adds
            // what it finds; `modules(...)` below layers the extras jdeps
            // cannot see on top (output of `suggestRuntimeModules`, plus the
            // reflection-typical JDK modules this graph is known to touch:
            // JNA native loading, JMX management, LDAP/JNDI naming, JDBC,
            // and the crypto providers). Validated via
            // `./gradlew :apps:desktop:createReleaseDistributable`.
            modules(
                "java.instrument",
                "jdk.security.auth",
                "jdk.unsupported",
                "java.management",
                "java.naming",
                "java.sql",
                "jdk.crypto.ec",
                "jdk.crypto.cryptoki",
            )

            // Bundled libmpv (see fetchBundledLibmpv above): the windows-x64
            // subtree is copied into the installed app image and exposed at
            // runtime as the compose.application.resources.dir property,
            // which Main.kt re-points jna.library.path at. Non-Windows
            // formats see no windows-x64 subtree and stay unchanged.
            appResourcesRootDir.set(rootProject.layout.projectDirectory.dir("tools/mpv/appResources"))

            linux {
                iconFile.set(packagingIconsDir.file("JellyPlay-linux.png"))
            }
            windows {
                menuGroup = packageName
                upgradeUuid = "6ce4b9a2-5f4e-4b0f-9dc3-2f4b8a9b1c7d"
                iconFile.set(packagingIconsDir.file("JellyPlay.ico"))
            }
            macOS {
                // Apple's jpackage bundler rejects app-versions whose FIRST
                // segment is zero ("The first number in an app-version cannot
                // be zero or negative" — plist CFBundleVersion rule), so the
                // pre-1.0 line (0.1.0 dev dry-run, 0.11.0 alphas) can never
                // package a dmg as-is. Shift 0.x.y up to 1.x.y for the macOS
                // BUNDLE version only; msi/deb keep the real numeric triple
                // and the About screen + release tags carry the display
                // version (desktop-build.properties), so this stays cosmetic.
                val versionParts = jellyplayPackageVersion.split('.')
                packageVersion = if (versionParts.first() == "0") {
                    "1." + versionParts.drop(1).joinToString(".")
                } else {
                    jellyplayPackageVersion
                }
                iconFile.set(packagingIconsDir.file("JellyPlay.icns"))
                bundleID = "com.raulshma.jellyplay.desktop"
            }
        }
    }
}
