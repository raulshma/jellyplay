import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

    // …music, third conveyor item — PARTIAL, omitted from nav v1: the
    // AudioQueueFacade cluster (instant-mix + now-playing screens) has no
    // desktop defs until the desktop player slice.
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

    // …settings, seventh conveyor item — PARTIAL, omitted from nav v1:
    // SettingsViewModel + AboutViewModel still need AdminRepository
    // (Hilt-only, no desktop def); the shell guards the settings routes.
    implementation(project(":shared:feature:settings"))

    // …admin, eighth conveyor item (DI registration only — latent:
    // AdminRepository/AdminStatisticsRepository have no desktop defs yet,
    // resolution is lazy so boot stays safe, and the desktop shell has no
    // admin nav entry).
    implementation(project(":shared:feature:admin"))



    // …editor, ninth conveyor item (DI registration only — latent:
    // StreamingSubtitleStore has no desktop def yet, resolution is lazy so
    // boot stays safe, and the desktop shell has no editor nav entry).
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


    // …onboarding, conveyor feature — LIVE; nav v1 registers
    // onboardingSection (reachable from Shortcuts). A desktop first-run
    // gate remains future work.
    implementation(project(":shared:feature:onboarding"))
    implementation(project(":shared:feature:details"))


    // …auth, Phase X cutover (feature-conveyor transform): desktop does
    // not render the auth screens yet (the shell signs in through its own
    // DesktopSignInPane, and the settings UserManagement drill-ins stay
    // dead-end-guarded), but the whole ctor graph is Koin-native here, so
    // the registration is live-resolvable, not latent.
    implementation(project(":shared:feature:auth"))


    // …home, Phase X cutover feature (the desktop landing screen) — module
    // compiles and homeModule is registered LATENT: four HomeViewModel ctor
    // deps are Android-only Hilt interop singles with no desktop defs yet,
    // and desktop nav does not wire a home entry, so nothing constructs the
    // VM. The nav wiring lands with the coordinator's post-merge pass.
    implementation(project(":shared:feature:home"))


    // Desktop libmpv binding (MpvDesktopEngine, Phase V2): JNA loads
    // mpv-2.dll / libmpv.so / libmpv.dylib at runtime.
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

compose.desktop {
    application {
        mainClass = "com.raulshma.jellyplay.desktop.MainKt"

        buildTypes.release.proguard {
            // Ship unsigned minimal packaging for V1; hardening is §Phase X.
            isEnabled = false
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "JellyPlay"
            packageVersion = "1.0.0"
            description = "JellyPlay — Jellyfin client for desktop"
            vendor = "JellyPlay"
            windows {
                menuGroup = packageName
                upgradeUuid = "6ce4b9a2-5f4e-4b0f-9dc3-2f4b8a9b1c7d"
            }
        }
    }
}
