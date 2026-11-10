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

# UI theme fonts (fdroid)

`fetch_ui_fonts.py` fetches the UI theme font binaries the **fdroid** flavor
bundles: that flavor must render the same typography as the store flavor with
zero Google Play services, so designsystem's androidMain carries real TTFs
that the app flavor wires in via `ThemeFontRegistry` (see
`FontFamilies.android.kt` — the GMS downloadable-fonts provider stays the
default). Destination is designsystem's `src/androidMain/res/font/` — a
single copy shared by the whole app (fdroid only installs it; the store
release ships the GMS path and strips the binaries via the resource
shrinker). The 17 desktop subset TTFs are plain byte-copies of
`src/jvmMain/composeResources/font/` (sha256-verified, done once with `cp` —
not produced by the script); the script fetches the 17 files for the 5
families that had no TTF anywhere in the repo. All 34 files cover the 10
distinct families the theme requests, at the app's weight contract (Normal
400 / Medium 500 / SemiBold 600 / Bold 700 — see `FontFamilies.jvm.kt`).

Upstream pin — google/fonts main HEAD at authoring time (2026-11-08):

```
GOOGLE_FONTS_COMMIT = 5bb28733cec313b63fbd415b574b5ae35c8aa8b8
```

Sources: DotGothic16 and Poppins ship upstream statics (direct downloads);
Manrope, Quicksand and Outfit only publish variable fonts — the script pins
the `wght` axis per weight with `fontTools.varLib.instancer`
(`updateFontNames=True`) and verifies every output is a true static (no
`fvar`, `OS/2.usWeightClass` matches, parsable, non-trivial cmap) before
keeping it. Raw downloads are cached under `tools/font/ui-original/`
(gitignored, re-obtainable via the script).

Regenerate (requires fontTools >= 4.50, tested with 4.68 — pure Python;
network needed):

```bash
python3 tools/font/fetch_ui_fonts.py
```

`--verify-only` re-runs the verification pass (no network) against the
existing files; the script exits non-zero and deletes its outputs if any
guarantee fails.

Size note — DotGothic16 is bundled in FULL (2,069,236 bytes): it is a
Japanese font (9,362 glyphs) and no UI-font subsetting infrastructure exists
(`subset_subfont.py` targets the libass subtitle asset and its name-table
requirements, not Compose resource fonts). ~2 MB for one theme-variant
display slot is accepted for the fdroid APK; the desktop build deliberately
keeps the monospace fallback instead (see `FontFamilies.jvm.kt`).

sha256 provenance of the fetched outputs (at the pinned commit):

```
dotgothic16_regular.ttf      2,069,236 bytes  3ad9af88726d42b40f7f365f0dcac785af73cf20ea6f1d5b44e57cc21150b8f1  (upstream static)
manrope_regular.ttf             97,436 bytes  2602c71d1f36c96da0022706f15da7d51102d19d10510b77b5a39bf861122807  (instanced wght=400)
manrope_medium.ttf              97,536 bytes  729037f5dc28daa949c5ce8e69d945bf14faf2649db737e6eb085c120c778dd0  (instanced wght=500)
manrope_semibold.ttf            97,584 bytes  2b2ab8c077256d2849465199657f042e36588a7b88d345acd2ab4e5807ebef99  (instanced wght=600)
manrope_bold.ttf                97,424 bytes  f9696798e2d82d52207ac00c4462bc86999d371ab87401db4067feb3def4d27b  (instanced wght=700)
quicksand_regular.ttf           78,936 bytes  795c0db0cd310f302a597daaa33b4d7a7f8c014c3be7ea53da1c9b3c8416547a  (instanced wght=400)
quicksand_medium.ttf            78,924 bytes  fb148a8489c23a224cb90c3291903e0c24e8dcc369f13116c95f13949d1c36af  (instanced wght=500)
quicksand_semibold.ttf          78,808 bytes  6d600a4d0c0798c4233f7a024171e2e8640eb7e3b106931ae0bfe4d9d4b43fe9  (instanced wght=600)
quicksand_bold.ttf              78,528 bytes  31ece1fb39f8798c22301ae939ec0596aa9400fa9109df444512c33c1bf603e8  (instanced wght=700)
poppins_regular.ttf            160,316 bytes  7e65201e9b79159e2300267cc885e16c8dcef2424cdfa09a29bfb0980a94a7ba  (upstream static)
poppins_medium.ttf             158,576 bytes  90373e7d838d32468438fc3e152dca0bdb12edcab99ea639f158790b1ba1fd05  (upstream static)
poppins_semibold.ttf           157,312 bytes  d3bf1bdaf0550e83da9ac0b1d1d9fe6db086835a83aa28578e609a394b9a0286  (upstream static)
poppins_bold.ttf               155,996 bytes  983676516167748b74de6f4771fb384c664fd913acb8b471122ecacf5da5ea6c  (upstream static)
outfit_regular.ttf              55,208 bytes  4759accaac579123f050e169f46cbbcd6b8b82faf9e4bbd56a3488ca647842a5  (instanced wght=400)
outfit_medium.ttf               55,148 bytes  68d0245451db7034854a1a2d69d6ec94e72fbc65e35f8a527177e153cd626188  (instanced wght=500)
outfit_semibold.ttf             55,400 bytes  69909d169ccbc530d4719c1574d50b2e023f68f678f7e4294fac76e7ce3d37e2  (instanced wght=600)
outfit_bold.ttf                 55,260 bytes  9b6c93be941b849d9861914326314577a19f5d85498cc20ce48dcbd5fa03b48f  (instanced wght=700)
```

License texts for the fetched families (all SIL OFL 1.1) are fetched by the
same script into `shared/core/designsystem/licenses/fonts/`
(`{DotGothic16,Manrope,Quicksand,Poppins,Outfit}-OFL.txt`).
