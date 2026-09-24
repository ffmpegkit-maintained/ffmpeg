#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Does a built AAR contain the filters its tier promises?

Issue #1 was a filter -- `drawtext` -- that PATCH-NOTES promised in `full` and
`full-gpl` and that no published AAR has ever carried. Nothing caught it for three
release lines and eleven versions, because every check we had looked at the *inputs*:
harfbuzz was in the library list, it was built, it was linked. What nobody looked at was
the **output**.

    A check that only reads the build scripts confirms what we meant to do.
    This one reads what we shipped.

The mechanism was specific -- since FFmpeg 6.1 `drawtext` depends on libharfbuzz, and
`configure` drops a filter *silently* when its dependency flag is missing -- but the
shape is general: anything can fall out of a build without a line in the log. So this
checks the artifact, not the recipe, and would have caught it whatever the cause.

Usage:
    python3 tools/check-filters.py <aar> [--tier full|full-gpl|basic|free]
    python3 tools/check-filters.py <aar> --list

Exit code is 1 when something promised is missing, so CI can gate on it.
"""
import argparse
import re
import sys
import zipfile

# What each tier promises, from docs/PATCH-NOTES.md. Filters only: a filter is a string
# in libavfilter's registration table, so its presence is a fact readable from the file.
#
# Deliberately short. A list that tries to be exhaustive goes stale and gets ignored;
# these are the ones a buyer notices the day they are gone.
PROMESSES = {
    "free": [],
    "basic": ["scale", "overlay", "crop", "transpose", "volume", "atempo"],
    "full": ["scale", "overlay", "crop", "transpose", "volume", "atempo",
             "drawtext", "subtitles", "ass", "drawbox"],
}
PROMESSES["full-gpl"] = PROMESSES["full"]


def filtres(chemin: str) -> bytes:
    """libavfilter's bytes, from whichever ABI the AAR carries first."""
    with zipfile.ZipFile(chemin) as z:
        noms = [n for n in z.namelist() if n.endswith("/libavfilter.so")]
        if not noms:
            sys.exit("no libavfilter.so in " + chemin + " -- is this an FFmpegKit AAR?")
        return z.read(sorted(noms)[0])


def libavutil(chemin: str) -> bytes:
    """libavutil's bytes, where FFmpeg records both its version and its configure line."""
    with zipfile.ZipFile(chemin) as z:
        noms = [n for n in z.namelist() if n.endswith("/libavutil.so")]
        if not noms:
            sys.exit("no libavutil.so in " + chemin + " -- is this an FFmpegKit AAR?")
        return z.read(sorted(noms)[0])


def configure(chemin: str) -> str:
    """The configure line FFmpeg recorded into libavutil, or an empty string.

    Read because it turns "the filter is missing" into "and here is what configure was
    told", which is the difference between a bug report and a diagnosis.
    """
    with zipfile.ZipFile(chemin) as z:
        noms = [n for n in z.namelist() if n.endswith("/libavutil.so")]
        if not noms:
            return ""
        m = re.search(rb"--enable-[^\x00]{20,8000}", z.read(sorted(noms)[0]))
        return m.group().decode("utf-8", "replace") if m else ""


def tier_devine(chemin: str) -> str:
    nom = chemin.lower()
    for t in ("full-gpl", "full", "basic", "free"):
        if t in nom:
            return t
    return "full"


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("aar")
    p.add_argument("--tier", choices=sorted(PROMESSES), default=None)
    p.add_argument("--pin", default=None,
                   help="the FFmpeg tag scripts/source.sh pins; fails when the "
                        "artifact does not carry it")
    p.add_argument("--list", action="store_true",
                   help="print what was found instead of judging it")
    a = p.parse_args()

    tier = a.tier or tier_devine(a.aar)
    blob = filtres(a.aar)
    cfg = configure(a.aar)

    # ⚠️ Is this the artifact we just built, or one that was lying around?
    #
    # A different question from "does it have the filters", and the one that went
    # unasked for ten weeks: the CI restored a June checkpoint, verified ITS alignment
    # and licence, went green, and discarded what it had just compiled. A pin moved for
    # a security fix had no effect anyone could see.
    #
    # The version FFmpeg records inside libavutil answers it, and a stale file cannot
    # fake it.
    if a.pin:
        vus = sorted({m.decode() for m in re.findall(rb"n[0-9]+\.[0-9]+\.[0-9]+",
                                                     libavutil(a.aar))})
        print("pin   : " + a.pin + "   found in libavutil: " + (", ".join(vus) or "(none)"))
        if a.pin not in vus:
            print("")
            print("MISMATCH: the artifact does not carry the pinned FFmpeg version.")
            print("  Either it was restored from a checkpoint instead of built, or the")
            print("  source cache was not invalidated when the pin moved.")
            return 1

    attendus = PROMESSES[tier]
    absents = [f for f in attendus if blob.count(f.encode()) == 0]

    print("AAR   : " + a.aar)
    print("tier  : " + tier)
    if a.list or absents:
        for f in attendus:
            n = blob.count(f.encode())
            print("  %-10s %s" % (f, "present" if n else "MISSING"))

    if not absents:
        print("\nOK: the %d filters this tier promises are all present." % len(attendus))
        return 0

    print("\nMISSING from a tier that promises them: " + ", ".join(absents))
    if cfg:
        # The likely cause, when we can name it. Not a guess: these are the flags
        # FFmpeg itself recorded, read back out of the shipped binary.
        for filtre, flag in (("drawtext", "--enable-libharfbuzz"),
                             ("subtitles", "--enable-libass"),
                             ("ass", "--enable-libass")):
            if filtre in absents and flag not in cfg:
                print("  %s needs %s, and configure was not given it." % (filtre, flag))
    return 1


if __name__ == "__main__":
    sys.exit(main())
