#!/usr/bin/env python3
"""Subset the bundled subtitle fallback font (subfont.ttf) for supported locales.

Audit BIN-1 (docs/perf/codebase-audit-2026-09.md): the asset ships ~6.2 MB of
"Droid Sans Fallback" unconditionally, but subtitle locales are limited to
en, de, es, fr, it, pt, ja, ko, zh. This script rebuilds the asset keeping
full glyph coverage for those locales (all Latin blocks, general punctuation,
symbol ranges, and ALL CJK/Hangul blocks) while dropping scripts no supported
locale uses (Greek, Cyrillic, Middle Eastern, Indic, Thai/Lao/Tibetan, etc.).

Policy rules, in order of precedence:
  1. A codepoint inside a KEEP_RANGES block is always kept.
  2. A codepoint inside a DROP_RANGES block is dropped.
  3. A codepoint listed in neither (unassigned gaps aside, none in practice)
     is KEPT — bias toward keeping.

The name table is kept in full (`--name-IDs=* --name-languages=*`) so libass
family-name matching ("Droid Sans Fallback") is unaffected; hinting and
default glyph-closure/layout behavior are left at fontTools defaults.

Usage:
  python tools/font/subset_subfont.py SRC_TTF OUT_TTF
      Subset SRC_TTF (the original "Droid Sans Fallback") to OUT_TTF, then
      verify the result and exit non-zero on any failure.

  python tools/font/subset_subfont.py --verify-only ORIGINAL_TTF SUBSET_TTF
      Re-run only the verification pass against an existing subset.

Requires: fontTools >= 4.50 (tested with 4.63).
"""

import argparse
import hashlib
import os
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

# Ranges are (block name, first codepoint, last codepoint), ascending.
KEEP_RANGES = [
    ("Basic Latin", 0x0000, 0x007F),
    ("Latin-1 Supplement", 0x0080, 0x00FF),
    ("Latin Extended-A", 0x0100, 0x017F),
    ("Latin Extended-B", 0x0180, 0x024F),
    ("IPA Extensions", 0x0250, 0x02AF),
    ("Spacing Modifier Letters", 0x02B0, 0x02FF),
    # Not in the audit lists but required by Latin combining sequences and
    # tiny — kept per the "bias toward keeping" rule.
    ("Combining Diacritical Marks", 0x0300, 0x036F),
    ("Hangul Jamo", 0x1100, 0x11FF),
    ("Latin Extended Additional", 0x1E00, 0x1EFF),
    ("General Punctuation", 0x2000, 0x206F),
    ("Superscripts and Subscripts", 0x2070, 0x209F),
    ("Currency Symbols", 0x20A0, 0x20CF),
    ("Combining Diacritical Marks for Symbols", 0x20D0, 0x20FF),
    ("Letterlike Symbols", 0x2100, 0x214F),
    ("Number Forms", 0x2150, 0x218F),
    # One band covering arrows, math, misc technical, enclosed alphanumerics,
    # box drawing, block elements, geometric shapes, misc symbols, dingbats.
    ("Arrows .. Dingbats (2190-27BF)", 0x2190, 0x27BF),
    ("Misc Math Symbols-A / Suppl Arrows-A", 0x27C0, 0x27FF),
    ("Suppl Arrows-B / Misc Math Symbols-B", 0x2900, 0x29FF),
    ("CJK Radicals Supplement", 0x2E80, 0x2EFF),
    # CJK-relevant; small — kept per the "bias toward keeping" rule.
    ("Kangxi Radicals", 0x2F00, 0x2FDF),
    ("Ideographic Description Characters", 0x2FF0, 0x2FFF),
    ("CJK Symbols and Punctuation", 0x3000, 0x303F),
    ("Hiragana", 0x3040, 0x309F),
    ("Katakana", 0x30A0, 0x30FF),
    ("Bopomofo", 0x3100, 0x312F),
    ("Hangul Compatibility Jamo", 0x3130, 0x318F),
    ("Kanbun", 0x3190, 0x319F),
    ("Bopomofo Extended", 0x31A0, 0x31BF),
    ("Katakana Phonetic Extensions", 0x31F0, 0x31FF),
    ("Enclosed CJK Letters and Months", 0x3200, 0x32FF),
    ("CJK Compatibility", 0x3300, 0x33FF),
    ("CJK Unified Ideographs Extension A", 0x3400, 0x4DBF),
    ("Yijing Hexagram Symbols", 0x4DC0, 0x4DFF),
    ("CJK Unified Ideographs", 0x4E00, 0x9FFF),
    ("Hangul Syllables", 0xAC00, 0xD7AF),
    ("Hangul Jamo Extended-B (and A slot)", 0xD7B0, 0xD7FF),
    ("CJK Compatibility Ideographs", 0xF900, 0xFAFF),
    ("Latin ligatures (Alphabetic Presentation Forms)", 0xFB00, 0xFB0D),
    ("Vertical Forms", 0xFE10, 0xFE1F),
    ("CJK Compatibility Forms", 0xFE30, 0xFE4F),
    ("Small Form Variants", 0xFE50, 0xFE6F),
    ("Halfwidth and Fullwidth Forms", 0xFF00, 0xFFEF),
    ("Specials (incl. U+FFFD replacement char)", 0xFFF0, 0xFFFF),
    # The only astral mappings in the original font (5 codepoints) — kept.
    ("Deseret (astral mappings of the source font)", 0x10400, 0x1044F),
]

