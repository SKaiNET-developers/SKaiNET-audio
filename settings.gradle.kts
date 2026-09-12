pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Resolved once, here, for the whole build -- sk.ainet.multiplatform (applied per-module)
    // and sk.ainet.npm-pins (applied once at root) both come from the same
    // sk.ainet.buildlogic:convention:1.0.0 implementation jar. Declaring an explicit version at
    // each individual application site instead fails with "plugin is already on the classpath
    // with an unknown version" once more than one plugin ID from that jar is resolved in the
    // same build -- Gradle can only pin one version per plugin ID, and only cleanly when it's
    // resolved exactly once, centrally.
    plugins {
        id("sk.ainet.multiplatform") version "1.0.0"
        id("sk.ainet.npm-pins") version "1.0.0"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "skainet-audio"

include("audio-core")
include("audio-wav")
include("audio-mel")
include("audio-source")
include("audio-stream")
include("audio-vad")
