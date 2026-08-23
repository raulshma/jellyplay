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

    // V3 feature conveyor: search (DI registration only for now — the nav
    // graph wiring lands with the desktop search slice).
    implementation(project(":shared:feature:search"))

    // …library, second conveyor item (DI registration only; PhotoExport is
    // desktop-inert until a gallery/share story lands there).
    implementation(project(":shared:feature:library"))

    // …music, third conveyor item (DI registration only — instant-mix needs
    // AudioQueueFacade defs that arrive with the desktop player slice).
    implementation(project(":shared:feature:music"))

    // …livetv, fourth conveyor item (DI registration only — documented-latent:
    // VM deps like mediaRepository have no desktop defs yet, resolution is
    // lazy so boot stays safe).
    implementation(project(":shared:feature:livetv"))

    // …downloads, fifth conveyor item (DI registration only — documented-latent:
    // downloadRepository/userDataMutator and the OfflineSyncManager single's
    // repository edges have no desktop defs yet, resolution is lazy so boot
    // stays safe).
    implementation(project(":shared:feature:downloads"))

    // …syncplay, sixth conveyor item (DI registration only — documented-latent:
    // mediaRepository has no desktop def yet, resolution is lazy so boot stays
    // safe, and the desktop shell has no SyncPlay nav entry).
    implementation(project(":shared:feature:syncplay"))

    // Desktop libmpv binding (MpvDesktopEngine, Phase V2): JNA loads
    // mpv-2.dll / libmpv.so / libmpv.dylib at runtime.
    implementation(libs.jna)

    // Swing Main dispatcher (SyncPlayPlaybackCore constructs on
    // Dispatchers.Main.immediate; plain JVM has none without this).
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
    // NavKey is public API surface of shared/core/ui's navigation helpers.
    implementation(libs.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
    // NavKey polymorphic registration enumerates Route's sealed leaves.
    implementation(kotlin("reflect"))

    // Image pipeline: coil3 desktop engine + OkHttp network fetcher.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)

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
