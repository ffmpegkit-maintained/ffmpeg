# Building from source (WSL2 / Ubuntu)

FFmpegKit's native build pipeline only runs on Linux or macOS. On Windows, use **WSL2 with Ubuntu** as your build environment. This guide covers setting up WSL2 and building the Android libraries from there.

## 1. Install WSL2 + Ubuntu

From an elevated PowerShell prompt on Windows:

```powershell
wsl --install -d Ubuntu-22.04
```

Reboot if prompted, then open the Ubuntu shell and finish the first-run setup (username/password). Make sure you're on WSL **2**, not WSL 1:

```powershell
wsl -l -v
```

## 2. Install build dependencies

Inside the Ubuntu shell:

```bash
sudo apt update
sudo apt install -y \
  autoconf automake libtool pkg-config curl git \
  build-essential yasm nasm \
  gperf texinfo bison ragel \
  python3 python3-pip \
  unzip zip wget \
  meson ninja-build \
  doxygen cmake autogen autopoint groff gtk-doc-tools libtasn1-bin
```

This is the full list needed for a `--full` build. `groff`, `doxygen`, `autogen`, `autopoint`, `gtk-doc-tools` and `libtasn1-bin` are easy to miss — without them, individual libraries fail deep into their own `configure`/`make` step (e.g. libiconv's man-page target fails with `groff: fatal error: cannot load 'DESC' description file` if `groff` is missing) rather than failing cleanly up front.

## 3. Install Android SDK + NDK

```bash
export ANDROID_SDK_ROOT="$HOME/android-sdk"
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
cd "$ANDROID_SDK_ROOT/cmdline-tools"
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
unzip cmdline-tools.zip && rm cmdline-tools.zip
mv cmdline-tools latest

export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "ndk;26.2.11394342"

export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/26.2.11394342"
```

Add these `export` lines to your `~/.bashrc` so they persist across shells.

## 4. Set up Java (JDK 17)

```bash
sudo apt install -y openjdk-17-jdk
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
```

## 5. Clone the repository

```bash
git clone --recurse-submodules https://github.com/ffmpegkit-maintained/ffmpeg-kit.git
cd ffmpeg-kit
```

If you already cloned without `--recurse-submodules`:

```bash
git submodule update --init --recursive
```

## 6. Build a variant

`android.sh` only has one variant preset: `--full` (every supported LGPL-compatible codec/library). There are no `--enable-audio` / `--enable-video` / `--enable-https` presets — those were upstream's historical Maven Central *artifact* names, not flags this script understands. The "Available variants" table in the README describes what upstream once shipped as separate artifacts; this fork currently only builds and publishes `full`.

`--full` enables both `gnutls` and `openssl` for TLS, which FFmpeg's own `configure` refuses outright ("GnuTLS and OpenSSL must not be enabled at the same time."). Drop one with `--disable-lib-<name>` — the examples below keep OpenSSL (Apache-2.0, no GPL-adjacent licensing questions) and drop GnuTLS:

```bash
# Full variant, all ABIs
./android.sh --full --enable-android-media-codec --enable-android-zlib --disable-lib-gnutls

# Full variant, arm64-v8a only (matches what CI builds and what's published)
./android.sh --full --enable-android-media-codec --enable-android-zlib --disable-lib-gnutls \
  --disable-arm-v7a --disable-arm-v7a-neon --disable-x86 --disable-x86-64
```

Useful flags:

- `--lts` (or `-l`) — build against the older NDK/API baseline (API 16) for maximum device compatibility.
- `-d` / `--debug` — keep debug symbols.
- `-s` / `--speed` — optimize the build for speed over size.
- `--disable-<arch>` — drop one ABI from the build (`arm-v7a`, `arm-v7a-neon`, `arm64-v8a`, `x86`, `x86-64`). There's no single `--arch=` flag to keep just one — disable everything you don't want, as in the arm64-v8a-only example above. `--disable-arm-v7a` alone does **not** also drop `arm-v7a-neon`; that's a separate flag.
- `--enable-gpl` — enables GPL-licensed codecs (x264/x265) when combined with `--enable-<library>`. Changes the resulting binary's license from LGPL-3.0 to GPL-3.0 — not done by `--full` alone.
- `--api-level=<n>` — override the minimum API level for this build.
- `--disable-lib-<library>` — drop one library from an otherwise-enabled set.

Run `./android.sh --help` for the full list of options.

## 7. Locate build output

Successful builds produce per-ABI shared libraries under `prebuilt/android-<arch>/ffmpeg/lib/` and the Android `.aar` package under `prebuilt/bundle-android-aar/`.

## 8. Verify 16 KB page size alignment

The build already links with `-Wl,-z,max-page-size=16384` by default (`scripts/function-android.sh` and the generated `Application.mk`), so a normal build should come out aligned without any extra steps. This is a verification step, not something you need to enable — CI runs the same check and fails the build if it's ever wrong:

```bash
for so in $(find prebuilt -name "*.so"); do
  echo "$so:"
  objdump -p "$so" | grep -A1 LOAD | grep align
done
```

Segment alignment should report `2**14` (16384) or higher. See the [Android 16 KB page size guide](https://developer.android.com/guide/practices/page-sizes) for background on this requirement.

## Troubleshooting

- **`autoreconf: command not found`** — install `autoconf`/`automake` (step 2).
- **NDK toolchain not found** — double check `ANDROID_NDK_ROOT` points at the exact NDK version directory, not the parent `ndk/` folder.
- **A library fails deep into its own `configure`/`make` step with no obvious cause** — almost always a missing apt dependency from step 2, not a real build bug. Check `build.log` for the actual command that failed; e.g. `groff: fatal error: cannot load 'DESC' description file` means `groff` is missing (libiconv's man-page target needs it even though you'll never read that man page).
- **`CMake Error ... Compatibility with CMake < 3.5 has been removed`** — a pinned library version's `CMakeLists.txt` predates current CMake's policy floor. Already worked around for `cpu_features` (`-DCMAKE_POLICY_VERSION_MINIMUM=3.5` in `android_ndk_cmake()`); if a future library hits the same thing, add the same flag to its build script.
- **`ld.lld: error: ... is incompatible with aarch64linux` during a library's own configure (not the final ffmpeg-kit link)** — a sign the host toolchain's own lib dir leaked into the link search path. Already fixed in `get_common_linked_libraries()`; if you see this again, check you haven't reintroduced `-L.../toolchains/llvm/prebuilt/<host>/lib` (the host-side path, distinct from `-L.../toolchains/llvm/prebuilt/<host>/<target-triple>/lib`, which is correct and needed).
- **`GnuTLS and OpenSSL must not be enabled at the same time.`** — `--full` turns on both; add `--disable-lib-gnutls` (or `--disable-lib-openssl` if you'd rather keep GnuTLS) as shown in step 6.
- **Build hangs on a sub-library `configure` step** — usually a missing dependency from step 2; check the relevant log under `<arch>/<library>.log` in the build directory for the actual `configure` error.
- **Out of memory in WSL2** — increase the memory limit for WSL2 in `%UserProfile%\.wslconfig` (Windows side):

  ```ini
  [wsl2]
  memory=8GB
  ```
