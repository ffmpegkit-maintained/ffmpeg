#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Does every coordinate of an LTS line carry the same latest version?

One line, one number, everywhere. A developer reading `ffmpeg-kit-full:8.1.8` and
`ffmpeg:8.1.7` cannot tell whether those are the same build, a fix that landed in one
place, or a mistake -- and neither can we, six weeks later.

How the 8.1 line drifted, measured 2026-09-24: `ffmpeg-kit-full-gpl:8.1.7` and
`:8.1.8` are byte-identical, same size and same SHA-256. 8.1.8 was a POM-only
republication -- Maven Central is immutable, so a metadata correction has to go out as
a new version carrying the same binaries. It was pushed for the eight tier names and
not for `ffmpeg` or the two POM aliases, and the line has read 8.1.7/8.1.8 ever since.

Nothing was wrong with doing that. What was missing is anything that noticed.

Usage:
    python3 tools/check-versions.py            # every line
    python3 tools/check-versions.py 8.1        # one line

Exit code is 1 when a line's coordinates disagree, so CI can gate on it.
"""
import re
import sys
import urllib.error
import urllib.request

GROUPE = "dev/ffmpegkit-maintained"
BASE = "https://repo1.maven.org/maven2/" + GROUPE + "/"

PALIERS = ["min", "min-gpl", "https", "https-gpl", "audio", "video", "full", "full-gpl"]

# Per line: the primary AAR, the eight tier AARs, the POM aliases, and the legacy name
# republished as a redirect. All of them must land on the same number.
LIGNES = {
    "6.0": ["ffmpeg"] + ["ffmpeg-kit-" + t for t in PALIERS]
           + ["ffmpeg-kit", "ffmpegkit", "ffmpeg-kit-free"],
    "7.1": ["ffmpeg"] + ["ffmpeg-kit-" + t for t in PALIERS]
           + ["ffmpeg-kit-71", "ffmpegkit-71", "ffmpeg-kit-free-71"],
    "8.1": ["ffmpeg"] + ["ffmpeg-kit-" + t for t in PALIERS]
           + ["ffmpeg-kit-81", "ffmpegkit-81", "ffmpeg-kit-free-81"],
}


def versions(artefact: str, prefixe: str):
    """Versions of this artifact on that line, newest last. Empty when absent."""
    url = BASE + artefact + "/maven-metadata.xml"
    try:
        with urllib.request.urlopen(url, timeout=30) as r:
            xml = r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None          # jamais publie : ce n'est pas un ecart
        raise
    vus = re.findall(r"<version>([^<]+)</version>", xml)
    return [v for v in vus if v.startswith(prefixe + ".")]


def examine(ligne: str) -> int:
    derniere = {}
    for a in LIGNES[ligne]:
        vs = versions(a, ligne)
        if vs is None:
            continue
        derniere[a] = vs[-1] if vs else "(rien sur cette ligne)"

    valeurs = sorted(set(derniere.values()))
    print("ligne %s :" % ligne)
    for a in LIGNES[ligne]:
        if a in derniere:
            print("  %-24s %s" % (a, derniere[a]))
    absents = [a for a in LIGNES[ligne] if a not in derniere]
    if absents:
        print("  (jamais publies : %s)" % ", ".join(absents))

    if len(valeurs) <= 1:
        print("  OK: one number, %s, everywhere it is published." % (valeurs[0] if valeurs else "-"))
        return 0

    print("")
    print("  MISMATCH: %s" % " vs ".join(valeurs))
    print("  One line must carry one number. A metadata-only republication still has")
    print("  to go out for every coordinate, or the line reads two versions at once.")
    return 1


def main() -> int:
    demandees = sys.argv[1:] or sorted(LIGNES)
    mauvais = 0
    for l in demandees:
        if l not in LIGNES:
            sys.exit("unknown line: " + l)
        mauvais += examine(l)
        print("")
    return 1 if mauvais else 0


if __name__ == "__main__":
    sys.exit(main())
