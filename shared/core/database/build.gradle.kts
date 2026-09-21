plugins {
    id("jellyplay.kmp.library.base")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.database"
    }

    sourceSets {
        getByName("jvmShared").dependencies {
            // Module/qualifier types appear in the public di signatures
            // (Koin construction owner).
            api(libs.koin.core)
        }

        commonMain.dependencies {
            api(project(":shared:core:model"))
            api(libs.room3.runtime)
            // databaseDaosModule moved from jvmShared to commonMain (all it
            // touches — JellyPlayDatabase + the DAO getters — is commonMain
            // Room 3), so the Koin DSL must resolve on every target.
            api(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("jvmMain").dependencies {
            implementation(libs.okio)
            // BundledSQLiteDriver for the desktop Room builder
            // (DesktopDatabaseModule).
            implementation(libs.androidx.sqlite.bundled)
        }
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            // BundledSQLiteDriver for in-memory DAO tests and the JVM-driver
            // migration chain verification.
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.koin.test)
            // room3-testing MigrationTestHelper (spike criterion c).
            implementation(libs.room3.testing)
        }
        getByName("jvmTest").resources.srcDir("$projectDir/schemas")

    }
}

// Room 3 KSP runs per target; schema JSONs continue accumulating in the
// repo-tracked shared/core/database/schemas directory (identity of the
// JellyPlayDatabase schema history is what MigrationTest verifies against).
// room3 kept the room.schemaLocation KSP arg name (verified against
// Context$ProcessorOptions in room3-compiler 3.0.3); the androidx.room3
// Gradle plugin (marker exists at 3.0.3) is deliberately not applied —
// the KSP-arg path is the minimal-diff route under this AGP-9 KMP setup.
dependencies {
    add("kspAndroid", libs.room3.compiler)
    add("kspJvm", libs.room3.compiler)
}

ksp {
    arg("room.schemaLocation", "$rootDir/shared/core/database/schemas")
}
