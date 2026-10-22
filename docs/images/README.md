# Image credits

The [Get it on GitHub badge](https://github.com/Kunzisoft/Github-badge) is by @flocke, based on F-Droid artwork. Used unchanged under [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/).

The [Obtainium badge](https://github.com/ImranR98/Obtainium/tree/main/assets/graphics) is from Obtainium's badge assets.

App screenshots use fixed sample content. No account credentials or personal data.

CI rebuilds the four screenshots when the app version changes on `main`. A manual Android checks run refreshes them too. It uses a fresh Android emulator, bundled images, and the real Compose screens. It never signs in or loads saved account settings.

The release screenshot shows the TizenTube carousel. Its two images are from [this public release](https://forum.mobilism.org/viewtopic.php?t=6429197). Feed fixtures also use public images from [Windfinder](https://forum.mobilism.org/viewtopic.php?t=6436583), [Flud+](https://forum.mobilism.org/viewtopic.php?t=6436566), and [Aloha](https://forum.mobilism.org/viewtopic.php?t=6436688). Titles, counts, and release text are sample data. Fixture assets live in `app/src/debug/assets/readme` and aren't included in release APKs.

To recapture locally, select an emulator with `ANDROID_SERIAL`, set its screen to 1080x2400 at 420 dpi, then run:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mobilism.material.ReadmeScreenshots
```

Pull `apps.png`, `release.png`, `downloads.png`, and `appearance.png` from `/sdcard/Android/data/cum.ahfu.mobi.material/files/readme-screenshots/` into this folder. Run `python scripts/check-readme-screenshots.py` before committing. Captures include only app content, without system notifications or the keyboard.
