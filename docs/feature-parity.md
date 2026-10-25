# Rewrite feature parity

Source audit against the local legacy manifest, drawer and action menus, settings XML, API interface, and UI classes. This is an implementation inventory, not a claim that every old server endpoint still works.

## Implemented

- Login, guest entry, account registration/password recovery screens, sign-out, and encrypted session storage.
- Favourite starting section, Apps/Games/Books navigation, Latest/Top feeds, category multiselect, inclusive post-date filters, and pagination.
- Release details, formatted text, parsed title metadata, collapsible description/changelog/mod/download sections, and paginated comments with signed-in posting.
- Full-screen images, zoom/paging, architecture groups, preferred hosts, host links, clipboard actions, and browser opening.
- Material 3 Expressive components and motion, Frappé/Geist defaults, theme support, appearance preferences, and reduced motion. Theme import is hidden for now.
- Premium settings for Mobilism Premium, MegaDebrid, and Real-Debrid, with encrypted credentials, account verification, supported-host indicators, and direct link resolution. Mobilism checks its live host list. Errors offer the original host; downloads still open in the browser.
- Native release search with saved section/category/date filters, paginated results, artwork, empty/error states, and retry. Cards and release pages show the original post date.
- My Applications with launcher-app discovery, installed-app search, optional system apps, downloaded marks, removal and Undo.
- Release thumbs-up, visible watch/download controls, and optional auto-watch when opening a file host. Browser opens are a rough download signal, not completion tracking.
- Following with installed-version baselines, automatic package/name matching, manual app linking, scheduled checks, and optional update notifications. Checks keep phone/TV and stable/preview releases separate. Following another version preserves downloaded history.

## Remaining functional work

| Area | Missing behavior | Main legacy reference |
| --- | --- | --- |
| Search and discovery | Discussion/other-result search, legacy advanced search filters, and remaining feed filters | `UI/Main/SearchActivity.java`, `logic/network/MobilismService.java` |
| Release interaction | Reply/quote comments, editing/deletion, previous-version history, library actions beyond Apps | `UI/Release/ReleaseActivtiy.java`, `UI/Discussion/CommentActivity.java`, `res/menu/menu_releases.xml` |
| Forum sections | Announcements, Contests, Chitchat, Services, Suggestions & Feedback, discussion categories/topics/comments | `res/menu/activity_main_drawer.xml`, `UI/Discussion/` |
| My Applications | Full non-launcher discovery, batch actions, save/copy/export, legacy data import | `UI/MyApplications/`, installed/downloaded/favorites action menus |
| Version tracker | Full version history, broader matching for ambiguous/unversioned releases, large-library performance | `UI/VersionTracker/` |
| Downloads | Native queue/progress, download state and files, destination selection, background operation, completion notifications, archive extraction, opening/installing APKs, failure/retry handling | `logic/download/`, `UI/Release/DownloadsActivity.java`, `UI/Settings/FilePickerActivity.java` |
| Premium | Mobilism Premium trial/plans/payment flows | `UI/AMember/`, premium/payment API endpoints |
| Messages | Message lists, conversation history, compose/reply, read state, deletion, unread notifications | `UI/Messages/`, message API endpoints |
| Settings and utilities | Notification preferences/sounds, content filters, cache controls, old listing options, download/extraction options, QR scanner, About, app-update flow | `res/xml/pref_general.xml`, `UI/QRCodeScanner/`, `UI/Settings/AboutActivity.java`, `logic/updater/` |

## Complete Compose and migration work

- Replace `ReleaseBody`'s `AndroidView`/`TextView` bridge with Compose rich text while retaining formatting, safe links, and text selection. Current screen layout and controls already use Compose.
- Apply the shared M3E components, semantic colors, motion preferences, and rounded shapes to each remaining screen.
- Add persistence and modern Android background, storage, package-visibility, install, and notification handling as their features land.
- Decide on legacy data import and package/signing continuity. The rewrite currently has a separate package and cannot silently inherit the old app's private storage.
- Complete session-expiry/guest-restriction coverage, registration/recovery verification, offline/retry cases, accessibility, gestures, process recreation, and physical-device checks.

## Recommended implementation order

1. Finish Apps: batch/library actions, release history, and broader matching coverage.
2. Native download pipeline and Mobilism Premium trial/plans/payment flows.
3. Comment replies/quotes, forum sections, private messages, and notifications.
4. Remaining settings/utilities, pure-Compose rich text, migration, and final parity testing.

Following checks inspect up to three Apps search pages per app. A failed check preserves prior results; a missing match does not prove there is no newer version. Android controls background timing. Notifications need permission and are sent once per version. Without an installed match, checks use the followed release as the baseline. The library stays local and does not sync with the forum account.

The largest remaining work is persistent downloads, forum and account features, and migration. Screen count alone is not a useful completion percentage.
