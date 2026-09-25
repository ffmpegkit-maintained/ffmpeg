#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Has a paid artifact been staged into this public repository?

The test harness under `test-app/` also exercises the paid Pro and Pro GPL tiers. It
takes the artifact by path -- `-PtestAar=/absolute/path` -- so the .aar never enters
the tree, and that is the real protection.

⚠️ This is the backstop, and it is deliberately wired as a **pre-commit hook**, not
only as a CI step. On a public repository a CI check runs after the push: the file is
already public by the time it fails, and rewriting history does not truly purge
GitHub -- this project learned that in July 2026 with a CLAUDE.md. A guard that can
only report an exposure after it happened is not a guard against exposure.

    git config core.hooksPath .githooks

Three .aar files are tracked on purpose, all free tier, all inputs to the Maven alias
publications. They are listed by path: a new one has to be added here deliberately,
which is the point.

Usage:
    python3 tools/check-no-paid-binaries.py            # what is tracked
    python3 tools/check-no-paid-binaries.py --staged   # what is about to be committed
"""
import subprocess
import sys

# Free-tier artifacts, inputs to publish-maven-free.yml's alias publications.
AUTORISES = {
    "android-6.0-lts/android/ffmpeg-kit-android-lib/prebuilt-aars/ffmpeg-kit-https.aar",
    "android-6.0-lts/android/ffmpeg-kit-android-lib/prebuilt-aars/ffmpeg-kit-https-gpl.aar",
    "android-6.0-lts/releases/ffmpeg-kit-6.0-lts-arm64-v8a.aar",
}

SUSPECTS = (".aar", ".jar", ".so", ".apk", ".zip")

# gradle-wrapper.jar is Gradle's own, present in every Android project.
TOLERES = ("gradle/wrapper/gradle-wrapper.jar",)


def fichiers(staged: bool):
    if staged:
        out = subprocess.run(["git", "diff", "--cached", "--name-only",
                              "--diff-filter=ACMR"],
                             capture_output=True, check=True).stdout
    else:
        out = subprocess.run(["git", "ls-files"], capture_output=True,
                             check=True).stdout
    return [l for l in out.decode("utf-8", "replace").splitlines() if l]


def main() -> int:
    staged = "--staged" in sys.argv
    fautifs = []
    for f in fichiers(staged):
        if not f.endswith(SUSPECTS):
            continue
        if any(f.endswith(t) for t in TOLERES):
            continue
        if f in AUTORISES:
            continue
        fautifs.append(f)

    quoi = "staged for commit" if staged else "tracked"
    if not fautifs:
        print("OK: no unexpected binary is %s." % quoi)
        return 0

    print("Unexpected binaries %s:" % quoi)
    for f in fautifs:
        marque = "  <- under test-app/, which must never hold an .aar" \
            if f.startswith("test-app/") else ""
        print("  " + f + marque)
    print("")
    print("This repository is public. A paid .aar committed here is published the")
    print("moment it is pushed, and history rewriting does not undo that.")
    print("")
    print("The harness takes its artifact by path and never copies it in:")
    print("  ./gradlew :app:assembleDebug -PtestAar=/absolute/path/to/ffmpeg-kit.aar")
    print("")
    print("If a binary genuinely belongs in the repository, add its path to")
    print("AUTORISES in this file, deliberately.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
