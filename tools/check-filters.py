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

What this tool can and cannot say
---------------------------------
It counts byte strings in `libavfilter.so`. A filter's name string is there when the
filter is compiled in and gone when it is not, so the signal is real -- but it is one
string, and the `ff_vf_*` symbols it would rather read are stripped from a release
`.so`. Measured 2026-09-24: `drawtext` occurs 0 times in an 8.1 build without it and
exactly 1 time in a 6.0 build with it.

The authority is ffmpeg's own registration table, which only a running build can show:
`-filters`. The device harness in test-app/ asks it, and that is what a release should
be gated on. This tool is the CI-side approximation -- cheap, no device, and good enough
to stop a build that lost a filter.

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
# Tiers that exist and promise no filter in particular: the dependency and pin
# checks still have to run on them, and argparse used to reject the name.
for _t in ("min", "min-gpl", "https", "https-gpl", "audio", "video"):
    PROMESSES.setdefault(_t, [])


def par_abi(chemin: str, lib: str) -> "dict[str, bytes]":
    """One entry per ABI the AAR carries, keyed by abi name.

    ⚠️ This used to read `sorted(noms)[0]` -- the first ABI alphabetically, always
    arm64-v8a -- and judge the whole artifact on it. An AAR shipping a good arm64 and
    a broken x86_64 would have passed, and nothing else looks. A bench that does not
    look somewhere returns a verdict of success for that place.
    """
    with zipfile.ZipFile(chemin) as z:
        noms = [n for n in z.namelist() if n.endswith("/" + lib)]
        if not noms:
            sys.exit("no " + lib + " in " + chemin + " -- is this an FFmpegKit AAR?")
        return {n.split("/")[-2]: z.read(n) for n in sorted(noms)}


def configure(chemin: str) -> str:
    """The configure line FFmpeg recorded into libavutil, or an empty string.

    Read because it turns "the filter is missing" into "and here is what configure was
    told", which is the difference between a bug report and a diagnosis.
    """
    for blob in par_abi(chemin, "libavutil.so").values():
        m = re.search(rb"--enable-[^\x00]{20,8000}", blob)
        if m:
            return m.group().decode("utf-8", "replace")
    return ""


# Shared libraries an .aar is expected to carry itself. The rest come from the device.
FOURNIES_PAR_ANDROID = {
    "liblog.so", "libandroid.so", "libdl.so", "libc.so", "libm.so", "libstdc++.so",
    "libmediandk.so", "libcamera2ndk.so", "libz.so", "libOpenSLES.so", "libaaudio.so",
    "libEGL.so", "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so", "libjnigraphics.so",
    "libvulkan.so", "libnativewindow.so", "libsync.so", "libbinder_ndk.so",
}


def dt_needed(blob: bytes) -> "set[str]":
    """The DT_NEEDED entries of an ELF64 little-endian shared object.

    ⚠️ Read from .dynamic, not by grepping the file for lib*.so. The grep version of
    this check reported libx265.so and libOpenCL.so as missing dependencies of the
    full-gpl tier -- strings living inside statically linked x265 code, nothing to do
    with linking. A false positive is how a check gets switched off.
    """
    import struct
    if blob[:4] != b"\x7fELF" or blob[4] != 2:      # ELF64 seulement (arm64, x86_64)
        return set()
    e_shoff, = struct.unpack_from("<Q", blob, 0x28)
    e_shentsize, e_shnum = struct.unpack_from("<HH", blob, 0x3A)
    dynamique = dynstr = None
    for i in range(e_shnum):
        o = e_shoff + i * e_shentsize
        sh_type, = struct.unpack_from("<I", blob, o + 4)
        sh_offset, = struct.unpack_from("<Q", blob, o + 0x18)
        sh_size, = struct.unpack_from("<Q", blob, o + 0x20)
        if sh_type == 6:                              # SHT_DYNAMIC
            dynamique = (sh_offset, sh_size)
        elif sh_type == 3 and dynstr is None:         # SHT_STRTAB
            dynstr = (sh_offset, sh_size)
        elif sh_type == 11:                           # SHT_DYNSYM -> son lien est .dynstr
            sh_link, = struct.unpack_from("<I", blob, o + 0x28)
            lo = e_shoff + sh_link * e_shentsize
            dynstr = (struct.unpack_from("<Q", blob, lo + 0x18)[0],
                      struct.unpack_from("<Q", blob, lo + 0x20)[0])
    if not dynamique or not dynstr:
        return set()

    noms = set()
    off, taille = dynamique
    for p in range(off, off + taille, 16):
        d_tag, d_val = struct.unpack_from("<Qq", blob, p)
        if d_tag == 0:                                # DT_NULL
            break
        if d_tag == 1:                                # DT_NEEDED
            base = dynstr[0] + d_val
            fin = blob.index(b"\x00", base)
            noms.add(blob[base:fin].decode("utf-8", "replace"))
    return noms


