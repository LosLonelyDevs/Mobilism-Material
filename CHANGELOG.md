# Changelog

## 0.1.6

Added release actions and automatic README screenshots.

- Put thumbs-up, watch/unwatch, and downloaded controls on the release page.
- Treat a file-host open as a rough downloaded mark. Copying a link doesn't count.
- Auto-watch downloaded apps by default, with a switch in Download settings. Keep an existing watch when opening an older release.
- Fill the adaptive launcher icon with deep blue and add a monochrome layer for themed icons.
- Put the GitHub download badge beside Obtainium.
- Refresh README screenshots on version bumps, including the image carousel. Use bundled sample content without account data.

All 49 unit tests and seven emulator checks pass. Lint has no errors. Thumbs-up remembers the state returned by the server; the API currently omits the initial state.

## 0.1.5

Added a Material 3 carousel above the release description.

- Swipe through rounded image previews or use the arrows. The counter shows your position.
- Tap an image to open it full size, with the existing zoom and gallery controls.
- Use Expressive motion for snapping and navigation. Respect reduced motion.
- Add README screenshots and an Obtainium install button, with prereleases enabled.

Tested the three-image TizenTube release on the emulator. All 45 tests pass; lint has no errors.

## 0.1.4

Added Following with installed-version checks and optional update notifications.

- Match an installed app automatically or link it yourself. Check now or let Android run background checks.
- Open the release from its notification. Repeat checks won't send the same alert again.
- Separate app names from version and mod details in list cards.
- Fix the doubled login border and add Android password-manager Autofill hints.
- Keep decorated Mod Info headings in their own section, including the Spotify post that was splitting incorrectly.
- Center and enlarge the release loader. Remove theme import from Settings.
- Shorten the README and release notes. Future releases use this changelog.

Tested on the Android 16 emulator. Checks cover recent search results; Android controls background timing. Password-manager save and fill prompts depend on the configured provider.

## 0.1.3

Added search and My Applications.

- Search releases by section, with images and infinite scrolling.
- Find releases from installed apps. Follow an app or mark a release as downloaded.
- Keep downloaded history when moving a watch to a newer release. Remove records with Undo.
- Check for newer versions manually. Phone/TV variants and stable/beta releases stay separate.

Tested on the Android 16 emulator. All 37 tests pass; lint has no errors. The signed APK and checksum are verified.

Checks cover recent search results. Installed-version matching and background alerts aren't in this version.

## 0.1.2

Split release pages into collapsible Description, What's new, Mod info, and Downloads sections.

- Show app names separately from versions, builds, and tags.
- Add read-only comments and a full-screen image viewer.
- Group downloads by architecture. Put preferred hosts first.
- Add Latest/Top feeds, category filters, and infinite scrolling.
- Use more of the Frappé palette, with consistent headings and rounded controls.

Tested on the Android 16 emulator. All 27 tests pass; lint has no errors. The release APK is signed.

Search, comment posting, and native downloads are still pending in this version.

## 0.1.1

First Compose preview, with Material 3 Expressive, Frappé, and Geist.

- Add login, feeds, release pages, and saved appearance settings.
- Fix list images and pagination.
- Turn download links into host buttons, with browser and copy actions.
- Store debrid keys encrypted. Hide saved inputs and highlight supported Mobilism hosts.

Tested login, browsing, images, links, clipboard, and saved settings on the emulator. All 14 tests, lint, the signed build, and CI pass.

Download links open in the browser. A supported-host indicator doesn't confirm account access.
