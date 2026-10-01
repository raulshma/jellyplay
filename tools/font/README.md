# Font subsetting (BIN-1)

`subset_subfont.py` shrinks the bundled subtitle fallback font
`shared/feature/player-video/src/androidMain/assets/subfont.ttf` ("Droid Sans
Fallback", 6,365,592 bytes as shipped) to the codepoint coverage the supported
subtitle locales (en, de, es, fr, it, pt, ja, ko, zh) can actually use. Audit
finding BIN-1 (docs/perf/codebase-audit-2026-09.md): the asset ships in every
APK unconditionally, so script coverage nobody renders is pure binary size.
The keep/drop policy lives in the script as explicit, auditable range lists
(`KEEP_RANGES` / `DROP_RANGES`): everything Latin (Basic Latin through Latin
Extended Additional, IPA, ligatures), punctuation/symbol ranges, and ALL
CJK-relevant blocks (radicals, kana, bopomofo, Hangul jamo + syllables, CJK
Unified + Ext A, compatibility and halfwidth/fullwidth forms) is kept; scripts
no supported locale uses (Greek, Cyrillic, Hebrew, Arabic, Indic, Thai, Lao,
Tibetan, etc.) is dropped; anything in neither list is kept — bias toward
keeping. The full name table and hinting are preserved so libass
family-name matching and rasterization are unaffected.

Regenerate (from a copy of the original, pre-subset font):

```bash
python tools/font/subset_subfont.py ORIGINAL_SUBFONT_TTF \
  shared/feature/player-video/src/androidMain/assets/subfont.ttf
```

The script verifies its own output and exits non-zero if any guarantee fails:
(a) nameID 1/4 records byte-identical to the original, (b) every kept
original-cmap codepoint still mapped, (c) dropped codepoints exactly match the
declared drop ranges, (d) sizes/glyph counts reported. `--verify-only` re-runs
just the verification against an existing pair.

Provenance — original font sha256 (pre-subset "Droid Sans Fallback"):

```
75048bc128cdaf5f840814717aef634c001a1e8613c3298415d295e30e132ca2
```
