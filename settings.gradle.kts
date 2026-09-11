pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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
