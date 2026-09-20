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

    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM-semantics code shared verbatim by android + desktop: every
        // OkHttp / Jellyfin-SDK implementation, the failover router, the
        // subtitle providers and the realtime WebSocket plumbing.
        // NOTE: the websocket event currency stays org.json —
        // WebSocketEvent.data is consumed as JSONObject by :shared:core:data
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
            // runCatchingRethrowingCancellation around every suspend fetch —
            // the helper lives below this module on purpose (repositories
            // and workers cross the same seam).
            implementation(project(":shared:core:concurrency"))
            implementation(libs.kotlinx.serialization.json)
            // suspend/Flow surface of the api client interfaces + OkHttpConfig's
            // StateFlow.
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("jvmShared").dependencies {
            // JellyfinApiEngine + the realtime channels inject
            // ServerIdentityStore / PlaybackStore / SubtitleProviderPreferencesStore.
            api(project(":shared:core:datastore"))
            // Koin construction owner (C4): networkJvmModule + qualifiers;
            // api so androidMain/jvmMain/jvmTest see the DSL without re-declaring.
            api(libs.koin.core)
            implementation(libs.jellyfin.core)
            implementation(libs.okhttp)
            implementation(libs.okhttp.logging.interceptor)
            implementation(libs.slf4j.api)
            implementation(libs.kotlinx.serialization.json)
            // Vestigial javax @Inject/@Singleton/@Named decorations remain on
            // the impl classes (inert: no Dagger/KSP processing exists
            // anywhere in the repo — Koin constructs every type). The
            // dependency only keeps those annotations compiling.
            implementation(libs.javax.inject)
            // (The plain dagger artifact that used to sit here
            // existed only to source dagger.Lazy for JellyfinApiEngine's ctor
            // — replaced by the local api/LazyProvider.kt fun interface. No
            // other dagger artifact exists in the repo.)
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
            // Self-signed-trust integration tests mint server certificates
            // with HandshakeCertificates/HeldCertificate (okhttp-tls is not
            // a transitive dep of mockwebserver in the 5.x line).
            implementation(libs.okhttp.tls)
            implementation(libs.mockk)
            // Koin module smoke tests (C4): load networkJvmModule +
            // desktopNetworkModule against the datastore modules.
            implementation(libs.koin.test)
        }
    }
}
