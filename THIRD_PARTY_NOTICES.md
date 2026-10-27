# Third-party notices

## Geist

Geist Regular, Medium, Semibold, and Bold are bundled without modifying the font binaries.

Copyright 2024 The Geist Project Authors. Licensed under the SIL Open Font License, Version 1.1. The complete license is included in `core/designsystem/src/main/res/raw/geist_ofl.txt` and packaged as an Android raw resource.

Project: https://github.com/vercel/geist-font

License source: https://github.com/vercel/geist-font/blob/main/OFL.txt

## Other bundled fonts

Alata, Atkinson Hyperlegible, and IBM Plex Sans use the SIL Open Font License 1.1. Their complete licenses ship in the APK. See [font sources and notices](docs/font-licenses.md).

## Theme palettes

Themes use the supplied Catppuccin, Rosé Pine, Solarized, Atom One, Tokyo, and Dracula/Alucard palettes and semantic mappings. Palette names identify their sources, not an affiliation or endorsement.

Project: https://github.com/catppuccin/catppuccin

## Orion Store

The Home hero composition, category shape selection, and polygon morph implementation adapt code from Orion Store by RookieEnough and contributors, at commit `bffea4103b05120904a9e0d1a8f3f0cac594c553`.

Source files: `app/src/main/java/com/orion/store/ui/Common.kt` and `app/src/main/java/com/orion/store/ui/StoreScreen.kt`.

Project: https://github.com/RookieEnough/Orion-Store

License: GNU General Public License, version 3. The full license is bundled in `core/designsystem/src/main/res/raw/orion_gpl3.txt`. Adapted portions retain GPL-3.0 terms; the combined app remains AGPL-3.0 under the licenses' section 13 combination provision.

Changes: Mobilism content and theme roles, shared focus/state feedback, manual hero paging, reduced-motion behavior, and section-specific layouts. Orion branding, catalog data, and installer code are not included.

## Libraries

Gradle resolves AndroidX/Jetpack Compose, Kotlin, coroutines, OkHttp, Coil, and jsoup from their configured repositories. Their upstream licenses remain applicable. The app's project license does not replace bundled assets' or dependencies' licenses.
