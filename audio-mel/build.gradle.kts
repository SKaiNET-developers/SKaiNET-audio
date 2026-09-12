plugins {
    alias(libs.plugins.skainet.multiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

// Targets: gradle.properties (skainet.targets). kotlin-test in commonTest is automatic.
skainet {
    namespace = "sk.ainet.audio.mel"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":audio-core"))
        }
    }
}
