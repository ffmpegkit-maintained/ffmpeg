# Migrating from upstream FFmpegKit

In April 2025, [arthenica/ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit) was archived and its `com.arthenica:ffmpeg-kit-*` artifacts were removed from Maven Central. Any project with a dependency like:

```gradle
implementation("com.arthenica:ffmpeg-kit-full:6.0")
```

now fails to resolve on a clean checkout or CI run, even though nothing in the app's own code changed. This guide covers moving that dependency to this fork.

## Is this a drop-in replacement?

Yes, for Android. Same package (`com.arthenica.ffmpegkit`), same classes (`FFmpegKit`, `FFprobeKit`, `MediaInformation`, session callbacks, etc.), same method signatures, **same artifact names** (`ffmpeg-kit-full`, `-https`, `-min`, `-audio`, `-video`, and their `-gpl` variants). **You do not need to change any call sites, and you don't need to look up which variant replaces which** — only the group ID changes.

If your app also targeted iOS, macOS, tvOS, Linux, Flutter, or React Native via FFmpegKit: this fork does not cover those platforms (see [README § Scope](../README.md#scope)). Look for a platform-specific maintained fork for those targets.

## Migration steps

### 1. Change the group ID

```diff
 dependencies {
-    implementation("com.arthenica:ffmpeg-kit-full:6.0")
+    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")
 }
```

That's it — the artifact name (`ffmpeg-kit-full` here) stays exactly what it was upstream. Whatever variant you used before (`-https`, `-min`, `-audio`, `-video`, or a `-gpl` one), swap the group ID the same way and keep the artifact name unchanged.

### 2. Pick a version

The version you request selects the FFmpeg line — always pin an exact version, never `+`/`latest`:

| Line | Version |
|---|---|
| 8.1 LTS (latest stable) | `8.1.7` |
| 7.1 LTS | `7.1.6` |
| 6.0 LTS | `6.0.3` |

### 3. Sync and rebuild

```bash
./gradlew --refresh-dependencies assembleDebug
```

A clean rebuild matters here: if Gradle's dependency cache still has a resolved copy of the old upstream artifact, you can get duplicate-class errors at merge time. `--refresh-dependencies` avoids that.

No local `.aar`, no `flatDir`, and no separate `smart-exception-java` dependency to add — it's pulled in transitively from Maven Central like any other library.

## What's different from upstream

- **ABI coverage**: only `arm64-v8a` is published. If your app shipped `armeabi-v7a` or `x86_64` builds, those aren't available prebuilt — see [BUILD.md](BUILD.md) to compile them yourself with `android.sh --arch=<abi>`.
- **minSdk**: 24, same floor as upstream's later releases — no change expected for most apps.
- **compileSdk/targetSdk**: 35, with 16 KB memory page alignment enforced — see [README § Compatibility](../README.md#compatibility).
- **License split by artifact, not by tier**: the non-`-gpl` artifacts are LGPL-3.0 (safe for closed-source apps); the `-gpl` ones are GPL-3.0 (copyleft applies to your app). Pick the non-`-gpl` name unless you specifically need `x264`/`x265`.

## Troubleshooting

- **`Duplicate class com.arthenica.ffmpegkit.*`** — an old cached copy of the upstream artifact is still on the classpath. Remove it from `build.gradle` (step 1) and run `./gradlew --refresh-dependencies`.
- **`Could not find com.arthenica:ffmpeg-kit-full`** (or any `com.arthenica:ffmpeg-kit-*`) — you still have the old Maven Central line somewhere (check submodules/flavors too); it will never resolve again upstream. Change the group ID, not the artifact name.
- **`Could not find dev.ffmpegkit-maintained:ffmpeg-kit-<variant>:<version>`** — you're requesting a version that doesn't exist for that line, or mixing versions across lines (e.g. `7.1.7` when only `7.1.6` is published). Pin one of the exact versions in the table above.
- **`UnsatisfiedLinkError` at runtime on a specific ABI** — your device/emulator ABI isn't `arm64-v8a`. Either target arm64-v8a-only devices or build the missing ABI from source ([BUILD.md](BUILD.md)).
