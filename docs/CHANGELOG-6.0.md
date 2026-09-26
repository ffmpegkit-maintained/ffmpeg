# Changelog — 6.0 LTS line

What is **live** for this line, newest first. One file per LTS line, because
[PATCH-NOTES.md](PATCH-NOTES.md) mixes all three and that is what made past unreleased
entries easy to misread as released.

> An entry appears here **only after** the publish succeeded **and** the artifact was
> re-downloaded from Maven Central and inspected. If a fix has no entry here, it is not
> live for this line — full stop. See [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md).

## 6.0.4 — 2026-09-26

FFmpeg `n6.1.6` + two CVE backports. This line builds **n6.1.6**, not 6.0 --
which is why `drawtext` was affected here too: the filter has depended on
libharfbuzz since 6.1. NDK **r27c** (`27.2.12479018`).

### Seven of the nine tiers could not be loaded at all before this release

`free`, `min`, `min-gpl`, `https`, `https-gpl`, `audio`, `video` failed at `dlopen`:

```
dlopen failed: can't enable GNU RELRO protection for
  ".../jni/x86_64/libswresample.so": Out of memory
```

Not a memory shortage. `PT_GNU_RELRO`, rounded up to 16 KB, extended past the last
`PT_LOAD`; the loader's `mprotect` covered unmapped memory and returned `ENOMEM`. NDK
r26c's `lld` rounds RELRO up to `max-page-size` without padding the final `LOAD`. It only
affects **small** libraries, which is why `full` and `full-gpl` worked and it looked
intermittent.

**All nine tiers load in 6.0.4.**

### Also fixed

- `libc++_shared.so` is 16 KB-aligned (r26c shipped it at 4 KB; it is copied from the NDK,
  so no linker flag of ours reached it). Google Play stops accepting updates without
  16 KB page support on **2027-02-01**.
- `libc++_shared.so` is present at all: `free`, `https` and `min` shipped **without** it
  while `libffmpegkit.so` links against it unconditionally.
- **`drawtext`** is present in `full`, `full-gpl` and `video`. Since FFmpeg 6.1 it depends
  on libharfbuzz, and `configure` drops a filter silently when its flag is absent.
- A **filtergraph parse error no longer kills the process**: `init_complex_filtergraph()`
  left an `AVFilterInOut *` uninitialised and freed it on the error path.
- **CVE-2026-64830** (vobsub) and **CVE-2026-64835** (adx), backported to `n6.1.6`.

### Verified on the live artifacts

Re-downloaded from `repo1.maven.org` after publication — not the release assets, not a
local copy:

| coordinate | 16 KB + RELRO | filters + pin | `libc++_shared` |
|---|---|---|---|
| `ffmpeg` | OK | OK | 2/2 ABI |
| `ffmpeg-kit-min` | OK | OK | 2/2 ABI |
| `ffmpeg-kit-https` | OK | OK | 2/2 ABI |
| `ffmpeg-kit-full-gpl` | OK | OK | 2/2 ABI |

`ffmpeg-kit-https` came back at 20 678 244 bytes. The stale copy that was tracked in this
repository until today -- the 6.0.3 bytes, refused by `check-elf-16kb.py` -- is 19 952 767.
The fresh artifact is the one that went out. See the known-issue entry in
[PATCH-NOTES.md](PATCH-NOTES.md) for how close that came to not being true.

Before publishing: **18 capability lists** compared against 6.0.3 for the two tiers that
could be loaded before — one difference, `drawtext` added, nothing removed — and **189
acceptance checks** (21 × 9 tiers) on a device, **0 failures**. The 41 skipped are tiers
genuinely without the encoder or filter involved, cross-checked against each artifact's
own capability tables.

All 12 coordinates carry one number: `check-versions.py 6.0` reports
`one number, 6.0.4, everywhere it is published`.
