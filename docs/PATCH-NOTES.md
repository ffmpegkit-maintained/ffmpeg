# Patch notes

Changes in this fork relative to upstream [arthenica/ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit), release by release. For native toolchain/build instructions see [BUILD.md](BUILD.md); for moving from the old Maven Central artifacts see [MIGRATION.md](MIGRATION.md).

## v8.1.5-lts-android — 2026-06-23

First release with a **working Whisper JNI bridge** for the Full and Full GPL tiers. No code change for the Free or Basic tiers.

- **Fix: Whisper was never actually built** — `main-android.sh` loop capped at index 50; Whisper is at index 92. Extended to `{1..93}`; added `whisper)` case in the dependency switch. Extended `android.sh` flags loop from `{0..61}` to `{0..92}`.
- **New: `libwhisperkit.so`** — JNI shared library built conditionally (only when `libwhisper.a` is present in the prebuilt tree). Added in `android/jni/Android.mk`; links all ggml static archives with `--start-group/--end-group`.
- **New: `whisperkitjni.c`** — C JNI bridge wrapping the Whisper.cpp v1.7.5 C API: `nativeInitContext`, `nativeFreeContext`, `nativeFullTranscribe`, `nativeGetSegmentCount/Text/T0/T1`, `nativeGetSystemInfo`.
- **New: `WhisperKit.java`** — public Java API: `createFromFile()`, `transcribe()`, `transcribeToSrt()`, `translate()`, `translateToSrt()`, low-level segment access. Graceful degradation on Free/Basic tiers (loads the library conditionally; `createFromFile()` throws a descriptive `IOException` when the native library is absent).

## v8.1.4-lts-android — 2026-06-23

Rebuild of all four tiers with **NDK r27c** (`27.2.12479018` — current NDK LTS), the first 8.1 release to use the newer toolchain. Source identical to 8.1.2. Maven artifact for the Free tier: `dev.ffmpegkit-maintained:ffmpeg-kit-free-81:8.1.4`.

- **NDK r26c → r27c** for the entire 8.1 line (all 4 tiers). The 6.0 and 7.1 lines remain on r26c.
- Clang from NDK r27c (`clang-r510928`) replaces the r26c compiler across all 8.1 native builds.

## v8.1.3-lts-android — 2026-06-23

Accidental release: version bump targeting r27c but the CI workflow download URL was not yet updated at tag time, resulting in an r26c build. The Maven Central artifact `ffmpeg-kit-free-81:8.1.3` exists but is functionally identical to 8.1.2 (same NDK r26c). Superseded by 8.1.4. Paid tiers were never built as 8.1.3.

## v8.1.2-lts-android — 2026-06-23

A third build tree, `android-8.1-lts/`, targeting FFmpeg `n8.1.2` (8.1 "Hoare" line, latest patch as of 2026-06-17). Same four-tier structure (Free/Basic/Full/Full GPL), same NDK r26c, same 16 KB page alignment. Free tier Maven artifact: `dev.ffmpegkit-maintained:ffmpeg-kit-free-81:8.1.2`.

**New in this line vs 7.1:**
- FFmpeg `n8.1.2` — latest stable in the 8.x line
- **Whisper.cpp v1.7.5** (ggml-org/whisper.cpp) integrated as library index 92 in the Full and Full GPL tiers — new `scripts/android/whisper.sh` build script, cmake-based, static, with `WHISPER_BUILD_TESTS/EXAMPLES/SERVER=OFF`
- Vulkan video codec acceleration improvements (H.264, AV1, ProRes) from upstream 8.x

