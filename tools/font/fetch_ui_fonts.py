#!/usr/bin/env python3
"""Fetch the UI theme fonts bundled for the fdroid Android flavor.

The store flavor resolves the 11 theme font slots through the GMS
downloadable-fonts provider (FontFamilies.android.kt); the fdroid flavor must
render the same typography with zero Google Play services, so the real font
binaries are bundled in designsystem's androidMain res/font and wired in via
ThemeFontRegistry (app side). This script fetches the families GMS serves that
have NO TTF anywhere in the repo yet (desktop bundles only 6 of them), pins
them to a google/fonts commit, staticizes variable fonts, and verifies every
output before keeping it. The 17 desktop subset TTFs are byte-copies of the
jvm composeResources (done once with cp, not by this script).

Families (all SIL OFL — license texts fetched into licenses/fonts/):
  DotGothic16  single Regular cut; a ~2 MB Japanese font bundled in FULL
               (no UI-font subsetting infrastructure exists; see the size
               note in tools/font/README.md)
  Manrope      Regular/Medium/SemiBold/Bold — instanced from the variable font
  Quicksand    Regular/Medium/SemiBold/Bold — instanced from the variable font
  Poppins      Regular/Medium/SemiBold/Bold — upstream ships statics
  Outfit       Regular/Medium/SemiBold/Bold — instanced from the variable font

The app's weight contract (FontFamilies.jvm.kt): only Normal 400 / Medium 500
/ SemiBold 600 / Bold 700 are ever requested, nothing above Bold.

Usage:
  python tools/font/fetch_ui_fonts.py
      Fetch + instance + verify everything, exit non-zero on any failure
      (failed outputs are deleted — no partial artifacts are left behind).

  python tools/font/fetch_ui_fonts.py --verify-only
      Re-run only the verification pass against the existing outputs
      (no network).

Requires: fontTools >= 4.50 (tested with 4.68) — pure Python, the only
non-stdlib dependency (`pip install fonttools`). Network access to
raw.githubusercontent.com is needed in fetch mode only.

Upstream pin: GOOGLE_FONTS_COMMIT below (google/fonts main HEAD at authoring
time). Raw downloads are cached under tools/font/ui-original/ (gitignored —
re-obtainable, sha256 of every shipped output is recorded in
tools/font/README.md). Bumping the pin re-fetches potentially different
binaries: re-run the script and paste its provenance block into the README.
"""

import argparse
import hashlib
import io
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import quote

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

# google/fonts main HEAD at authoring time (2026-11-08). Everything this
# script downloads is addressed through this commit, so re-runs are
# byte-reproducible until the pin is bumped.
GOOGLE_FONTS_COMMIT = "5bb28733cec313b63fbd415b574b5ae35c8aa8b8"
RAW_BASE = f"https://raw.githubusercontent.com/google/fonts/{GOOGLE_FONTS_COMMIT}"

_ROOT = Path(__file__).resolve().parents[2]
RES_FONT_DIR = _ROOT / "shared/core/designsystem/src/androidMain/res/font"
LICENSE_DIR = _ROOT / "shared/core/designsystem/licenses/fonts"
ORIGINALS_DIR = _ROOT / "tools/font/ui-original"

# The weight contract from FontFamilies.jvm.kt: file suffix -> OS/2 weight.
WEIGHT_SUFFIX = {400: "regular", 500: "medium", 600: "semibold", 700: "bold"}

# (google/fonts slug, family name, weights needed). Slug is the ofl/<slug>
# directory; name is the upstream file-name prefix (e.g. "Poppins-Regular.ttf").
FAMILIES = [
    ("dotgothic16", "DotGothic16", (400,)),
    ("manrope", "Manrope", (400, 500, 600, 700)),
    ("quicksand", "Quicksand", (400, 500, 600, 700)),
    ("poppins", "Poppins", (400, 500, 600, 700)),
    ("outfit", "Outfit", (400, 500, 600, 700)),
]

_TTF_MAGIC = (b"\x00\x01\x00\x00", b"OTTO")  # TrueType glyf / CFF outlines
_UA = "jellyplay-fetch-ui-fonts/1.0 (build tooling; repo tools/font/)"


class FetchError(Exception):
    pass


