// Root build — plugins declared `apply false` so every subproject's plugin application resolves
// from ONE shared classloader scope (see skainet-cartridge's root build for why).
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    // Applied for real (not apply false) -- sk.ainet.npm-pins is root-project-scoped by design
    // (Yarn root extensions assert they belong to rootProject). Every module here builds js
    // and/or wasmJs targets, and sk.ainet.multiplatform refuses to configure a web target
    // unless this is applied at the root -- see SkainetMultiplatformPlugin.requireNpmPins().
    // No pins declared yet (nothing to pin currently); this just puts the mechanism in force
    // so a future CVE-driven npm pin is one skainet { npmPins { pin(...) } } block away instead
    // of a new plugin to adopt under time pressure.
    alias(libs.plugins.skainet.npmPins)
}

// GROUP / VERSION_NAME come from gradle.properties — the same keys the vanniktech Maven publish
// plugin reads for its auto-coordinates, so `project(":audio-x")` dependencies between modules
// resolve to the exact published coordinates in every POM (mirrors SKaiNET's root build).
allprojects {
    group = providers.gradleProperty("GROUP").getOrElse("sk.ainet.audio")
    version = providers.gradleProperty("VERSION_NAME").getOrElse("unspecified")
}
