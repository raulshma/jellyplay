package com.raulshma.jellyplay.feature.onboarding

// No wallpaper-derived dynamic color on web — brand schemes apply instead
// (desktop precedent). The AppearanceStep hides only the dynamic-theming
// toggle row; every other appearance control renders unchanged.
internal actual val supportsDynamicColor: Boolean = false
