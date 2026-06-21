# Patch notes

Changes in this fork relative to upstream [arthenica/ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit), release by release. For native toolchain/build instructions see [BUILD.md](BUILD.md); for moving from the old Maven Central artifacts see [MIGRATION.md](MIGRATION.md).

## v6.0.0-lts-android — 2026-06-20

First release of the maintained fork.

**Added**

- Prebuilt `ffmpeg-kit-6.0-lts-arm64-v8a.aar`, compiled against **NDK r26c** (`26.2.11394342`), available from [Releases](https://github.com/ffmpegkit-maintained/ffmpeg-kit/releases).
- Android-only native build pipeline (`android.sh` and supporting scripts), imported from upstream and kept buildable independent of the other platform scripts upstream shipped.
- `docs/BUILD.md` — WSL2/Ubuntu build guide for producing the `.aar` from source.
- `CONTRIBUTING.md` and license upgrade to **LGPL-3.0**.

**Changed**

- Project scope narrowed to Android only — see [README § Scope](../README.md#scope) for rationale. iOS/macOS/tvOS/Linux/Flutter/React Native sources from upstream are not carried into this fork.

**Known gaps (tracked, not yet shipped)**

- `compileSdk`/`targetSdk` are still **33** in `android/ffmpeg-kit-android-lib/build.gradle`; bumping to **35** (Android 15) is planned but not done.
- 16 KB memory page size alignment is documented as a manual check ([BUILD.md § 8](BUILD.md#8-verify-16-kb-page-size-alignment)) but not yet verified or enforced in CI.
- Only `arm64-v8a` is published as a prebuilt release; other ABIs must be built from source.
- Maven Central publication is in progress; the local `.aar` is the only distribution method for now.

See [README § Compatibility](../README.md#compatibility) for the current state of these items.
