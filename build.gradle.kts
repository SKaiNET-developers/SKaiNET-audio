// Root build — plugins declared `apply false` so every subproject's plugin application resolves
// from ONE shared classloader scope (see skainet-cartridge's root build for why).
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
}

// GROUP / VERSION_NAME come from gradle.properties — the same keys the vanniktech Maven publish
// plugin reads for its auto-coordinates, so `project(":audio-x")` dependencies between modules
// resolve to the exact published coordinates in every POM (mirrors SKaiNET's root build).
allprojects {
    group = providers.gradleProperty("GROUP").getOrElse("sk.ainet.audio")
    version = providers.gradleProperty("VERSION_NAME").getOrElse("unspecified")
}
