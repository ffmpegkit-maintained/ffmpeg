# Patch notes

Changes in this fork relative to upstream [arthenica/ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit), release by release. For native toolchain/build instructions see [BUILD.md](BUILD.md); for moving from the old Maven Central artifacts see [MIGRATION.md](MIGRATION.md).

## Unreleased (source only — no tag/release yet)

Closes the two "known gaps" from `v6.0.0-lts-android` below. Not yet shipped as a downloadable `.aar` — push a new `v*` tag to trigger the CI build and cut a release once these are ready to ship.

**Added**

- Real CI build: `.github/workflows/build.yml` now actually runs `./android.sh --full ...` on `ubuntu-24.04` (NDK r26c, JDK 17, arm64-v8a only) instead of the previous placeholder, triggered on `v*` tags. Produces a downloadable `ffmpeg-kit-full-arm64-v8a` artifact.
- CI step that enforces 16 KB ELF segment alignment on every built `.so` and fails the build if any library isn't aligned.
- `-Wl,-z,max-page-size=16384` / `-Wl,-z,common-page-size=16384` added to the native linker flags (`scripts/function-android.sh` `get_ldflags()` for the FFmpeg libraries, and a new `android/jni/Application.mk` for `libffmpegkit.so`).

**Changed**

- `compileSdk`/`targetSdk` bumped from 33 to **35** (Android 15) in `android/ffmpeg-kit-android-lib/build.gradle`.
- Android Gradle Plugin bumped 8.1.0 → **8.6.0**, Gradle wrapper 8.2.1 → **8.7** (required for clean compileSdk 35 support; verified locally with `./gradlew :ffmpeg-kit-android-lib:assembleRelease`).
- `ndkVersion` field in the Gradle module corrected to `26.2.11394342` (r26c) to match what's actually used; this field doesn't drive the native build itself (that's `android.sh`/`ndk-build`), it was just stale.

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
