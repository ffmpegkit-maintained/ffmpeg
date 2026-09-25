#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Do the docs describe the ABIs we actually publish?

README.md and docs/MIGRATION.md said "arm64-v8a only" and told developers that x86_64
"isn't available prebuilt — compile it yourself". Measured 2026-09-25 by downloading
the published artifacts:

    ffmpeg:6.0.3 / 7.1.6 / 8.1.7          arm64-v8a
    ffmpeg-kit-min:6.0.3                  arm64-v8a, x86_64
    ffmpeg-kit-audio:7.1.6                arm64-v8a, x86_64
    ffmpeg-kit-full-gpl:6.0.3 / 7.1.6     arm64-v8a, x86_64

The 24 tier names have carried x86_64 since they were first published. The statement
was wrong for 24 of the 25 coordinates, and it sent anyone on an emulator off to build
from source for a binary that was already on Maven Central.

Same shape as the rest of this release: the document described what we believed we
shipped. This reads the artifact and compares.

Usage:
    python3 tools/check-doc-abi.py                 # fetch the published artifacts
    python3 tools/check-doc-abi.py <aar> [...]     # check local ones instead

Exit code is 1 when a doc claims an ABI set the artifacts do not have.
"""
import io
import os
import re
import sys
import urllib.error
import urllib.request
import zipfile

BASE = "https://repo1.maven.org/maven2/dev/ffmpegkit-maintained/"

# One per family; fetching all 25 on every CI run would be unkind to Maven Central.
TEMOINS = [
    ("ffmpeg", "8.1.7"),
    ("ffmpeg-kit-min", "8.1.8"),
    ("ffmpeg-kit-full-gpl", "8.1.8"),
]

# Phrases that assert an ABI set. Each maps to what it claims.
INTERDITS = [
    (r"arm64-v8a only", "arm64-v8a seul"),
    (r"only `arm64-v8a` is published", "arm64-v8a seul"),
    (r"All are `arm64-v8a`", "arm64-v8a seul"),
]

DOCS = ["README.md", "docs/MIGRATION.md", "docs/BUILD.md"]


def prose(chemin: str) -> "list[str]":
    """The lines that actually assert something: no code blocks, no quotations.

    ⚠️ The first version of this check matched words anywhere in the file and flagged
    two lines that are correct: a comment inside a ```bash block explaining what
    `--disable-x86-64` does, and the sentence that *quotes* the old wrong claim in
    order to correct it. A code block shows a command, not a promise; a quotation
    reports what the text used to say. Neither asserts anything.

    A check that flags what is fine is how a check gets switched off.
    """
    lignes = []
    dans_bloc = False
    for l in io.open(chemin, encoding="utf-8").read().split("\n"):
        if l.lstrip().startswith("```"):
            dans_bloc = not dans_bloc
            lignes.append("")
            continue
        if dans_bloc:
            lignes.append("")
            continue
        # Ce qui est entre guillemets droits est rapporte, pas affirme.
        lignes.append(re.sub(r'"[^"]*"', "", l))
    return lignes


def abis(octets: bytes) -> "set[str]":
    with zipfile.ZipFile(io.BytesIO(octets)) as z:
        return {n.split("/")[1] for n in z.namelist()
                if n.startswith("jni/") and n.count("/") > 1}


def telecharge(nom: str, version: str):
    url = BASE + nom + "/" + version + "/" + nom + "-" + version + ".aar"
    try:
        with urllib.request.urlopen(url, timeout=120) as r:
            return r.read()
    except urllib.error.URLError as e:
        print("  (%s:%s injoignable : %s)" % (nom, version, e))
        return None


def main() -> int:
    vus = {}
    if len(sys.argv) > 1:
        for f in sys.argv[1:]:
            vus[os.path.basename(f)] = abis(io.open(f, "rb").read())
    else:
        for nom, version in TEMOINS:
            o = telecharge(nom, version)
            if o:
                vus[nom + ":" + version] = abis(o)

    if not vus:
        print("no artifact could be read; not judging the docs on nothing.")
        return 0

    print("ABIs actually published:")
    for k in sorted(vus):
        print("  %-28s %s" % (k, ", ".join(sorted(vus[k]))))

    # ⚠️ The claim is about what a developer can depend on. If ANY published
    # coordinate carries an ABI, "only arm64-v8a is published" is false -- the
    # sentence does not say "the primary artifact".
    toutes = set().union(*vus.values())
    print("\nunion across the artifacts: " + ", ".join(sorted(toutes)))

    fautifs = []
    if toutes - {"arm64-v8a"}:
        for d in DOCS:
            if not os.path.exists(d):
                continue
            for ligne, texte in enumerate(prose(d), start=1):
                for motif, dit in INTERDITS:
                    m = re.search(motif, texte)
                    if m:
                        fautifs.append((d, ligne, m.group(), dit))

    print("")
    if not fautifs:
        print("OK: no document claims an ABI set the artifacts contradict.")
        return 0

    for d, ligne, trouve, dit in fautifs:
        print("%s:%d: \"%s\" claims %s" % (d, ligne, trouve, dit))
    print("")
    print("but the published artifacts carry " + ", ".join(sorted(toutes)) + ".")
    print("A developer reading this builds from source for a binary already on Maven.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
