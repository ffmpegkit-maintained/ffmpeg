# FFmpegKit (Maintained Fork)

> Community-maintained continuation of [FFmpegKit](https://github.com/arthenica/ffmpeg-kit), archived by its original author in April 2025.

[![License: LGPL-3.0](https://img.shields.io/badge/license-LGPL--3.0-blue.svg)](LICENSE)
[![Build](https://github.com/ffmpegkit-maintained/ffmpeg-kit/actions/workflows/build.yml/badge.svg)](https://github.com/ffmpegkit-maintained/ffmpeg-kit/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/ffmpegkit-maintained/ffmpeg-kit?label=release)](https://github.com/ffmpegkit-maintained/ffmpeg-kit/releases)
[![NDK](https://img.shields.io/badge/NDK-r26c-success.svg)](docs/BUILD.md)
[![minSdk](https://img.shields.io/badge/minSdk-24-success.svg)](#compatibility)
[![Maven Central (6.0)](https://img.shields.io/maven-central/v/dev.ffmpegkit-maintained/ffmpeg-kit-free?label=maven-central%206.0)](https://central.sonatype.com/artifact/dev.ffmpegkit-maintained/ffmpeg-kit-free)
[![Maven Central (7.1)](https://img.shields.io/maven-central/v/dev.ffmpegkit-maintained/ffmpeg-kit-free-71?label=maven-central%207.1)](https://central.sonatype.com/artifact/dev.ffmpegkit-maintained/ffmpeg-kit-free-71)

## Why this fork exists

[FFmpegKit](https://github.com/arthenica/ffmpeg-kit) was the de-facto standard for running FFmpeg on Android, iOS, macOS and tvOS. In April 2025, its original author archived the repository and stopped maintaining it, leaving thousands of apps depending on a library that could no longer:

- Target **Android SDK 35** (Android 15) without manifest/build warnings
- Support **16 KB memory page sizes**, mandatory for new apps and updates on Google Play starting November 2025
- Receive security and FFmpeg/codec updates

This fork exists to keep FFmpegKit alive for the Android ecosystem: same API surface, same package names, but with the build system, native libraries and tooling updated to keep working on current and future Android versions.

We are not affiliated with the original author. All credit for the original design and years of work goes to the upstream project; this fork simply continues maintenance under the same license.

## Scope

This fork is **Android-only, intentionally**. Maintaining a single platform well is more valuable to this project's users than spreading effort thin across all the platforms upstream FFmpegKit supported. Focus areas are:

- **Android SDK 35** (Android 15) compatibility, kept current as new SDK levels ship.
- **16 KB memory page size** support, required by Google Play for new and updated apps.
- **Long-term maintenance**: security patches, NDK/toolchain bumps, and FFmpeg/codec updates on an LTS-style cadence rather than chasing every upstream FFmpeg release.

**Out of scope, intentionally:** iOS, macOS, tvOS, Linux, Flutter and React Native bindings. Upstream FFmpegKit's source for these platforms (`apple.sh`, `ios.sh`, `macos.sh`, `tvos.sh`, `linux.sh` and their respective directories) is not imported into this fork. If you need FFmpeg on those platforms, look for other actively maintained forks or projects targeting them specifically — this project won't take on that maintenance burden.

## Quick start

### Two LTS lines

Two build trees are published and maintained in parallel — pick the FFmpeg version that suits your project:

| Line | FFmpeg | Free tier (Maven Central) | Paid tiers (Gumroad) |
|---|---|---|---|
| **6.0 LTS** | n6.0 (stable, long track record) | `dev.ffmpegkit-maintained:ffmpeg-kit-free:6.0.1` | Current file on [Basic](https://ffmpegkit.gumroad.com/l/iqppf) / [Full](https://ffmpegkit.gumroad.com/l/ffmpegkit-lts-android) / [Full GPL](https://ffmpegkit.gumroad.com/l/bctphn) |
| **7.1 LTS** | n7.1.5 (newer codecs, same API) | `dev.ffmpegkit-maintained:ffmpeg-kit-free-71:7.1.5` | Current file on [Basic](https://ffmpegkit.gumroad.com/l/iqppf) / [Full](https://ffmpegkit.gumroad.com/l/ffmpegkit-lts-android) / [Full GPL](https://ffmpegkit.gumroad.com/l/bctphn) |

Both lines use the same four tiers, the same API surface, and the same NDK r26c / compileSdk 35 / 16 KB page alignment. The Gumroad products always contain the latest build for each tier — if you're unsure which line is in the current download, check the release notes on the product page.

### Add the Free tier

**Via Gradle (Maven Central — recommended):**

```gradle
// 6.0 LTS
implementation 'dev.ffmpegkit-maintained:ffmpeg-kit-free:6.0.1'

// 7.1 LTS
implementation 'dev.ffmpegkit-maintained:ffmpeg-kit-free-71:7.1.5'
```

**Direct download:** the prebuilt `.aar` is also attached to each [GitHub release](https://github.com/ffmpegkit-maintained/ffmpeg-kit/releases) for build systems that don't use Maven Central.

For the paid tiers, download the `.aar` from [Gumroad](https://ffmpegkit.gumroad.com) and drop it in `app/libs/`, then:

```gradle
// app/build.gradle
repositories { flatDir { dirs("libs") } }

dependencies {
    implementation(name: "ffmpeg-kit-release", ext: "aar")
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("com.arthenica:smart-exception-java:0.2.1")
}
```

### Call site (unchanged from upstream)

```java
FFmpegKit.executeAsync("-i input.mp4 -c:v mpeg4 output.mp4", session -> {
    if (ReturnCode.isSuccess(session.getReturnCode())) {
        // SUCCESS
    }
});
```

Migrating from `com.arthenica:ffmpeg-kit-*`? See [docs/MIGRATION.md](docs/MIGRATION.md).

## Available tiers

Four separately-built AARs, so you only pay for and ship the codec coverage your app actually needs. All four are `arm64-v8a` only; see [README § Compatibility](#compatibility) for NDK/SDK details that apply to all of them. Each tier is built for both the **6.0 LTS** and **7.1 LTS** lines (see [Quick start](#quick-start)).

| | **Free** | **Basic** | **Full** | **Full GPL** |
|---|---|---|---|---|
| Distribution | Maven Central, free | Gumroad, $19 | Gumroad, $29 | Gumroad, $39 |
| License | LGPL-3.0 | LGPL-3.0 | LGPL-3.0 | **GPL-3.0** ⚠️ |
| Build workflows (6.0 / 7.1) | `build-free.yml` / `build-71-free.yml` | `build-basic.yml` / `build-71-basic.yml` | `build.yml` / `build-71-full.yml` | `build-gpl.yml` / `build-71-gpl.yml` |
| Maven coordinates (6.0) | `dev.ffmpegkit-maintained:ffmpeg-kit-free:6.0.1` | — | — | — |
| Maven coordinates (7.1) | `dev.ffmpegkit-maintained:ffmpeg-kit-free-71:7.1.5` | — | — | — |
| Android `MediaCodec` (hardware accel) | ❌ | ✅ | ✅ | ✅ |
| H.264 **decode** | ✅ (native FFmpeg) | ✅ (native FFmpeg) | ✅ (native FFmpeg) | ✅ (native FFmpeg) |
| H.264 **encode** | ❌ | ✅ via `openh264` | ✅ via `openh264` | ✅ via `x264` |
| H.265/HEVC **decode** | ✅ (native FFmpeg) | ✅ (native FFmpeg) | ✅ (native FFmpeg) | ✅ (native FFmpeg) |
| H.265/HEVC **encode** | ❌ | ❌ | ✅ via `kvazaar` | ✅ via `x265` |
| AV1 | ✅ `libaom`, `dav1d` | ✅ `libaom`, `dav1d` | ✅ `libaom`, `dav1d` | ✅ `libaom`, `dav1d` |
| VP8/VP9 | ✅ `libvpx` | ✅ `libvpx` | ✅ `libvpx` | ✅ `libvpx` |
| Theora | ❌ | ✅ `libtheora` | ✅ `libtheora` | ✅ `libtheora` |
| Audio codecs (Opus, Speex, Vorbis) | ✅ | ✅ | ✅ | ✅ |
| Audio codecs (MP3, AMR, MP2) | ❌ | ✅ | ✅ | ✅ |
| Images (WebP, GIF, JPEG, PNG, TIFF) | ❌ | ✅ | ✅ | ✅ |
| Subtitle/text rendering (`libass`, `harfbuzz`, `freetype`, `fontconfig`, `fribidi`) — also covers FFmpeg's `drawtext` filter | ❌ | ❌ | ✅ | ✅ |
| OCR (`tesseract`, `leptonica`) | ❌ | ❌ | ✅ | ✅ |
| SRT (secure streaming) | ❌ | ❌ | ✅ | ✅ |
| Audio fingerprinting (`chromaprint`) | ❌ | ❌ | ✅ | ✅ |
| TLS | ❌ | ✅ `openssl` | ✅ `openssl` | ✅ `openssl` |
| `xvidcore`, `libvidstab`, `rubberband` | ❌ | ❌ | ❌ | ✅ (GPL-licensed) |
| `zimg`, `snappy`, `soxr`, `libxml2`, Android `zlib` | ❌ | ✅ | ✅ | ✅ |

**H.264/H.265 note:** every tier can *play back* H.264/H.265 content — decoding is built into FFmpeg itself, not tied to any of `openh264`/`kvazaar`/`x264`/`x265`. What differs between tiers is whether you can *encode/produce* H.264 or H.265 output, and with which encoder.

**Free** is intentionally software-only (no `MediaCodec`) for consistent behavior across devices regardless of manufacturer hardware codec quirks, while still giving real, modern video encoding (VP9/AV1 via `libvpx`/`libaom`, not just decode) for free via Maven Central. Published at `dev.ffmpegkit-maintained:ffmpeg-kit-free` (6.0 line) and `dev.ffmpegkit-maintained:ffmpeg-kit-free-71` (7.1 line); tag-triggered builds handle publishing automatically.

**GnuTLS is never included** in any tier, on purpose: it conflicts with OpenSSL in FFmpeg's own `configure` (both provide TLS, only one can be enabled at a time) — see [docs/PATCH-NOTES.md](docs/PATCH-NOTES.md).

**⚠️ Full GPL tier license note:** `--enable-gpl` adds `x264`, `x265`, `xvidcore`, `libvidstab` and `rubberband`, which makes the resulting AAR **GPL-3.0 instead of LGPL-3.0**. Copyleft applies to your own app if you statically or dynamically link it — review what that means for your project's licensing before choosing this tier over Full. Kept as a fully separate artifact/workflow/cache so it never mixes with the LGPL builds.

## Compatibility

State of the `main` branch source (and of any `.aar` produced by the CI build going forward):

| | Current |
|---|---|
| **NDK** | r26c (`26.2.11394342`) |
| **minSdk** | 24 (Android 7.0) |
| **compileSdk / targetSdk** | 35 (Android 15) |
| **ABI** | `arm64-v8a` only — CI builds and publishes this ABI exclusively; other ABIs are buildable from source via `android.sh` but not published |
| **16 KB page size alignment** | Enforced — native libraries are linked with `-Wl,-z,max-page-size=16384`, and the CI build fails if any `.so` isn't 16 KB-aligned (see the "Verify 16 KB page size alignment" step in [.github/workflows/build.yml](.github/workflows/build.yml)) |

> Current releases ([v6.0.1-lts-android](https://github.com/ffmpegkit-maintained/ffmpeg-kit/releases/tag/v6.0.1-lts-android) and [v7.1.5-lts-android](https://github.com/ffmpegkit-maintained/ffmpeg-kit/releases/tag/v7.1.5-lts-android)) are fully up to date with this table — compileSdk 35, NDK r26c, and 16 KB alignment enforced.

`android.sh` has no `audio`/`video`/`https` build presets (those were upstream's historical Maven Central artifact names, not flags this script understands) — this fork's tiers (Free/Basic/Full/Full GPL, see [README § Available tiers](#available-tiers)) are defined by which `--disable-lib-*`/`--enable-gpl` flags each workflow passes, not by upstream's old variant names.

See [docs/PATCH-NOTES.md](docs/PATCH-NOTES.md) and the [GitHub wiki](https://github.com/ffmpegkit-maintained/ffmpeg-kit/wiki) for history.

## Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md) — how to contribute
- [docs/BUILD.md](docs/BUILD.md) — building the native libraries and AAR from source
- [docs/MIGRATION.md](docs/MIGRATION.md) — moving from upstream `com.arthenica:ffmpeg-kit-*` (Maven Central) to this fork
- [docs/PATCH-NOTES.md](docs/PATCH-NOTES.md) — what changed in this fork vs. upstream, release by release
- [GitHub wiki](https://github.com/ffmpegkit-maintained/ffmpeg-kit/wiki) — FAQ, troubleshooting, and deeper compatibility notes

## License

This project is distributed under the **GNU Lesser General Public License v3.0**. See [LICENSE](LICENSE) for the full text.
