plugins {
    id("jellyplay.kmp.library.base")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.model"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            // Annotation-only Compose usage (@Immutable/@Stable on models):
            // compose.runtime suffices, no compiler plugin needed — same pattern
            // the legacy :core:model module documented. androidx.compose.runtime
            // ships multiplatform variants resolved from the shared BOM.
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.runtime)
            // The home row modules' offline projections (core/model/home) parse
            // the offline store's `lastPlayedDate` stamps and apply the Next Up
            // day cutoff — the same kotlinx-datetime line the data layer pins
            // (0.8.0, ABI note in the version catalog).
            implementation(libs.kotlinx.datetime)
        }
        getByName("commonTest").dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
