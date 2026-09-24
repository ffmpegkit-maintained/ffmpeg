#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Does any vendored fftools function free a pointer it never initialised?

Issue #1 carried a second defect, reported as a tombstone:

    libavfilter.so  avfilter_inout_free
    libffmpegkit.so init_complex_filtergraph

`AVFilterInOut *inputs, *outputs, *cur;` -- no initialiser. `graph_parse()` writes to
&inputs only on success; on a parse error it returns without touching it. The `fail:`
label then freed whatever the stack happened to hold.

Why this needs a tool at all
----------------------------
⚠️ No compiler flag catches it. Measured 2026-09-24 with the NDK's own clang, on a
minimal reduction of the real function: `-Wall -Wextra` says nothing, and neither does
`-Wconditional-uninitialized`, which exists for exactly this shape. The address of the
variable is passed to a function, so clang assumes the callee initialises it. That is
why the defect survived `-Werror` builds for the life of this fork.

And it cannot be caught at runtime either, which was tried first: freeing an
uninitialised pointer is undefined behaviour, so whether it segfaults depends on what
the previous call left on the stack. On a Pixel 7 Pro, 180 failing parses of three
different shapes against the *unfixed* binary crashed nothing -- the value there
happens to be null. The reporter saw it on Android 12. A bench that cannot see the
defect returns a verdict of success for that defect, so this reads the source instead.

What it flags
-------------
Inside one function: a local pointer declared with no initialiser, that is then passed
to a free-like call at or after a `fail:`-style label. That is the whole shape, and it
is narrow on purpose -- a check that flags everything gets switched off.

Usage:
    python3 tools/check-uninit-free.py                 # every vendored fftools_*.c
    python3 tools/check-uninit-free.py <file> [...]    # just these

Exit code is 1 when something is flagged, so CI can gate on it.
"""
import io
import os
import re
import sys

LIBERATEURS = ("avfilter_inout_free", "av_freep", "av_buffer_unref",
               "avfilter_graph_free", "av_dict_free", "avformat_close_input")

# `TYPE *name, *name2;` with no `=` before the `;`. Deliberately pointer locals only:
# those are what a free-like call takes, and an uninitialised int is a different bug.
DECL = re.compile(r"^\s{2,}(?!return\b)([A-Za-z_][A-Za-z0-9_ ]*?)\s+((?:\*\s*[A-Za-z_]\w*\s*,\s*)*\*\s*[A-Za-z_]\w*)\s*;\s*$")
LABEL = re.compile(r"^(fail|end|cleanup|error)\w*\s*:\s*$")
DEBUT_FN = re.compile(r"^[A-Za-z_][\w \*]*\**\w+\s*\([^;]*\)\s*$|^\{")


def fichiers_par_defaut():
    trouves = []
    for racine, _, noms in os.walk("."):
        if ".git" in racine:
            continue
        for n in noms:
            if n.startswith("fftools_") and n.endswith(".c"):
                trouves.append(os.path.join(racine, n).replace("\\", "/"))
    return sorted(trouves)


def fonctions(lignes):
    """Cut the file into (premiere_ligne, lignes) blocks at column-0 braces."""
    bloc, debut = [], 0
    dedans = False
    for i, l in enumerate(lignes):
        if l.startswith("{"):
            dedans, bloc, debut = True, [], i
            continue
        if dedans and l.startswith("}"):
            yield debut, bloc
            dedans = False
            continue
        if dedans:
            bloc.append(l)


def examine(chemin):
    lignes = io.open(chemin, encoding="utf-8", errors="replace").read().splitlines()
    trouves = []
    for debut, bloc in fonctions(lignes):
        sans_init = {}
        for j, l in enumerate(bloc):
            m = DECL.match(l)
            if not m:
                continue
            for nom in re.findall(r"\*\s*([A-Za-z_]\w*)", m.group(2)):
                sans_init[nom] = debut + j + 2

        i_label = None
        for j, l in enumerate(bloc):
            if LABEL.match(l.strip()) and l.strip() == l.lstrip():
                i_label = j
                break
        if i_label is None or not sans_init:
            continue

        # WARNING: the property that matters is not "is it assigned somewhere" -- it
        # is *where the failure of the writing call goes*.
        #
        #   ret = graph_parse(..., &inputs, ...);
        #   if (ret < 0) goto fail;               the path that reaches the label is
        #   fail: avfilter_inout_free(&inputs);   exactly the one that wrote nothing
        #
        #   ret = unescape(&val, ...);
        #   if (ret < 0) return ret;              that failure never reaches the label,
        #   fail: av_freep(&val);                 so the pointer is always written
        #
        # Both hand a pointer's address to a callee. Only the first is a defect, and
        # only the destination of the error branch tells them apart. Checking textual
        # order instead flags the second, and a false positive is how a check gets
        # switched off -- so the rule encodes the real distinction.
        label = bloc[i_label].strip().rstrip(":")
        dangereux = {}
        for nom, ligne in sans_init.items():
            motif = r"\w+\s*\([^;]*&\s*" + re.escape(nom) + r"\b"
            for j, l in enumerate(bloc):
                if not re.search(motif, l):
                    continue
                suite = chr(10).join(bloc[j:j + 4])
                if re.search(r"goto\s+" + re.escape(label) + r"\b", suite):
                    dangereux[nom] = ligne
                    break
        sans_init = dangereux
        if not sans_init:
            continue

        for j in range(i_label, len(bloc)):
            for f in LIBERATEURS:
                for nom in re.findall(re.escape(f) + r"\(&?\s*([A-Za-z_]\w*)", bloc[j]):
                    if nom in sans_init:
                        trouves.append((sans_init[nom], nom, f, debut + j + 2))
    return trouves


def main() -> int:
    cibles = sys.argv[1:] or fichiers_par_defaut()
    total = 0
    for c in cibles:
        for ligne_decl, nom, liberateur, ligne_free in examine(c):
            total += 1
            print("%s:%d: '%s' is declared without an initialiser" % (c, ligne_decl, nom))
            print("%s:%d:   and freed here by %s() on an error path" % (c, ligne_free, liberateur))
    print("")
    print("checked %d file(s)" % len(cibles))
    if not total:
        print("OK: no pointer is freed on an error path without being initialised.")
        return 0
    print("")
    print("%d site(s). A callee that writes the pointer only on success leaves this" % total)
    print("one holding whatever the stack held, and the error path frees that.")
    print("Fix by initialising at the declaration: TYPE *p = NULL;")
    return 1


if __name__ == "__main__":
    sys.exit(main())
