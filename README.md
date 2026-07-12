# FFmpegKit for Android — Maintained Drop-in Replacement

**The actively maintained continuation of FFmpegKit for Android.** Every artifact from the
original `com.arthenica:ffmpeg-kit-*` line — `full`, `https`, `min`, `audio`, `video` and their
GPL variants — kept alive, security-tracked, and updated for Android SDK 35 and 16 KB memory
pages. **Migration is a single change: the group ID.** Same artifact names, same package, same
API, same call sites.

[![Maven Central](https://img.shields.io/badge/Maven%20Central-dev.ffmpegkit--maintained-blue)](https://central.sonatype.com/search?q=ffmpeg+kit)
[![License](https://img.shields.io/badge/License-LGPL--3.0-green)](LICENSE)
[![Android](https://img.shields.io/badge/Android-SDK%2035%20%C2%B7%2016KB%20pages-brightgreen)]()

---

## Your build just broke? Start here.

If your Android build suddenly fails with one of these:

```
Could not resolve com.arthenica:ffmpeg-kit-full
Could not find com.arthenica:ffmpeg-kit-https:6.0-2
Could not find com.arthenica:ffmpeg-kit-full-gpl:6.0.1.LTS
Could not find com.arthenica:ffmpeg-kit-min:5.1.LTS
Could not find com.arthenica:ffmpeg-kit-audio
```

…nothing is wrong with your project. **FFmpegKit was retired on January 6, 2025, and its
binaries were removed from Maven Central on April 1, 2025.** The dependency no longer exists.

**The fix — change the group ID, keep everything else:**

```gradle
// old — dead
- implementation 'com.arthenica:ffmpeg-kit-full:6.0-2.LTS'

// new — maintained, same artifact name
+ implementation 'dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7'
```

That's the whole migration. The package (`com.arthenica.ffmpegkit`), the classes
(`FFmpegKit`, `FFmpegKitConfig`, `FFprobeKit`) and your call sites are unchanged. No NDK setup,
no `android.sh`, no local build. Full guide: [docs/MIGRATION.md](docs/MIGRATION.md).

---

## Why this fork exists

FFmpegKit was the de-facto standard for FFmpeg on Android. In April 2025 its author archived the
repository and stopped maintenance, leaving thousands of apps on a library that could no longer:

- **Target Android SDK 35 (Android 15)** without build/manifest warnings
- **Support 16 KB memory page sizes** — mandatory on Google Play for new and updated apps since
  November 2025
- **Receive security and FFmpeg/codec updates**

This fork keeps FFmpegKit alive for Android: **same artifact names, same API, same package**,
with the build system, native libraries and tooling kept current — and security tracked branch
by branch.

*Not affiliated with the original author. All credit for the original design goes to the upstream
project; this fork continues maintenance under the same license.*

---

## What makes this fork different

Anyone can recompile FFmpeg into an AAR. These are the things a recompile alone doesn't produce:

- **Security tracked and proven, not assumed.** CVEs are audited branch by branch, verified by
  binary inspection of the shipped `.so` (not just release notes), and fixed — including on
  branches upstream has stopped patching. See [docs/PATCH-NOTES.md](docs/PATCH-NOTES.md) and the
  published security advisories.
- **Three LTS lines maintained in parallel — including the ones others dropped.** Most forks ship
  only the latest FFmpeg. This one backports fixes to **6.0** and **7.1** as well, for apps that
  cannot move to 8.x.
- **The original artifact names, kept.** `ffmpeg-kit-full`, `-https`, `-min`, `-audio`, `-video`
  and the `-gpl` variants all exist here under the same names — so migration is a group-ID swap,
  not a lookup exercise.
- **License clarity, up front.** Every artifact states its license and what it means for *your*
  app: LGPL for closed-source apps, GPL only in the clearly-marked `-gpl` variants. No
  mislabelled artifacts. *(Documentation, not legal advice — confirm your own case.)*
- **On-device speech recognition (WhisperKit).** Transcription, subtitles and translation
  entirely on device — a capability no recompiled FFmpeg has. *(Pro, 8.1 line.)*

---

## Quick start

### Pick your FFmpeg line

Three build trees are maintained in parallel. **The line is the version** — same artifact, pick
the version that matches the line you want. **Always pin the exact version; never use `+` or
`latest`, or you'll jump across lines.**

| Line | FFmpeg | Version to request | NDK |
|------|--------|--------------------|-----|
| **8.1 LTS** | n8.1.2 ("Hoare", latest stable) | `8.1.7` | r27c |
| **7.1 LTS** | n7.1.5 (newer codecs, same API) | `7.1.6` | r26c |
| **6.0 LTS** | n6.1.6 (long track record) | `6.0.3` | r26c |

### Add the dependency

Most apps want **`ffmpeg-kit-full`** (all LGPL codecs). Maven Central:

```gradle
// 8.1 LTS — the full LGPL build
implementation 'dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7'

// or 7.1 LTS         implementation 'dev.ffmpegkit-maintained:ffmpeg-kit-full:7.1.6'
// or 6.0 LTS         implementation 'dev.ffmpegkit-maintained:ffmpeg-kit-full:6.0.3'
```

JitPack: the historical fallback coordinate `com.github.ffmpegkit-maintained:ffmpeg:<version>` remains available for apps already using it. The named artifacts above (`ffmpeg-kit-full`, `-https`, `-min`, `-audio`, `-video` and their `-gpl` variants) are published to Maven Central and as direct downloads, not JitPack.

Direct download: the prebuilt `.aar` is attached to each GitHub release.

### Call site (unchanged from upstream)

```java
FFmpegKit.executeAsync("-i input.mp4 -c:v mpeg4 output.mp4", session -> {
    if (ReturnCode.isSuccess(session.getReturnCode())) {
        // SUCCESS
    }
});
```

---

## The full artifact matrix — free on Maven Central

Every original FFmpegKit package, rebuilt and maintained. Pick the smallest one that covers the
codecs your app needs — a smaller build means a smaller APK. All are `arm64-v8a`, SDK 35, 16 KB
aligned. `MediaCodec` hardware acceleration and `zlib` are included in **every** variant (Android
system libraries).

| Artifact | Adds on top of base FFmpeg | License |
|----------|----------------------------|---------|
| **`ffmpeg-kit-min`** | nothing (bare FFmpeg + system libs) | LGPL-3.0 |
| **`ffmpeg-kit-https`** | `gmp`, `gnutls` (TLS / https URLs) | LGPL-3.0 |
| **`ffmpeg-kit-audio`** | `lame`, `opus`, `libvorbis`, `speex`, `opencore-amr`, `shine`, `soxr`, `twolame`, `vo-amrwbenc`, `libilbc` | LGPL-3.0 |
| **`ffmpeg-kit-video`** | `dav1d`, `libvpx`, `kvazaar`, `libass`, `libtheora`, `libwebp`, `zimg`, `freetype`, `fontconfig`, `fribidi`, `snappy` | LGPL-3.0 |
| **`ffmpeg-kit-full`** | **audio + video + https** (recommended default) | LGPL-3.0 |
| **`ffmpeg-kit-min-gpl`** | min + `x264`, `x265`, `xvid`, `vidstab` | **GPL-3.0** ⚠️ |
| **`ffmpeg-kit-https-gpl`** | https + `x264`, `x265`, `xvid`, `vidstab` | **GPL-3.0** ⚠️ |
| **`ffmpeg-kit-full-gpl`** | full + `x264`, `x265`, `xvid`, `vidstab` | **GPL-3.0** ⚠️ |

> **Decode is always available.** Every variant plays H.264/H.265/AV1/VP8-9 — decoding is built
> into FFmpeg itself. What changes between variants is which **encoders** you get. `full` gives
> LGPL encoders (`kvazaar` for H.265, `openh264` for H.264 where enabled); the `-gpl` variants
> add the higher-quality `x264`/`x265`.

⚠️ **The `-gpl` variants are GPL-3.0.** Enabling `x264`/`x265`/`xvid`/`vidstab` makes the AAR
GPL-3.0, and copyleft then applies to your own app if you link it — your whole app must be
GPL-compatible. **If your app is closed-source, use the non-GPL variant** (`ffmpeg-kit-full`,
not `-full-gpl`). The GPL builds are kept as fully separate artifacts so they never contaminate
the LGPL ones.

---

## Pro — beyond what a recompile can do

The free artifacts above give you the complete FFmpeg toolkit. **Pro** ([jokobee.com/ffmpegkit](https://jokobee.com/ffmpegkit))
adds the layer upstream FFmpegKit never had:

- **WhisperKit** — on-device speech recognition, subtitles and translation *(8.1 line)*
- **OCR** (Tesseract / Leptonica) and **audio fingerprinting** (chromaprint)
- **Priority support** and a security-response window

Pricing and per-line details at [jokobee.com/ffmpegkit](https://jokobee.com/ffmpegkit).

---

## WhisperKit — on-device speech recognition *(Pro, 8.1 line)*

Powered by Whisper.cpp v1.7.5. **Audio never leaves the device** — no server, no internet needed
for transcription.

📱 **Working demo app:** [ffmpegkit-maintained/whisper-demo-android](https://github.com/ffmpegkit-maintained/whisper-demo-android)
— picks any video on your phone, generates real-time subtitles on-device, switches FR/EN/ES live.

```java
// Extract 16 kHz mono PCM with FFmpegKit, then:
try (WhisperKit wk = WhisperKit.createFromFile(modelPath)) {
    String text = wk.transcribe(pcm);        // plain text
    String srt  = wk.transcribeToSrt(pcm);   // SRT subtitles
    String eng  = wk.translate(pcm);          // → English, offline
}
```

Translate to any language via pluggable providers (DeepL and LibreTranslate included), then burn
subtitles back into the video with FFmpegKit. Full API and examples:
[docs/WHISPERKIT.md](docs/WHISPERKIT.md).

---

## Compatibility

| | 6.0 / 7.1 LTS | 8.1 LTS |
|---|---|---|
| NDK | r26c | r27c |
| minSdk | 24 (Android 7.0) | 24 (Android 7.0) |
| compileSdk / targetSdk | 35 (Android 15) | 35 (Android 15) |
| ABI | arm64-v8a only | arm64-v8a only |
| 16 KB page alignment | Enforced | Enforced |

16 KB alignment is enforced with `-Wl,-z,max-page-size=16384`; **CI fails the build if any `.so`
isn't aligned.** Other ABIs are buildable from source via `android.sh` but not published.

---

## Scope — Android only, on purpose

Maintaining one platform well beats spreading thin. **In scope:** Android SDK 35 compatibility,
16 KB pages, LTS-cadence security/codec updates across 6.0/7.1/8.1.

**Out of scope, intentionally:** iOS, macOS, tvOS, Linux, Flutter and React Native bindings. If
you need FFmpeg there, look for a fork targeting those platforms specifically.

---

## FAQ

**Is this affiliated with Arthenica / the original FFmpegKit?**
No. It's an independent, community-maintained continuation under the same license. All credit for
the original work goes upstream.

**How hard is migration?**
Change `com.arthenica` to `dev.ffmpegkit-maintained` and pick a version. The artifact name
(`ffmpeg-kit-full`, etc.), the package and the API are identical — your code doesn't change.

**Which variant should I use?**
`ffmpeg-kit-full` covers almost everyone. Use a smaller variant (`https`, `audio`, `video`,
`min`) to shrink your APK, or a `-gpl` variant if you need `x264`/`x265` **and** your app is
GPL-compatible.

**LGPL or GPL — which do I want?**
LGPL (`ffmpeg-kit-full` and the non-`-gpl` variants) is safe for closed-source apps. GPL (the
`-gpl` variants) requires your app to be GPL-compatible. When in doubt, use the non-`-gpl` build.

**Which FFmpeg line should I pick?**
Start with **8.1** unless you're pinned to older behaviour. 6.0 and 7.1 are maintained and
security-patched for apps that can't move to 8.x — not legacy dumps.

**Why arm64-v8a only?**
Modern apps targeting SDK 35 and 16 KB pages run on 64-bit devices. Other ABIs are buildable from
source but not published, to keep the maintained surface focused.

**Does it work with React Native or Flutter?**
Not directly — this is a native Android AAR. RN/Flutter bindings are out of scope.

---

## From the same maintainer

**[yt-dlp-android](https://github.com/ffmpegkit-maintained/yt-dlp-android)** — a clean Android
wrapper for yt-dlp (1000+ sites) that solves the `no impersonate target available` error other
wrappers hit. The natural companion: download with yt-dlp-android, process and transcribe here.

---

## Documentation

- [docs/MIGRATION.md](docs/MIGRATION.md) — moving from `com.arthenica:ffmpeg-kit-*`
- [docs/BUILD.md](docs/BUILD.md) — building the native libraries and AAR from source
- [docs/PATCH-NOTES.md](docs/PATCH-NOTES.md) — changes vs upstream, release by release, incl. security
- [docs/WHISPERKIT.md](docs/WHISPERKIT.md) — full WhisperKit reference
- [GitHub wiki](https://github.com/ffmpegkit-maintained/ffmpeg/wiki) — FAQ, troubleshooting, compatibility

---

## License

The LGPL variants are distributed under the **GNU Lesser General Public License v3.0**; the
`-gpl` variants under the **GNU General Public License v3.0**. See [LICENSE](LICENSE). Third-party
components retain their original licenses — see [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt),
bundled in the AAR and readable at runtime via
`context.assets.open("THIRD-PARTY-NOTICES.txt")`.

---

*Maintained by [Jokobee](https://jokobee.com) · contact@jokobee.com · Keeping FFmpegKit alive for
Android.*
