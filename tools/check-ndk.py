#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Is the NDK actually in use the one this line declares?

⚠️ This has already gone wrong once, and the record is in the patch notes:

    8.1.3 -- Accidental release: version bump targeting r27c but the CI workflow
    download URL was not yet updated at tag time, resulting in an r26c build. The
    Maven Central artifact exists but is functionally identical to 8.1.2.

Two places name the toolchain -- `ndkVersion` in the line's build.gradle, and the
download URL in each workflow -- and nothing compared them. A version published on the
strength of a toolchain it was not built with is unfixable: Maven Central is immutable.

Now that NDK r26c is known to produce libraries that cannot be loaded at all (RELRO
running past the last LOAD on small libraries, `libc++_shared.so` aligned on 4 KB), the
gap is no longer cosmetic.

This compares Pkg.Revision of ${ANDROID_NDK_ROOT} against the ndkVersion declared for
the line, and refuses when they differ.

Usage, from a line directory (android-7.1-lts, …):
    python3 ../tools/check-ndk.py
    python3 ../tools/check-ndk.py --root . --ndk /home/runner/.ndk/android-ndk-r27c

Exit code is 1 when they differ, or when either one cannot be read -- "cannot tell" is
not "fine".
"""
import argparse
import io
import os
import re
import sys


def declaree(racine):
    """ndkVersion from the line's android library build.gradle."""
    p = os.path.join(racine, "android", "ffmpeg-kit-android-lib", "build.gradle")
    if not os.path.isfile(p):
        return None, p
    s = io.open(p, encoding="utf-8", errors="replace").read()
    m = re.search(r'ndkVersion\s+["\']([^"\']+)["\']', s)
    return (m.group(1) if m else None), p


def en_usage(racine_ndk):
    """Pkg.Revision from the NDK that will actually compile."""
    if not racine_ndk:
        return None, "(ANDROID_NDK_ROOT vide)"
    p = os.path.join(racine_ndk, "source.properties")
    if not os.path.isfile(p):
        return None, p
    for l in io.open(p, encoding="utf-8", errors="replace"):
        if l.lower().startswith("pkg.revision"):
            return l.split("=", 1)[1].strip(), p
    return None, p


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--ndk", default=os.environ.get("ANDROID_NDK_ROOT", ""))
    a = ap.parse_args()

    dec, ou_dec = declaree(a.root)
    use, ou_use = en_usage(a.ndk)

    print("declared : %-24s (%s)" % (dec or "UNREADABLE", ou_dec))
    print("in use   : %-24s (%s)" % (use or "UNREADABLE", ou_use))
    print("")

    if dec is None or use is None:
        print("One of the two could not be read, so they cannot be compared.")
        print("A build whose toolchain cannot be established must not be published:")
        print("Maven Central is immutable and a wrong one cannot be corrected.")
        return 1

    if dec != use:
        print("::error::ndkVersion says %s, the NDK on PATH is %s" % (dec, use))
        print("MISMATCH. This is how 8.1.3 shipped: the version bump named r27c and the")
        print("workflow still downloaded r26c. Update BOTH the workflow download URL and")
        print("ndkVersion, or the artifact is built by a toolchain nobody chose.")
        return 1

    print("OK: the NDK in use is the one this line declares (%s)." % dec)
    return 0


if __name__ == "__main__":
    sys.exit(main())
