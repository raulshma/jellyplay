import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import jellyplay.buildlogic.DesktopPackagingExtension

plugins {
    id("org.jetbrains.kotlin.jvm") // version inherited from the plugin classpath
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    // Packaging mechanics — fetchBundledLibmpv + writeDesktopBuildInfo (task
    // names byte-compatible with the former inline definitions; CI release
    // lanes reference them), the numeric packageVersion grammar, the release
    // channel, and the macOS bundle-version shift — live in the
    // build-logic-convention included build now. The relocated history and
    // the dependency-conveyor notes are appended to
    // docs/kmp-migration-plan.md ("Desktop packaging history").
    id("jellyplay.desktop.packaging")
}

kotlin {
    jvmToolchain(17)
}

// Desktop shell (docs/kmp-migration-plan.md): Compose Window + tray
// + menubar + shortcuts over the shared core stack. Feature modules land here
// one conveyor step at a time (§V1c/V3); the per-feature LIVE/latent notes
// that used to run alongside these declarations moved to that doc's
// "Desktop packaging history" section — the dependency set below is complete
// and unchanged.
dependencies {
    implementation(compose.desktop.currentOs)

    implementation(project(":shared:core:model"))
    implementation(project(":shared:core:designsystem"))
    implementation(project(":shared:core:ui"))
    implementation(project(":shared:core:datastore"))
    implementation(project(":shared:core:database"))
    implementation(project(":shared:core:network"))
    implementation(project(":shared:core:data"))
    // Cancellation-safe suspend wrappers — the desktop flow harness's
    // suspend bodies use them instead of bare runCatching.
    implementation(project(":shared:core:concurrency"))
    // MediaEngine contract for the desktop player engine.
    implementation(project(":shared:core:player-contract"))

    // Feature conveyor (V3) — every module's LIVE/latent story is in
    // docs/kmp-migration-plan.md; player-live is the one LATENT registration
    // (Android-only engine seams; its jvm target exists for its shared
    // ViewModel's jvmTest suite).
    implementation(project(":shared:feature:search"))
    implementation(project(":shared:feature:library"))
    implementation(project(":shared:feature:music"))
    implementation(project(":shared:feature:livetv"))
    implementation(project(":shared:feature:downloads"))
    implementation(project(":shared:feature:syncplay"))
    // …settings — the harness names SettingsViewModel directly (the shell's
    // own screens only render settingsSection, so the ViewModel supertype
    // was never on the shell classpath before).
    implementation(project(":shared:feature:settings"))
    implementation(libs.lifecycle.viewmodel)
    implementation(project(":shared:feature:admin"))
    implementation(project(":shared:feature:editor"))
    implementation(project(":shared:feature:calendar"))
    implementation(project(":shared:feature:requests"))
    implementation(project(":shared:feature:shortcuts"))
    implementation(project(":shared:feature:newsletter"))
    implementation(project(":shared:feature:insights"))
    implementation(project(":shared:feature:arrqueue"))
    implementation(project(":shared:feature:onboarding"))
    implementation(project(":shared:feature:details"))
    implementation(project(":shared:feature:auth"))
    implementation(project(":shared:feature:home"))
    implementation(project(":shared:feature:player-live"))
    implementation(project(":shared:feature:player-audio"))
    implementation(project(":shared:feature:player-book"))
    implementation(project(":shared:feature:player-video"))
    // Shared shell graph (shared appSections): one entryProvider behind both
    // shells — DesktopAppRoot supplies the desktop ShellHostHooks and the
    // conditional VideoPlayer registration; the dead-end guard derives from
    // the same graph's registration ledger.
    implementation(project(":shared:feature:shell"))

    // Desktop libmpv binding (MpvDesktopEngine): JNA loads
    // mpv-2.dll / libmpv.so / libmpv.dylib at runtime; also resolves
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
    // The shared MediaEngine double + pollUntil (the fake-twin merge): the
    // desktop's app-side FakeMediaEngine copy was deleted; its suites now
    // construct the fixtures class with LoadBehavior.AUTO_PLAY — the same
    // personality the deleted twin hardcoded. Test-scoped edge per the
    // test-fixtures house rules.
    testImplementation(project(":shared:core:test-fixtures"))
    // Real org.json for the WebSocketEvent fixtures (DesktopIdleAmbientControllerTest):
    // WebSocketEvent.data is a non-null org.json.JSONObject — the same
    // "real org.json for the desktop target" edge shared:core:network /
    // shared:core:data declare, scoped here to tests because the app code
    // deliberately parses the Sessions push with kotlinx instead.
    testImplementation(libs.org.json)
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

// The committed packaging icon assets (drawn once by the since-deleted
// generatePackagingIcons task — placeholder brand art whose outputs are real
// committed files ordinary builds never regenerate; PackagingIconAssetsTest
// validates the bytes structurally).
val packagingIconsDir = layout.projectDirectory.dir("packaging/icons")

// The version grammar (jellyplayDesktopPackaging.packageVersion and friends)
// comes from the jellyplay.desktop.packaging convention plugin above.
val packaging = extensions.getByType(DesktopPackagingExtension::class)

compose.desktop {
    application {
        mainClass = "com.raulshma.jellyplay.desktop.MainKt"

        // KCEF (the desktop EPUB reader's Chromium host) needs the AWT
        // module opens or CEF init fails with an accessibility error. Module
        // access flags only — no heap/perf semantics (unlike the heap pins
        // deliberately omitted above).
        jvmArgs(
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        )

        // Deliberately NOT applied: the
        // suggested -Xms256m/-Xmx2g pins a heap ceiling BELOW the JVM default
        // (25% of physical RAM) on >8 GB machines, a player OOM regression
        // risk. Only add jvmArgs here after A/B-ing via
        // tools/perf/desktop-baseline.sh.

        buildTypes.release.proguard {
            // Ship unsigned minimal packaging for V1.
            isEnabled = false
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg)
            packageName = "JellyPlay"
            packageVersion = packaging.packageVersion.get()
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

            // Bundled libmpv (see the fetchBundledLibmpv task from the
            // convention plugin): the windows-x64 subtree is copied into the
            // installed app image and exposed at runtime as the
            // compose.application.resources.dir property, which Main.kt
            // re-points jna.library.path at. Non-Windows formats see no
            // windows-x64 subtree and stay unchanged.
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
                // The 0.x.y -> 1.x.y shift (Apple's jpackage rejects
                // app-versions whose first segment is zero — plist
                // CFBundleVersion rule) lives in the convention plugin's
                // macOsBundleVersion; msi/deb keep the real numeric triple
                // and the About screen + release tags carry the display
                // version (desktop-build.properties), so this stays cosmetic.
                packageVersion = packaging.macOsBundleVersion.get()
                iconFile.set(packagingIconsDir.file("JellyPlay.icns"))
                bundleID = "com.raulshma.jellyplay.desktop"
            }
        }
    }
}
