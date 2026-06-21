# Migrating from upstream FFmpegKit

In April 2025, [arthenica/ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit) was archived and its `com.arthenica:ffmpeg-kit-*` artifacts were removed from Maven Central. Any project with a dependency like:

```gradle
implementation("com.arthenica:ffmpeg-kit-full:6.0")
```

now fails to resolve on a clean checkout or CI run, even though nothing in the app's own code changed. This guide covers moving that dependency to this fork.

## Is this a drop-in replacement?

Yes, for Android. Same package (`com.arthenica.ffmpegkit`), same classes (`FFmpegKit`, `FFprobeKit`, `MediaInformation`, session callbacks, etc.), same method signatures. **You do not need to change any call sites** — only the dependency declaration changes.

If your app also targeted iOS, macOS, tvOS, Linux, Flutter, or React Native via FFmpegKit: this fork does not cover those platforms (see [README § Scope](../README.md#scope)). Look for a platform-specific maintained fork for those targets.

## Migration steps

### 1. Remove the old dependency

Delete the now-broken Maven Central reference from your module's `build.gradle`:

```diff
 dependencies {
-    implementation("com.arthenica:ffmpeg-kit-full:6.0")
 }
```

### 2. Download the replacement `.aar`

Grab the variant matching what you had before from [Releases](https://github.com/ffmpegkit-maintained/ffmpeg-kit/releases):

| If you used upstream... | Use this fork's... |
|---|---|
| `ffmpeg-kit-full` | `full` variant |
| `ffmpeg-kit-audio` | `audio` variant |
| `ffmpeg-kit-video` | `video` variant |
| `ffmpeg-kit-https` | `https` variant |
| `ffmpeg-kit-min` | not published — build from source with `android.sh` if you need it |

Place the downloaded `.aar` in `app/libs/`.

### 3. Point Gradle at the local file

```gradle
repositories {
    flatDir {
        dirs("libs")
    }
}

dependencies {
    implementation(name: "ffmpeg-kit-full-release", ext: "aar")

    // Same runtime dependency upstream required — keep it
    implementation("com.arthenica:smart-exception-java:0.2.1")
}
```

### 4. Sync and rebuild

```bash
./gradlew --refresh-dependencies assembleDebug
```

A clean rebuild matters here: if Gradle's dependency cache still has a resolved copy of the old Maven Central artifact from before it was pulled, you can get duplicate-class errors at merge time. `--refresh-dependencies` avoids that.

## What's different from upstream

- **ABI coverage**: only `arm64-v8a` is published as a prebuilt release here. If your app shipped `armeabi-v7a` or `x86_64` builds, those aren't available prebuilt — see [BUILD.md](BUILD.md) to compile them yourself with `android.sh --arch=<abi>`.
- **minSdk**: 24, same floor as upstream's later releases — no change expected for most apps.
- **compileSdk/targetSdk**: 35 in source as of the latest commit. The currently downloadable `v6.0.0-lts-android` release predates that bump and is still compileSdk 33 — see [README § Compatibility](../README.md#compatibility) for which one you're actually getting until a new tagged release is cut.
- **No Maven Central yet**: until publication lands, you maintain the `.aar` file in your repo rather than pulling it as a remote dependency. Track an upgrade path in [PATCH-NOTES.md](PATCH-NOTES.md) once Maven Central support ships.

## Troubleshooting

- **`Duplicate class com.arthenica.ffmpegkit.*`** — an old cached copy of the upstream artifact is still on the classpath. Remove it from `build.gradle` (step 1) and run `./gradlew --refresh-dependencies`.
- **`Could not find com.arthenica:ffmpeg-kit-full`** — you still have the old Maven Central line somewhere (check submodules/flavors too); it will never resolve again upstream.
- **`UnsatisfiedLinkError` at runtime on a specific ABI** — your device/emulator ABI isn't `arm64-v8a`. Either target arm64-v8a-only devices or build the missing ABI from source ([BUILD.md](BUILD.md)).
