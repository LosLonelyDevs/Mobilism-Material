# Mobilism Material

An Android source rewrite in Kotlin and Jetpack Compose, starting with the existing login, favourite section, feed, and release-detail flow. Material 3 Expressive supplies the components and motion. Catppuccin Frappé and bundled Geist are the defaults.

This is an early source rewrite, not a feature-complete replacement. Debug compilation, Android lint, parser tests, and emulator checks cover the initial flows. See [verification notes](docs/verification.md) for the tested scope.

## Source layout

| Module | Responsibility |
| --- | --- |
| `app` | Navigation, screens, view models, appearance preferences |
| `core:data` | Mobilism API, session state, encrypted token storage |
| `core:designsystem` | Semantic themes, fonts, Expressive controls, motion |

Legacy decoded Java, smali, resources, APKs, and local credentials are ignored. The new Gradle project does not compile or package them.

## Build

Use JDK 21 and Android SDK 37. Set `ANDROID_HOME`, or create an ignored `local.properties` with your own `sdk.dir`. The checked-in Gradle 9.6.1 wrapper downloads a checksum-pinned distribution.

```powershell
./gradlew.bat :app:assembleDebug
```

On macOS/Linux, use `bash ./gradlew :app:assembleDebug`. Versioned APKs are written beneath `app/build/outputs/apk/` and copied to `build/apk/`, for example `MobilismMaterial_v0.1.1-debug.apk`. The build does not read `.env`, inject credentials, install an APK, or contact a device.

Release builds are unsigned unless the process provides `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Unsigned filenames retain the `-unsigned` suffix. Never commit signing material or credentials. The manual **Build APK** workflow accepts debug or release; release requires the four `ANDROID_*` signing secrets documented in the workflow. Automatic CI runs tests, lint, and a debug build.

During migration the app ID is `cum.ahfu.mobi.material`, separate from the legacy `cum.ahfu.mobi`. It does not replace the legacy install or share its data. The minimum Android version is 8.0 (API 26).

## First slice

- Login, guest entry, account registration, password recovery, and first-use favourite section selection.
- Apps, Games, and Books navigation; Home/Top feeds with pagination; release text, images, and links.
- Error states distinguish blocked Network permission, DNS/network failure, server rejection, and session expiry. Passwords are not saved; session tokens use Android Keystore encryption.
- Frappé colors, Geist/System font selection, persistent appearance preferences, JSON theme import, and reduced motion.
- Native Expressive buttons, loading indicators, flexible app bars, and spring-based screen/press motion.

Release downloads appear as host-name buttons with adjacent copy controls. Settings stores Real-Debrid and MegaDebrid keys encrypted; supported hosts turn theme green using the providers' published host lists. This indicator does not validate a key, subscription, or current host availability, and links still open in the device browser.

Unrestricted debrid downloads, background transfers, search, categories, comments, messages, and other legacy features are not ported. See [migration scope](docs/migration.md).

## Themes and motion

Screens consume semantic Material roles rather than palette constants. New themes do not require changes to feature screens. [Theme format](docs/themes.md), [importable Frappé example](docs/themes/frappe-custom.json), and [motion decisions](docs/motion.md) document the extension points.

Material 3 is pinned to `1.5.0-beta02` for its Expressive APIs. Dependency versions live in `gradle/libs.versions.toml`; upgrades need compilation and interaction checks.

## License

The repository's supplied license is [AGPL-3.0](LICENSE). Bundled Geist fonts retain their separate [SIL Open Font License](core/designsystem/src/main/res/raw/geist_ofl.txt). See [third-party notices](THIRD_PARTY_NOTICES.md).
