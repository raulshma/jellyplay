import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.feature.book"
    }

    sourceSets {
        // android + desktop share the document/back-end layer verbatim: the
        // CBZ pager (java.util.zip.ZipFile + natural sort) and the OkHttp
        // reader-cache fetcher carry java.* bodies, while the page decoders
        // and PDF back-ends split per platform behind commonMain seams.
        // (The jvmShared middle source set comes from the convention plugin.)

        commonMain.dependencies {
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
        getByName("jvmTest").dependencies {
            // (kotlin("test") comes from the convention plugin.)
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
