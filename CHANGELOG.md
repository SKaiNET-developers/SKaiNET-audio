# Changelog

All notable changes to SKaiNET-audio are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.2.1] - 2026-09-13

### Changed

- Bumped `sk.ainet.multiplatform`/`sk.ainet.npm-pins` from `1.0.0` to `1.1.0` — picks up the
  new centralized `jvmTarget` default (`JvmTarget.JVM_17` for the plain `jvm()` target),
  previously left unset and implicitly following whatever JDK ran the build. No source changes
  needed; verified via `javap` that compiled class files now carry major version 61 (Java 17)
  regardless of host JDK.

## [0.2.0] - 2026-09-12

### Added

- `linuxX64` target across all six modules (alongside the existing `linuxArm64`).
- `.github/dependabot.yml` — automated `gradle` and `github-actions` dependency-CVE scanning
  (there was none before this release).

### Changed

- All six modules now adopt `sk.ainet.multiplatform`/`sk.ainet.npm-pins` from
  [SKaiNET-build-logic](https://github.com/SKaiNET-developers/SKaiNET-build-logic) instead of
  hand-rolled per-module target lists and `android {}` blocks — no functional change, but the
  `linuxX64` gap above is now structurally impossible to reintroduce (the shared `linux` target
  group always adds `linuxX64` and `linuxArm64` together).

## [0.1.0] - 2026-09-11

First public release. Pure Kotlin Multiplatform audio preprocessing and streaming
utilities for the SKaiNET ASR family, with no dependency on the SKaiNET engine.
Generalized from the audio and stream modules of the SKaiNET Whisper project.

### Added

- **`audio-core`** — neutral value types (`Pcm`, `FeatureFrames`, `FeatureExtractor`) and a
  linear `Resampler` to 16 kHz mono.
- **`audio-wav`** — `Wav`, a single RIFF/WAV codec: 16-bit PCM decode with any channel count
  downmixed to mono, mono encode, pure Kotlin over `ByteArray`.
- **`audio-mel`** — `MelSpectrogram(filterbank, MelConfig)`, a parameterized log-mel front end.
  `MelConfig.whisper()` (80 mel, verified against `whisper.log_mel_spectrogram`, cosine ≈ 1.0)
  and `MelConfig.parakeet()` (128 mel, natural length). `MelFilterbanks.slaney(...)` generates
  Slaney-normalized triangular filterbanks.
- **`audio-source`** — the `AudioSource` Flow abstraction with `WavAudioSource` (JVM) and
  `MicAudioSource` (Android `AudioRecord`).
- **`audio-stream`** — `windows(...)`, chunk-boundary-independent window buffering with
  terminal-window marking.
- **`audio-vad`** — `VoiceActivityDetector` with a default `EnergyVad`, and `Endpointer`,
  which turns per-frame speech decisions into line events (`LineOpened` / `Pcm` / `LineClosed`).
- Targets: `jvm`, `android`, `androidNativeArm32`, `androidNativeArm64`, `iosArm64`,
  `macosArm64`, `linuxArm64`, `js`, `wasmJs` (the `audio-source` and `audio-stream` modules
  omit `js`).
- Maven Central publishing under `sk.ainet.audio:audio-*`, mirroring the SKaiNET engine
  repository's release workflow.

[Unreleased]: https://github.com/SKaiNET-developers/SKaiNET-audio/compare/0.2.1...HEAD
[0.2.1]: https://github.com/SKaiNET-developers/SKaiNET-audio/compare/0.2.0...0.2.1
[0.2.0]: https://github.com/SKaiNET-developers/SKaiNET-audio/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/SKaiNET-developers/SKaiNET-audio/releases/tag/0.1.0
