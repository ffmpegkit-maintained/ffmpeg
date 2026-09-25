#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Did the security patches this line carries actually go into this build?

The 7.1 and 6.0 lines receive their CVE fixes as patches, because upstream has
published no newer point release on those branches. Their `SOURCE_ID` therefore never
moves, and nothing about the pinned tag distinguishes a tree with the backports from
one without.

⚠️ Two holes this closes, and the second is the real one:

  1. `apply_library_patches` printed to stdout only on FAILURE. A patch that applied
     was invisible, so a CI log could not be audited for what went in.

  2. Nothing in the produced artifact said which patches it contained. A `prebuilt/`
     restored from the checkpoint can ship binaries without the fixes, and no
     downstream check would notice -- exactly the mechanism that let `drawtext`
     vanish from eleven releases. The recipe was right; the delivery was not; nobody
     read the delivery.

Each applied patch is now recorded in `prebuilt/.security-patches`. This compares that
manifest against `patches/<lib>/*.patch` in the repository and fails when they differ,
so a stale tree cannot be published no matter why it is stale.

Usage, from a line directory (android-7.1-lts, …):
    python3 ../tools/check-security-patches.py
    python3 ../tools/check-security-patches.py --root .

Exit code is 1 when a patch the repository carries is missing from the build.
"""
import argparse
import hashlib
import io
import os
import sys


def empreinte(chemin: str) -> str:
    with io.open(chemin, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()[:16]


def attendues(racine: str) -> "dict[tuple[str, str], str]":
    """What the repository says must be in: {(lib, patch): sha}."""
    out = {}
    base = os.path.join(racine, "patches")
    if not os.path.isdir(base):
        return out
    for lib in sorted(os.listdir(base)):
        d = os.path.join(base, lib)
        if not os.path.isdir(d):
            continue
        for n in sorted(os.listdir(d)):
            if n.endswith(".patch"):
                out[(lib, n)] = empreinte(os.path.join(d, n))
    return out


def declarees(racine: str) -> "dict[tuple[str, str], str]":
    """What the build recorded: {(lib, patch): sha}."""
    out = {}
    m = os.path.join(racine, "prebuilt", ".security-patches")
    if not os.path.isfile(m):
        return out
    for l in io.open(m, encoding="utf-8", errors="replace"):
        p = l.split()
        if len(p) >= 3:
            out[(p[0], p[1])] = p[2]
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    a = ap.parse_args()

    att = attendues(a.root)
    dec = declarees(a.root)

    if not att:
        print("OK: this line carries no patches, so there is nothing to verify.")
        return 0

    print("patches the repository carries : %d" % len(att))
    print("patches this build recorded     : %d" % len(dec))
    print("")

    mauvais = []
    for (lib, nom), sha in sorted(att.items()):
        vu = dec.get((lib, nom))
        if vu is None:
            etat, souci = "MISSING", True
        elif vu != sha:
            etat, souci = "DIFFERENT (%s, expected %s)" % (vu, sha), True
        else:
            etat, souci = "in the build", False
        print("  %-10s %-34s %s" % (lib, nom, etat))
        if souci:
            mauvais.append((lib, nom))

    en_trop = sorted(set(dec) - set(att))
    for lib, nom in en_trop:
        print("  %-10s %-34s recorded but no longer in the repository" % (lib, nom))

    print("")
    if not mauvais:
        if en_trop:
            print("OK: every patch the repository carries is in this build.")
            print("    (%d recorded patch(es) are no longer in the repository -- harmless,"
                  % len(en_trop))
            print("     but worth removing from patches/ if the pin has moved past them.)")
        else:
            print("OK: the build carries exactly the patches the repository declares.")
        return 0

    print("MISSING from the build: " + ", ".join("%s/%s" % x for x in mauvais))
    print("")
    print("These are security backports. A build that does not contain them must not")
    print("be published, whatever else is correct about it. The usual cause is a")
    print("prebuilt/ tree restored from the checkpoint and reused without recompiling.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
