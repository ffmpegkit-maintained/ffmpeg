# Working in this repository

Notes for anyone — human or coding agent — making changes here. Project-specific, and
short on purpose: everything else lives in [`docs/`](docs/).

> This file is **public**. Commercial details (pricing, store identifiers, the names of
> CI secrets, private infrastructure) are kept in local notes that `.gitignore` excludes,
> and must not come back into a tracked file.

## Layout

Three LTS lines, each a self-contained build tree:

```
android-8.1-lts/    FFmpeg 8.1.x
android-7.1-lts/    FFmpeg 7.1.x
android-6.0-lts/    FFmpeg 6.1.x   ← yes, 6.1: the directory name tracks the LTS line,
                                     not the FFmpeg version it builds
```

⚠️ **A change to one line almost always belongs in all three.** The trees are copies,
not a shared library, so a fix applied once is a fix missing twice. The `drawtext`
regression in [#1](https://github.com/ffmpegkit-maintained/ffmpeg/issues/1) lived in six
files that each needed the same four lines.

Inside a line:

| | |
|---|---|
| `android.sh` | entry point; enables/disables libraries per tier |
| `scripts/function.sh` | the library table — names, indices, dependency cascades |
| `scripts/{android,apple}/<lib>.sh` | how each library is built |
| `scripts/{android,apple}/ffmpeg.sh` | **how each library is handed to FFmpeg's `configure`** |
| `android/ffmpeg-kit-android-lib/src/main/cpp/fftools_*.c` | vendored fftools, patched to run in-process |

## Adding a library

Four places, and missing the last one fails **silently**:

1. `function.sh` — a name and an index in `get_library_name` / the reverse lookup.
2. `main-android.sh` — its dependency check.
3. `scripts/android/<lib>.sh` — the build.
4. `scripts/android/ffmpeg.sh` — a `case` branch adding `--enable-lib<name>` and the
   pkg-config flags.

⚠️ Skipping 4 produces a build that **succeeds**: the library is compiled, linked, and
present in the `.so` — while everything in FFmpeg that depends on it is dropped by
`configure` without a word in the log. That is exactly how `drawtext` went missing from
every published build for three release lines.

## Checking what you shipped, not what you meant to ship

```bash
python3 tools/check-filters.py <built.aar> --tier full-gpl
```

Reads a built AAR and fails when a tier is missing a filter the patch notes promise. Run
it after a build: every other check in this repository reads the build *inputs*, and the
inputs were all correct while the output was wrong.

## Conventions

- **The three LTS lines are listed newest first** — 8.1, then 7.1, then 6.0 — in the
  README, in release notes, and in any table that enumerates them.
- **Patch notes are written after the fact.** An entry goes into
  [`docs/PATCH-NOTES.md`](docs/PATCH-NOTES.md) only once the artifact is live and has been
  re-downloaded and inspected — never as a plan. See
  [`docs/RELEASE-CHECKLIST.md`](docs/RELEASE-CHECKLIST.md).
- **Publishing to Maven Central is permanent.** A version cannot be removed or amended,
  only superseded. Confirm before tagging a release.
- ⚠️ **Never add `actions/upload-artifact`, or a cache branch, to a workflow that builds
  a paid tier.** On a public repository both are downloadable by any signed-in GitHub
  user. Both happened here and were removed on 2026-06-22; paid tiers push their
  checkpoints to a separate private repository instead.

## Quality pass after a green build

Report findings as a Markdown table with `Severity | Category | Description`
(`Critical` / `Medium` / `Minor` / `OK`), covering: JNI (leaks, use-after-free), Java
(NPE, unclosed resources), security (artifact exposure, workflow triggers), version
consistency, and documentation.
