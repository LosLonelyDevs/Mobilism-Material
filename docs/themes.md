# Theme format, version 1

Catppuccin Frappé is the initial built-in dark theme. Its palette and semantic colors come from the supplied theme definition. Wallpaper colors do not override it. Geist uses local Regular, Medium, Semibold, and Bold font files; System sans serif is also available.

`ThemeDefinition` holds identity, display name, light/dark mode, and a complete Material `ColorScheme`. Screens use that scheme through `MaterialTheme`; palette constants belong only in theme definitions. Typography and motion are separate preferences.

## Import

Open **Settings → Appearance → Import theme** and select a JSON document. The example [frappe-custom.json](themes/frappe-custom.json) reproduces the default colors with a different ID so it can be imported. Change its ID, name, and roles to create another theme.

- `schemaVersion`: integer `1`.
- `id`: 2–64 lowercase letters, digits, or hyphens, beginning with a letter or digit. Built-in IDs cannot be overwritten.
- `name`: 1–60 characters after trimming.
- `dark`: boolean controlling light/dark system-bar appearance.
- `colors`: all named roles in the example, each an opaque `#RRGGBB` value. Missing or malformed roles reject the import.

Files are limited to 64 KiB. Imported documents are stored atomically in private app storage. Importing the same custom ID replaces that theme; it cannot replace the built-in Frappé theme. An unreadable theme falls back to the built-in theme. Selection, font, and reduced motion persist across launches.

Role pairs such as `primary`/`onPrimary` and `surface`/`onSurface` must remain readable. Schema validation checks structure, not perceptual contrast; theme authors must check contrast, disabled/error states, and content at enlarged font scales before distributing themes.

## Extending the system

Add bundled definitions to `ThemeCatalog.builtIn`, or import custom definitions without editing feature screens. Keep role names and version 1 behavior stable. If the contract changes, add explicit schema migration rather than silently changing existing documents. Future theme packs can add previews and metadata around this contract.

The startup window uses Frappé until saved appearance state loads. Runtime colors, navigation bars, and all Compose screens then follow the selected theme. The native release-text bridge explicitly receives the same text/link colors and font.