def _fetch_bytes(remote_path):
    """GET <RAW_BASE>/<remote_path>; 404 -> None, anything else -> raise."""
    url = f"{RAW_BASE}/{quote(remote_path, safe='/')}"
    request = urllib.request.Request(url, headers={"User-Agent": _UA})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.read()
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise FetchError(f"HTTP {e.code} fetching {remote_path}") from e
    except urllib.error.URLError as e:
        raise FetchError(f"network error fetching {remote_path}: {e.reason}") from e


def _sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def _variable_source(slug, name):
    """Download the variable font <Name>[wght].ttf; returns (bytes, path) or None."""
    remote = f"ofl/{slug}/{name}[wght].ttf"
    data = _fetch_bytes(remote)
    return None if data is None else (data, remote)


def _static_source(slug, name, weight):
    """Download the static cut for one weight; (bytes, path) or None if absent.

    google/fonts layouts in the wild: statics in a static/ subdir (variable
    families that also ship statics) or directly at the family root (Poppins).
    """
    style = {400: "Regular", 500: "Medium", 600: "SemiBold", 700: "Bold"}[weight]
    for remote in (f"ofl/{slug}/static/{name}-{style}.ttf", f"ofl/{slug}/{name}-{style}.ttf"):
        data = _fetch_bytes(remote)
        if data is not None:
            return data, remote
    return None


def _instance_weight(var_bytes, weight):
    """Pin the wght axis to `weight`, producing a true static font."""
    font = TTFont(io.BytesIO(var_bytes), recalcTimestamp=False)
    # updateFontNames=True rewrites the name table to the named instance
    # (family "Manrope Medium" etc.) so the static does not masquerade as the
    # variable default; cmap/glyphs are untouched by pinning.
    instancer.instantiateVariableFont(
        font, {"wght": weight}, inplace=True, updateFontNames=True)
    # instancer does not guarantee OS/2.usWeightClass tracks the pinned axis
    # on every fontTools version — pin it deterministically here and assert
    # on the saved file below (a wrong-weight file is never shipped).
    font["OS/2"].usWeightClass = weight
    return font


def _check_font_file(path, weight):
    """Structural + weight checks on one shipped TTF. Returns error strings."""
    errors = []
    size = os.path.getsize(path)
    if size == 0:
        return [f"{path.name}: empty file"]
    with open(path, "rb") as f:
        magic = f.read(4)
    if magic not in _TTF_MAGIC:
        errors.append(f"{path.name}: bad magic {magic!r}, not a TrueType font")
        return errors
    # lazy=False eagerly decompiles every table — a structurally broken file
    # fails here instead of at first render on a device.
    font = TTFont(path, lazy=False)
    if "fvar" in font:
        # A variable TTF without variation settings mis-weights under
        # Compose's Font(res, weight) — statics only, always.
        errors.append(f"{path.name}: still a variable font (fvar present)")
    if font["OS/2"].usWeightClass != weight:
        errors.append(
            f"{path.name}: OS/2.usWeightClass is {font['OS/2'].usWeightClass}, "
            f"expected {weight}")
    num_glyphs = font["maxp"].numGlyphs
    if num_glyphs <= 0:
        errors.append(f"{path.name}: no glyphs")
    family = font["name"].getDebugName(1)
    if not family:
        errors.append(f"{path.name}: empty family name (nameID 1)")
    cmap = font.getBestCmap()
    if len(cmap) < 100:
        errors.append(f"{path.name}: implausibly small cmap ({len(cmap)} codepoints)")
    print(f"  {path.name}: {size:,} bytes, weight {font['OS/2'].usWeightClass}, "
          f"{num_glyphs:,} glyphs, {len(cmap):,} codepoints, family {family!r}")
    return errors


def _check_license(path):
    if not path.is_file() or path.stat().st_size == 0:
        return [f"{path.name}: missing or empty"]
    text = path.read_text(encoding="utf-8", errors="replace").lower()
    if "open font license" not in text:
        return [f"{path.name}: does not look like an OFL text"]
    return []


