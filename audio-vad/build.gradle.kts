plugins {
    alias(libs.plugins.skainet.multiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

// Targets: gradle.properties (skainet.targets). kotlin-test in commonTest is automatic.
skainet {
    namespace = "sk.ainet.audio.vad"
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
