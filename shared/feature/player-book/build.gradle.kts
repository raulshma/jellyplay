import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.feature.book"
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

    // web breadth: the target compiles — commonMain was already
    // seam-clean (BookContentResolver/BookDocumentOpener bind in the platform
    // modules), so only the four platform expects needed wasmJs actuals that
    // degrade honestly (null decode, no-op window ops, error-reporting EPUB
    // host). There is no BookDocumentOpener binding on web for now, so the
    // reader stays off the browser at runtime until the orchestrator wires
    // one. The karma/Chrome browser run stays off like core:ui/core:network/
    // requests — jvmTest pins the semantics.
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
        // android + desktop share the document/back-end layer verbatim: the
        // CBZ pager (java.util.zip.ZipFile + natural sort) and the OkHttp
        // reader-cache fetcher carry java.* bodies, while the page decoders
        // and PDF back-ends split per platform behind commonMain seams.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // NetworkQualifiers.streamingHttpClient — the reader-cache fetcher
            // rides the same streaming client the players use.
            implementation(project(":shared:core:network"))
            // Cancellation-safe suspend wrappers for the book session loader
            // and the platform document/prober back-ends.
            implementation(project(":shared:core:concurrency"))
            // okio.Path is the commonMain file handle the document/opener
            // seams pass around.
            implementation(libs.okio)
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
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.runtime.compose)
            // Koin owns the book-reader ViewModel (one framework per type).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        getByName("jvmShared").dependencies {
            // Plain GET streaming for the reader cache (BookContentResolver).
            implementation(libs.okhttp)
            // CBR (RAR) extraction — see the catalog entry's license note.
            implementation(libs.junrar)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
            // Compose UI regression for the TOC tick rail (ReaderTocRailUiTest):
            // injected tap/drag through the real gesture detectors. The UI
            // test needs a real skia scene: currentOs pulls the skiko-awt
            // runtime (native lib) the ui-test scene renders with.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
        getByName("androidMain").dependencies {
            // WindowCompat / WindowInsetsControllerCompat for the immersive
            // reader window ops.
            implementation(libs.androidx.core.ktx)
            // PDF outline (TOC) parsing — pages still raster through the
            // platform PdfRenderer; pdfbox-android is only the outline walk.
            implementation(libs.pdfbox.android)
        }
        getByName("jvmMain").dependencies {
            // Desktop PDF rendering. Android intentionally uses the platform
            // android.graphics.pdf.PdfRenderer (no dependency there).
            implementation(libs.pdfbox)
            // Desktop EPUB host — KCEF/Chromium; the screen owns KCEF.init and
            // shows the first-run CEF download progress.
            implementation(libs.compose.webview.multiplatform)
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Generated accessors land in
// `...feature.book.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.book.generated.resources"

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface. Scoped
// to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before (the
// identical block lives in shared/core/ui, shared/feature/requests and the
// other web modules).
configurations.configureEach {
    if (name.lowercase().contains("wasmjs")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.navigation3:navigation3-ui"))
                .using(module(libs.jb.navigation3.ui.get().toString()))
                .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
        }
    }
}
