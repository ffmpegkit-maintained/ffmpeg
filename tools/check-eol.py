#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Can the shell scripts in this repository actually be run by bash on Linux?

A build script is executed by bash on a Linux runner. A CR at the end of every line
makes `source function.sh` die at its first `{`, and bash then reports only the
symptom -- "get_arch_name: command not found", four hundred times over -- and never
the cause.

That is not hypothetical. The 8.1 security build of 2026-09-24 failed exactly there:
the scripts had been edited from a Windows checkout with core.autocrlf=true, where the
working tree is CRLF by design, and the conversion travelled into the commit.
.gitattributes now pins *.sh to eol=lf, and this reads back what the repository
actually holds.

Reading the bytes in Python, not with grep or awk: on Windows those read in text mode
and silently strip the CR, so a guard written with them reports "clean" for a file that
is not. A guard that cannot see the defect it exists for is worse than none.

Usage:
    python3 tools/check-eol.py           # every tracked *.sh
    python3 tools/check-eol.py a.sh b.sh # just these

Exit code is 1 when a script carries CR, so CI can gate on it.
"""
import io
import subprocess
import sys


def suivis():
    """Tracked *.sh, as git records them -- the bytes the runner will check out."""
    out = subprocess.run(["git", "ls-files", "-z", "*.sh"],
                         capture_output=True, check=True).stdout
    return [n.decode() for n in out.split(b"\0") if n]


def contenu_suivi(chemin: str) -> bytes:
    """The bytes git RECORDS for this path, not the bytes on disk.

    ⚠️ This is the whole point, and the previous version got it wrong.

    The docstring above already said "this reads back what the repository actually
    holds", but the code opened the file in the working tree. On Linux CI the two are
    the same. On a Windows checkout with core.autocrlf=true the working tree is CRLF
    **by design**, so the guard reported 415 offending scripts on a repository whose
    commits are clean -- measured 2026-09-25.

    A guard that reports a defect where there is none is a guard someone switches off,
    and then it is not there on the day the defect is real. Reading the recorded blob
    gives the same verdict on every platform.
    """
    return subprocess.run(["git", "show", ":" + chemin],
                          capture_output=True, check=True).stdout


def crs(chemin: str) -> int:
    """How many lines end with CR. Binary read of the recorded blob, deliberately."""
    try:
        return contenu_suivi(chemin).count(b"\r\n")
    except subprocess.CalledProcessError:
        # Pas dans l'index (fichier nomme a la main, ou non suivi) : on lit le disque.
        with io.open(chemin, "rb") as f:
            return f.read().count(b"\r\n")


def main() -> int:
    fichiers = sys.argv[1:] or suivis()
    fautifs = []
    for f in fichiers:
        try:
            n = crs(f)
        except OSError as e:
            print("  %-60s unreadable: %s" % (f, e))
            return 1
        if n:
            fautifs.append((f, n))

    print("checked %d shell script(s)" % len(fichiers))
    if not fautifs:
        print("OK: none of them carries CR line endings.")
        return 0

    for f, n in fautifs:
        print("  %-60s %d CRLF line(s)" % (f, n))
    print("")
    print("These cannot be sourced by bash on Linux. .gitattributes pins *.sh to")
    print("eol=lf; a file that reaches a commit as CRLF was written past it. Fix with:")
    print("  git add --renormalize <file>")
    return 1


if __name__ == "__main__":
    sys.exit(main())
