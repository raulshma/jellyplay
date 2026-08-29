import org.jetbrains.compose.desktop.application.dsl.TargetFormat
// NOTE: java.awt/java.io must be IMPORTED here rather than written as inline
// FQNs — this project applies the JVM plugin, so the identifier `java`
// collides with the `java { }` extension accessor in script scope and every
// `java.<pkg>…` chain fails script compilation ("Unresolved reference 'awt'").
import java.awt.Color
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
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

tasks.named<Test>("test") {
    useJUnitPlatform()
    // libmpv for MpvDesktopEngine's tests: dev-only checkout location
    // (gitignored, fetched per machine); without it the engine tests skip.
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

            // includeAllModules WON over explicit modules(...) enumeration:
            // this graph pulls 30 shared feature/core implementation edges plus
            // reflection edges JNA cannot declare statically (com.sun.jna
            // native loading), coil's ServiceLoader-registered OkHttp network
            // fetcher and kotlinx.serialization polymorphic lookups — jlink
            // auto-analysis misses those classes of wiring, and each missed
            // hint costs a full package + boot-smoke cycle to discover.
            // Merging everything into the single unified module keeps
            // ServiceLoader and reflection intact. Measured cost (wave 10A,
            // Windows, version 0.1.0): app image ~276 MB, MSI installer
            // ~155 MB (MSIs embed timestamps — exact bytes do not reproduce) —
            // accepted for v1.
            includeAllModules = true

            linux {
                iconFile.set(packagingIconsDir.file("JellyPlay-linux.png"))
            }
            windows {
                menuGroup = packageName
                upgradeUuid = "6ce4b9a2-5f4e-4b0f-9dc3-2f4b8a9b1c7d"
                iconFile.set(packagingIconsDir.file("JellyPlay.ico"))
            }
            macOS {
                iconFile.set(packagingIconsDir.file("JellyPlay.icns"))
                bundleID = "com.raulshma.jellyplay.desktop"
            }
        }
    }
}
