import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.shell"
        compileSdk = 37
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // web breadth: the commonMain surface (AdminRefreshGate /
    // OnboardingGate / ShellSessionController / UserMessageHost — the four
    // policy/state files, java-free) now carries a wasmJs target. Scope is
    // honest: the 21-feature aggregator (appSections + the per-feature
    // Section builders) stays in jvmShared — it is the consuming shells'
    // graph, and apps/web wires its own WebAppRoot section graph, so wasmJs
    // compiles the commonMain policy surface only. The browser test task
    // stays off like core:ui/core:network/music — jvmTest pins semantics.
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // appSections must see every *Section builder it registers —
        // detailsSection lives in :shared:feature:details' jvmShared source
        // set, invisible from a consumer's commonMain — so the shared shell
        // graph lives in this module's own jvmShared middle set (details'
        // exact wiring), shared verbatim by the only two consumers: the
        // Android and desktop shells.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            // HomeMode in the ShellHostHooks surface (not re-exported by
            // core:ui — implementation there).
            implementation(project(":shared:core:model"))
            // Navigation vocabulary: Route / NavKey / Navigator.
            implementation(project(":shared:core:ui"))
            // Star-topology aggregator: one dependency per feature whose
            // *Section builder appSections registers. Nothing depends on this
            // module except the two shells.
            implementation(project(":shared:feature:home"))
            implementation(project(":shared:feature:library"))
            implementation(project(":shared:feature:search"))
            implementation(project(":shared:feature:livetv"))
            implementation(project(":shared:feature:details"))
            implementation(project(":shared:feature:editor"))
            implementation(project(":shared:feature:player-audio"))
            implementation(project(":shared:feature:player-book"))
            implementation(project(":shared:feature:downloads"))
            implementation(project(":shared:feature:auth"))
            implementation(project(":shared:feature:settings"))
            implementation(project(":shared:feature:admin"))
            implementation(project(":shared:feature:music"))
            implementation(project(":shared:feature:syncplay"))
            implementation(project(":shared:feature:onboarding"))
            implementation(project(":shared:feature:newsletter"))
            implementation(project(":shared:feature:insights"))
            implementation(project(":shared:feature:requests"))
            implementation(project(":shared:feature:arrqueue"))
            implementation(project(":shared:feature:calendar"))
            implementation(project(":shared:feature:shortcuts"))
            // The 22nd feature: player-live's Koin module rides the shared
            // registration list (SharedFeatureModules) beside its section
            // peers — the desktop bridge-probed video-player alternative
            // lives there.
            implementation(project(":shared:feature:player-live"))
            // The musicContent lambda invokes the @Composable MusicHomeScreen.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.material3)
            // The UserMessageHost seam's shared UiText resolver (suspend
            // getString) — the shared core:ui keeps compose-resources
            // implementation-scoped, so the module that resolves messages
            // carries the runtime (core/ui precedent).
            implementation(compose.components.resources)
            // ShellHostHooks.surpriseRequests is Flow<Unit>.
            implementation(libs.kotlinx.coroutines.core)
            // entryProvider / EntryProviderScope / NavEntry — runtime only:
            // NavDisplay and the *Section builders' own nav3-ui imports stay
            // in the consuming shells / feature modules.
            implementation(libs.navigation3.runtime)
        }
        // AdminRefreshGate policy pins (settings/core-data precedent).
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            // The session controller's arbitration/collect tests (runTest +
            // fake clock, the core-data jvmTest pattern).
            implementation(libs.coroutines.test)
        }
        // SharedFeatureModules (jvmShared) collects every feature Koin
        // module as a Module value — the features' own koin edges are
        // implementation-scoped and invisible cross-project, so the
        // aggregator declares the type it collects.
        getByName("jvmShared").dependencies {
            implementation(libs.koin.core)
        }
    }
}
