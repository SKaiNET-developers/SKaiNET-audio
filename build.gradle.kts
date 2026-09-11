// Root build — plugins declared `apply false` so every subproject's plugin application resolves
// from ONE shared classloader scope (see skainet-cartridge's root build for why).
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
}

allprojects {
    group = (findProperty("group") as String?) ?: "sk.ainet.audio"
    version = (findProperty("VERSION_NAME") as String?) ?: "0.1.0"
}
