plugins {
    alias(libs.plugins.skainet.multiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

// Targets: gradle.properties (skainet.targets) -- must be readable while the plugin applies,
// see SkainetTargets' own doc comment for why this can't live in the skainet { } block below.
// kotlin-test in commonTest is added automatically (SkainetMultiplatformExtension's
// kotlinTestInCommonTest default), so no explicit dependencies block is needed here.
skainet {
    namespace = "sk.ainet.audio.core"
}