DROP_RANGES = [
    ("Greek and Coptic", 0x0370, 0x03FF),
    ("Greek Extended", 0x1F00, 0x1FFF),
    ("Cyrillic (+ Supplement/Extended)", 0x0400, 0x052F),
    ("Armenian", 0x0530, 0x058F),
    ("Hebrew", 0x0590, 0x05FF),
    ("Arabic", 0x0600, 0x06FF),
    ("Syriac", 0x0700, 0x074F),
    ("Arabic Supplement", 0x0750, 0x077F),
    ("Thaana", 0x0780, 0x07BF),
    ("NKo / Samaritan / Mandaic / Syriac Suppl / Arabic Ext", 0x07C0, 0x08FF),
    ("Indic (Devanagari .. Sinhala)", 0x0900, 0x0DFF),
    ("Thai", 0x0E00, 0x0E7F),
    ("Lao", 0x0E80, 0x0EFF),
    ("Tibetan", 0x0F00, 0x0FFF),
    ("Myanmar", 0x1000, 0x109F),
    ("Georgian", 0x10A0, 0x10FF),
    ("Ethiopic (+ Supplement)", 0x1200, 0x139F),
    ("Cherokee", 0x13A0, 0x13FF),
    ("Canadian Aboriginal Syllabics", 0x1400, 0x167F),
    ("Ogham", 0x1680, 0x169F),
    ("Runic", 0x16A0, 0x16FF),
    ("Philippine scripts", 0x1700, 0x177F),
    ("Khmer", 0x1780, 0x17FF),
    ("Mongolian", 0x1800, 0x18AF),
    ("Canadian Aboriginal Syllabics Extended", 0x18B0, 0x18FF),
    ("Braille", 0x2800, 0x28FF),
    ("Glagolitic", 0x2C00, 0x2C5F),
    ("Coptic", 0x2C80, 0x2CFF),
    ("Tifinagh", 0x2D30, 0x2DFF),
    ("Ethiopic Extended", 0x2D80, 0x2DDF),
    # Chinese minority script, ~1.2k codepoints, not used by supported locales.
    ("Yi Syllables / Yi Radicals", 0xA000, 0xA4CF),
    ("Hebrew Presentation Forms", 0xFB1D, 0xFB4F),
    ("Arabic Presentation Forms-A", 0xFB50, 0xFDFF),
    ("Arabic Presentation Forms-B", 0xFE70, 0xFEFF),
]


def _covering_range(ranges, cp):
    for name, lo, hi in ranges:
        if lo <= cp <= hi:
            return name
    return None


def _check_ranges_disjoint():
    """KEEP wins over DROP on conflict, so assert the lists never overlap."""
    for kname, klo, khi in KEEP_RANGES:
        for dname, dlo, dhi in DROP_RANGES:
            if klo <= dhi and dlo <= khi:
                raise SystemExit(
                    f"policy error: keep '{kname}' overlaps drop '{dname}'"
                )


def _is_kept(cp):
    if _covering_range(KEEP_RANGES, cp) is not None:
        return True
    return _covering_range(DROP_RANGES, cp) is None  # unlisted -> keep


def _name_bytes(font, name_ids):
    """Raw name-record bytes keyed by (nameID, platform, encoding, language)."""
    out = {}
    for rec in font["name"].names:
        if rec.nameID in name_ids:
            key = (rec.nameID, rec.platformID, rec.platEncID, rec.langID)
            out[key] = rec.string
    return out


def _sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def do_subset(src, dst):
    _check_ranges_disjoint()
    # recalcTimestamp=False keeps head.modified (and the whole output)
    # byte-reproducible across runs; wired the same way subset.main's
    # load_font does it for the default option value.
    font = TTFont(src, recalcTimestamp=False)
    orig_cps = set(font.getBestCmap())
    keep = {cp for cp in orig_cps if _is_kept(cp)}
    dropped = orig_cps - keep

    options = subset.Options()
    # Keep the FULL name table (libass family matching depends on it),
    # including the legacy Mac-platform records (`--name-legacy`, without
    # which fontTools drops them and the family name is no longer
    # byte-identical); everything else stays at fontTools defaults (hinting
    # kept, default glyph-closure and layout behavior, no timestamp recalc).
    options.parse_opts(["--name-IDs=*", "--name-languages=*", "--name-legacy"])
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=sorted(keep))
    subsetter.subset(font)
    font.save(dst)

    print(f"subset: {len(keep)} codepoints kept, {len(dropped)} dropped (of {len(orig_cps)} mapped)")
    by_range = {}
    for cp in dropped:
        name = _covering_range(DROP_RANGES, cp) or "<unlisted — MUST NOT HAPPEN>"
        by_range[name] = by_range.get(name, 0) + 1
    for name in sorted(by_range):
        print(f"  dropped {by_range[name]:5d}  {name}")
    print(f"src sha256:  {_sha256(src)}")
    print(f"out sha256: {_sha256(dst)}")


