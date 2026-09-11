[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENCE)
[![Maven Central](https://img.shields.io/maven-central/v/sk.ainet.audio/audio-vad-js.svg)](https://central.sonatype.com/artifact/sk.ainet.audio/audio-vad-js)

# SKaiNET-audio

Shared audio preprocessing and streaming utilities for the SKaiNET.

## Modules

| Module | What it is |
|---|---|
| `audio-core` | Neutral value types (`FeatureFrames`, `FeatureExtractor`), linear `Resampler` → 16 kHz mono. |
| `audio-wav` | `Wav` — the single RIFF/WAV codec (16-bit PCM decode any-channels→mono, mono encode), pure Kotlin over `ByteArray`. |
| `audio-mel` | `MelSpectrogram(filterbank, MelConfig)` — parameterized log-mel: `MelConfig.whisper()` (80 mel, verified vs `whisper.log_mel_spectrogram` golden), `MelConfig.parakeet()` (128 mel, natural length). Plus `MelFilterbanks.slaney(...)` generation. |
| `audio-source` | `AudioSource` Flow abstraction + `WavAudioSource` (jvm) + `MicAudioSource` (android, AudioRecord). |
| `audio-stream` | `windows(...)` — chunk-boundary-independent window buffering with terminal-window marking. |
| `audio-vad` | `VoiceActivityDetector` (+ default `EnergyVad`) and `Endpointer` — line-oriented speech events (`LineOpened` / `Pcm` / `LineClosed`) that drive a streaming cartridge's line semantics. |

## Building

```
./gradlew build          # or :audio-mel:jvmTest etc.
```

## Using the published artifacts

All modules publish to Maven Central under the `sk.ainet.audio` group, artifact ID = module name:

```kotlin
dependencies {
    implementation("sk.ainet.audio:audio-mel:0.1.0")   // pulls audio-core transitively
    implementation("sk.ainet.audio:audio-vad:0.1.0")
}
```

## Publishing

Publishing mirrors the [SKaiNET](https://github.com/SKaiNET-developers/skainet) engine repo:
the [vanniktech maven-publish plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)
with POM metadata and Maven Central flags in `gradle.properties`, and a tag-triggered
`release` workflow (`.github/workflows/publish.yml`).
