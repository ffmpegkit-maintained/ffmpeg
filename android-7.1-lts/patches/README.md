# Upstream patches applied after checkout

Each `<library>/` folder holds `*.patch` files applied to `src/<library>` once the
source tag is checked out. They are produced with `git format-patch` (or fetched as
`<commit>.patch` from GitHub), so `git apply` takes them as-is.

## Why this exists

Until 2026-09-24 the only lever on upstream source was the tag pin. Closing four CVEs
on the 6.0 line in July therefore meant rebasing the whole tree from `n6.0` to `n6.1.6`
— a large change to carry four small ones. And when upstream stops releasing on a
branch, as it has on 7.1.x and 6.1.x, even that lever is gone: there is no newer tag to
move to.

## Two properties the mechanism has

- **Idempotent.** CI restores `src/` from a cache, so patches are applied against trees
  that are already patched at least as often as fresh ones. Each patch is
  reverse-checked first and skipped when already present.
- **Loud.** ⚠️ A patch that neither applies nor is already applied **stops the build**.
  A security backport that quietly failed to apply would be worse than no mechanism:
  the build stays green and the fix is simply not in the artifact. That is the same
  shape as the `drawtext` bug — a library that was built, linked, and never reached
  `configure`.

## Both properties were witnessed, not assumed

Run in isolation on 2026-09-24, against a throwaway git tree:

| case | result |
|---|---|
| a patch that does not apply | `failed to apply`, exit 1, the line after the call never printed |
| the same patch applied twice | applied once, then `already applied, skipped`, exit 0, content correct |

The second run is the one that matters for CI, where `src/` comes back from a cache
already patched. The first is the one that matters for security: ⚠️ an earlier version
of this guard called `exit 1` from inside a command substitution, so it killed only the
subshell and the build carried on. It read exactly like a working guard.

## Adding one

1. Drop the `.patch` in `patches/<library>/`, numbered so the order is explicit.
2. Name it for what it does: `0001-vobsub-CVE-2026-64830.patch`.
3. Check it applies to the **pinned tag**, not to master:
   `git clone --depth 1 --branch <tag> … && git apply --check <patch>`.
4. Remove it when the pin moves to a release that already contains the fix — a patch
   that is always "already applied" is a line of noise that will one day hide a real
   failure.
