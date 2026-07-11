# Release checklist — ffmpeg-kit (all LTS lines)

Read this before pushing any tag or running `publish-maven-free.yml` /
updating a Gumroad product file. Written 2026-07-11 after finding that a JNI
memory leak fix (`registerNewNativeFFmpegPipe`) had been applied to all three
LTS lines' source on the same day (commit `bfedd63`), but only ever reached
the **published** artifact for one of them (8.1) — 6.0 and 7.1 carried the
fix in source for nearly three weeks without a single user receiving it.

## Why git tags and PATCH-NOTES.md are not enough

- **Tags can rot.** Two tags in this repo (`v6.0.1-lts-android`,
  `v8.1.7-lts-android`) were found pointing at the exact same, unrelated
  commit — evidence of history-rewrite damage. A checklist step that trusts
  `git tag` output can be silently wrong.
- **PATCH-NOTES.md records intent, not fact.** It documented `v6.0.3` and
  `v7.1.6` as if released; neither was ever tagged or published. A changelog
  entry is not proof of shipping.
- **The only trustworthy source of "what users actually have" is the live
  artifact itself** — the AAR on Maven Central (or the file currently on the
  Gumroad product page), inspected directly. Everything else (tags, commit
  messages, changelog prose) is a claim, not a fact.

## Before tagging/publishing a fix that touches shared code

Shared code = anything under `android-*-lts/android/.../src/main/cpp/` or
`.../src/main/java/` that exists with near-identical content across more than
one LTS line (the JNI bridge: `ffmpegkit.c`, `ffprobekit.c`,
`ffmpegkit_exception.c`, `NativeLoader.java`, etc. — not the FFmpeg source
tree itself, which is genuinely line-specific).

1. **Identify every line that contains this code.** `grep` the same
   function/pattern across `android-6.0-lts/`, `android-7.1-lts/`,
   `android-8.1-lts/`.
2. **For each line, download the CURRENTLY LIVE artifact** (Maven Central for
   Free; the file actually on the Gumroad product page for paid tiers — ask
   before assuming) and check directly whether the fix is present:
   - String-based checks work for error messages / config strings
     (`strings`-style search, see this session's CVE audit for the pattern).
   - For anything without a distinguishing string (like a missing
     `Release*`/`Free*` call), disassemble the specific function and compare
     against a known-fixed vs known-leaky version. NDK ships
     `llvm-objdump`/`llvm-readelf` at
     `$ANDROID_HOME/ndk/<version>/toolchains/llvm/prebuilt/<host>/bin/` — no
     extra tooling needed.
3. **Answer explicitly, per line: "Is this fix live right now?"** Not "is it
   in `main`", not "is there a tag that looks right" — is it in the artifact
   a user would download today.
4. **Every line where the answer is "no" goes into tonight's release too.**
   A fix that's fixed on one line and not the others isn't done.

## After publishing

Record the release in **`docs/CHANGELOG-<line>.md`** (one file per LTS line,
not the shared `PATCH-NOTES.md` — that file mixes all three lines and is
what made past unreleased entries easy to misread as released). Add the
entry **only after** the publish step has actually succeeded and you've
re-downloaded the artifact to confirm the fix is there — never before, never
as a "planned" placeholder. If a line's changelog has no entry for a fix,
that fix is not live for that line, full stop.
