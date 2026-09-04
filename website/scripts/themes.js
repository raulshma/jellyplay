/**
 * JellyPlay website theming — 1:1 port of the app's design system
 * (shared/core/designsystem/.../theme/{Theme,ColorGenerator,*Theme}.kt).
 *
 * Owns: color scheme resolution for all 8 theme variants, light/dark/OLED,
 * contrast levels, accent seeds, per-variant fonts/shapes/borders/gradients.
 * Writes Material 3 role values as CSS custom properties on <html>.
 */
(function () {
  'use strict';

  // ─────────────────────────────────────────────────────────────
  // Color math (ColorGenerator.kt port — algorithm-identical)
  // ─────────────────────────────────────────────────────────────

  function colorToHsl(hex) {
    const r = ((hex >> 16) & 0xff) / 255;
    const g = ((hex >> 8) & 0xff) / 255;
    const b = (hex & 0xff) / 255;
    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    if (max === min) return [0, 0, max];
    const l = (max + min) / 2;
    const d = max - min;
    const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    let h;
    if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) * 60;
    else if (max === g) h = ((b - r) / d + 2) * 60;
    else h = ((r - g) / d + 4) * 60;
    if (h >= 360) h -= 360;
    return [Math.min(Math.max(h, 0), 360), Math.min(Math.max(s, 0), 1), Math.min(Math.max(l, 0), 1)];
  }

  function hslToHex(h, s, l) {
    const c = (1 - Math.abs(2 * l - 1)) * s;
    const m = l - 0.5 * c;
    const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
    const seg = Math.floor(h / 60); // h in [0,360) → seg 0..5
    let r = 0, g = 0, b = 0;
    if (seg === 0) { r = c; g = x; }
    else if (seg === 1) { r = x; g = c; }
    else if (seg === 2) { g = c; b = x; }
    else if (seg === 3) { g = x; b = c; }
    else if (seg === 4) { r = x; b = c; }
    else { r = c; b = x; }
    const to255 = (v) => Math.round((v + m) * 255);
    return (to255(r) << 16) | (to255(g) << 8) | to255(b);
  }

  function linearizeSrgb(component) {
    return component < 0.03928 ? component / 12.92 : Math.pow((component + 0.055) / 1.055, 2.4);
  }

  function calculateLuminance(hex) {
    const r = linearizeSrgb(((hex >> 16) & 0xff) / 255);
    const g = linearizeSrgb(((hex >> 8) & 0xff) / 255);
    const b = linearizeSrgb((hex & 0xff) / 255);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  function bestContrast(hex) {
    return calculateLuminance(hex) > 0.5 ? 0x000000 : 0xffffff;
  }

  // Composites an alpha color over an opaque base (for Compose Color.copy(alpha)
  // roles rendered on the web, where CSS needs a concrete hex).
  function composite(fgHex, alpha, bgHex) {
    const fr = (fgHex >> 16) & 0xff, fg = (fgHex >> 8) & 0xff, fb = fgHex & 0xff;
    const br = (bgHex >> 16) & 0xff, bg = (bgHex >> 8) & 0xff, bb = bgHex & 0xff;
    const mix = (f, b) => Math.round(f * alpha + b * (1 - alpha));
    return (mix(fr, br) << 16) | (mix(fg, bg) << 8) | mix(fb, bb);
  }

  const CSS_STYLES = { TONAL_SPOT: 0, VIBRANT: 1, EXPRESSIVE: 2, MUTED: 3, MONOCHROME: 4 };

  /**
   * Port of ColorGenerator.generateColorScheme — produces the full role set
   * from a seed color, matching the app's HSL tones exactly.
   */
  function generateColorScheme(seedColor, style, darkTheme, oledMode, contrastLevel) {
    const [hue, sat] = colorToHsl(seedColor);
    const cl = contrastLevel || 'default';

    const primary = (() => {
      let s;
      if (style === CSS_STYLES.MONOCHROME) s = 0;
      else if (style === CSS_STYLES.MUTED) s = Math.max(sat * 0.5, 0.15);
      else if (style === CSS_STYLES.VIBRANT || style === CSS_STYLES.EXPRESSIVE) s = Math.min(sat * 1.2, 1);
      else s = sat;
      let l = darkTheme ? 0.8 : 0.4;
      if (cl === 'medium') l = darkTheme ? 0.85 : 0.35;
      else if (cl === 'high') l = darkTheme ? 0.9 : 0.25;
      const h = style === CSS_STYLES.EXPRESSIVE ? (hue + 240) % 360 : hue;
      return hslToHex(h, s, l);
    })();

    const primaryContainer = (() => {
      let s;
      if (style === CSS_STYLES.MONOCHROME) s = 0;
      else if (style === CSS_STYLES.MUTED) s = sat * 0.4;
      else if (style === CSS_STYLES.VIBRANT || style === CSS_STYLES.EXPRESSIVE) s = sat * 1.1;
      else s = sat * 0.8;
      const l = darkTheme ? 0.3 : 0.9;
      const h = style === CSS_STYLES.EXPRESSIVE ? (hue + 240) % 360 : hue;
      return hslToHex(h, s, l);
    })();

    const secondary = (() => {
      let s;
      if (style === CSS_STYLES.MONOCHROME) s = 0;
      else if (style === CSS_STYLES.MUTED) s = sat * 0.2;
      else if (style === CSS_STYLES.VIBRANT) s = sat * 0.6;
      else if (style === CSS_STYLES.EXPRESSIVE) s = sat * 0.8;
      else s = sat * 0.3;
      return hslToHex(hue, s, darkTheme ? 0.7 : 0.5);
    })();

    const secondaryContainer = (() => {
      let s;
      if (style === CSS_STYLES.MONOCHROME) s = 0;
      else if (style === CSS_STYLES.MUTED) s = sat * 0.15;
      else if (style === CSS_STYLES.VIBRANT) s = sat * 0.5;
      else if (style === CSS_STYLES.EXPRESSIVE) s = sat * 0.7;
      else s = sat * 0.2;
      return hslToHex(hue, s, darkTheme ? 0.22 : 0.92);
    })();

    const tertiaryOffset = style === CSS_STYLES.MUTED ? 30 : (style === CSS_STYLES.VIBRANT || style === CSS_STYLES.EXPRESSIVE) ? 120 : 60;

    const tertiary = (() => {
      let s;
      if (style === CSS_STYLES.MONOCHROME) s = 0;
      else if (style === CSS_STYLES.MUTED) s = sat * 0.3;
      else if (style === CSS_STYLES.VIBRANT || style === CSS_STYLES.EXPRESSIVE) s = Math.min(sat * 1.1, 1);
      else s = sat * 0.5;
      return hslToHex((hue + tertiaryOffset) % 360, s, darkTheme ? 0.8 : 0.4);
    })();

    const tertiaryContainer = (() => {
      let s;
      if (style === CSS_STYLES.MONOCHROME) s = 0;
      else if (style === CSS_STYLES.MUTED) s = sat * 0.2;
      else if (style === CSS_STYLES.VIBRANT || style === CSS_STYLES.EXPRESSIVE) s = sat * 0.9;
      else s = sat * 0.4;
      return hslToHex((hue + tertiaryOffset) % 360, s, darkTheme ? 0.3 : 0.9);
    })();

    // OLED background is pure black; otherwise a near-unsaturated tint of the seed hue.
    const background = (darkTheme && oledMode)
      ? 0x000000
      : hslToHex(hue, style === CSS_STYLES.MONOCHROME ? 0 : Math.min(sat * 0.05, 0.04), darkTheme ? 0.08 : 0.98);

    const surfaceVariant = hslToHex(hue, sat * 0.1, darkTheme ? 0.22 : 0.9);

    const onSurface = (() => {
      if (darkTheme) return cl === 'high' ? 0xffffff : cl === 'medium' ? 0xf5eff4 : 0xe6e1e5;
      return cl === 'high' ? 0x000000 : cl === 'medium' ? 0x111014 : 0x1c1b1f;
    })();

    const onSurfaceVariant = (() => {
      if (darkTheme) return cl === 'high' ? 0xffffff : cl === 'medium' ? 0xddd6dc : 0xcac4d0;
      return cl === 'high' ? 0x2b292f : cl === 'medium' ? 0x3a373d : 0x49454f;
    })();

    const outline = (() => {
      if (darkTheme) return cl === 'high' ? 0xe6e0e5 : cl === 'medium' ? 0xb5afb6 : 0x938f99;
      return cl === 'high' ? 0x3d3a40 : cl === 'medium' ? 0x5c5860 : 0x79747e;
    })();

    let surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
      surfaceContainerHigh, surfaceContainerHighest;
    if (darkTheme) {
      if (oledMode) {
        surfaceContainerLowest = 0x000000; surfaceContainerLow = 0x0a0a0a;
        surfaceContainer = 0x111111; surfaceContainerHigh = 0x1a1a1a; surfaceContainerHighest = 0x222222;
      } else {
        surfaceContainerLowest = 0x0f0e11; surfaceContainerLow = 0x1d1b20;
        surfaceContainer = 0x211f26; surfaceContainerHigh = 0x2b2930; surfaceContainerHighest = 0x36343b;
      }
    } else {
      surfaceContainerLowest = 0xffffff; surfaceContainerLow = 0xf7f2fa;
      surfaceContainer = 0xf3edf7; surfaceContainerHigh = 0xece6f0; surfaceContainerHighest = 0xe6e0e9;
    }

    return {
      primary,
      onPrimary: bestContrast(primary),
      primaryContainer,
      onPrimaryContainer: bestContrast(primaryContainer),
      secondary,
      onSecondary: bestContrast(secondary),
      secondaryContainer,
      onSecondaryContainer: bestContrast(secondaryContainer),
      tertiary,
      onTertiary: bestContrast(tertiary),
      tertiaryContainer,
      onTertiaryContainer: bestContrast(tertiaryContainer),
      background,
      onBackground: onSurface,
      surface: background,
      onSurface,
      surfaceVariant,
      onSurfaceVariant,
      outline,
      outlineVariant: darkTheme ? 0x49454f : 0xcac4d0,
      error: darkTheme ? 0xf2b8b5 : 0xb3261e,
      onError: darkTheme ? 0x601410 : 0xffffff,
      errorContainer: darkTheme ? 0x8c1d18 : 0xf9dedc,
      onErrorContainer: darkTheme ? 0xf9dedc : 0x410e0b,
      surfaceContainerLowest,
      surfaceContainerLow,
      surfaceContainer,
      surfaceContainerHigh,
      surfaceContainerHighest,
    };
  }

  /** withOledSurfaces() port (Theme.kt). */
  function withOledSurfaces(scheme) {
    return Object.assign({}, scheme, {
      background: 0x000000,
      surface: 0x000000,
      surfaceContainerLowest: 0x000000,
      surfaceContainerLow: 0x0a0a0a,
      surfaceContainer: 0x111111,
      surfaceContainerHigh: 0x1a1a1a,
      surfaceContainerHighest: 0x222222,
    });
  }

  // ─────────────────────────────────────────────────────────────
  // Static palettes (Color.kt) — Standard brand + contrast ramps
  // ─────────────────────────────────────────────────────────────

  const BRAND_LIGHT = {
    primary: 0x904b3e, onPrimary: 0xffffff, primaryContainer: 0xffdad3, onPrimaryContainer: 0x733429,
    secondary: 0x775650, onSecondary: 0xffffff, secondaryContainer: 0xffdad3, onSecondaryContainer: 0x5d3f3a,
    tertiary: 0x6f5c2e, onTertiary: 0xffffff, tertiaryContainer: 0xfae0a6, onTertiaryContainer: 0x554519,
    error: 0xba1a1a, onError: 0xffffff, errorContainer: 0xffdad6, onErrorContainer: 0x93000a,
    background: 0xfff8f6, onBackground: 0x231918, surface: 0xfff8f6, onSurface: 0x231918,
    surfaceVariant: 0xf5ddd9, onSurfaceVariant: 0x534340,
    outline: 0x857370, outlineVariant: 0xd8c2be,
    surfaceContainerLowest: 0xffffff, surfaceContainerLow: 0xfff0ee, surfaceContainer: 0xfceae6,
    surfaceContainerHigh: 0xf7e4e1, surfaceContainerHighest: 0xf1dfdb,
  };

  const BRAND_DARK = {
    primary: 0xffb4a6, onPrimary: 0x561e15, primaryContainer: 0x733429, onPrimaryContainer: 0xffdad3,
    secondary: 0xe7bdb5, onSecondary: 0x442a24, secondaryContainer: 0x5d3f3a, onSecondaryContainer: 0xffdad3,
    tertiary: 0xddc48c, onTertiary: 0x3d2e04, tertiaryContainer: 0x554519, onTertiaryContainer: 0xfae0a6,
    error: 0xffb4ab, onError: 0x690005, errorContainer: 0x93000a, onErrorContainer: 0xffdad6,
    background: 0x1a1110, onBackground: 0xf1dfdb, surface: 0x1a1110, onSurface: 0xf1dfdb,
    surfaceVariant: 0x534340, onSurfaceVariant: 0xd8c2be,
    outline: 0xa08c89, outlineVariant: 0x534340,
    surfaceContainerLowest: 0x140c0b, surfaceContainerLow: 0x231918, surfaceContainer: 0x271d1c,
    surfaceContainerHigh: 0x322826, surfaceContainerHighest: 0x3d3230,
  };

  const BRAND_MEDIUM_LIGHT = Object.assign({}, BRAND_LIGHT, {
    primary: 0x5e241a, primaryContainer: 0xa1594b, onPrimaryContainer: 0xffffff,
    secondary: 0x4b2f2a, secondaryContainer: 0x87655e, onSecondaryContainer: 0xffffff,
    tertiary: 0x433409, tertiaryContainer: 0x7e6b3b, onTertiaryContainer: 0xffffff,
    error: 0x740006, errorContainer: 0xcf2c27, onErrorContainer: 0xffffff,
    onSurface: 0x180f0d, onBackground: 0x180f0d, onSurfaceVariant: 0x413330,
    outline: 0x5f4f4c, outlineVariant: 0x7b6966,
    surfaceContainer: 0xf7e4e1, surfaceContainerHigh: 0xebd9d6, surfaceContainerHighest: 0xdfcecb,
  });

  const BRAND_MEDIUM_DARK = Object.assign({}, BRAND_DARK, {
    primary: 0xffd2ca, onPrimary: 0x48140b, primaryContainer: 0xcc7b6c, onPrimaryContainer: 0x000000,
    secondary: 0xfed2ca, onSecondary: 0x381f1a, secondaryContainer: 0xae8881, onSecondaryContainer: 0x000000,
    tertiary: 0xf3daa0, onTertiary: 0x312400, tertiaryContainer: 0xa48e5b, onTertiaryContainer: 0x000000,
    error: 0xffd2cc, onError: 0x540003, errorContainer: 0xff5449, onErrorContainer: 0x000000,
    onSurface: 0xffffff, onBackground: 0xffffff, onSurfaceVariant: 0xeed7d3,
    outline: 0xc2ada9, outlineVariant: 0xa08c88,
    surfaceContainerLowest: 0x0d0605, surfaceContainerLow: 0x251b1a, surfaceContainer: 0x302624,
    surfaceContainerHigh: 0x3b302e, surfaceContainerHighest: 0x463b39,
  });

  const BRAND_HIGH_LIGHT = Object.assign({}, BRAND_LIGHT, {
    primary: 0x511a11, primaryContainer: 0x76362b, onPrimaryContainer: 0xffffff,
    secondary: 0x3f2520, secondaryContainer: 0x60423c, onSecondaryContainer: 0xffffff,
    tertiary: 0x382a02, tertiaryContainer: 0x58471b, onTertiaryContainer: 0xffffff,
    error: 0x600004, errorContainer: 0x98000a, onErrorContainer: 0xffffff,
    onSurface: 0x000000, onBackground: 0x000000, onSurfaceVariant: 0x000000,
    outline: 0x372926, outlineVariant: 0x554643,
    surfaceContainerLow: 0xffede9, surfaceContainer: 0xf1dfdb,
    surfaceContainerHigh: 0xe2d1cd, surfaceContainerHighest: 0xd4c3c0,
  });

  const BRAND_HIGH_DARK = Object.assign({}, BRAND_DARK, {
    primary: 0xffece8, onPrimary: 0x000000, primaryContainer: 0xffae9f, onPrimaryContainer: 0x210100,
    secondary: 0xffece8, onSecondary: 0x000000, secondaryContainer: 0xe3b9b1, onSecondaryContainer: 0x190604,
    tertiary: 0xffeecd, onTertiary: 0x000000, tertiaryContainer: 0xd8c089, onTertiaryContainer: 0x110a00,
    error: 0xffece9, onError: 0x000000, errorContainer: 0xffaea4, onErrorContainer: 0x220001,
    onSurface: 0xffffff, onBackground: 0xffffff, onSurfaceVariant: 0xffffff,
    outline: 0xffece8, outlineVariant: 0xd4beba,
    surfaceContainerLowest: 0x000000, surfaceContainerLow: 0x271d1c, surfaceContainer: 0x392e2c,
    surfaceContainerHigh: 0x443937, surfaceContainerHighest: 0x504442,
  });

  // ─────────────────────────────────────────────────────────────
  // Hand-authored variant schemes (Theme.kt)
  // ─────────────────────────────────────────────────────────────

  const SYNTHWAVE_ACCENTS = {
    primary: { magenta: 0xff007f, cyan: 0x00f0ff, violet: 0x9d00ff, orange: 0xff5e00 },
    secondary: { magenta: 0x00f0ff, cyan: 0xff007f, violet: 0xff007f, orange: 0x00f0ff },
    tertiary: { magenta: 0xffe600, cyan: 0x9d00ff, violet: 0x00f0ff, orange: 0xff007f },
  };

  function getSynthwaveColorScheme(accent) {
    const primary = SYNTHWAVE_ACCENTS.primary[accent] || SYNTHWAVE_ACCENTS.primary.magenta;
    const secondary = SYNTHWAVE_ACCENTS.secondary[accent] || SYNTHWAVE_ACCENTS.secondary.magenta;
    const tertiary = SYNTHWAVE_ACCENTS.tertiary[accent] || SYNTHWAVE_ACCENTS.tertiary.magenta;
    const bg = 0x0c061a;
    return {
      primary, onPrimary: 0x0c061a,
      primaryContainer: composite(primary, 0.2, bg), onPrimaryContainer: primary,
      secondary, onSecondary: 0x0c061a,
      secondaryContainer: composite(secondary, 0.2, bg), onSecondaryContainer: secondary,
      tertiary, onTertiary: 0x0c061a,
      tertiaryContainer: composite(tertiary, 0.2, bg), onTertiaryContainer: tertiary,
      error: 0xf2b8b5, onError: 0x601410, errorContainer: 0x8c1d18, onErrorContainer: 0xf9dedc,
      background: bg, onBackground: 0xf5eefc,
      surface: 0x120926, onSurface: 0xf5eefc,
      surfaceVariant: 0x241542, onSurfaceVariant: 0xd8c8f0,
      outline: primary, outlineVariant: 0x462c75,
      surfaceContainerLowest: 0x06030d, surfaceContainerLow: 0x0f0720, surfaceContainer: 0x160c2d,
      surfaceContainerHigh: 0x20123e, surfaceContainerHighest: 0x2b1952,
    };
  }

  const SOOTHING_DARK_ACCENTS = { ocean: 0x6cacde, lavender: 0xb4a7ff, sage: 0x7ecfa0, coral: 0xff8a80, amber: 0xffd180, rose: 0xff80ab };
  const SOOTHING_LIGHT_ACCENTS = { ocean: 0x1877f2, lavender: 0x8b7fe8, sage: 0x4caf6e, coral: 0xe85d5d, amber: 0xe8a43a, rose: 0xe85a8a };

  function getSoothingColorScheme(accent, isDark) {
    const a = isDark
      ? (SOOTHING_DARK_ACCENTS[accent] || SOOTHING_DARK_ACCENTS.ocean)
      : (SOOTHING_LIGHT_ACCENTS[accent] || SOOTHING_LIGHT_ACCENTS.ocean);
    if (isDark) {
      const bg = 0x0d1117;
      return {
        primary: a, onPrimary: bg,
        primaryContainer: composite(a, 0.18, bg), onPrimaryContainer: a,
        secondary: composite(a, 0.7, bg), onSecondary: 0xe6edf3,
        secondaryContainer: composite(a, 0.18, 0x161b22), onSecondaryContainer: a,
        tertiary: a, onTertiary: bg,
        tertiaryContainer: composite(a, 0.15, bg), onTertiaryContainer: a,
        error: 0xf2b8b5, onError: 0x601410, errorContainer: 0x8c1d18, onErrorContainer: 0xf9dedc,
        background: bg, onBackground: 0xe6edf3,
        surface: 0x161b22, onSurface: 0xe6edf3,
        surfaceVariant: 0x21262d, onSurfaceVariant: 0xb1bac4,
        outline: 0x30363d, outlineVariant: 0x21262d,
        surfaceContainerLowest: 0x0a0e14, surfaceContainerLow: 0x161b22, surfaceContainer: 0x1c2128,
        surfaceContainerHigh: 0x21262d, surfaceContainerHighest: 0x2d333b,
      };
    }
    const surf = 0xffffff;
    return {
      primary: a, onPrimary: 0xffffff,
      primaryContainer: composite(a, 0.12, surf), onPrimaryContainer: a,
      secondary: composite(a, 0.65, surf), onSecondary: 0xffffff,
      secondaryContainer: composite(a, 0.15, 0xf0f2f5), onSecondaryContainer: a,
      tertiary: a, onTertiary: 0xffffff,
      tertiaryContainer: composite(a, 0.12, 0xf0f2f5), onTertiaryContainer: a,
      error: 0xba1a1a, onError: 0xffffff, errorContainer: 0xffdad6, onErrorContainer: 0x93000a,
      background: 0xf0f2f5, onBackground: 0x1c1e21,
      surface: surf, onSurface: 0x1c1e21,
      surfaceVariant: 0xe4e6eb, onSurfaceVariant: 0x606770,
      outline: 0xced0d4, outlineVariant: 0xd8dadf,
      surfaceContainerLowest: 0xffffff, surfaceContainerLow: 0xf0f2f5, surfaceContainer: 0xffffff,
      surfaceContainerHigh: 0xe4e6eb, surfaceContainerHighest: 0xced0d4,
    };
  }

  function getMonochromeColorScheme(isDark) {
    if (isDark) {
      return {
        primary: 0xffffff, onPrimary: 0x000000, primaryContainer: 0x1c1c1c, onPrimaryContainer: 0xffffff,
        secondary: 0xffffff, onSecondary: 0x000000, secondaryContainer: 0x1e1e1e, onSecondaryContainer: 0xffffff,
        tertiary: 0xe51937, onTertiary: 0xffffff, tertiaryContainer: 0x2e0509, onTertiaryContainer: 0xffb4b5,
        error: 0xe51937, onError: 0xffffff, errorContainer: 0x4c0008, onErrorContainer: 0xffdada,
        background: 0x000000, onBackground: 0xffffff,
        surface: 0x0c0c0c, onSurface: 0xffffff,
        surfaceVariant: 0x1a1a1a, onSurfaceVariant: 0xcccccc,
        outline: 0x2c2c2c, outlineVariant: 0x1a1a1a,
        surfaceContainerLowest: 0x000000, surfaceContainerLow: 0x0a0a0a, surfaceContainer: 0x111111,
        surfaceContainerHigh: 0x1a1a1a, surfaceContainerHighest: 0x262626,
      };
    }
    return {
      primary: 0x000000, onPrimary: 0xffffff, primaryContainer: 0xeeeeee, onPrimaryContainer: 0x000000,
      secondary: 0x000000, onSecondary: 0xffffff, secondaryContainer: 0xeaeaea, onSecondaryContainer: 0x000000,
      tertiary: 0xe51937, onTertiary: 0xffffff, tertiaryContainer: 0xffecee, onTertiaryContainer: 0xe51937,
      error: 0xe51937, onError: 0xffffff, errorContainer: 0xffdada, onErrorContainer: 0x4c0008,
      background: 0xffffff, onBackground: 0x000000,
      surface: 0xf9f9f9, onSurface: 0x000000,
      surfaceVariant: 0xeaeaea, onSurfaceVariant: 0x333333,
      outline: 0xcccccc, outlineVariant: 0xe0e0e0,
      surfaceContainerLowest: 0xffffff, surfaceContainerLow: 0xf9f9f9, surfaceContainer: 0xf0f0f0,
      surfaceContainerHigh: 0xe5e5e5, surfaceContainerHighest: 0xdcdcdc,
    };
  }

  const AURORA_SURFACES = {
    background: 0x040a18, onBackground: 0xe2ecf5,
    surface: 0x081426, onSurface: 0xe2ecf5,
    surfaceVariant: 0x14273d, onSurfaceVariant: 0xa9c1d4,
    outline: 0x2a4258, outlineVariant: 0x1a2f44,
    surfaceContainerLowest: 0x030812, surfaceContainerLow: 0x071224, surfaceContainer: 0x050d1c,
    surfaceContainerHigh: 0x122640, surfaceContainerHighest: 0x193252,
  };

  // ─────────────────────────────────────────────────────────────
  // Variant registry — mirrors ThemeVariant.kt + accent lists
  // ─────────────────────────────────────────────────────────────

  const VARIANTS = {
    standard: {
      label: 'Standard',
      darkLocked: false,
      allowsOled: true,
      hasContrast: true,
      fonts: { display: "'Space Grotesk'", body: "'Roboto Flex'" },
      shapes: { xs: 12, sm: 14, md: 20, lg: 28, xl: 36 },
      border: { width: 1, formula: 'outline30' },
      accents: [
        { id: 'brand', label: 'Brand (Default)', light: 0x904b3e, dark: 0xffb4a6 },
        { id: 'sapphire', label: 'Sapphire Blue', light: 0x1976d2, dark: 0x90caf9 },
        { id: 'emerald', label: 'Emerald Green', light: 0x388e3c, dark: 0xa5d6a7 },
        { id: 'amethyst', label: 'Amethyst Purple', light: 0x7b1fa2, dark: 0xe040fb },
        { id: 'rose', label: 'Rose Pink', light: 0xc2185b, dark: 0xf48fb1 },
        { id: 'coral', label: 'Coral Orange', light: 0xf57c00, dark: 0xffcc80 },
        { id: 'amber', label: 'Amber Gold', light: 0xfbc02d, dark: 0xffe082 },
        { id: 'crimson', label: 'Crimson Red', light: 0xd32f2f, dark: 0xef9a9a },
      ],
      resolve(state, dark) {
        // "brand" accent + default contrast uses the authored palette;
        // other swatches run the generator (TONAL_SPOT, like the app default).
        if (getAccentId(state) === 'brand') {
          if (state.contrast === 'medium') return dark ? BRAND_MEDIUM_DARK : BRAND_MEDIUM_LIGHT;
          if (state.contrast === 'high') return dark ? BRAND_HIGH_DARK : BRAND_HIGH_LIGHT;
          return dark ? BRAND_DARK : BRAND_LIGHT;
        }
        const swatch = this.accents.find((a) => a.id === getAccentId(state)) || this.accents[0];
        return generateColorScheme(dark ? swatch.dark : swatch.light, CSS_STYLES.TONAL_SPOT, dark, state.oled, state.contrast);
      },
    },
    synthwave: {
      label: 'Synthwave',
      darkLocked: true,
      allowsOled: false,
      hasContrast: false,
      fonts: { display: "'Orbitron'", body: "'Share Tech Mono'" },
      shapes: { xs: 0, sm: 0, md: 0, lg: 0, xl: 0 },
      border: { width: 1.5, formula: 'gradient' },
      gradient: ['#0D061A', '#1B0B3A'],
      accents: [
        { id: 'magenta', label: 'Magenta', light: 0xff007f, dark: 0xff007f },
        { id: 'cyan', label: 'Cyan', light: 0x00f0ff, dark: 0x00f0ff },
        { id: 'violet', label: 'Violet', light: 0x9d00ff, dark: 0x9d00ff },
        { id: 'orange', label: 'Orange', light: 0xff5e00, dark: 0xff5e00 },
      ],
      resolve(state) { return getSynthwaveColorScheme(getAccentId(state)); },
    },
    soothing: {
      label: 'Soothing',
      darkLocked: false,
      allowsOled: false,
      hasContrast: false,
      fonts: { display: "'Nunito Sans'", body: "'Nunito Sans'" },
      shapes: { xs: 10, sm: 12, md: 16, lg: 22, xl: 28 },
      border: { width: 0.8, formula: 'outline35' },
      accents: [
        { id: 'ocean', label: 'Ocean', light: 0x1877f2, dark: 0x6cacde },
        { id: 'lavender', label: 'Lavender', light: 0x8b7fe8, dark: 0xb4a7ff },
        { id: 'sage', label: 'Sage', light: 0x4caf6e, dark: 0x7ecfa0 },
        { id: 'coral', label: 'Coral', light: 0xe85d5d, dark: 0xff8a80 },
        { id: 'amber', label: 'Amber', light: 0xe8a43a, dark: 0xffd180 },
        { id: 'rose', label: 'Rose', light: 0xe85a8a, dark: 0xff80ab },
      ],
      resolve(state, dark) { return getSoothingColorScheme(getAccentId(state), dark); },
    },
    monochrome: {
      label: 'Monochrome',
      darkLocked: false,
      allowsOled: true, // implies OLED-dark surfaces by itself
      hasContrast: false,
      fonts: { display: "'DotGothic16'", body: "'Space Grotesk'" },
      shapes: { xs: 8, sm: 12, md: 16, lg: 24, xl: 32 },
      border: { width: 1, formula: 'outline45' },
      resolve(state, dark) { return getMonochromeColorScheme(dark); },
    },
    vivid: {
      label: 'Vivid',
      darkLocked: false,
      allowsOled: true,
      hasContrast: false,
      fonts: { display: "'Outfit'", body: "'Outfit'" },
      shapes: { xs: 14, sm: 18, md: 24, lg: 30, xl: 36 },
      border: { width: 1.25, formula: 'primary40' },
      accents: [
        { id: 'punch', label: 'Punch Pink', light: 0xdb1c5d, dark: 0xff6b9d },
        { id: 'azure', label: 'Electric Azure', light: 0x2962ff, dark: 0x7c9eff },
        { id: 'lime', label: 'Lime Surge', light: 0x4d7e2b, dark: 0xaed581 },
        { id: 'tangerine', label: 'Tangerine', light: 0xc54118, dark: 0xff8a50 },
        { id: 'grape', label: 'Grape Pop', light: 0x8e24aa, dark: 0xce93d8 },
      ],
      resolve(state, dark) {
        const a = this.accents.find((x) => x.id === getAccentId(state)) || this.accents[0];
        let s = generateColorScheme(dark ? a.dark : a.light, CSS_STYLES.VIBRANT, dark, false, 'default');
        if (state.oled && dark) s = withOledSurfaces(s);
        return s;
      },
    },
    aurora: {
      label: 'Aurora',
      darkLocked: true,
      allowsOled: false,
      hasContrast: false,
      fonts: { display: "'Manrope'", body: "'Manrope'" },
      shapes: { xs: 12, sm: 16, md: 22, lg: 30, xl: 38 },
      border: { width: 1, formula: 'primary25' },
      gradient: ['#040A18', '#0A2A33'],
      accents: [
        { id: 'emerald', label: 'Emerald', light: 0x34d399, dark: 0x6ee7b7 },
        { id: 'violet', label: 'Violet', light: 0xa78bfa, dark: 0xc4b5fd },
        { id: 'cyan', label: 'Cyan', light: 0x22d3ee, dark: 0x67e8f9 },
        { id: 'rose', label: 'Rose', light: 0xfb7185, dark: 0xfda4af },
        { id: 'ice', label: 'Ice', light: 0x93c5fd, dark: 0xbfdbfe },
      ],
      resolve(state) {
        const a = this.accents.find((x) => x.id === getAccentId(state)) || this.accents[0];
        return Object.assign(
          generateColorScheme(a.dark, CSS_STYLES.TONAL_SPOT, true, false, 'default'),
          AURORA_SURFACES
        );
      },
    },
    sakura: {
      label: 'Sakura',
      darkLocked: false,
      allowsOled: true,
      hasContrast: false,
      fonts: { display: "'Quicksand'", body: "'Quicksand'" },
      shapes: { xs: 12, sm: 16, md: 20, lg: 26, xl: 32 },
      border: { width: 0.8, formula: 'outline40' },
      accents: [
        { id: 'rose', label: 'Blossom Rose', light: 0xb05771, dark: 0xf2a3bc },
        { id: 'peach', label: 'Peach', light: 0x9b634b, dark: 0xf3b697 },
        { id: 'lavender', label: 'Lavender', light: 0x74699f, dark: 0xbfb3ea },
        { id: 'mint', label: 'Mint', light: 0x417962, dark: 0x93d6bc },
      ],
      resolve(state, dark) {
        const a = this.accents.find((x) => x.id === getAccentId(state)) || this.accents[0];
        let s = generateColorScheme(dark ? a.dark : a.light, CSS_STYLES.MUTED, dark, false, 'default');
        if (state.oled && dark) s = withOledSurfaces(s);
        return s;
      },
    },
    vector_pop: {
      label: 'Vector Pop',
      darkLocked: false,
      allowsOled: true,
      hasContrast: false,
      fonts: { display: "'Poppins'", body: "'Poppins'" },
      shapes: { xs: 6, sm: 8, md: 10, lg: 12, xl: 16 },
      border: { width: 2, formula: 'outline100' },
      accents: [
        { id: 'cobalt', label: 'Cobalt', light: 0x1d4ed8, dark: 0x5b8def },
        { id: 'tomato', label: 'Tomato', light: 0xd83542, dark: 0xff6b6b },
        { id: 'sunflower', label: 'Sunflower', light: 0xf2b705, dark: 0xffd34d },
        { id: 'kelly', label: 'Kelly Green', light: 0x0d864b, dark: 0x4cc38a },
      ],
      resolve(state, dark) {
        const a = this.accents.find((x) => x.id === getAccentId(state)) || this.accents[0];
        let s = generateColorScheme(dark ? a.dark : a.light, CSS_STYLES.VIBRANT, dark, false, 'default');
        // True-ink outline override (VectorPopTheme.kt)
        s = Object.assign({}, s, { outline: dark ? 0xd6d6d0 : 0x141414 });
        if (state.oled && dark) s = withOledSurfaces(s);
        return s;
      },
    },
  };

  // ─────────────────────────────────────────────────────────────
  // Applying a scheme to the document
  // ─────────────────────────────────────────────────────────────

  const hex = (v) => '#' + v.toString(16).padStart(6, '0');

  const ROLE_TO_VAR = {
    primary: '--md-sys-color-primary', onPrimary: '--md-sys-color-on-primary',
    primaryContainer: '--md-sys-color-primary-container', onPrimaryContainer: '--md-sys-color-on-primary-container',
    secondary: '--md-sys-color-secondary', onSecondary: '--md-sys-color-on-secondary',
    secondaryContainer: '--md-sys-color-secondary-container', onSecondaryContainer: '--md-sys-color-on-secondary-container',
    tertiary: '--md-sys-color-tertiary', onTertiary: '--md-sys-color-on-tertiary',
    tertiaryContainer: '--md-sys-color-tertiary-container', onTertiaryContainer: '--md-sys-color-on-tertiary-container',
    error: '--md-sys-color-error', onError: '--md-sys-color-on-error',
    errorContainer: '--md-sys-color-error-container', onErrorContainer: '--md-sys-color-on-error-container',
    background: '--md-sys-color-background', onBackground: '--md-sys-color-on-background',
    surface: '--md-sys-color-surface', onSurface: '--md-sys-color-on-surface',
    surfaceVariant: '--md-sys-color-surface-variant', onSurfaceVariant: '--md-sys-color-on-surface-variant',
    outline: '--md-sys-color-outline', outlineVariant: '--md-sys-color-outline-variant',
    surfaceContainerLowest: '--md-sys-color-surface-container-lowest',
    surfaceContainerLow: '--md-sys-color-surface-container-low',
    surfaceContainer: '--md-sys-color-surface-container',
    surfaceContainerHigh: '--md-sys-color-surface-container-high',
    surfaceContainerHighest: '--md-sys-color-surface-container-highest',
  };

  function rgbaCss(v, alpha) {
    const r = (v >> 16) & 0xff, g = (v >> 8) & 0xff, b = v & 0xff;
    return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
  }

  const systemDark = () => window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;

  function resolveState(raw) {
    const variantId = VARIANTS[raw.variant] ? raw.variant : 'standard';
    const variant = VARIANTS[variantId];
    // Accents are stored per variant (like the app's separate
    // synthwaveAccent/auroraAccent/... settings) so switching styles never
    // carries one variant's seed into another with a colliding accent id.
    const accents = {};
    if (raw.accents && typeof raw.accents === 'object') {
      Object.keys(raw.accents).forEach((vid) => {
        const v = VARIANTS[vid];
        if (v && v.accents && v.accents.some((a) => a.id === raw.accents[vid])) {
          accents[vid] = raw.accents[vid];
        }
      });
    }
    let accent;
    if (variant.accents) {
      accent = accents[variantId] && variant.accents.some((a) => a.id === accents[variantId])
        ? accents[variantId]
        : variant.accents[0].id;
      accents[variantId] = accent;
    }
    const mode = ['system', 'light', 'dark'].includes(raw.mode) ? raw.mode : 'system';
    const contrast = ['default', 'medium', 'high'].includes(raw.contrast) ? raw.contrast : 'default';
    return {
      variant: variantId,
      accents,
      mode,
      contrast,
      oled: !!raw.oled,
    };
  }

  function getAccentId(state) {
    return state.accents[state.variant] || 'brand';
  }

  function applyTheme(state) {
    const root = document.documentElement;
    const variant = VARIANTS[state.variant];
    const dark = variant.darkLocked || state.mode === 'dark' || (state.mode === 'system' && systemDark());
    // Same precedence as the app: High Contrast wins over OLED surfaces.
    const applyOled = (state.oled || state.variant === 'monochrome') && dark && variant.allowsOled
      && state.contrast !== 'high';

    let scheme = variant.resolve(
      Object.assign({}, state, { oled: applyOled }),
      dark
    );
    if (applyOled && (state.variant === 'standard')) {
      // Standard brand/swatch palettes go through resolve(), but authored
      // brand palettes need the same OLED surface rewrite the app applies.
      scheme = withOledSurfaces(scheme);
    }

    const style = root.style;
    for (const role in ROLE_TO_VAR) {
      if (scheme[role] === undefined) continue;
      style.setProperty(ROLE_TO_VAR[role], hex(scheme[role]));
    }

    // Derived roles the app leaves to M3 defaults.
    style.setProperty('--md-sys-color-surface-dim', hex(scheme.surfaceContainerLowest));
    style.setProperty('--md-sys-color-surface-bright', hex(scheme.surfaceContainerHighest));
    style.setProperty('--md-sys-color-surface-tint', hex(scheme.primary));
    style.setProperty('--md-sys-color-inverse-surface', hex(dark ? 0xe6e0e9 : 0x313033));
    style.setProperty('--md-sys-color-inverse-on-surface', hex(dark ? 0x313033 : 0xf4eff4));
    style.setProperty('--md-sys-color-inverse-primary', hex(dark ? 0x6750a4 : 0xd0bcff));

    // Per-variant chrome
    root.setAttribute('data-variant', state.variant);
    root.setAttribute('data-dark', dark ? 'true' : 'false');
    style.setProperty('color-scheme', dark ? 'dark' : 'light');

    style.setProperty('--font-display', variant.fonts.display + ", 'Space Grotesk', sans-serif");
    style.setProperty('--font-body', variant.fonts.body + ", 'Roboto Flex', sans-serif");

    for (const k in variant.shapes) {
      style.setProperty('--md-sys-shape-' + k, variant.shapes[k] + 'px');
    }

    // Card border tokens (ThemeVariant.cardBorder)
    style.setProperty('--card-border-width', variant.border.width + 'px');
    let borderColor;
    switch (variant.border.formula) {
      case 'gradient':
        borderColor = 'linear-gradient(135deg, ' + hex(scheme.primary) + ', ' + hex(scheme.secondary) + ')';
        break;
      case 'primary25': borderColor = rgbaCss(scheme.primary, 0.25); break;
      case 'primary40': borderColor = rgbaCss(scheme.primary, 0.4); break;
      case 'outline35': borderColor = rgbaCss(scheme.outline, 0.35); break;
      case 'outline40': borderColor = rgbaCss(scheme.outline, 0.4); break;
      case 'outline45': borderColor = rgbaCss(scheme.outline, 0.45); break;
      case 'outline100': borderColor = hex(scheme.outline); break;
      default: borderColor = rgbaCss(scheme.outline, 0.3);
    }
    style.setProperty('--card-border-color', borderColor);
    style.setProperty('--card-gradient-border', variant.border.formula === 'gradient' ? '1' : '0');

    // Full-bleed gradient backgrounds (Synthwave / Aurora)
    if (variant.gradient) {
      style.setProperty('--app-bg-gradient', 'linear-gradient(180deg, ' + variant.gradient[0] + ' 0%, ' + variant.gradient[1] + ' 100%)');
      style.setProperty('--app-bg-solid', variant.gradient[0]);
    } else {
      style.removeProperty('--app-bg-gradient');
      style.removeProperty('--app-bg-solid');
    }

    // Browser chrome: each meta reflects its own scheme's background, so an OS
    // dark/light flip lands on the right color even before a re-resolve.
    const metaDark = document.querySelector('meta[name="theme-color"][media*="dark"]');
    const metaLight = document.querySelector('meta[name="theme-color"][media*="light"]');
    const darkBg = dark ? scheme.background : variant.resolve(Object.assign({}, state), true).background;
    const lightBg = dark ? variant.resolve(Object.assign({}, state, { oled: false }), false).background : scheme.background;
    if (metaDark) metaDark.setAttribute('content', hex(darkBg));
    if (metaLight) metaLight.setAttribute('content', hex(lightBg));

    // FOUC snapshot for the inline head script
    try { localStorage.setItem('jp_theme_bg', hex(scheme.background)); } catch (e) {}

    return { scheme, dark };
  }

  // ─────────────────────────────────────────────────────────────
  // State + UI wiring
  // ─────────────────────────────────────────────────────────────

  const STORAGE_KEY = 'jellyplay_theme_v2';

  function loadState() {
    try {
      return resolveState(JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}'));
    } catch (e) {
      return resolveState({});
    }
  }

  function saveState(state) {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); } catch (e) {}
  }

  let current = loadState();

  function refresh(announce) {
    const result = applyTheme(current);
    if (announce !== false) renderPanel(result.dark);
    saveState(current);
  }

  const el = (id) => document.getElementById(id);

  function renderPanel(dark) {
    const variant = VARIANTS[current.variant];
    const modeRadios = document.querySelectorAll('input[name="jp-mode"]');
    modeRadios.forEach((r) => {
      r.checked = r.value === current.mode;
      r.disabled = variant.darkLocked;
    });
    document.getElementById('jp-mode-lock-note').style.display = variant.darkLocked ? '' : 'none';

    const contrastWrap = document.getElementById('jp-contrast-group');
    contrastWrap.style.display = variant.hasContrast ? '' : 'none';
    document.querySelectorAll('input[name="jp-contrast"]').forEach((r) => {
      r.checked = r.value === current.contrast;
    });

    const oledWrap = document.getElementById('jp-oled-group');
    oledWrap.style.display = variant.allowsOled ? '' : 'none';
    el('jp-oled-toggle').checked = current.oled && variant.allowsOled;

    // Style chips
    const styleRow = el('jp-style-row');
    styleRow.innerHTML = '';
    Object.keys(VARIANTS).forEach((id) => {
      const v = VARIANTS[id];
      const chip = document.createElement('button');
      chip.type = 'button';
      chip.className = 'style-chip' + (id === current.variant ? ' active' : '');
      chip.dataset.value = id;
      chip.setAttribute('aria-pressed', id === current.variant ? 'true' : 'false');
      chip.textContent = v.label;
      styleRow.appendChild(chip);
    });

    // Accent swatches
    const accentRow = el('jp-accent-row');
    accentRow.innerHTML = '';
    if (variant.accents && variant.accents.length) {
      document.getElementById('jp-accent-group').style.display = '';
      variant.accents.forEach((a) => {
        const dot = document.createElement('button');
        dot.type = 'button';
        dot.className = 'accent-dot' + (a.id === getAccentId(current) ? ' active' : '');
        dot.dataset.value = a.id;
        dot.title = a.label;
        dot.setAttribute('aria-label', a.label);
        dot.style.setProperty('--dot-color', hex(dark ? a.dark : a.light));
        accentRow.appendChild(dot);
      });
    } else {
      document.getElementById('jp-accent-group').style.display = 'none';
    }
  }

  function initThemePanel() {
    const btn = el('theme-switcher-btn');
    const panel = el('theme-panel');
    const wrapper = document.querySelector('.theme-switcher-wrapper');
    if (!btn || !panel || !wrapper) return;

    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      wrapper.classList.toggle('active');
    });
    document.addEventListener('click', (e) => {
      if (!wrapper.contains(e.target)) wrapper.classList.remove('active');
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') wrapper.classList.remove('active');
    });

    panel.addEventListener('click', (e) => {
      const styleChip = e.target.closest('.style-chip');
      if (styleChip) {
        current.variant = styleChip.dataset.value;
        refresh();
        return;
      }
      const accentDot = e.target.closest('.accent-dot');
      if (accentDot) {
        current.accents[current.variant] = accentDot.dataset.value;
        refresh();
        return;
      }
    });

    document.querySelectorAll('input[name="jp-mode"]').forEach((r) => {
      r.addEventListener('change', () => { current.mode = r.value; refresh(); });
    });
    document.querySelectorAll('input[name="jp-contrast"]').forEach((r) => {
      r.addEventListener('change', () => { current.contrast = r.value; refresh(); });
    });
    el('jp-oled-toggle').addEventListener('change', (e) => { current.oled = e.target.checked; refresh(); });
  }

  // Public API (also used for the FOUC bootstrap path)
  window.JellyPlayThemes = { VARIANTS, applyTheme, resolveState, refresh };

  function boot() {
    applyTheme(current);
    initThemePanel();
    refresh();
    // Re-resolve when the OS flips while "system" mode is active.
    if (window.matchMedia) {
      const mq = window.matchMedia('(prefers-color-scheme: dark)');
      const onChange = () => { if (current.mode === 'system') refresh(); };
      if (mq.addEventListener) mq.addEventListener('change', onChange);
      else if (mq.addListener) mq.addListener(onChange);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