def do_verify(original_path, subset_path):
    """Fail loudly (non-zero exit) if the subset is not policy-conformant."""
    errors = []
    # lazy=False eagerly decompiles every table — a structurally broken
    # subset fails here instead of at first render on a device.
    orig = TTFont(original_path, lazy=False)
    sub = TTFont(subset_path, lazy=False)
    orig_cps = set(orig.getBestCmap())
    sub_cps = set(sub.getBestCmap())

    # (a) Family name records (nameID 1 and 4) byte-identical to the original.
    orig_names = _name_bytes(orig, {1, 4})
    sub_names = _name_bytes(sub, {1, 4})
    if orig_names != sub_names:
        changed = {k: (orig_names.get(k), sub_names.get(k))
                   for k in set(orig_names) | set(sub_names)
                   if orig_names.get(k) != sub_names.get(k)}
        errors.append(f"name records for nameID 1/4 changed: {changed}")

    # (b) Every original-cmap codepoint the policy keeps is still mapped.
    expected = {cp for cp in orig_cps if _is_kept(cp)}
    missing = sorted(expected - sub_cps)
    if missing:
        errors.append(
            f"{len(missing)} kept codepoints missing from subset cmap, "
            f"e.g. {[hex(c) for c in missing[:10]]}"
        )

    # (c) Dropped codepoints are exactly the policy drops — anything dropped
    #     outside the declared drop ranges is an accidental drop.
    policy_drop = orig_cps - expected
    actual_dropped = orig_cps - sub_cps
    accidental = sorted(actual_dropped - policy_drop)
    if accidental:
        errors.append(
            f"{len(accidental)} codepoints dropped outside the declared drop "
            f"ranges, e.g. {[hex(c) for c in accidental[:10]]}"
        )

    # No invented mappings beyond the original cmap.
    invented = sorted(sub_cps - orig_cps)
    if invented:
        errors.append(
            f"subset cmap contains {len(invented)} codepoints absent from the "
            f"original, e.g. {[hex(c) for c in invented[:10]]}"
        )

    # (d) Sizes and glyph counts.
    orig_glyphs = orig["maxp"].numGlyphs
    sub_glyphs = sub["maxp"].numGlyphs
    orig_size = os.path.getsize(original_path)
    sub_size = os.path.getsize(subset_path)
    print("verification summary:")
    print(f"  original: {orig_size:,} bytes, {orig_glyphs:,} glyphs, {len(orig_cps):,} mapped codepoints")
    print(f"  subset:   {sub_size:,} bytes, {sub_glyphs:,} glyphs, {len(sub_cps):,} mapped codepoints")
    print(f"  reduction: {orig_size - sub_size:,} bytes ({100.0 * (orig_size - sub_size) / orig_size:.1f}%)")
    print(f"  glyphs removed: {orig_glyphs - sub_glyphs:,}")
    print(f"  codepoints dropped: {len(actual_dropped):,} (policy: {len(policy_drop):,})")
    still_mapped = sorted(policy_drop - actual_dropped)
    if still_mapped:
        print(f"  note: {len(still_mapped)} policy-dropped cp still mapped "
              f"(size concern only), e.g. {[hex(c) for c in still_mapped[:10]]}")
    print(f"  name records: original {len(orig['name'].names)}, subset {len(sub['name'].names)}")

    if errors:
        print("VERIFY FAILED:", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        return 1
    print("VERIFY OK: family names byte-identical, all kept codepoints present, drops within declared ranges")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Locale-scoped subsetter for the bundled subfont.ttf asset (audit BIN-1).")
    parser.add_argument("src", help="original (pre-subset) TTF path")
    parser.add_argument("dst", help="subset output TTF path (existing subset with --verify-only)")
    parser.add_argument("--verify-only", action="store_true",
                        help="skip subsetting; verify src (original) vs dst (subset)")
    args = parser.parse_args(argv)

    if args.verify_only:
        return do_verify(args.src, args.dst)

    do_subset(args.src, args.dst)
    rc = do_verify(args.src, args.dst)
    if rc != 0:
        os.remove(args.dst)  # never leave a failed artifact behind
        print(f"removed failed output {args.dst}", file=sys.stderr)
    return rc


if __name__ == "__main__":
    sys.exit(main())
