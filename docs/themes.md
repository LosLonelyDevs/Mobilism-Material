# Theme format, version 1

Appearance has 12 built-in themes: Catppuccin Latte/Frappé, Rosé Pine Dawn/Rosé Pine, Solarized Light/Dark, Atom One Light/Dark, Tokyo Day/Night, and Alucard/Dracula. One picker has collapsible Light and Dark sections, sorted alphabetically. The current theme's section opens by default. Each option shows its palette below the name.

Catppuccin Frappé and Geist remain the defaults. Wallpaper colors do not override the selected palette. Font names preview their own typeface. Bundled fonts are open source; [licenses ship in the APK](font-licenses.md). System sans serif is also available.

Use custom font stays last in the font menu. It accepts TTF, OTF, and TTC files up to 10 MiB, validates them, and saves a private local copy. Invalid files leave the current font unchanged.

`ThemeDefinition` holds identity, display name, light/dark mode, and a complete Material `ColorScheme`. Screens use that scheme through `MaterialTheme`; palette constants belong only in theme definitions. Typography and motion are separate preferences.

## Theme definitions

Theme import is hidden in Settings for now. Existing saved themes still load. The example [frappe-custom.json](themes/frappe-custom.json) documents the format for development; it is not a user import flow.

- `schemaVersion`: integer `1`.
- `id`: 2–64 lowercase letters, digits, or hyphens, beginning with a letter or digit. Built-in IDs cannot be overwritten.
- `name`: 1–60 characters after trimming.
- `dark`: boolean controlling light/dark system-bar appearance.
- `colors`: all Material roles in the example, each an opaque `#RRGGBB` value. Missing or malformed Material roles reject the import.
- Optional `colors.success` and `colors.onSuccess` style supported-host buttons. Frappé uses green `#A6D189` on `#232634`. Older version 1 themes without these roles fall back to their tertiary pair.
- Optional `reading`/`onReading`, `featured`/`onFeatured`, and `personalization`/`onPersonalization` pairs style Books and architecture controls, popularity and preferred hosts, and appearance controls respectively. They fall back to tertiary, secondary, and primary pairs for older themes. Frappé uses peach, yellow, and pink.

Heading colors follow hierarchy through one `AppHeading` component: page titles use primary blue, section headings secondary mauve, and subheadings reading peach. The same level uses the same color on every screen. Feature accents apply to icons, buttons, badges, and surfaces: Apps blue, Games mauve, Books peach, replies and refresh teal, popularity yellow, appearance pink, and saved/supported states green. Tinted containers derive from semantic colors; screens never hardcode palette values. Labels and icons remain present so color is not the only cue.

Files are limited to 64 KiB. Imported documents are stored atomically in private app storage. Importing the same custom ID replaces that theme; it cannot replace the built-in Frappé theme. An unreadable theme falls back to the built-in theme. Selection, font, and reduced motion persist across launches.

Role pairs such as `primary`/`onPrimary` and `surface`/`onSurface` must remain readable. Schema validation checks structure, not perceptual contrast; theme authors must check contrast, disabled/error states, and content at enlarged font scales before distributing themes.

## Extending the system

Add bundled definitions to `ThemeCatalog.builtIn`, or import custom definitions without editing feature screens. Keep role names and version 1 behavior stable. If the contract changes, add explicit schema migration rather than silently changing existing documents. Future theme packs can add previews and metadata around this contract.

The startup window uses Frappé until saved appearance state loads. Runtime colors, navigation bars, and all Compose screens then follow the selected theme. The native release-text bridge explicitly receives the same text/link colors and font.
