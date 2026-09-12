plugins {
    alias(libs.plugins.skainet.multiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

// AudioSource abstraction + platform producers: WAV file (jvm), microphone (android).
// Targets: gradle.properties (skainet.targets, no js -- wasmJs only). kotlin-test in commonTest
// is automatic.
skainet {
    namespace = "sk.ainet.audio.source"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines)
            implementation(project(":audio-wav"))
        }
    }
}
