plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("kmpLibraryBase") {
            id = "jellyplay.kmp.library.base"
            implementationClass = "jellyplay.buildlogic.KmpLibraryBasePlugin"
        }
        register("kmpLibraryCompose") {
            id = "jellyplay.kmp.library.compose"
            implementationClass = "jellyplay.buildlogic.KmpLibraryComposePlugin"
        }
    }
}

dependencies {
    // The Gradle plugin JARs the convention plugins apply (and compile
    // against). Version-catalog PLUGIN aliases don't convert to dependency
    // notations inside this kotlin-dsl build ("Cannot convert the provided
    // notation to an object of type Dependency"), so the real artifacts are
    // declared directly — with versions still read from the shared
    // libs.versions.toml (see settings.gradle.kts), keeping the pins
    // single-sourced.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.compose:compose-gradle-plugin:${libs.versions.composeMultiplatform.get()}")
}
