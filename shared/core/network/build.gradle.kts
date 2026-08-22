@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.network"
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

    wasmJs {
        browser()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM-semantics code shared verbatim by android + desktop: every
        // OkHttp / Jellyfin-SDK implementation, the failover router, the
        // subtitle providers and the realtime WebSocket plumbing. Wasm gets a
        // pure-Kotlin HTTP stack when its consumers ship (plan §Phase W).
        // NOTE: the websocket event currency stays org.json —
        // WebSocketEvent.data is consumed as JSONObject by legacy :core:data
        // (RemoteControlReceiver / SyncPlayManager), which must keep compiling
        // unchanged. The android target resolves org.json from android.jar;
        // the jvm target pulls the real org.json artifact (declared on jvmMain
        // so it never enters the Android AAR's consumer metadata).
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            api(project(":shared:core:model"))
            implementation(libs.kotlinx.serialization.json)
            // suspend/Flow surface of the api client interfaces + OkHttpConfig's
            // StateFlow.
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("jvmShared").dependencies {
            // JellyfinApiEngine + the realtime channels inject
            // ServerIdentityStore / PlaybackStore / SubtitleProviderPreferencesStore.
            api(project(":shared:core:datastore"))
            implementation(libs.jellyfin.core)
            implementation(libs.okhttp)
            implementation(libs.okhttp.logging.interceptor)
            implementation(libs.slf4j.api)
            implementation(libs.kotlinx.serialization.json)
            // @Inject/@Singleton/@Named stay on the impl classes; Dagger reads
            // them from binaries at the legacy shim's KSP processing.
            implementation(libs.javax.inject)
            // dagger.Lazy ctor params on JellyfinApiEngine (deferred SDK +
            // OkHttp construction off the synchronous Hilt graph).
            implementation(libs.dagger)
        }
        getByName("jvmMain").dependencies {
            // Real org.json for the desktop target (see jvmShared note above);
            // android resolves the same classes from its own framework jar.
            implementation(libs.org.json)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.okhttp)
            implementation(libs.okhttp.mockwebserver)
            implementation(libs.mockk)
        }
    }
}
