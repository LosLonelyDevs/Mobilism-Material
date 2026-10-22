# Rewrite feature parity

Source audit against the local legacy manifest, drawer and action menus, settings XML, API interface, and UI classes. This is an implementation inventory, not a claim that every old server endpoint still works.

## Implemented

- Login, guest entry, account registration/password recovery screens, sign-out, and encrypted session storage.
- Favourite starting section, Apps/Games/Books navigation, Latest/Top feeds, category multiselect, and pagination.
- Release details, formatted text, parsed title metadata, collapsible description/changelog/mod/download sections, and read-only paginated comments.
- Full-screen images, zoom/paging, architecture groups, preferred hosts, host links, clipboard actions, and browser opening.
- Material 3 Expressive components and motion, Frappé/Geist defaults, custom themes, appearance preferences, and reduced motion.
- Encrypted debrid keys and supported-host indicators. These do not yet resolve unrestricted links or download files.
- Native release search, section filters, paginated results, artwork, empty/error states, and retry.
- My Applications with launcher-app discovery, installed-app search, optional system apps, local watchlists and downloaded marks, removal and Undo.
- Manual watchlist checks against recent Apps search results, conservative version/build comparisons, package/name matching, TV separation, and stable/preview handling. Moving a watch preserves downloaded history.

## Remaining functional work

| Area | Missing behavior | Main legacy reference |
| --- | --- | --- |
| Search and discovery | Discussion/other-result search, legacy advanced search filters, and remaining feed filters | `UI/Main/SearchActivity.java`, `logic/network/MobilismService.java` |
| Release interaction | Post/reply/quote comments, likes, previous-version history, library actions beyond Apps | `UI/Release/ReleaseActivtiy.java`, `UI/Discussion/CommentActivity.java`, `res/menu/menu_releases.xml` |
| Forum sections | Announcements, Contests, Chitchat, Services, Suggestions & Feedback, discussion categories/topics/comments | `res/menu/activity_main_drawer.xml`, `UI/Discussion/` |
| My Applications | Automatic installed-version matching, non-launcher packages, batch actions, save/copy/export, legacy data import | `UI/MyApplications/`, installed/downloaded/favorites action menus |
| Version tracker | Background checks, notifications, full version history, installed-version baselines, matching ambiguous/unversioned releases | `UI/VersionTracker/` |
| Downloads | Native queue/progress, download state and files, destination selection, background operation, completion notifications, archive extraction, opening/installing APKs, failure/retry handling | `logic/download/`, `UI/Release/DownloadsActivity.java`, `UI/Settings/FilePickerActivity.java` |
| Premium and debrid | Mobilism Premium account/trial/plans/payment flows and link resolution; Real-Debrid/MegaDebrid unrestricted-link resolution and account/error handling | `UI/AMember/`, premium/payment API endpoints, legacy debrid integration |
| Messages | Message lists, conversation history, compose/reply, read state, deletion, unread notifications | `UI/Messages/`, message API endpoints |
| Settings and utilities | Notification preferences/sounds, content filters, cache controls, old listing options, download/extraction options, QR scanner, About, app-update flow | `res/xml/pref_general.xml`, `UI/QRCodeScanner/`, `UI/Settings/AboutActivity.java`, `logic/updater/` |

## Complete Compose and migration work

- Replace `ReleaseBody`'s `AndroidView`/`TextView` bridge with Compose rich text while retaining formatting, safe links, and text selection. Current screen layout and controls already use Compose.
- Apply the shared M3E components, semantic colors, motion preferences, and rounded shapes to each remaining screen.
- Add persistence and modern Android background, storage, package-visibility, install, and notification handling as their features land.
- Decide on legacy data import and package/signing continuity. The rewrite currently has a separate package and cannot silently inherit the old app's private storage.
- Complete session-expiry/guest-restriction coverage, registration/recovery verification, offline/retry cases, accessibility, gestures, process recreation, and physical-device checks.

## Recommended implementation order

1. Finish Apps: installed-version matching, batch/library actions, release history, and background update tracking.
2. Premium/debrid link resolution and the native download pipeline.
3. Comment writing, forum sections, private messages, and notifications.
4. Remaining settings/utilities, pure-Compose rich text, migration, and final parity testing.

Current watchlist checks inspect up to three Apps search pages per watched app. A failed check preserves prior results; no newer match is not a guarantee that a newer version does not exist. The library is local to this installation and does not synchronize with the forum account.

The largest remaining engineering work is persistent downloads, automatic installed-app/update tracking, and account/notification integration. Screen count alone is not a useful completion percentage.