**Status:** Free tier published on Maven Central as `dev.ffmpegkit-maintained:ffmpeg-kit-free-81:8.1.2`. Paid tiers (Basic/Full/Full GPL) built and available on [Gumroad](https://ffmpegkit.gumroad.com).

**FFmpeg 8.x breaking API changes found during port** — all documented in `doc/APIchanges` in the FFmpeg source tree. Changes were required in our copies of `fftools_ffmpeg.c` and `fftools_ffprobe.c` (these are frozen snapshots of FFmpeg's internal CLI tool source, carried in the repo since 6.0 and only updated when a new FFmpeg version removes the fields they use):

- **`--disable-postproc` configure flag removed** — `libpostproc` was removed from FFmpeg 8.0 entirely. Dropping the flag from `scripts/android/ffmpeg.sh` (and the unused `linux/` and `apple/` variants for consistency).

- **`AVFrame.key_frame` removed** (deprecated since FFmpeg 6.1) — use `!!(frame->flags & AV_FRAME_FLAG_KEY)`. Affected: `fftools_ffmpeg.c` (4 sites), `fftools_ffprobe.c` (1 site).

- **`AVFrame.interlaced_frame` removed** (deprecated since FFmpeg 6.1) — use `!!(frame->flags & AV_FRAME_FLAG_INTERLACED)`. Affected: `fftools_ffmpeg.c` (1 site), `fftools_ffprobe.c` (1 site).

- **`AVFrame.top_field_first` removed** (deprecated since FFmpeg 6.1) — reading: `!!(frame->flags & AV_FRAME_FLAG_TOP_FIELD_FIRST)`; writing: `frame->flags |= AV_FRAME_FLAG_TOP_FIELD_FIRST` or `&= ~AV_FRAME_FLAG_TOP_FIELD_FIRST`. Affected: `fftools_ffmpeg.c` (3 read sites, 1 write site), `fftools_ffprobe.c` (1 read site).

- **`AVCodecContext.ticks_per_frame` removed** — was used in two duration-calculation expressions. In the general case (non-interlaced), effectively cancelled out (same value in numerator and denominator), replaced with `1`. Affected: `fftools_ffmpeg.c` (4 sites in 2 identical blocks).

- **`AVStream.nb_side_data` / `AVStream.side_data` removed** — stream-level packet side data moved to `AVStream.codecpar->coded_side_data` / `nb_coded_side_data`. **`av_stream_new_side_data()` also removed**, replaced by `av_packet_side_data_new(&codecpar->coded_side_data, &codecpar->nb_coded_side_data, type, size, 0)` (available since FFmpeg 7.0). Affected: `fftools_ffmpeg.c` (3 blocks, 5 sites total), `fftools_ffprobe.c` (1 block), `fftools_ffmpeg_demux.c` (1 site).

- **`av_stream_get_side_data()` removed** — replaced by `av_packet_side_data_get(codecpar->coded_side_data, nb_coded_side_data, type)` which returns a `const AVPacketSideData *` (access `.data` for the payload). Affected: `fftools_ffmpeg_filter.c` (1 site — display matrix auto-rotation).

- **`AV_CODEC_CAP_SUBFRAMES` removed** — this codec capability flag was eliminated in FFmpeg 8.0. The only use was a diagnostic log line in `fftools_opt_common.c`; the entire `if` block was removed. Affected: `fftools_opt_common.c` (1 site).

- **`AVFilter.process_command` member removed from public struct** — this function pointer was part of the internal `AVFilter` struct exposed publicly; it was removed in FFmpeg 8.x as part of filter API cleanup. The only use was a ternary `filter->process_command ? 'C' : '.'` in a diagnostic listing in `fftools_opt_common.c`. Replaced with literal `0` (always outputs `'.'`). Affected: `fftools_opt_common.c` (1 site).

- **`AVFrame.pkt_pos` / `AVFrame.pkt_size` removed** — were used in ffprobe's frame output to print the originating packet's byte position and size. No equivalent exists on `AVFrame` in FFmpeg 8.x (this information is now only available at demux time on `AVPacket`). Replaced with `print_str_opt("pkt_pos", "N/A")` / `print_str_opt("pkt_size", "N/A")` — these fields will show as N/A in ffprobe output for frames decoded with this build. Affected: `fftools_ffprobe.c` (2 sites).

- **`FF_PROFILE_UNKNOWN` renamed to `AV_PROFILE_UNKNOWN`** — part of a broader `FF_PROFILE_*` → `AV_PROFILE_*` rename in FFmpeg 8.0. Affected: `fftools_ffprobe.c` (1 site).

- **`AVHDRVividColorToneMappingParams` three-spline fields restructured** — the individual spline parameters (`three_Spline_TH_mode`, `three_Spline_TH_enable_MB`, `three_Spline_TH_enable`, `three_Spline_TH_Delta1`, `three_Spline_TH_Delta2`, `three_Spline_enable_Strength`) were direct fields on the parent struct in FFmpeg 7.x. In FFmpeg 8.x they are now per-element fields (`th_mode`, `th_enable_mb`, `th_enable`, `th_delta1`, `th_delta2`, `enable_strength`) inside the new `AVHDRVivid3SplineParams` sub-struct, accessible via `tm_params->three_spline[j]` (note also: lowercase `s` in the array field name). Affected: `fftools_ffprobe.c` (1 block).

**Other port fixes (non-API):**

- **`--disable-postproc` configure abort** — this was the first build failure: FFmpeg configure exits immediately with "Unknown option" for any unrecognised flag, so this single stale flag blocked the entire FFmpeg compile step.
- **`src/main/cpp/` directory missing from git** — the JNI source tree (`ffmpegkit.c`, `fftools_*.c/h`, `AndroidManifest.xml`, all Java sources) was not included when the `android-8.1-lts/` directory was initially set up. Copied from `android-7.1-lts/`, which carries the same codebase.
- **All `.sh` scripts missing execute bit** — the entire `android-8.1-lts/scripts/` tree was added to git with mode `100644` instead of `100755`, causing `android.sh` to fail with "Permission denied" (exit 126) before any build work started. Fixed with `git update-index --chmod=+x` on all `.sh` files.
- **`android/gradlew` missing execute bit** — same root cause: `android-8.1-lts/android/gradlew` was tracked at mode `100644` instead of `100755`, causing `./gradlew: Permission denied` at the Gradle AAR assembly step (after `ndk-build` had already succeeded). Fixed with `git update-index --chmod=+x android-8.1-lts/android/gradlew`.

## v7.1.5-lts-android — 2026-06-23

A second build tree, `android-7.1-lts/`, coexists with the original `android-6.0-lts/`. Same four tiers (Free/Basic/Full/Full GPL), same NDK r26c, same 16 KB page size alignment — FFmpeg updated to `n7.1.5`. Free tier published on Maven Central as `dev.ffmpegkit-maintained:ffmpeg-kit-free-71:7.1.5`; paid tiers available on [Gumroad](https://ffmpegkit.gumroad.com).

**Port findings** — all four tiers compiled cleanly against FFmpeg `n7.1.5` with only two generic fixes needed:

- All four tiers (Free/Basic/Full/Full GPL) **compile cleanly** against FFmpeg `n7.1.5`, with only two generic fixes needed, neither library-specific:
  - `scripts/android/ffmpeg.sh`: `emms.h` moved from `libavutil/x86/emms.h` to `libavutil/emms.h` upstream between 6.0 and 7.1 — updated the path our script manually copies it from/to.
  - `ffmpegkit.c` / `ffprobekit.c` (FFmpegKit's own JNI bridge files, not copied from upstream FFmpeg): both call `strlen`/`strcpy` without `#include <string.h>` — a latent bug that happened to compile under 6.0 because some FFmpeg header transitively pulled it in; that path is gone in 7.1, exposing it. Added the missing include directly.
- An initial audit (before any of this was attempted) predicted a large, risky JNI wrapper rewrite would be needed, because upstream's `fftools/ffmpeg.c` shrank from 4196 to 1017 lines between 6.0 and 7.1 with logic moved into new `ffmpeg_dec.c`/`ffmpeg_enc.c`/`ffmpeg_sched.c` files. **That turned out not to apply to us**: those new files are internal to upstream's own standalone `ffmpeg` CLI binary, never compiled as a library — FFmpegKit's wrapper has always had its own independent fork of the transcode logic (`fftools_ffmpeg.c`, unchanged in this fork since 6.0) that only depends on the real public API (`libavcodec`/`libavformat`/`libavutil`), which stayed compatible. The bitfield-truncation warning patch (`-Wno-error=single-bit-bitfield-constant-conversion` in `Android.mk`) is also still needed for the same reason — our copy of `fftools_ffmpeg_mux_init.c` is unchanged, even though upstream's own version fixed it (`int` → `unsigned` bitfields) in the part of the file we don't carry forward.
- **Runtime-verified, not just compile-checked**: a temporary local test app (`test-app/`, gitignored, not part of the repo) confirmed on a real Pixel 7 Pro (arm64-v8a) that the Free tier's ported `.aar` actually works — `FFmpegKitConfig.getFFmpegVersion()` reports `n7.1.5`, `FFmpegKit.execute("-version")` runs and returns success, zero `UnsatisfiedLinkError`.
- An attempt to automate this same check in CI via an instrumented test (`android-7.1-lts/.../src/androidTest/.../SmokeTest.java` + `.github/workflows/port-71-smoketest.yml`) hit a hard platform wall: the modern Android Emulator **categorically refuses** to boot an `arm64-v8a` system image on an `x86_64` host — `FATAL: Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host` — confirmed identically on both a GitHub Actions runner and a local Windows machine. Switching to a genuine ARM64 GitHub-hosted runner (`ubuntu-24.04-arm`, free for public repos, matching host/guest architecture) avoids that specific error but hit a second blocker first: that image doesn't auto-configure `ANDROID_HOME`/`ANDROID_SDK_ROOT` at all (tracked upstream: [actions/runner-images#11460](https://github.com/actions/runner-images/issues/11460)). Not pursued further since the real-device path above already gave a conclusive answer; both diagnostic files are left in the repo in case the CI-emulator path is worth revisiting later.

## Unreleased (source only — no tag/release yet)

Closes the two "known gaps" from `v6.0.0-lts-android` below. Not yet shipped as a downloadable `.aar` — push a new `v*` tag to trigger the CI build and cut a release once these are ready to ship.

**Added**

- Real CI build: `.github/workflows/build.yml` now actually runs `./android.sh --full ...` on `ubuntu-24.04` (NDK r26c, JDK 17, arm64-v8a only) instead of the previous placeholder, triggered on `v*` tags. Produces a downloadable `ffmpeg-kit-full-arm64-v8a` artifact.
- CI step that enforces 16 KB ELF segment alignment on every built `.so` and fails the build if any library isn't aligned.
- `-Wl,-z,max-page-size=16384` / `-Wl,-z,common-page-size=16384` added to the native linker flags (`scripts/function-android.sh` `get_ldflags()` for the FFmpeg libraries, and a new `android/jni/Application.mk` for `libffmpegkit.so`).
- Three more build tiers alongside the original `full` build (renamed **Full**, `build.yml`), each its own workflow/cache/`ci-cache-*` branch so none of them ever share a cache namespace or mix artifacts:
  - **Full GPL** (`build-gpl.yml`, $39 on Gumroad): `--full --enable-gpl`, adds `x264`/`x265`/`xvidcore`/`libvidstab`/`rubberband`. Changes the AAR's license to **GPL-3.0** — confirmed via a dedicated CI step that checks the GPLv3 text both at `android/ffmpeg-kit-android-lib/src/main/res/raw/license.txt` and inside the built `.aar`'s `res/raw/license.txt`.
  - **Basic** (`build-basic.yml`, $19 on Gumroad): `--full` minus `kvazaar` (H.265 encode) and four niche blocks (OCR: `tesseract`+`leptonica`; subtitle/text rendering: `libass`+`harfbuzz`+`freetype`+`fontconfig`+`fribidi`, which also drops FFmpeg's `drawtext` filter; `srt`; `chromaprint`). `openh264` (H.264 encode) is deliberately kept — it's the single most common use case, an earlier draft dropped it too and that made the tier much less useful.
  - **Free** (`build-free.yml`, Maven Central, no charge): software-only (no `--enable-android-media-codec`), explicit `--enable-libaom --enable-dav1d --enable-libvpx --enable-opus --enable-libvorbis --enable-speex` instead of `--full`-minus-something, since it's a much smaller set. No H.264/H.265 encode, no TLS, no images beyond what FFmpeg needs internally, no subtitles/OCR/SRT/fingerprinting.
  - See README § Available tiers for the full per-tier feature table shown to customers.
- Maven Central publishing for the Free tier: `com.vanniktech.maven.publish` (pinned to **0.34.0** — 0.35+ requires AGP 8.13+, all four tiers are pinned to AGP 8.6.0) applied to `ffmpeg-kit-android-lib/build.gradle`, coordinates `dev.ffmpegkit-maintained:ffmpeg-kit-free`, full POM metadata for Central Portal validation. `build-free.yml` gained a `Publish to Maven Central` step gated on `startsWith(github.ref, 'refs/tags/')` (not `workflow_dispatch`) since a Central Portal release can't be un-published. Credentials via `OSSRH_USERNAME`/`OSSRH_PASSWORD` (Sonatype user token) and `GPG_PRIVATE_KEY`/`GPG_PASSPHRASE` (in-memory signing key) repo secrets. Namespace `dev.ffmpegkit-maintained` verified directly on the Central Portal (not the `io.github.<org>` auto-verification path, which only covers personal GitHub usernames, not organizations).

**Changed**

- `compileSdk`/`targetSdk` bumped from 33 to **35** (Android 15) in `android/ffmpeg-kit-android-lib/build.gradle`.
- Android Gradle Plugin bumped 8.1.0 → **8.6.0**, Gradle wrapper 8.2.1 → **8.7** (required for clean compileSdk 35 support; verified locally with `./gradlew :ffmpeg-kit-android-lib:assembleRelease`).
- `ndkVersion` field in the Gradle module corrected to `26.2.11394342` (r26c) to match what's actually used; this field doesn't drive the native build itself (that's `android.sh`/`ndk-build`), it was just stale.
- Removed the manual `android.publishing.singleVariant('release') { withJavadocJar(); withSourcesJar() }` block from `ffmpeg-kit-android-lib/build.gradle`: `com.vanniktech.maven.publish` auto-configures the exact same thing for Android libraries, and having both threw "singleVariant publishing DSL used multiple times ... is not allowed" at configuration time. `./gradlew :ffmpeg-kit-android-lib:assembleRelease` verified to still produce an identical AAR with the plugin handling it alone.
- Groovy gotcha while wiring up the plugin: `publishToMavenCentral(automaticRelease: true)` fails ("Could not find method ... for arguments [{automaticRelease=true}]") because Groovy's named-argument-via-map sugar only applies to methods that accept a `Map`, not a plain `Boolean` parameter — the correct call is the positional `publishToMavenCentral(true)`.

**CI build fixes (getting the from-source build actually green on NDK r26c / Ubuntu 24.04)**

Each of these surfaced one at a time as the CI build progressed further with each fix:

- `yes | sdkmanager --licenses` failed under GitHub Actions' default `bash -e -o pipefail`: `yes` gets a broken-pipe exit once `sdkmanager` stops reading, which `pipefail` turned into a step failure even though licenses were accepted fine. Wrapped with `set +o pipefail` / `set -o pipefail`.
- Missing apt prerequisites (`groff` broke libiconv's man-page build target; restored the rest of upstream's proven list: `doxygen cmake autogen autopoint gtk-doc-tools libtasn1-bin`) and an incomplete arch restriction (`--disable-arm-v7a` alone still built the neon variant; added `--disable-arm-v7a-neon`).
- NDK r24+ clang turned `-Wimplicit-function-declaration` (libaom neon intrinsics) and `-Wincompatible-function-pointer-types` (SDL's GLES2 typedefs) into hard errors by default. Downgraded both to warnings centrally in `get_common_cflags()`.
- `cpu_features` v0.8.0's `CMakeLists.txt` declares an old `cmake_minimum_required` that current CMake refuses outright ("Compatibility with CMake < 3.5 has been removed"). Added `-DCMAKE_POLICY_VERSION_MINIMUM=3.5` to the shared `android_ndk_cmake()` helper and to libaom's separate cmake invocation.
- That same policy fix didn't cover `cpu_features`' own vendored googletest sub-build (a separate nested `cmake` invocation with the identical issue). Disabled it outright with `-DBUILD_TESTING=OFF` instead of chasing the flag through a nested process.
- `get_common_linked_libraries()` included `-L.../toolchains/llvm/prebuilt/linux-x86_64/lib` — the NDK's **host**-side lib dir, not a target-arch one. libvpx's configure does a `-static` link probe as part of its toolchain capability check, and `ld.lld` picked up the host's `libc++abi.a` there and rejected it ("is incompatible with aarch64linux" → "Toolchain is unable to link executables"). Removed that path; the target toolchain lib dir + target sysroot already cover everything needed for aarch64 linking.
- `fftools_ffmpeg_mux_init.c` assigns the literal `1` to several 1-bit signed bitfields (truncates to `-1`, harmless but now clang-flagged) during the **always-run** `ndk-build` step for `libffmpegkit.so` itself — found via an isolated `--enable-libass`-only test build that reaches that same `ndk-build` step in ~10 minutes instead of waiting on the other ~25 libraries `--full` enables. Fixed with `-Wno-error=single-bit-bitfield-constant-conversion` in `MY_CFLAGS` in `android/jni/Android.mk`.
- **The actual cause of a `--full` attempt hanging for ~6h** until GitHub's own hard timeout killed it (conclusion: cancelled, no error — confirmed via direct API check on `run_started_at`/`updated_at`; the bitfield fix above was real but unrelated to this): `--full` enables `gnutls`, `lame`, and `libass`, each independently cascading `set_virtual_library "libiconv" 1`. `--disable-lib-gnutls` then calls `set_library "gnutls" 0`, which cascades `set_virtual_library "libiconv" 0` too — unconditionally wiping what `lame`/`libass` had just enabled. `libiconv`'s "OK" flag then never gets set, and `main-android.sh`'s dependency-resolution loop (which only exits once every enabled library reaches "completed") spins forever re-printing `INFO: Skipping <lib>, dependencies built=...` for every library still waiting on it (`fontconfig`, `lame`, `libass`, `libxml2`) — confirmed via the actual `build.log`: 198,019 such lines (~6,600 repeats of the full pass) by the time it was cancelled. Fixed by making `set_virtual_library()` ignore disable calls entirely (`$2 == 0` returns immediately) — these are shared platform-capability flags multiple unrelated libraries request independently, so one consumer being disabled must never cascade-disable a dependency another still-enabled consumer needs. Verified via the same isolated test build with `--disable-lib-gnutls` added (the exact flag that triggers it) and a 15-minute step timeout, so a still-broken fix would fail fast rather than risk another multi-hour hang.

**CI tooling**

- Added a failure-diagnostics step that dumps the toolchain env vars and the most recently modified `config.log` files under `src/`, since the only log we printed before (`src/ffmpeg/ffbuild/config.log`) doesn't cover failures in the other ~30 libraries the `full` variant builds.
- Cached `~/.ndk` (keyed on the fixed NDK version) and `prebuilt/` + `src/` (keyed on a hash of `android.sh` + `scripts/**`, no `restore-keys` fallback — android.sh's incremental build only checks whether `prebuilt/<arch>/<lib>` exists, not what flags built it, so a stale cache from a different script version must not be silently reused) to avoid recompiling already-succeeded libraries on every retry. Turned out `actions/cache`'s save step is skipped entirely when the job is cancelled or times out — exactly the failure mode above — so it had never actually saved anything across several attempts; added a second, independent checkpoint mechanism in `build.yml` that pushes `prebuilt/` + `src/` to a `ci-cache` branch every 2 minutes in the background, since a `git push` that already succeeded survives a later kill.
- Added `.github/workflows/test-harfbuzz.yml`, a `workflow_dispatch`-only diagnostic that isolates `libass`'s dependency chain (`freetype`, `fribidi`, `fontconfig`, `harfbuzz`) plus the native `ndk-build` step, sharing `build.yml`'s exact cache key so a clean run there also primes the cache for the next full build. Used to find and verify the bitfield fix above in ~10 minutes instead of multi-hour `--full` round trips. Delete once no longer needed for this kind of triage.

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
