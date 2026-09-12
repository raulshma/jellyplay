import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
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

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // the wasmJs target — Room 3 on the web. room3-runtime ships wasmJs
    // klibs and sqlite-web 2.7.1 provides WebWorkerSQLiteDriver (a Web
    // Worker + SQLite WASM + OPFS driver), so the browser build gets the
    // same Room database the android/desktop builds have. Browser-only on
    // purpose: OPFS exists only in secure browsing contexts, so a nodejs()
    // test lane could not exercise the real driver path — the wasmJsTest
    // suite below runs on wasmJsBrowserTest (ChromeHeadless; the same lane
    // apps/web's wasmJsTest already uses — Chrome is present on the dev
    // machines; the kmp-build.yml lane compile-gates it without running it,
    // per that workflow's headless-Chrome stance).
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
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
            // ( Koin construction owner).
            api(libs.koin.core)
        }

        getByName("commonMain").dependencies {
            api(project(":shared:core:model"))
            api(libs.room3.runtime)
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("jvmMain").dependencies {
            implementation(libs.okio)
            // BundledSQLiteDriver for the desktop Room builder
            // (DesktopDatabaseModule).
            implementation(libs.androidx.sqlite.bundled)
        }
        getByName("wasmJsMain").dependencies {
            // WebWorkerSQLiteDriver for the web Room builder
            // (WebDatabaseModule) — the driver only, the worker script that
            // implements its protocol is the local npm package below.
            implementation(libs.androidx.sqlite.web)
            // Koin construction owner (webDatabaseModule + qualifiers),
            // mirroring the jvmShared api() role for the platform modules.
            api(libs.koin.core)
            // org.w3c.dom.Worker for createWebDatabaseWorker()'s js() interop
            // (same pin core:network's wasmJsMain uses).
            implementation(libs.kotlinx.browser)
            // The vendored Web Worker (webworker/worker.js) implementing the
            // WebWorkerSQLiteDriver protocol over @sqlite.org/sqlite-wasm
            // (OPFS). Local-directory npm dep — Kotlin's yarn store links it
            // and webpack 5 assembles the worker + the sqlite3.wasm binary +
            // the OPFS async-proxy worker into sibling assets of whatever
            // browser binary pulls this module (apps/web's webapp bundle and
            // this module's own karma bundle). Pattern proven upstream by the
            // room-web-demo under the same Kotlin 2.3.21 toolchain.
            implementation(
                npm("jellyplay-sqlite-wasm-worker", layout.projectDirectory.dir("webworker").asFile)
            )
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            // BundledSQLiteDriver for in-memory DAO tests and the JVM-driver
            // migration chain verification.
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.koin.test)
            // room3-testing MigrationTestHelper (spike criterion c).
            implementation(libs.room3.testing)
        }
        getByName("jvmTest").resources.srcDir("$projectDir/schemas")

        // proof lane: one real DAO roundtrip through the actual
        // WebWorkerSQLiteDriver (OPFS) under wasmJsBrowserTest. kotlin("test")
        // only — the module under test is the production commonMain code plus
        // the wasmJsMain DI wiring; coroutines-test for the runTest wrapper
        // (kotlin.test rejects `suspend @Test` on wasmJs — apps/web's
        // wasmJsTest note).
        getByName("wasmJsTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }

    }
}

// Room 3 KSP runs per target; schema JSONs continue accumulating in the
// repo-tracked shared/core/database/schemas directory (identity of the
// JellyPlayDatabase schema history is what MigrationTest verifies against).
// room3 kept the room.schemaLocation KSP arg name (verified against
// Context$ProcessorOptions in room3-compiler 3.0.3); the androidx.room3
// Gradle plugin (marker exists at 3.0.3) is deliberately not applied —
// the KSP-arg path is the minimal-diff route under this AGP-9 KMP setup.
// kspWasmJs joins android/jvm: the wasm Room implementation is generated by
// the same compiler (DAOs are suspend/Flow — no commonMain source changes
// were needed), and the wasm KSP run must emit byte-identical schema JSONs
// (guard: `git status shared/core/database/schemas/` stays clean — if the
// wasm run ever rewrites them, that is a blocker signal, not drift to keep).
dependencies {
    add("kspAndroid", libs.room3.compiler)
    add("kspJvm", libs.room3.compiler)
    add("kspWasmJs", libs.room3.compiler)
}

ksp {
    arg("room.schemaLocation", "$rootDir/shared/core/database/schemas")
}

