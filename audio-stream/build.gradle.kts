plugins {
    alias(libs.plugins.skainet.multiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

// Flow windowing / buffering utilities shared by the streaming ASR runtimes.
// Targets: gradle.properties (skainet.targets, no js -- wasmJs only).
skainet {
    namespace = "sk.ainet.audio.stream"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
