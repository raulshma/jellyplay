import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.designsystem"
        // Google Fonts certificates resource (font_certs.xml) → R class.
        // (androidResources.enable itself comes from the convention plugin;
        // see its KDoc for the MissingResourceException story.)
    }

    sourceSets {
        getByName("androidMain").dependencies {
            // Google Fonts provider + Palette swatch extraction (Android-only halves).
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.google.fonts)
            implementation(libs.palette.ktx)
            implementation(libs.coil.compose)
        }
        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            // JetBrains CMP distribution: only publisher of JVM
            // compose binaries (see catalog note). Android resolves androidx
            // via the JB→androidx redirection.
            api(libs.jb.compose.runtime)
            api(libs.jb.compose.ui)
            api(libs.jb.compose.foundation)
            api(libs.jb.compose.animation)
            api(libs.jb.compose.material3)
            // Compose-resources runtime (Font resource loading, jvm actuals).
            implementation(compose.components.resources)
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly (same pattern as shared/core/ui and
// shared/feature/*). Legacy package + `.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.core.designsystem.generated.resources"
