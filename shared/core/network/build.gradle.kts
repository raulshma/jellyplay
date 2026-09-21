plugins {
    id("jellyplay.kmp.library.base")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.network"
    }

    sourceSets {
        commonMain.dependencies {
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
        // kotlin("test") + coroutines-test on commonTest cover both test lanes
        // through the commonTest → jvmTest hierarchy edge.
        commonTest.dependencies {
            implementation(libs.coroutines.test)
        }
        getByName("jvmTest").dependencies {
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
