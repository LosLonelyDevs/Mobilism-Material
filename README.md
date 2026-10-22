# Mobilism Material

An Android source rewrite in Kotlin and Jetpack Compose, starting with the existing login, favourite section, feed, and release-detail flow. Material 3 Expressive supplies the components and motion. Catppuccin Frappé and bundled Geist are the defaults.

This is an initial source milestone, not a feature-complete replacement. Compilation, tests, installation, and device validation have not been run for this milestone at the owner's request. The API mapping comes from the legacy app and still needs integration validation.

## Source layout

| Module | Responsibility |
| --- | --- |
| `app` | Navigation, screens, view models, appearance preferences |
| `core:data` | Mobilism API, session state, encrypted token storage |
| `core:designsystem` | Semantic themes, fonts, Expressive controls, motion |

Legacy decoded Java, smali, resources, APKs, and local credentials are ignored. The new Gradle project does not compile or package them.

## Build

Use JDK 21 and Android SDK 36. Set `ANDROID_HOME`, or create an ignored `local.properties` with your own `sdk.dir`. The checked-in Gradle 8.13 wrapper downloads a checksum-pinned distribution.

```powershell
./gradlew.bat :app:assembleDebug
```

On macOS/Linux, use `./gradlew :app:assembleDebug`. The expected output is `app/build/outputs/apk/debug/app-debug.apk`. The build does not read `.env`, inject credentials, install an APK, or contact a device. Release signing is not configured yet.

During migration the app ID is `cum.ahfu.mobi.material`, separate from the legacy `cum.ahfu.mobi`. It does not replace the legacy install or share its data. The minimum Android version is 8.0 (API 26).

## First slice

- Login, guest entry, account registration, password recovery, and first-use favourite section selection.
- Apps, Games, and Books navigation; Home/Top feeds with pagination; release text, images, and links.
- Error states distinguish blocked Network permission, DNS/network failure, server rejection, and session expiry. Passwords are not saved; session tokens use Android Keystore encryption.
- Frappé colors, Geist/System font selection, persistent appearance preferences, JSON theme import, and reduced motion.
- Native Expressive buttons, loading indicators, flexible app bars, and spring-based screen/press motion.

The download-link sheet currently opens hosts in the browser. Premium/debrid downloads, background transfers, search, categories, comments, messages, and other legacy features are not ported. See [migration scope](docs/migration.md).

## Themes and motion

Screens consume semantic Material roles rather than palette constants. New themes do not require changes to feature screens. [Theme format](docs/themes.md), [importable Frappé example](docs/themes/frappe-custom.json), and [motion decisions](docs/motion.md) document the extension points.

Material 3 is pinned to `1.5.0-beta02` for its Expressive APIs. Dependency versions live in `gradle/libs.versions.toml`; upgrades need compilation and interaction checks.

## License

The repository's supplied license is [AGPL-3.0](LICENSE). Bundled Geist fonts retain their separate [SIL Open Font License](core/designsystem/src/main/res/raw/geist_ofl.txt). See [third-party notices](THIRD_PARTY_NOTICES.md).
