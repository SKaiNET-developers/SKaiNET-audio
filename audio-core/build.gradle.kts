import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

kotlin {
    android {
        namespace = "sk.ainet.audio.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    jvm()
    androidNativeArm32()
    androidNativeArm64()
    iosArm64()
    macosArm64()
    linuxArm64()
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
