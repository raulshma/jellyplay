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
        }
        getByName("commonTest").dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
