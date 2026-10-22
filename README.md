# Mobilism Material

I'm rebuilding Mobilism in Kotlin and Jetpack Compose. Same basic flow, with Material 3 Expressive components and motion. Catppuccin Frappé and Geist are the defaults.

[Download the APK](https://github.com/LosLonelyDevs/Mobilism-Material/releases)

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22cum.ahfu.mobi.material%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FLosLonelyDevs%2FMobilism-Material%22%2C%22author%22%3A%22LosLonelyDevs%22%2C%22name%22%3A%22Mobilism%20Material%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%7D%22%7D"><img src="docs/images/obtainium.png" alt="Install with Obtainium" width="180"></a>

The Obtainium button includes prereleases.

Still a work in progress. Requires Android 8+. Installs alongside the old app and keeps its own data.

## Screenshots

<p>
  <img src="docs/images/apps.png" alt="Apps feed with separate names and version details" width="200">
  <img src="docs/images/release.png" alt="Release details and description" width="200">
  <img src="docs/images/downloads.png" alt="Downloads grouped by architecture with file host buttons" width="200">
  <img src="docs/images/appearance.png" alt="Catppuccin Frappé theme, fonts, and motion settings" width="200">
</p>

## What works

- Apps, Games, and Books: search, Latest/Top feeds, category filters, and infinite scrolling.
- Release pages: collapsible sections, grouped downloads, read-only comments, and a full-screen image viewer.
- Following: installed-version checks, background checks, and optional update notifications. My Applications keeps installed apps and downloaded marks.
- Preferred architectures and file hosts, debrid host indicators, font choices, and reduced motion.

Downloads currently open in your browser. Native downloads, unrestricted debrid links, and several legacy features are still pending. [Remaining work](docs/feature-parity.md).

## Build

Use JDK 21 and Android SDK 37. Set `ANDROID_HOME` or `sdk.dir` in `local.properties`.

```powershell
./gradlew.bat :app:assembleDebug
```

On macOS/Linux: `bash ./gradlew :app:assembleDebug`. APKs go to `build/apk/`.

CI runs tests, lint, and a build. Bump `versionName` and `versionCode`, then push to `main` to publish a signed prerelease with checksums. [Release workflow](.github/workflows/release.yml).

[Verification](docs/verification.md) · [Themes](docs/themes.md) · [Migration](docs/migration.md)

## License

[AGPL-3.0](LICENSE). Geist uses the [SIL Open Font License](core/designsystem/src/main/res/raw/geist_ofl.txt). [Third-party notices](THIRD_PARTY_NOTICES.md).
