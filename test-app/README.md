# Real-device test harness

Two Android activities that run against a built `.aar` on a real device or emulator.
They exist because the things that shipped broken in this project could not be seen
any other way.

```bash
git config core.hooksPath .githooks        # once per clone, see "The .aar" below

./gradlew :app:assembleDebug -PtestAar=/absolute/path/to/ffmpeg-kit.aar
adb install -r app/build/outputs/apk/debug/app-debug.apk

adb shell am start -S -n dev.ffmpegkit.smoke/.SmokeActivity        # inventory
adb shell am start -S -n dev.ffmpegkit.smoke/.AcceptanceActivity   # acceptance
adb logcat -d -s SMOKE ACCEPT
```

Both write a file to the app's external files dir; pull it and compare two versions.

## `SmokeActivity` — what the build contains

Renders `drawtext`, survives a bad filtergraph, and dumps the **full inventory**:
`-filters`, `-encoders`, `-decoders`, `-muxers`, `-demuxers`, `-protocols`, `-formats`,
`-bsfs`, `-pix_fmts`. Diff that file between the published build and the candidate and
you see every capability gained or lost. For 8.1.9 the diff across nine artifacts was
one line: `drawtext` added, nothing removed.

## `AcceptanceActivity` — whether the build works

21 operations an app actually performs: encode H.264/H.265/VP9/MP3/Opus, remux without
re-encoding, scale+crop+hflip, overlay, burn in ASS subtitles, volume and atempo, pull
a thumbnail at 1.5 s, write metadata and read it back, concatenate, handle two
deliberate errors, and still answer afterwards.

Each check reads the **file produced**, not the return code, and where possible has
`ffprobe` read it back — the codec actually written, the width after the crop, the
duration after the concat.

⚠️ Tiers differ. `min` has no x264; the free tier has no `libmp3lame`. The suite asks
`-encoders`, `-filters`, `-muxers` and `-demuxers` what the build can do and marks
anything missing `ABSENT` without running it. It does **not** classify by matching
ffmpeg's error text — that is guessing, and it guessed wrong three times, most
seriously on the survival check, which wrote a PNG and so failed on tiers without the
PNG encoder for a reason that looked exactly like a crash. The survival check now uses
`-f null -`, which needs no encoder at all.

## The `.aar`

**It never enters this tree.** The repository is public and this harness also tests the
paid Pro and Pro GPL tiers, so the artifact is passed by path with `-PtestAar=`.

A CI check would not protect against a slip here: it runs *after* the push, when the
file is already public, and rewriting history does not truly purge GitHub. So the
backstop is a **pre-commit** hook — `git config core.hooksPath .githooks` — which runs
`tools/check-no-paid-binaries.py` and refuses the commit. CI runs it too, as a second
net for a clone that never set the hook.

`app/libs/` is still honoured, and still ignored, for a quick experiment by hand.

## What it caught

- `drawtext` missing from every published build (issue #1) — and present again
- `ffmpeg-kit-min` and `ffmpeg-kit-https` could not load at all: linked against
  `libc++_shared.so`, never packaged
- `dev.ffmpegkit-maintained:ffmpeg` carried one ABI, so on an x86_64 emulator it was
  binary-translated and crashed inside the translation layer

None of the three was visible to the compiler or to CI.
