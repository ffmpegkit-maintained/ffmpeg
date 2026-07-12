# FFmpegKit (Maintained Fork)

> Community-maintained continuation of [FFmpegKit](https://github.com/arthenica/ffmpeg-kit), archived by its original author in April 2025.

[![Website](https://img.shields.io/badge/website-jokobee.com-blue.svg)](https://www.jokobee.com/ffmpegkit)
[![License: LGPL-3.0](https://img.shields.io/badge/license-LGPL--3.0-blue.svg)](LICENSE)
[![Build](https://github.com/ffmpegkit-maintained/ffmpeg-kit/actions/workflows/build.yml/badge.svg)](https://github.com/ffmpegkit-maintained/ffmpeg-kit/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/ffmpegkit-maintained/ffmpeg-kit?label=release)](https://github.com/ffmpegkit-maintained/ffmpeg/releases)
[![NDK](https://img.shields.io/badge/NDK-r26c-success.svg)](docs/BUILD.md)
[![minSdk](https://img.shields.io/badge/minSdk-24-success.svg)](#compatibility)
[![Maven Central](https://img.shields.io/maven-central/v/dev.ffmpegkit-maintained/ffmpeg?label=maven-central)](https://central.sonatype.com/artifact/dev.ffmpegkit-maintained/ffmpeg)

> ### 📱 See it in action — [**Whisper Demo app**](https://github.com/ffmpegkit-maintained/whisper-demo-android)
>
> A complete, open-source Android app built with this library: pick any video on your phone and get
> **real-time subtitles** generated **100% on-device** by WhisperKit, with **live FR / EN / ES translation**.
> Clone it, drop in your AAR, and run — **[github.com/ffmpegkit-maintained/whisper-demo-android](https://github.com/ffmpegkit-maintained/whisper-demo-android)**
>
> <a href="https://github.com/ffmpegkit-maintained/whisper-demo-android"><img src="https://raw.githubusercontent.com/ffmpegkit-maintained/whisper-demo-android/main/docs/screenshots/02-subtitles-fr.png" width="200" alt="Whisper Demo — on-device subtitles"></a>

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

### Three LTS lines

Three build trees are published and maintained in parallel — pick the FFmpeg version that suits your project:

| Line | FFmpeg | Free tier (Maven Central) | Paid tiers (Jokobee) |
|---|---|---|---|
| **8.1 LTS** | n8.1.2 (latest stable, FFmpeg 8.x "Hoare") — **NDK r27c** | `dev.ffmpegkit-maintained:ffmpeg:8.1.7` | [Pro](https://www.jokobee.com/ffmpegkit) / [Pro GPL](https://www.jokobee.com/ffmpegkit) |
| **7.1 LTS** | n7.1.5 (newer codecs, same API) | `dev.ffmpegkit-maintained:ffmpeg:7.1.6` | [Pro](https://www.jokobee.com/ffmpegkit) / [Pro GPL](https://www.jokobee.com/ffmpegkit) |
| **6.0 LTS** | n6.1.6 (stable, long track record) | `dev.ffmpegkit-maintained:ffmpeg:6.0.3` | [Pro](https://www.jokobee.com/ffmpegkit) / [Pro GPL](https://www.jokobee.com/ffmpegkit) |

All lines use the same API surface, compileSdk 35, and 16 KB page alignment. The 6.0 and 7.1 lines use NDK r26c; the 8.1 line uses NDK r27c. Each LTS line has its own dedicated tiers — browse the full catalogue at **[jokobee.com/ffmpegkit](https://www.jokobee.com/ffmpegkit)**.

### Add the Free tier

**Via Gradle (Maven Central — recommended):**

```gradle
// 8.1 LTS (FFmpeg 8.x "Hoare" — latest stable, NDK r27c)
implementation 'dev.ffmpegkit-maintained:ffmpeg:8.1.7'

// 7.1 LTS
implementation 'dev.ffmpegkit-maintained:ffmpeg:7.1.6'

// 6.0 LTS
implementation 'dev.ffmpegkit-maintained:ffmpeg:6.0.3'
```

**Via JitPack (alternative to Maven Central):**

```gradle
// settings.gradle — add JitPack repository
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

```gradle
// app/build.gradle
// 8.1 LTS (FFmpeg 8.x "Hoare" — latest stable, NDK r27c)
implementation 'com.github.ffmpegkit-maintained:ffmpeg:8.1.7'

// 7.1 LTS
implementation 'com.github.ffmpegkit-maintained:ffmpeg:7.1.6'

// 6.0 LTS
implementation 'com.github.ffmpegkit-maintained:ffmpeg:6.0.3'
```

**Direct download:** the prebuilt `.aar` is also attached to each [GitHub release](https://github.com/ffmpegkit-maintained/ffmpeg/releases) for build systems that don't use Maven Central.

> **Need H.264/H.265 encode, hardware MediaCodec, TLS, subtitles, or OCR?** The [Pro tier](https://www.jokobee.com/ffmpegkit) adds those (see [Available tiers](#available-tiers) for pricing per line). Need **on-device speech recognition or subtitle generation**? The [Pro and Pro GPL](https://www.jokobee.com/ffmpegkit) tiers add WhisperKit — see [docs/WHISPERKIT.md](docs/WHISPERKIT.md).

For the paid tiers, download the `.aar` from [Jokobee](https://www.jokobee.com/ffmpegkit) and drop it in `app/libs/`, then:

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

> **On-device speech recognition** is available in the 8.1 Pro and Pro GPL tiers via [WhisperKit](#whisperkit--on-device-speech-recognition-81-pro--pro-gpl) — transcription, SRT subtitles, and translation to any language without sending audio to a server.

## WhisperKit — on-device speech recognition (8.1 Pro / Pro GPL)

**WhisperKit** is a new Java API exclusive to the **8.1 Pro** and **8.1 Pro GPL** tiers. It brings on-device speech recognition and subtitle generation directly to Android, powered by [Whisper.cpp v1.7.5](https://github.com/ggml-org/whisper.cpp). Audio never leaves the device — no server, no internet connection required for transcription.

> 📱 **Full working demo app:** [**ffmpegkit-maintained/whisper-demo-android**](https://github.com/ffmpegkit-maintained/whisper-demo-android) — an open-source Android app that picks any video on your phone, generates **real-time subtitles on-device**, and switches **FR / EN / ES** live. The easiest way to see WhisperKit in action and a copy-paste starting point for your own app.
>
> | Home | Real-time subtitles (FR) | Live translation (EN) |
> |:---:|:---:|:---:|
> | [![Home](https://raw.githubusercontent.com/ffmpegkit-maintained/whisper-demo-android/main/docs/screenshots/01-home.png)](https://github.com/ffmpegkit-maintained/whisper-demo-android) | [![Subtitles FR](https://raw.githubusercontent.com/ffmpegkit-maintained/whisper-demo-android/main/docs/screenshots/02-subtitles-fr.png)](https://github.com/ffmpegkit-maintained/whisper-demo-android) | [![Translation EN](https://raw.githubusercontent.com/ffmpegkit-maintained/whisper-demo-android/main/docs/screenshots/03-translation-en.png)](https://github.com/ffmpegkit-maintained/whisper-demo-android) |

### What it can do

| | Code |
|---|---|
| Transcribe audio → plain text | `wk.transcribe(pcm)` |
| Transcribe audio → SRT subtitles | `wk.transcribeToSrt(pcm)` |
| Transcribe + translate to **English** (Whisper built-in, offline) | `wk.translate(pcm)` |
| Transcribe + translate to **any language** via external service | `wk.transcribeAndTranslate(pcm, provider, "fr")` |
| Transcribe + translate to any language → SRT | `wk.transcribeToSrtAndTranslate(pcm, provider, "de")` |

### Quick start

```java
// 1. Extract 16 kHz mono PCM from a video using FFmpegKit
String pcmPath = context.getCacheDir() + "/audio.pcm";
FFmpegKit.executeAsync(
    "-i /path/to/video.mp4 -ar 16000 -ac 1 -f f32le " + pcmPath,
    session -> {
        if (!ReturnCode.isSuccess(session.getReturnCode())) return;
        try {
            // 2. Load PCM into float[]
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(pcmPath));
            java.nio.FloatBuffer fb = java.nio.ByteBuffer.wrap(bytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
            float[] pcm = new float[fb.remaining()];
            fb.get(pcm);

            // 3. Transcribe (GGML model downloaded separately — see docs/WHISPERKIT.md)
            try (WhisperKit wk = WhisperKit.createFromFile(
                    context.getFilesDir() + "/ggml-base.bin")) {

                String text = wk.transcribe(pcm);               // plain text
                String srt  = wk.transcribeToSrt(pcm);          // SRT subtitles
                String eng  = wk.translate(pcm);                 // → English (offline)
            }
        } catch (IOException e) { /* handle */ }
    });
```

### Translation to any language

Two ready-to-use `TranslationProvider` implementations are included. Both use Android's built-in `HttpsURLConnection` — no extra dependencies.

**DeepL** (500 000 characters/month free — [get API key](https://www.deepl.com/pro-api)):

```java
TranslationProvider deepl = new DeepLTranslationProvider("YOUR_DEEPL_API_KEY");

try (WhisperKit wk = WhisperKit.createFromFile(modelPath)) {
    // Transcribe audio and translate each subtitle segment to French
    String frenchSrt = wk.transcribeToSrtAndTranslate(pcm, deepl, "FR");
}
```

**LibreTranslate** (open-source, self-hostable — no vendor lock-in):

```java
TranslationProvider lt = new LibreTranslateProvider();  // public instance
// or: new LibreTranslateProvider("https://your-server.example.com", "API_KEY");

try (WhisperKit wk = WhisperKit.createFromFile(modelPath)) {
    String germanSrt = wk.transcribeToSrtAndTranslate(pcm, lt, "de");
}
```

`TranslationProvider` is also a plain functional interface — implement it inline for Google Translate, Azure, or any other service.

### Burn subtitles into the video

Transcription, translation, and subtitle burning in a single Android pipeline — entirely on-device for the transcription step, with an optional translation service for any target language:

```java
// 1. Extract PCM  →  2. WhisperKit → SRT  →  3. FFmpegKit burns subtitles
FFmpegKit.executeAsync("-i " + videoPath + " -ar 16000 -ac 1 -f f32le " + pcmPath, s1 -> {
    // ... load PCM, call wk.translateToSrt(pcm) or transcribeToSrtAndTranslate ...
    // write SRT to srtPath, then:
    FFmpegKit.executeAsync("-i " + videoPath + " -vf subtitles=" + srtPath + " " + outputPath, s2 -> {});
});
```

> **Note:** subtitle burning (`-vf subtitles=`) requires `libass`, which is in the Pro and Pro GPL tiers. See [docs/WHISPERKIT.md](docs/WHISPERKIT.md) for the complete working code.

## Available tiers

Three separately-built AARs, so you only pay for and ship the codec coverage your app actually needs. All three are `arm64-v8a` only; see [README § Compatibility](#compatibility) for NDK/SDK details that apply to all of them. Each tier is built for the **6.0 LTS**, **7.1 LTS**, and **8.1 LTS** lines (see [Quick start](#quick-start)).

> **The Basic tier has been discontinued** (no longer sold or built). Its former content (MediaCodec hardware accel, `openh264` H.264 encode, TLS) is bundled into **Pro** — see [docs/PATCH-NOTES.md](docs/PATCH-NOTES.md). A more complete **Free** tier is planned to absorb some of this capability; until then, Free remains software-decode-only as described below.

| | **Free** | **Pro** | **Pro GPL** |
|---|---|---|---|
| Distribution | Maven Central, free | Jokobee, $29 (6.0/7.1) · $34 (8.1) | Jokobee, $39 (6.0/7.1) · $34 (8.1) |
| License | LGPL-3.0 | LGPL-3.0 | **GPL-3.0** ⚠️ |
| Build workflows (6.0 / 7.1 / 8.1) | `build-free.yml` / `build-71-free.yml` / `build-81-free.yml` | `build.yml` / `build-71-full.yml` / `build-81-full.yml` ¹ | `build-gpl.yml` / `build-71-gpl.yml` / `build-81-gpl.yml` ¹ |
| Maven coordinates (8.1) | `dev.ffmpegkit-maintained:ffmpeg:8.1.7` | — | — |
| Maven coordinates (7.1) | `dev.ffmpegkit-maintained:ffmpeg:7.1.6` | — | — |
| Maven coordinates (6.0) | `dev.ffmpegkit-maintained:ffmpeg:6.0.3` | — | — |
| Android `MediaCodec` (hardware accel) | ❌ | ✅ | ✅ |
| H.264 **decode** | ✅ (native FFmpeg) | ✅ (native FFmpeg) | ✅ (native FFmpeg) |
| H.264 **encode** | ❌ | ✅ via `openh264` | ✅ via `x264` |
| H.265/HEVC **decode** | ✅ (native FFmpeg) | ✅ (native FFmpeg) | ✅ (native FFmpeg) |
| H.265/HEVC **encode** | ❌ | ✅ via `kvazaar` | ✅ via `x265` |
| AV1 | ✅ `libaom`, `dav1d` | ✅ `libaom`, `dav1d` | ✅ `libaom`, `dav1d` |
| VP8/VP9 | ✅ `libvpx` | ✅ `libvpx` | ✅ `libvpx` |
| Theora | ❌ | ✅ `libtheora` | ✅ `libtheora` |
| Audio codecs (Opus, Speex, Vorbis) | ✅ | ✅ | ✅ |
| Audio codecs (MP3, AMR, MP2) | ❌ | ✅ | ✅ |
| Images (WebP, GIF, JPEG, PNG, TIFF) | ❌ | ✅ | ✅ |
| Subtitle/text rendering (`libass`, `harfbuzz`, `freetype`, `fontconfig`, `fribidi`) — also covers FFmpeg's `drawtext` filter | ❌ | ✅ | ✅ |
| OCR (`tesseract`, `leptonica`) | ❌ | ✅ | ✅ |
| SRT (secure streaming) | ❌ | ✅ | ✅ |
| Audio fingerprinting (`chromaprint`) | ❌ | ✅ | ✅ |
| TLS | ❌ | ✅ `openssl` | ✅ `openssl` |
| `xvidcore`, `libvidstab`, `rubberband` | ❌ | ❌ | ✅ (GPL-licensed) |
| `zimg`, `snappy`, `soxr`, `libxml2`, Android `zlib` | ❌ | ✅ | ✅ |
| **WhisperKit** — on-device speech recognition (Whisper.cpp v1.7.5) | ❌ | ✅ 8.1 only | ✅ 8.1 only |
| **TranslationProvider** — `DeepLTranslationProvider`, `LibreTranslateProvider` | ❌ | ✅ 8.1 only | ✅ 8.1 only |

**H.264/H.265 note:** every tier can *play back* H.264/H.265 content — decoding is built into FFmpeg itself, not tied to any of `openh264`/`kvazaar`/`x264`/`x265`. What differs between tiers is whether you can *encode/produce* H.264 or H.265 output, and with which encoder.

**Free** is intentionally software-only (no `MediaCodec`) for consistent behavior across devices regardless of manufacturer hardware codec quirks, while still giving real, modern video encoding (VP9/AV1 via `libvpx`/`libaom`, not just decode) for free via Maven Central. Published at `dev.ffmpegkit-maintained:ffmpeg:8.1.7` (8.1 line, NDK r27c), `dev.ffmpegkit-maintained:ffmpeg:7.1.6` (7.1 line, NDK r26c), and `dev.ffmpegkit-maintained:ffmpeg:6.0.3` (6.0 line, NDK r26c).

¹ **8.1 Pro and Pro GPL** include WhisperKit (on-device Whisper.cpp speech recognition) — see [docs/WHISPERKIT.md](docs/WHISPERKIT.md). The 6.0 and 7.1 Pro/Pro GPL tiers do not include WhisperKit (Android 8.x feature only).

**GnuTLS is never included** in any tier, on purpose: it conflicts with OpenSSL in FFmpeg's own `configure` (both provide TLS, only one can be enabled at a time) — see [docs/PATCH-NOTES.md](docs/PATCH-NOTES.md).

**⚠️ Pro GPL tier license note:** `--enable-gpl` adds `x264`, `x265`, `xvidcore`, `libvidstab` and `rubberband`, which makes the resulting AAR **GPL-3.0 instead of LGPL-3.0**. Copyleft applies to your own app if you statically or dynamically link it — review what that means for your project's licensing before choosing this tier over Pro. Kept as a fully separate artifact/workflow/cache so it never mixes with the LGPL builds.

## Compatibility

State of the `main` branch source (and of any `.aar` produced by the CI build going forward):

| | 6.0 LTS / 7.1 LTS | 8.1 LTS |
|---|---|---|
| **NDK** | r26c (`26.2.11394342`) | **r27c** (`27.2.12479018`) |
| **minSdk** | 24 (Android 7.0) | 24 (Android 7.0) |
| **compileSdk / targetSdk** | 35 (Android 15) | 35 (Android 15) |
| **ABI** | `arm64-v8a` only | `arm64-v8a` only |
| **16 KB page size alignment** | Enforced | Enforced |

`arm64-v8a` is the only ABI CI builds and publishes; other ABIs are buildable from source via `android.sh` but not published. 16 KB alignment is enforced with `-Wl,-z,max-page-size=16384` — the CI build fails if any `.so` isn't aligned (see the "Verify 16 KB page size alignment" step in the relevant workflow).

> Current releases: [v8.1.7-lts-android](https://github.com/ffmpegkit-maintained/ffmpeg/releases/tag/v8.1.7-lts-android), [v7.1.6-lts-android](https://github.com/ffmpegkit-maintained/ffmpeg/releases/tag/v7.1.6-lts-android), [v6.0.3-lts-android](https://github.com/ffmpegkit-maintained/ffmpeg/releases/tag/v6.0.3-lts-android) — all fully up to date with this table.

`android.sh` has no `audio`/`video`/`https` build presets (those were upstream's historical Maven Central artifact names, not flags this script understands) — this fork's tiers (Free/Pro/Pro GPL, see [README § Available tiers](#available-tiers)) are defined by which `--disable-lib-*`/`--enable-gpl` flags each workflow passes, not by upstream's old variant names.

See [docs/PATCH-NOTES.md](docs/PATCH-NOTES.md) and the [GitHub wiki](https://github.com/ffmpegkit-maintained/ffmpeg/wiki) for history.

## From the same maintainer

> **Need to download the videos before processing them?**
>
> [**yt-dlp-android**](https://github.com/ffmpegkit-maintained/yt-dlp-android) — a clean Android library wrapping [yt-dlp](https://github.com/yt-dlp/yt-dlp), supporting 1000+ sites. Solves the impersonation error (`The extractor is attempting impersonation, but no impersonate target is available`) that all existing Android wrappers hit. Free tier via JitPack; Pro tier with native curl-cffi for full impersonation support.
>
> The natural companion to ffmpeg-kit: download with yt-dlp-android, process and transcribe with this library.

---

## Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md) — how to contribute
- [docs/BUILD.md](docs/BUILD.md) — building the native libraries and AAR from source
- [docs/MIGRATION.md](docs/MIGRATION.md) — moving from upstream `com.arthenica:ffmpeg-kit-*` (Maven Central) to this fork
- [docs/PATCH-NOTES.md](docs/PATCH-NOTES.md) — what changed in this fork vs. upstream, release by release
- [docs/WHISPERKIT.md](docs/WHISPERKIT.md) — WhisperKit full documentation: model setup, API reference, DeepL/LibreTranslate integration, end-to-end examples
- [GitHub wiki](https://github.com/ffmpegkit-maintained/ffmpeg/wiki) — FAQ, troubleshooting, and deeper compatibility notes

## License

This project is distributed under the **GNU Lesser General Public License v3.0**. See [LICENSE](LICENSE) for the full text.

## Third-Party Notices

Third-party components bundled or built into this project retain their original
licenses. See [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt), which is also copied
into the AAR assets at build time (readable at runtime via
`context.assets.open("THIRD-PARTY-NOTICES.txt")`).
