# Changelog

## Unreleased

- Choose a default section and landing tab.
- Keep theme previews compact and center host dialogs.
- Nest replies under their parent comments.
- Simplify Settings subtitles, diagnostics export, and About.
- Limit new changelog entries to 40 words.
- Fix opening Wear OS releases.

## 0.1.16

- Keep your app list and search scroll position when returning from a release.
- Add Home tabs for Apps, Books, and Games with featured releases, latest and popular collections, categories, reviews, and recent discussions.
- Add Discussions with separate app and game forums, topic search, original posts, and paged comments. Show forum activity, compact topic previews, and pinned badges.
- Restyle comments with compact author rows, icon-only collapse controls, and indented quotes. Keep quoted replies with unfinished drafts and make plain URLs clickable.
- Add pull-to-refresh for discussions and release pages, URL actions in the options menu, and a single Expressive loading indicator in Discussions.
- Add Inbox and Sent tabs, conversation history, mark as read, and private message replies. Fix escaped emojis in message previews.
- Group direct APK links with their mirrors and add discovered hosts to preferences. Show a checkmark for verified Premium access and sort hosts by preferred with Premium access, Premium access, preferred, then other, alphabetically within each group.
- Improve host selection spacing and account menus. Hide Sign in again for verified MegaDebrid accounts and shorten diagnostic log text.
- Fix thumbs up and space apart release action buttons. Fix version comparison so Following finds newer releases such as Televizo instead of falling back to older versions.
- Add AMOLED Black, keep the navigation drawer full height, and remove extra Home headings.
- Download manual builds directly as APK files without a ZIP wrapper.

## 0.1.14

- Add Mobilism Premium accounts and direct downloads. Renew MegaDebrid sessions automatically after signing in once.
- Post release comments, keep unfinished drafts, and display comment emojis correctly.
- Improve mod sections and download variants. Open linked forum releases inside the app.
- Separate book titles, authors, series, and formats. Sort named lists alphabetically.
- Improve connection recovery and error messages. Add Export logs to Settings and login for troubleshooting.

## 0.1.13

- Add 12 themes in one picker with collapsible Light and Dark sections, alphabetical sorting, and palette previews. Includes Atom One, Tokyo, and Dracula/Alucard.
- Preview font names in their own typeface. Bundle only open-source fonts with their licenses, and add Use custom font for local TTF, OTF, and TTC files.
- Fix MegaDebrid sign-in to obtain a download token and check Premium access. Keep both debrid services available by default.
- Give download buttons stronger contrast and cards more depth. Center a single release image.
- Put the feed title beside the menu. Remove the duplicate link-handler setting; Android handles that preference.

## 0.1.12

- Add a Sort tab to search filters: newest posts, oldest posts, or latest activity. Keep post dates in order across pages, with progress while checking matches.
- Open Mobilism release links directly in the app, including when it's already running. Keep the link pending through login or guest access.
- Add Open Mobilism links in Settings to choose Android's default link handler. Keep About last.
- Stop suggesting an inactive subscription when MegaDebrid returns an unexplained link error.

## 0.1.11

- Bundle Mobilism's host list so a forum connection failure cannot block debrid refreshes. Keep checking account access and provider availability live.
- Keep verified accounts marked correctly when a host refresh fails. Make it clear that cached hosts stay inactive until refresh succeeds.
- Add About at the bottom of Settings, with the app version, release notes, and GitHub link.

## 0.1.10

- Check host availability before showing green download buttons. Stop using stale host lists while refreshing, and exclude hosts reported as down.
- Give provider cards matching tonal controls and a quieter remove button.
- Explain Premium requirements and keep unexplained MegaDebrid errors from implying a file was deleted.

## 0.1.9

- Verify Real-Debrid keys and MegaDebrid tokens before saving. Recheck saved accounts when the app starts and when refreshing hosts.
- Make green host buttons resolve downloads through verified debrid accounts and open the direct link. Try the other supported service if the first fails.
- Show download errors with an option to open the original host. Keep copying links unchanged, and record a download only after opening a link.

## 0.1.8

- Put post dates beside replies and views in lists, and beside the author and replies on release pages. Remove the Posted label.
- Collapse Post date filters unless a date filter is active. Make active filter pills smaller and add space below the tabs.
- Add a Hide 18+ content setting for browsing and search. Keep it off by default and use Mobilism's category exclusions.

## 0.1.7

Added post dates and shared browse/search filters.

- Keep app names separate from versions when titles have extra labels, alternate version markers, or truncated tags.
- Show post dates on release cards and pages, including downloaded and followed releases.
- Add inclusive start/end date filters with Material date pickers. Save filters per section and carry them into search, including searches from installed apps.
- Keep filtered pagination moving past unmatched pages. Offer a continue action for sparse results.
- Detect Mod info from varied headings, feature wording, formatting, and nearby advertisement statements. Keep descriptions and changelogs separate.

All 73 unit tests and eight emulator checks pass. Lint has no errors. Checked the title parser against the reported release formats.

## 0.1.6

Added release actions and automatic README screenshots.

- Put thumbs-up, watch/unwatch, and downloaded controls on the release page.
- Keep just the images in the release carousel. Remove the counter and arrow strip.
- Simplify debrid cards with a check beside the provider name, a host dropdown, and compact refresh and remove buttons. Remove a saved key before replacing it.
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
