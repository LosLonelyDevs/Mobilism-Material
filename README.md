# Mobilism Material

I'm rebuilding Mobilism in Kotlin and Jetpack Compose. Same basic flow, with Material 3 Expressive components and motion. Catppuccin Frappé and Geist are the defaults.

<p>
<a href="https://github.com/LosLonelyDevs/Mobilism-Material/releases"><img src="docs/images/github.png" alt="Get it on GitHub" width="180"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22cum.ahfu.mobi.material%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FLosLonelyDevs%2FMobilism-Material%22%2C%22author%22%3A%22LosLonelyDevs%22%2C%22name%22%3A%22Mobilism%20Material%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%7D%22%7D"><img src="docs/images/obtainium.png" alt="Install with Obtainium" width="180"></a>
</p>

The Obtainium button includes prereleases.

Still a work in progress. Requires Android 8+. Installs alongside the old app and keeps its own data.

## Screenshots

<p>
  <img src="docs/images/apps-hero.png" alt="Apps Home with featured releases and latest apps" width="200">
  <img src="docs/images/books-hero.png" alt="Books Home with featured reads in the light theme" width="200">
  <img src="docs/images/games-hero.png" alt="Games Home with featured games in AMOLED Black" width="200">
</p>
<p>
  <img src="docs/images/apps.png" alt="Latest apps with separate names and version details" width="200">
  <img src="docs/images/release.png" alt="Release details with the Material 3 Expressive image carousel" width="200">
  <img src="docs/images/downloads.png" alt="Downloads grouped by architecture with file host buttons" width="200">
  <img src="docs/images/appearance.png" alt="Catppuccin Frappé theme, fonts, and motion settings" width="200">
</p>

Screenshots use sample data. CI refreshes them on version bumps and screenshot test changes.

## Features

- [Home tabs](docs/home.md) for Apps, Books, and Games, with featured releases, categories, reviews, and discussions.
- Search, Latest and Top feeds, date/category filters, and infinite scrolling.
- Clear app versions and book details, collapsible release sections, and full-screen images.
- [Comments](docs/release-comments.md) with quoted replies, saved drafts, compact spacing, and individual collapse controls.
- [Discussions](docs/discussions.md) with forum browsing, topic search, and replies.
- Inbox, Sent, conversation history, mark as read, and message replies.
- Clickable URLs and emoji support in release text, comments, and messages.
- Following with background update checks and optional notifications. My Applications tracks installed and downloaded apps.
- Mobilism Premium, MegaDebrid, and Real-Debrid links with encrypted account storage.
- Grouped downloads, preferred architectures, and preferred file hosts.
- 13 themes including AMOLED Black, bundled and custom fonts, and reduced motion.

Downloads open in your browser. Green buttons resolve through a verified Premium account first. Native downloads and several legacy features are still pending. [Remaining work](docs/feature-parity.md).

Older saved logins need one fresh sign-in for Inbox access.

Connection trouble? Reproduce it, then use **Export logs** in Settings or on the login screen. [Troubleshooting](docs/troubleshooting.md).

Opening a file host marks that release downloaded as a rough record. Auto-watch is on by default; you can turn it off in Download settings.

## Build

Use JDK 21 and Android SDK 37. Set `ANDROID_HOME` or `sdk.dir` in `local.properties`.

```powershell
./gradlew.bat :app:assembleDebug
```

On macOS/Linux: `bash ./gradlew :app:assembleDebug`. APKs go to `build/apk/`.

CI runs tests, lint, and a build. Bump `versionName` and `versionCode`, then push to `main` to publish a signed release with checksums, marked Latest on GitHub. [Release workflow](.github/workflows/release.yml).

[Verification](docs/verification.md) · [Themes](docs/themes.md) · [Migration](docs/migration.md)

## License

[AGPL-3.0](LICENSE). Bundled fonts use the [SIL Open Font License](docs/font-licenses.md). [Third-party notices](THIRD_PARTY_NOTICES.md).
