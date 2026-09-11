# skainet-audio

Shared audio preprocessing and streaming utilities for the SKaiNET ASR family
([skainet-moonshine](../skainet-moonshine), skainet-parakeet). Pure Kotlin Multiplatform,
no SKaiNET/tensor dependency — an official SKaiNET project, MIT licensed.

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

Origin: generalized from the proven [SKaiNET-whisper-KMP](../SKaiNET-whisper-KMP) audio/stream
modules (mel verified bit-exact against `whisper.log_mel_spectrogram`, cosine ≈ 1.0).

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

1. Bump `VERSION_NAME` in `gradle.properties`.
2. Tag the commit with that exact version (`0.2.0` or `v0.2.0`) and push the tag. The workflow
   refuses tags that disagree with `VERSION_NAME`.
3. The workflow signs every publication and uploads to Maven Central. Required repository
   secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`,
   `SIGNING_PASSWORD` — the same names the SKaiNET repo uses.

Every push/PR to `main`/`develop` runs `verify-poms.yml`, which publishes to Maven local without
signing and checks that no POM carries `unspecified` versions or wrong sibling coordinates.
To test locally:

```
./gradlew publishToMavenLocal -PRELEASE_SIGNING_ENABLED=false -PsignAllPublications=false
./.github/scripts/validate-published-poms.sh
```
