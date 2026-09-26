#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Will every .so in this AAR actually load on a 16 KB-page device -- and at all?

Two properties, read from the ELF program headers of the shipped artifact.

⚠️ 1. Every PT_LOAD must be aligned on 16 KB.

   The build passes `-Wl,-z,max-page-size=16384`, so everything it links is aligned.
   `libc++_shared.so` is NOT linked here: it is a prebuilt copied out of the NDK, and no
   linker flag reaches it. Measured 2026-09-25: NDK r26c ships it at p_align 0x1000, NDK
   r27c at 0x4000. The 6.0 and 7.1 lines were on r26c, so every one of their artifacts
   carried one library that cannot load on a 16 KB device -- and it is the library
   `libffmpegkit.so` needs unconditionally, so it fails before anything else.

⚠️ 2. PT_GNU_RELRO, rounded up to the page size, must not run past the last PT_LOAD.

   This is the one that mattered today. Android's loader mprotects
   [relro_start, round_up(relro_end)]. When that range extends beyond the mapped
   segments, mprotect answers ENOMEM and bionic reports it verbatim:

     dlopen failed: can't enable GNU RELRO protection for "…/libavdevice.so": Out of memory

   which reads like a memory shortage and is not one. NDK r26c's lld rounds RELRO up to
   max-page-size without padding the final LOAD to match; r27c does. It only bites SMALL
   libraries, so it hit `libswresample.so` and `libavdevice.so` and spared the large
   ones -- 7 of the 9 tiers on 6.0 and 7.1 could not be loaded at all, while `full` and
   `full-gpl` worked and made it look intermittent.

Why this reads the AAR and not `prebuilt/`: the old inline check ran
`find prebuilt -name "*.so"`, which never sees `libc++_shared.so` (Gradle adds it) and
never looked at RELRO. It was green throughout. A check that reads the build tree
answers for the build tree; the thing customers install is the AAR.

Usage:
    python3 tools/check-elf-16kb.py <aar> [<aar> ...]
    python3 tools/check-elf-16kb.py --dir prebuilt        # every .so under a directory

Exit code is 1 when any library would fail to load.
"""
import argparse
import io
import os
import struct
import sys
import zipfile

PAGE = 16384
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552


def phdrs(blob):
    """(p_type, p_vaddr, p_memsz, p_align) for each program header, or None."""
    if len(blob) < 64 or blob[:4] != b"\x7fELF" or blob[4] != 2:
        return None                        # pas un ELF 64 bits
    e_phoff, = struct.unpack_from("<Q", blob, 0x20)
    e_phentsize, e_phnum = struct.unpack_from("<HH", blob, 0x36)
    out = []
    for i in range(e_phnum):
        o = e_phoff + i * e_phentsize
        if o + 56 > len(blob):
            return None
        p_type, = struct.unpack_from("<I", blob, o)
        p_vaddr, = struct.unpack_from("<Q", blob, o + 0x10)
        p_memsz, = struct.unpack_from("<Q", blob, o + 0x28)
        p_align, = struct.unpack_from("<Q", blob, o + 0x30)
        out.append((p_type, p_vaddr, p_memsz, p_align))
    return out


def juger(nom, blob):
    """Liste des reproches, vide si la bibliotheque est chargeable."""
    p = phdrs(blob)
    if p is None:
        return []
    charges = [(v, m, a) for t, v, m, a in p if t == PT_LOAD]
    if not charges:
        return []
    reproches = []

    mal_alignes = sorted(set(a for _, _, a in charges if a < PAGE))
    if mal_alignes:
        reproches.append("p_align %s < 16 Ko" % ", ".join("0x%x" % a for a in mal_alignes))

    fin_charge = max(v + m for v, m, _ in charges)
    page = max(a for _, _, a in charges)
    for t, v, m, _ in p:
        if t != PT_GNU_RELRO:
            continue
        haut = (v + m + page - 1) // page * page
        if haut > fin_charge:
            reproches.append("RELRO jusqu'a 0x%x depasse le dernier LOAD 0x%x"
                             % (haut, fin_charge))
    return reproches


def depuis_aar(chemin):
    z = zipfile.ZipFile(chemin)
    for n in sorted(x for x in z.namelist() if x.endswith(".so")):
        yield n, z.read(n)


def depuis_dossier(racine):
    for base, _, fichiers in os.walk(racine):
        for f in sorted(fichiers):
            if f.endswith(".so"):
                c = os.path.join(base, f)
                with io.open(c, "rb") as fh:
                    yield os.path.relpath(c, racine), fh.read()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cibles", nargs="*", help="un ou plusieurs .aar")
    ap.add_argument("--dir", default=None, help="parcourir un dossier au lieu d'un .aar")
    a = ap.parse_args()

    sources = []
    if a.dir:
        sources.append((a.dir, depuis_dossier(a.dir)))
    for c in a.cibles:
        sources.append((c, depuis_aar(c)))
    if not sources:
        print("rien a verifier : donner un .aar ou --dir")
        return 1

    mauvais = 0
    lus = 0
    for etiquette, entrees in sources:
        print("== %s" % etiquette)
        for nom, blob in entrees:
            lus += 1
            reproches = juger(nom, blob)
            if reproches:
                mauvais += 1
                print("   %-42s %s" % (nom, " ; ".join(reproches)))
                print("   ::error file=%s::%s" % (nom, " ; ".join(reproches)))
        if lus == 0:
            print("   aucune bibliotheque native lue")

    print("")
    print("%d bibliotheque(s) lue(s), %d refusee(s)" % (lus, mauvais))
    if lus == 0:
        # Une garde qui ne lit rien ne doit pas rendre un verdict de succes.
        print("AUCUNE bibliotheque lue -- le chemin est probablement faux. Refus.")
        return 1
    if mauvais:
        print("")
        print("Ces bibliotheques ne se chargeront pas : `dlopen` echoue avant que la")
        print("moindre fonction soit appelee. Un artefact qui les contient ne doit pas")
        print("etre publie. Cause connue : NDK r26c ; le r27c corrige les deux calculs.")
        return 1
    print("OK: toutes les bibliotheques sont alignees sur 16 Ko et leur RELRO tient")
    print("    dans les segments charges.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
