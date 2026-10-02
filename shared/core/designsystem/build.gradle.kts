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
        }
    }
}

// The compose-resources `packageOfResClass` (the legacy
// `...core.designsystem.generated.resources` string) is a path-derived
// default from the convention plugin now — see KmpLibraryComposePlugin.