def dependances_absentes(chemin: str) -> "set[str]":
    """Shared libraries the .so files need and the .aar does not contain."""
    manquantes = set()
    with zipfile.ZipFile(chemin) as z:
        sos = [n for n in z.namelist() if n.endswith(".so")]
        presentes = {n.split("/")[-1] for n in sos}
        for n in sos:
            for nom in dt_needed(z.read(n)):
                if nom not in presentes and nom not in FOURNIES_PAR_ANDROID:
                    manquantes.add(nom)
    return manquantes


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
    avfilter = par_abi(a.aar, "libavfilter.so")
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
        mauvais = []
        for abi, blob in par_abi(a.aar, "libavutil.so").items():
            vus = sorted({m.decode()
                          for m in re.findall(rb"n[0-9]+\.[0-9]+\.[0-9]+", blob)})
            print("pin   : %s   %-12s libavutil: %s"
                  % (a.pin, abi, ", ".join(vus) or "(none)"))
            if a.pin not in vus:
                mauvais.append(abi)
        if mauvais:
            print("")
            print("MISMATCH in " + ", ".join(mauvais) +
                  ": the artifact does not carry the pinned FFmpeg version.")
            print("  Either it was restored from a checkpoint instead of built, or the")
            print("  source cache was not invalidated when the pin moved.")
            return 1

    # WARNING: does every .so the artifact needs actually travel with it?
    #
    # A build can link against libc++_shared.so and not package it, and nothing
    # static notices: the filters are all there, the pin matches, the licence is
    # bundled. The app then dies on the very first load, before any of it runs:
    #
    #   UnsatisfiedLinkError: dlopen failed: library "libc++_shared.so" not found
    #
    # Measured 2026-09-24 on the freshly built `min` and `https` tiers, which named
    # it and did not carry it. The .so lists what it needs; this reads that list
    # back against what the .aar holds.
    manquantes = sorted(dependances_absentes(a.aar))
    if manquantes:
        print("AAR   : " + a.aar)
        print("")
        print("MISSING shared libraries the artifact itself asks for: "
              + ", ".join(manquantes))
        print("  Linked against, never packaged. Every app using this .aar dies on")
        print("  the first load with UnsatisfiedLinkError, whatever else is correct.")
        return 1

    attendus = PROMESSES[tier]
    print("AAR   : " + a.aar)
    print("tier  : " + tier)
    print("abis  : " + ", ".join(sorted(avfilter)))

    absents = set()
    for abi, blob in sorted(avfilter.items()):
        manque = [f for f in attendus if blob.count(f.encode()) == 0]
        absents.update(manque)
        if a.list or manque:
            print("  " + abi + ":")
            for f in attendus:
                print("    %-10s %s"
                      % (f, "present" if blob.count(f.encode()) else "MISSING"))

    if not absents:
        print("\nOK: the %d filters this tier promises are present in all %d abi(s)."
              % (len(attendus), len(avfilter)))
        return 0

    absents = sorted(absents)
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
