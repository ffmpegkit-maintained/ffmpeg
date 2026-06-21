# FFmpegKit (Maintained Fork)

> Community-maintained continuation of [FFmpegKit](https://github.com/arthenica/ffmpeg-kit), archived by its original author in April 2025.

[![License: LGPL-3.0](https://img.shields.io/badge/license-LGPL--3.0-blue.svg)](LICENSE)
[![Build](https://github.com/ffmpegkit-maintained/ffmpeg-kit/actions/workflows/build.yml/badge.svg)](https://github.com/ffmpegkit-maintained/ffmpeg-kit/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/badge/maven--central-coming%20soon-orange.svg)](https://github.com/ffmpegkit-maintained/ffmpeg-kit/releases)

## Why this fork exists

[FFmpegKit](https://github.com/arthenica/ffmpeg-kit) was the de-facto standard for running FFmpeg on Android, iOS, macOS and tvOS. In April 2025, its original author archived the repository and stopped maintaining it, leaving thousands of apps depending on a library that could no longer:

- Target **Android SDK 35** (Android 15) without manifest/build warnings
- Support **16 KB memory page sizes**, mandatory for new apps and updates on Google Play starting November 2025
- Receive security and FFmpeg/codec updates

This fork exists to keep FFmpegKit alive for the Android ecosystem: same API surface, same package names, but with the build system, native libraries and tooling updated to keep working on current and future Android versions.

We are not affiliated with the original author. All credit for the original design and years of work goes to the upstream project; this fork simply continues maintenance under the same license.

## Installation

### Maven Central

Publication to Maven Central is in progress and not yet available. Track progress in [Releases](https://github.com/ffmpegkit-maintained/ffmpeg-kit/releases).

```gradle
// Coming soon
dependencies {
    implementation("io.github.ffmpegkit-maintained:ffmpeg-kit-full:<version>")
}
```

### Local AAR (current method)

Until Maven Central artifacts are published, consume the prebuilt `.aar` directly:

1. Download the `.aar` for the variant you need from [Releases](https://github.com/ffmpegkit-maintained/ffmpeg-kit/releases).
2. Place it in your app module, e.g. `app/libs/`.
3. Reference it from your module's `build.gradle`:

```gradle
repositories {
    flatDir {
        dirs("libs")
    }
}

dependencies {
    implementation(name: "ffmpeg-kit-full-release", ext: "aar")

    // Required runtime dependencies
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("com.arthenica:smart-exception-java:0.2.1")
}
```

## Available variants

FFmpegKit ships several prebuilt variants so you only include the codecs/protocols your app needs:

| Variant | Description | Typical use case |
|---|---|---|
| `full` | All supported external/native libraries and codecs enabled | Apps needing maximum format/codec coverage |
| `audio` | Audio codecs and filters only (no video codecs) | Audio-only processing, transcoding, waveform/visualizers |
| `video` | Video + audio codecs, no extra external libraries | General-purpose video editing/transcoding |
| `https` | Minimal codec set with HTTPS/TLS support for network streams | Streaming/remote-source playback and conversion |

Pick the smallest variant that covers your codec/protocol needs to keep your app size down.

## Compatibility

- **Android SDK 35** (Android 15) as `compileSdk`/`targetSdk` — no deprecated API warnings.
- **16 KB page size** support — native libraries are built/aligned for devices and emulators using 16 KB memory pages, a requirement for new and updated apps on Google Play.
- Minimum supported API level matches upstream FFmpegKit (API 24+), see [docs/BUILD.md](docs/BUILD.md) for details on native toolchain versions.

## Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md) — how to contribute
- [docs/BUILD.md](docs/BUILD.md) — building the native libraries and AAR from source

## License

This project is distributed under the **GNU Lesser General Public License v3.0**. See [LICENSE](LICENSE) for the full text.
