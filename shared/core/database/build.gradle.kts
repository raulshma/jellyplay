import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.database"
        compileSdk = 37
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // No wasmJs target: web v1 ships without Room (plan §Phase W — the server
    // stays the source of truth; session-scoped state only).
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM-semantics code shared verbatim by android + desktop: the
        // javax.crypto AES-GCM TokenCipher body.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("jvmShared").dependencies {
            // Module/qualifier types appear in the public di signatures
            // (Phase C4 Koin construction owner).
            api(libs.koin.core)
        }

        getByName("commonMain").dependencies {
            api(project(":shared:core:model"))
            api(libs.room.runtime)
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("jvmMain").dependencies {
            implementation(libs.okio)
            // BundledSQLiteDriver for the desktop Room builder
            // (DesktopDatabaseModule, Phase C4).
            implementation(libs.androidx.sqlite.bundled)
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            // BundledSQLiteDriver for in-memory DAO tests and the JVM-driver
            // migration chain verification (plan §S4).
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.koin.test)
        }
        getByName("jvmTest").resources.srcDir("$projectDir/schemas")

    }
}

// Room KSP runs per target; schema JSONs continue accumulating in the
// repo-tracked shared/core/database/schemas directory (identity of the
// JellyPlayDatabase schema history is what MigrationTest verifies against;
// moved from the deleted core/database shim in wave 8A).
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

ksp {
    arg("room.schemaLocation", "$rootDir/shared/core/database/schemas")
}