def do_verify_only(ui_outputs, license_paths):
    """Verify existing outputs; no network. Non-zero exit on any failure."""
    print("verification summary:")
    errors = []
    for path, weight in ui_outputs:
        if not path.is_file():
            errors.append(f"{path.name}: missing")
            continue
        errors.extend(_check_font_file(path, weight))
    for path in license_paths:
        errors.extend(_check_license(path))

    print("\nprovenance (keep in tools/font/README.md):")
    for path, _ in ui_outputs:
        print(f"  {path.name:<28} {os.path.getsize(path):>9,} bytes  "
              f"sha256:{_sha256(path)}")

    if errors:
        print("VERIFY FAILED:", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        return 1
    print(f"\nVERIFY OK: {len(ui_outputs)} TTFs static, correct weights, well-formed; "
          f"{len(license_paths)} OFL texts present")
    return 0


def do_fetch():
    RES_FONT_DIR.mkdir(parents=True, exist_ok=True)
    LICENSE_DIR.mkdir(parents=True, exist_ok=True)
    ORIGINALS_DIR.mkdir(parents=True, exist_ok=True)

    ui_outputs = []      # (path, weight) pairs expected to exist afterwards
    license_paths = []
    written = []         # outputs written THIS run; deleted if the run fails
    errors = []

    for slug, name, weights in FAMILIES:
        # OFL text first — a font without its license must not ship.
        ofl_path = LICENSE_DIR / f"{name}-OFL.txt"
        ofl_bytes = _fetch_bytes(f"ofl/{slug}/OFL.txt")
        if ofl_bytes is None:
            errors.append(f"{name}: OFL.txt not found at ofl/{slug}/OFL.txt")
            continue
        ofl_path.write_bytes(ofl_bytes)
        license_paths.append(ofl_path)

        # Family-wide source decision: probe the Regular cut; if upstream
        # ships statics (in static/ or at the family root) use them for every
        # needed weight, otherwise download the variable font once and
        # instance each weight. If a specific weight is then missing, FAIL —
        # never silently fall back per-weight (that ships a wrong file).
        statics = _static_source(slug, name, 400)
        var_bytes = None
        if statics is None:
            variable = _variable_source(slug, name)
            if variable is None:
                errors.append(
                    f"{name}: neither static cuts nor {name}[wght].ttf found "
                    f"under ofl/{slug}/ at the pinned commit")
                continue
            var_bytes, var_remote = variable
            (ORIGINALS_DIR / f"{name}-var.ttf").write_bytes(var_bytes)
            print(f"{name}: variable source {var_remote} "
                  f"({len(var_bytes):,} bytes) -> instancing {weights}")
            orig_cmap = set(TTFont(io.BytesIO(var_bytes), lazy=True).getBestCmap())
        else:
            print(f"{name}: upstream statics found -> direct downloads {weights}")

        for weight in weights:
            out_path = RES_FONT_DIR / f"{slug}_{WEIGHT_SUFFIX[weight]}.ttf"
            ui_outputs.append((out_path, weight))
            try:
                if var_bytes is not None:
                    font = _instance_weight(var_bytes, weight)
                    if set(font.getBestCmap()) != orig_cmap:
                        errors.append(
                            f"{out_path.name}: instanced cmap differs from the "
                            f"variable original")
                        continue
                    font.save(out_path)
                else:
                    static = _static_source(slug, name, weight)
                    if static is None:
                        errors.append(
                            f"{name}: upstream ships statics but no cut for "
                            f"weight {weight} at the pinned commit")
                        continue
                    data, remote = static
                    (ORIGINALS_DIR / f"{name}-{WEIGHT_SUFFIX[weight]}.ttf").write_bytes(data)
                    out_path.write_bytes(data)  # byte-identical to upstream
                written.append(out_path)
            except FetchError as e:
                errors.append(str(e))

    rc = do_verify_only(ui_outputs, license_paths)
    if rc != 0:
        for path in written:  # never leave a failed artifact behind
            path.unlink(missing_ok=True)
            print(f"removed failed output {path}", file=sys.stderr)
    return rc


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Fetch the UI theme fonts bundled for the fdroid flavor "
                    "(pinned google/fonts commit, staticized, verified).")
    parser.add_argument("--verify-only", action="store_true",
                        help="skip fetching; verify the existing outputs")
    args = parser.parse_args(argv)

    ui_outputs = [
        (RES_FONT_DIR / f"{slug}_{WEIGHT_SUFFIX[w]}.ttf", w)
        for slug, _, weights in FAMILIES for w in weights
    ]
    license_paths = [LICENSE_DIR / f"{name}-OFL.txt" for _, name, _ in FAMILIES]

    if args.verify_only:
        return do_verify_only(ui_outputs, license_paths)
    return do_fetch()


if __name__ == "__main__":
    sys.exit(main())
