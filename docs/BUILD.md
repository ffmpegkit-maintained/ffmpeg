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
  meson ninja-build
```

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
sdkmanager "platform-tools" "platforms;android-35" "ndk;26.3.11579264"

export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/26.3.11579264"
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

Use the `android.sh` script with the flag matching the variant you need:

```bash
# Full variant (all codecs/libraries)
./android.sh --enable-full

# Audio-only variant
./android.sh --enable-audio

# Video variant
./android.sh --enable-video

# HTTPS-enabled minimal variant
./android.sh --enable-https
```

Useful flags:

- `--lts` — build against the older NDK/API baseline for maximum device compatibility.
- `-d` / `--debug` — keep debug symbols.
- `-s` / `--speed` — optimize the build for speed over size.
- `--arch=<arch>` — restrict the build to a single ABI (`arm-v7a`, `arm64-v8a`, `x86`, `x86-64`) instead of building all of them.

Run `./android.sh --help` for the full list of options.

## 7. Locate build output

Successful builds produce per-ABI shared libraries under `prebuilt/android-<arch>/ffmpeg/lib/` and the Android `.aar` package under `prebuilt/bundle-android-aar/`.

## 8. Verify 16 KB page size alignment

After building, confirm native libraries are aligned for 16 KB memory pages:

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
- **Build hangs on a sub-library `configure` step** — usually a missing dependency from step 2; check the relevant log under `<arch>/<library>.log` in the build directory for the actual `configure` error.
- **Out of memory in WSL2** — increase the memory limit for WSL2 in `%UserProfile%\.wslconfig` (Windows side):

  ```ini
  [wsl2]
  memory=8GB
  ```
