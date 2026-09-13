import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.auth"
        compileSdk = 37
        minSdk = 28
        // Compose-resources packaging (device-pass finding): with the
        // AGP-9 KMP library plugin, android resources are OFF by default, so
        // copyAndroidMainComposeResourcesToAndroidAssets never runs and the
        // app APK ships this module's Res accessors with NO backing .cvr
        // assets — runtime MissingResourceException on the first string read.
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // web breadth: the target compiles after the AddServerViewModel
    // failure classifiers went expect/actual — the javax/java.net taxonomy
    // moved verbatim to jvmShared (android+desktop stay byte-identical), and
    // wasmJsMain models the ktor/fetch taxonomy. The ViewModels still resolve
    // AuthRepository/ServerDiscoveryRepository, whose impls live in core:data's
    // jvmShared half — the web stack registers no binding yet, so the module
    // stays unrouted on web (orchestrator wiring lands separately).
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
        // The javax/java.net actuals of the AddServerViewModel failure
        // classifiers (JDK-only — no deps), requests-module jvmShared shape:
        // one copy serves BOTH android and desktop.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            // AuthRepository + ServerDiscoveryRepository (both Koin singles in
            // dataJvmModule — the whole ctor graph is Koin-native on BOTH
            // platforms, calendar/requests/shortcuts class).
            implementation(project(":shared:core:data"))
            implementation(project(":shared:core:ui"))
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.animation)
            implementation(libs.jb.compose.material3)
            // Compose-resources runtime (stringResource/StringResource API).
            implementation(compose.components.resources)
            implementation(libs.tabler.icons.outline)
            implementation(libs.tabler.icons.filled)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped with the syncplay move: no auth file imports it —
            // AuthNavigation uses entry<Route> from the nav3 runtime/ui
            // artifacts only.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            // Koin owns the auth ViewModels (feature conveyor: one framework
            // per type — the Hilt annotations were stripped at the move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        getByName("androidMain").dependencies {
            // The local-network seam actuals bridge LocalNetworkAccess
            // (:shared:core:ui androidMain since the cutover), which
            // keeps the Android 17 permission logic (and its MainActivity
            // consumer) in one place.
        }
        getByName("wasmJsMain").dependencies {
            // The wasmJs failure-classifier actual references ktor types
            // (HttpRequestTimeoutException, ktor-io IOException) to classify
            // transport failures — core:data/network expose ktor only as
            // implementation deps, so the same direct edge apps/web needed
            // for its connect-flow classifier applies here.
            implementation(libs.ktor.client.core)
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:auth
// so migrated files keep their `com.raulshma.jellyplay.feature.auth` imports;
// generated accessors land in `...feature.auth.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.auth.generated.resources"

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface. Scoped
// to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before (the
// identical block lives in shared/core/ui and shared/feature/requests).
configurations.configureEach {
    if (name.lowercase().contains("wasmjs")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.navigation3:navigation3-ui"))
                .using(module(libs.jb.navigation3.ui.get().toString()))
                .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
        }
    }
}
